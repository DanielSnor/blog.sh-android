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
}
