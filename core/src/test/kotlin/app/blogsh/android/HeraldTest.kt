package app.blogsh.android

import app.blogsh.android.model.EngineError
import app.blogsh.android.model.Herald
import app.blogsh.android.model.Refusal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The site built by the app itself. A mistake here is a site left
 * behind what was changed, a build after every keypress, or a failure
 * nobody is told of.
 */
class HeraldTest {
    /** Counts the builds, and can be told how the next one ends. */
    private class Site {
        @Volatile var builds = 0
        val fails = ArrayDeque<Throwable>()
        @Volatile var slow = false
        @Volatile var open: String? = "one"

        suspend fun build() {
            builds += 1
            if (slow) delay(120)
            fails.removeFirstOrNull()?.let { throw it }
        }
    }

    // One thread, as the screens have one: what the herald does it does in turn.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    @After
    fun stop() = scope.cancel()

    private fun herald(site: Site) =
        Herald(pause = 50, retry = 50, open = { site.open }, rebuild = { site.build() }, scope = scope, plainly = { "the site does not show this yet" })

    /** Waits for something to come true; false when it never did. */
    private fun until(what: () -> Boolean): Boolean = runBlocking {
        repeat(400) {
            if (what()) return@runBlocking true
            delay(25)
        }
        what()
    }

    private fun pause(ms: Long) = runBlocking { delay(ms) }

    @Test
    fun aChangeIsBuiltOnceAfterAMoment() {
        val site = Site()
        val herald = herald(site)
        herald.owe()
        assertEquals(Herald.Build.Owed, herald.build)
        assertEquals(0, site.builds)
        assertTrue(until { herald.build == Herald.Build.None })
        assertEquals(1, site.builds)
    }

    /** Several changes in a row -- a queue being put in order -- are one build. */
    @Test
    fun changesInARowAreOneBuild() {
        val site = Site()
        val herald = herald(site)
        repeat(5) { herald.owe() }
        assertEquals(0, site.builds)
        assertTrue(until { herald.build == Herald.Build.None })
        pause(200)
        assertEquals(1, site.builds)
    }

    /** A change made while a build runs may have missed it: built once more. */
    @Test
    fun aChangeDuringABuildIsBuiltAgain() {
        val site = Site()
        val herald = herald(site)
        site.slow = true
        herald.owe()
        assertTrue(until { herald.isBuilding })
        herald.owe()
        assertTrue(herald.isBuilding)
        assertTrue(until { herald.build == Herald.Build.None })
        assertEquals(2, site.builds)
    }

    /** A publish builds the whole site: what was owed is paid without a build. */
    @Test
    fun whatSomethingElseBuiltIsNotBuiltAgain() {
        val site = Site()
        val herald = herald(site)
        herald.owe()
        herald.settled()
        assertEquals(Herald.Build.None, herald.build)
        pause(300)
        assertEquals(0, site.builds)
    }

    /** The engine busy with something else: the same wish a little later, not a failure. */
    @Test
    fun aBusyEngineIsAskedAgain() {
        val site = Site()
        val herald = herald(site)
        site.fails.add(EngineError.Refused(Refusal(ok = false, error = "busy", message = "busy")))
        herald.owe()
        assertTrue(until { site.builds == 2 && herald.build == Herald.Build.None })
        assertEquals(2, site.builds)
    }

    @Test
    fun aBuildThatFailsSaysSoAndIsPutAwayWhenRead() {
        val site = Site()
        val herald = herald(site)
        site.fails.add(EngineError.Refused(Refusal(ok = false, error = "rebuild_failed", message = "no disk")))
        herald.owe()
        assertTrue(until { herald.build is Herald.Build.Failed })
        assertTrue((herald.build as Herald.Build.Failed).why.contains("no disk"))
        assertEquals(1, site.builds)
        herald.acknowledge()
        assertEquals(Herald.Build.None, herald.build)
    }

    /**
     * Another blog opened before the build ran: its engine is not asked
     * to build for a change it never had.
     */
    @Test
    fun anotherBlogsEngineIsNotAsked() {
        val site = Site()
        val herald = herald(site)
        herald.owe()
        site.open = "another"
        assertTrue(until { herald.build == Herald.Build.None })
        assertEquals(0, site.builds)
    }

    @Test
    fun whatIsOwedIsSaidInTheWordsGiven() {
        val herald = herald(Site())
        herald.owe("the queue")
        assertEquals("the queue", herald.owedFor)
        herald.owe()
        assertFalse(herald.owedFor.isEmpty() || herald.owedFor == "the queue")
    }

    @Test
    fun whatWasDoneIsSaid() {
        val herald = herald(Site())
        herald.say("")
        assertNull(herald.note)
        herald.say("Published")
        assertEquals("Published", herald.note)
    }

    /**
     * A build owed to a blog is not forgotten because another blog was
     * opened before it ran: it waits, and runs once its blog is open again.
     */
    @Test
    fun aBuildOwedToABlogWaitsForItWhileAnotherIsOpen() {
        val site = Site()
        val herald = herald(site)
        herald.owe("a post deleted")
        // Blog B is opened before the pause is over, as the first screen says it.
        site.open = "two"
        herald.opened("two")
        assertEquals(Herald.Build.None, herald.build)
        pause(300)
        assertEquals(0, site.builds)
        // Back at A: owed again, in the words it was owed in, and built.
        site.open = "one"
        herald.opened("one")
        assertEquals(Herald.Build.Owed, herald.build)
        assertEquals("a post deleted", herald.owedFor)
        assertTrue(until { site.builds == 1 && herald.build == Herald.Build.None })
    }

    /**
     * The same, where nobody said the blog was changed before the pause
     * ran out: the build that finds another blog open keeps the debt.
     */
    @Test
    fun aBuildThatFindsAnotherBlogOpenKeepsItsDebt() {
        val site = Site()
        val herald = herald(site)
        herald.owe()
        site.open = "two"
        pause(200)
        assertTrue(until { herald.build == Herald.Build.None })
        assertEquals(0, site.builds)
        site.open = "one"
        herald.opened("one")
        assertTrue(until { site.builds == 1 && herald.build == Herald.Build.None })
    }

    /** The other blog's own debt is its own: each is built once, for itself. */
    @Test
    fun twoBlogsDebtsAreKeptApart() {
        val site = Site()
        val herald = herald(site)
        herald.owe("of a")
        site.open = "two"
        herald.opened("two")
        herald.owe("of b")
        assertTrue(until { site.builds == 1 && herald.build == Herald.Build.None })
        site.open = "one"
        herald.opened("one")
        assertEquals("of a", herald.owedFor)
        assertTrue(until { site.builds == 2 && herald.build == Herald.Build.None })
        // Nothing is left over for either.
        site.open = "two"
        herald.opened("two")
        assertEquals(Herald.Build.None, herald.build)
    }

    /**
     * The herald's own build failed; then something else built the whole
     * site. "The site could not be built" has nothing left to say.
     */
    @Test
    fun aFailedBuildIsPutAwayOnceSomethingElseBuiltTheSite() {
        val site = Site()
        val herald = herald(site)
        site.fails.add(EngineError.Refused(Refusal(ok = false, error = "rebuild_failed", message = "no disk")))
        herald.owe()
        assertTrue(until { herald.build is Herald.Build.Failed })
        herald.settled()
        assertEquals(Herald.Build.None, herald.build)
    }

    /**
     * An action's answer came after another blog was opened: the build
     * it owes is the blog's it was made on. The open blog is not built
     * for it, and the debt waits for its own.
     */
    @Test
    fun aDebtWhoseAnswerCameLateIsItsOwnBlogs() {
        val site = Site()
        val herald = herald(site)
        // The delete was sent on A; B was opened before it answered.
        site.open = "two"
        herald.opened("two")
        herald.owe("a post deleted", owed = "one")
        assertEquals(Herald.Build.None, herald.build)
        pause(300)
        assertEquals(0, site.builds)
        site.open = "one"
        herald.opened("one")
        assertEquals("a post deleted", herald.owedFor)
        assertTrue(until { site.builds == 1 && herald.build == Herald.Build.None })
    }

    /**
     * A publish on another blog built that blog's site: what the open
     * blog is owed is still owed, and what waited for the other is paid.
     */
    @Test
    fun anotherBlogsBuildPaysNothingOfTheOpenOnes() {
        val site = Site()
        val herald = herald(site)
        herald.owe("of a", owed = "one")
        herald.settled("two")
        assertEquals(Herald.Build.Owed, herald.build)
        assertTrue(until { site.builds == 1 && herald.build == Herald.Build.None })
        // A debt parked for B is paid by B's own build, said while A is open.
        herald.owe("of b", owed = "two")
        herald.settled("two")
        site.open = "two"
        herald.opened("two")
        assertEquals(Herald.Build.None, herald.build)
        pause(200)
        assertEquals(1, site.builds)
    }
}
