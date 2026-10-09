package app.blogsh.android

import app.blogsh.android.model.ActionAnswer
import app.blogsh.android.model.Blog
import app.blogsh.android.model.BlogShelf
import app.blogsh.android.model.DeliveryFile
import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.Markdown
import app.blogsh.android.model.Media
import app.blogsh.android.model.Outbox
import app.blogsh.android.model.Reach
import app.blogsh.android.model.Receipt
import app.blogsh.android.model.Refusal
import app.blogsh.android.model.ServerSettings
import app.blogsh.android.model.Shot
import app.blogsh.android.model.Unsent
import app.blogsh.android.model.Waiting
import app.blogsh.android.model.WaitingRoom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.util.UUID

private fun id(): String = UUID.randomUUID().toString()

/** A directory of the test's own, gone when the test is. */
private fun <T> room(body: (File) -> T): T {
    val home = java.nio.file.Files.createTempDirectory("waiting").toFile()
    try {
        return body(home)
    } finally {
        home.deleteRecursively()
    }
}

private fun moment(seconds: Long): Long = (1_780_000_000 + seconds) * 1000

private fun answer(slug: String): ActionAnswer = Engine.decode("""{"ok":true,"slug":"$slug"}""".toByteArray())

/**
 * Posts put by on the device until their blog can be reached. A mistake
 * here is writing that is lost, or a post sent without its pictures.
 */
class WaitingTest {
    /**
     * What was put by comes back whole: the words, the files under the
     * names the text has for them, and what a card in the form needs.
     */
    @Test
    fun aPostPutByComesBackWithItsPictures() = room { home ->
        val blog = id()
        val picture = Shot("photo.jpg", byteArrayOf(1, 2, 3), 40, 30)
        val video = Shot("clip.mp4", byteArrayOf(9, 8), 0, 0, kind = Shot.Kind.Video, poster = byteArrayOf(7), converted = false)
        val post = Waiting(title = "Hello", tags = "a, b", text = "Text\n\n![a hill](photo.jpg)\n\n!![](clip.mp4)", at = moment(0))
        WaitingRoom.put(post, listOf(picture, video), blog, home)

        val kept = WaitingRoom.all(blog, home)
        assertEquals(1, kept.size)
        assertEquals(post.id, kept[0].id)
        assertEquals("Hello", kept[0].title)
        assertEquals(listOf("photo.jpg", "clip.mp4"), kept[0].pieces.map { it.name })

        val shots = WaitingRoom.shots(kept[0], blog, home)
        assertEquals(listOf("photo.jpg", "clip.mp4"), shots.map { it.name })
        assertArrayEquals(byteArrayOf(1, 2, 3), shots[0].data)
        assertEquals(40, shots[0].width)
        assertEquals(30, shots[0].height)
        assertEquals(Shot.Kind.Video, shots[1].kind)
        assertArrayEquals(byteArrayOf(7), shots[1].poster)
        assertFalse(shots[1].converted)
    }

    /**
     * As a delivery: the pictures first, the markdown last -- and the
     * markdown dated by when the post was written, not by when it goes.
     */
    @Test
    fun aWaitingPostIsDeliveredDatedByWhenItWasWritten() = room { home ->
        val blog = id()
        val post = Waiting(title = "Hello there", text = "![x](photo.jpg)", at = moment(0))
        WaitingRoom.put(post, listOf(Shot("photo.jpg", byteArrayOf(1), 1, 1)), blog, home)
        val kept = WaitingRoom.all(blog, home)[0]
        val files = WaitingRoom.delivery(kept, blog, home)
        assertEquals(listOf("photo.jpg", "hello-there.md"), files.map { it.name })
        assertArrayEquals(byteArrayOf(1), files[0].data)
        val markdown = String(files[1].data, Charsets.UTF_8)
        // ...and under the receipt it was given when it was put by.
        val receipt = checkNotNull(kept.receipt)
        assertTrue(markdown, markdown.startsWith("---\ntitle: Hello there\ndate: ${Markdown.stamp(moment(0))}\nreceipt: $receipt\n---\n\n"))
        assertTrue(markdown, markdown.endsWith("![x](photo.jpg)\n"))
    }

    /** The date in the header is a moment with its offset, to the second. */
    @Test
    fun theWrittenDateCarriesItsOffset() {
        assertEquals("2026-05-28T22:26:40+02:00", Markdown.stamp(1_780_000_000_000, ZoneId.of("Europe/Prague")))
        assertEquals("2026-05-28T22:26:40+02:00", Markdown.stamp(1_780_000_000_999, ZoneId.of("Europe/Prague")))
        assertEquals("", Markdown.frontMatter("", ""))
    }

    /**
     * The time a schedule sends is written in the year that was picked:
     * the engine files and addresses the post by the year as written.
     */
    @Test
    fun aScheduledTimeIsWrittenInTheYearThatWasPicked() {
        val prague = ZoneId.of("Europe/Prague")
        val picked = java.time.ZonedDateTime.of(2027, 1, 1, 0, 15, 0, 0, prague).toInstant().toEpochMilli()
        assertEquals("2027-01-01T00:15:00+01:00", Markdown.stamp(picked, prague))
        // West of Greenwich the last hours of a year are already the next in UTC.
        val lima = ZoneId.of("America/Lima")
        val late = java.time.ZonedDateTime.of(2026, 12, 31, 23, 45, 0, 0, lima).toInstant().toEpochMilli()
        assertEquals("2026-12-31T23:45:00-05:00", Markdown.stamp(late, lima))
    }

    /** Any number wait, each blog's own, in the order they were written. */
    @Test
    fun postsWaitInTheOrderTheyWereWritten() = room { home ->
        val blog = id()
        val other = id()
        WaitingRoom.put(Waiting(title = "second", at = moment(60)), emptyList(), blog, home)
        WaitingRoom.put(Waiting(title = "first", at = moment(0)), emptyList(), blog, home)
        WaitingRoom.put(Waiting(title = "elsewhere", at = moment(30)), emptyList(), other, home)
        assertEquals(listOf("first", "second"), WaitingRoom.all(blog, home).map { it.title })
        assertEquals(listOf("elsewhere"), WaitingRoom.all(other, home).map { it.title })
        assertTrue(WaitingRoom.all(id(), home).isEmpty())
    }

    /**
     * Sent or thrown away, a post is gone with its files; a blog that
     * leaves the app takes all of its own, and nobody else's.
     */
    @Test
    fun whatIsSentOrThrownAwayWaitsNoLonger() = room { home ->
        val blog = id()
        val other = id()
        val one = Waiting(title = "one", at = moment(0))
        val two = Waiting(title = "two", at = moment(1))
        WaitingRoom.put(one, listOf(Shot("a.jpg", byteArrayOf(1), 1, 1)), blog, home)
        WaitingRoom.put(two, emptyList(), blog, home)
        WaitingRoom.put(Waiting(title = "kept", at = moment(2)), emptyList(), other, home)

        WaitingRoom.remove(one.id, blog, home)
        assertEquals(listOf("two"), WaitingRoom.all(blog, home).map { it.title })
        assertFalse(File(File(home, blog), one.id).exists())

        WaitingRoom.removeAll(blog, home)
        assertTrue(WaitingRoom.all(blog, home).isEmpty())
        assertEquals(listOf("kept"), WaitingRoom.all(other, home).map { it.title })
    }

    /** What the blog said to a post it would not take stays with the post. */
    @Test
    fun aRefusalIsKeptWithThePost() = room { home ->
        val blog = id()
        val post = Waiting(title = "one", at = moment(0))
        WaitingRoom.put(post, emptyList(), blog, home)
        WaitingRoom.note("Too large.", post.id, blog, home)
        assertEquals("Too large.", WaitingRoom.all(blog, home)[0].problem)
        WaitingRoom.note(null, post.id, blog, home)
        assertNull(WaitingRoom.all(blog, home)[0].problem)
    }

    /** A name is a file of the post's own directory, whatever it says. */
    @Test
    fun aNameCannotLeaveThePostsDirectory() = room { home ->
        val blog = id()
        val post = Waiting(title = "one", at = moment(0))
        WaitingRoom.put(post, listOf(Shot("../../escaped.jpg", byteArrayOf(1), 1, 1)), blog, home)
        assertFalse(File(home, "escaped.jpg").exists())
        assertFalse(File(File(home, blog), "escaped.jpg").exists())
        assertEquals(1, WaitingRoom.shots(WaitingRoom.all(blog, home)[0], blog, home).size)
    }

    /** It is called what the post being written would be called. */
    @Test
    fun aWaitingPostIsCalledByItsTitleOrItsFirstWords() {
        assertEquals("Hello", Waiting(title = " Hello ", text = "x").headline)
        assertEquals("First words", Waiting(text = "![a](b.jpg)\n\n# First words\nmore").headline)
    }

    /** A receipt is what the engine takes for one, and no two posts share one. */
    @Test
    fun aReceiptIsSixteenLowercaseHexDigitsOfItsOwn() {
        val one = Receipt.mint()
        val two = Receipt.mint()
        assertTrue(one, Regex("^[0-9a-f]{16}$").matches(one))
        assertNotEquals(one, two)
        assertFalse(Receipt.isOne("ABCDEF0123456789"))
        assertFalse(Receipt.isOne("abc"))
        assertFalse(Receipt.isOne("0123456789abcdef\nx: y"))
        assertEquals("---\ntitle: T\nreceipt: 0123456789abcdef\n---\n\n", Markdown.frontMatter("T", "", receipt = "0123456789abcdef"))
        // What is not a receipt is not written: the engine refuses a bad one.
        assertEquals("---\ntitle: T\n---\n\n", Markdown.frontMatter("T", "", receipt = "nope"))
    }

    /**
     * The post being written keeps its receipt from one opening of the
     * form to the next, and takes it along when it is put by and back.
     */
    @Test
    fun aPostKeepsItsReceiptWhereverItWaits() = room { home ->
        val notes = MemoryNotes()
        val blog = id()
        Unsent("T", "", "x", moment(0), receipt = "0123456789abcdef").keep(blog, notes)
        assertEquals("0123456789abcdef", Unsent.kept(blog, notes)?.receipt)

        WaitingRoom.put(Waiting(title = "T", text = "x", at = moment(0), receipt = "0123456789abcdef"), emptyList(), blog, home)
        assertEquals("0123456789abcdef", WaitingRoom.all(blog, home)[0].receipt)
        // One put by without a name is given one, written down with it.
        WaitingRoom.put(Waiting(title = "U", at = moment(1)), emptyList(), blog, home)
        val given = WaitingRoom.all(blog, home)[1].receipt
        assertTrue(Receipt.isOne(given ?: ""))
        assertEquals(given, WaitingRoom.all(blog, home)[1].receipt)
    }
}

/**
 * A post taken back into the form: its pictures stay in its files for as
 * long as the form has it. Before, they were deleted on the way in and
 * lived in the form's memory alone.
 */
class HeldTest {
    /**
     * Held, a post is not listed and so not sent -- and is all there:
     * its words and its pictures, to be found again by its name.
     */
    @Test
    fun aHeldPostIsNotListedAndKeepsItsPictures() = room { home ->
        val blog = id()
        val post = Waiting(title = "Hello", text = "![x](photo.jpg)", at = moment(0))
        WaitingRoom.put(post, listOf(Shot("photo.jpg", byteArrayOf(1, 2, 3), 4, 3)), blog, home)
        WaitingRoom.put(Waiting(title = "Other", at = moment(1)), emptyList(), blog, home)

        WaitingRoom.hold(post.id, blog, home)

        assertEquals(listOf("Other"), WaitingRoom.all(blog, home).map { it.title })
        val held = checkNotNull(WaitingRoom.one(post.id, blog, home))
        assertEquals(true, held.held)
        assertEquals("Hello", held.title)
        val shots = WaitingRoom.shots(held, blog, home)
        assertEquals(listOf("photo.jpg"), shots.map { it.name })
        assertArrayEquals(byteArrayOf(1, 2, 3), shots.first().data)
    }

    /** A held post the form no longer has waits again; the one it has stays held. */
    @Test
    fun aHeldPostNobodyHoldsWaitsAgain() = room { home ->
        val blog = id()
        val other = id()
        val one = Waiting(title = "one", at = moment(0))
        val two = Waiting(title = "two", at = moment(1))
        WaitingRoom.put(one, emptyList(), blog, home)
        WaitingRoom.put(two, emptyList(), blog, home)
        WaitingRoom.put(Waiting(title = "elsewhere", at = moment(2)), emptyList(), other, home)
        WaitingRoom.hold(one.id, blog, home)
        WaitingRoom.hold(two.id, blog, home)

        assertTrue(WaitingRoom.release(blog, except = two.id, home = home))
        assertEquals(listOf("one"), WaitingRoom.all(blog, home).map { it.title })
        assertEquals(true, WaitingRoom.one(two.id, blog, home)?.held)

        assertTrue(WaitingRoom.release(blog, home = home))
        assertEquals(listOf("one", "two"), WaitingRoom.all(blog, home).map { it.title })
        assertEquals(listOf("elsewhere"), WaitingRoom.all(other, home).map { it.title })
        // Nothing held: nothing to let go of.
        assertFalse(WaitingRoom.release(blog, home = home))
    }

    /** Sent, put by anew or thrown away by the form, the held post goes with its files. */
    @Test
    fun aHeldPostGoesWhenTheFormIsDoneWithIt() = room { home ->
        val blog = id()
        val post = Waiting(title = "one", at = moment(0))
        WaitingRoom.put(post, listOf(Shot("a.jpg", byteArrayOf(1), 1, 1)), blog, home)
        WaitingRoom.hold(post.id, blog, home)
        WaitingRoom.remove(post.id, blog, home)
        assertNull(WaitingRoom.one(post.id, blog, home))
        assertFalse(File(File(home, blog), post.id).exists())
    }

    /**
     * The writing in the form remembers which post it was taken back
     * from, from one opening of the form to the next.
     */
    @Test
    fun theWritingRemembersThePostItCameFrom() {
        val notes = MemoryNotes()
        val blog = id()
        val post = id()
        Unsent("T", "", "x", moment(0), from = post).keep(blog, notes)
        assertEquals(post, Unsent.kept(blog, notes)?.from)
        // The same words under another origin are written down again.
        Unsent("T", "", "x", moment(5), from = null).keep(blog, notes)
        assertNull(Unsent.kept(blog, notes)?.from)
    }

    /** "Its pictures were not kept" is said only of pictures the form does not have back. */
    @Test
    fun picturesTheFormHasBackAreNotSaidToBeMissing() {
        val kept = Unsent(text = "![a](photo-1.jpg \"One.\")\n\n![b](photo-2.jpg)")
        assertFalse(kept.namesPictures(beyond = listOf("photo-1.jpg", "photo-2.jpg")))
        assertTrue(kept.namesPictures(beyond = listOf("photo-1.jpg")))
        assertTrue(kept.namesPictures(beyond = emptyList()))
        assertFalse(Unsent(text = "no pictures").namesPictures(beyond = emptyList()))
    }

    /** A held post is not on its way out: the run that sends what waits passes it by. */
    @Test
    fun aHeldPostIsNotSent() = room { home ->
        runBlocking {
            val blog = id()
            val got = mutableListOf<List<String>>()
            val held = Waiting(title = "held", at = moment(0))
            WaitingRoom.put(held, emptyList(), blog, home)
            WaitingRoom.put(Waiting(title = "waits", at = moment(1)), emptyList(), blog, home)
            WaitingRoom.hold(held.id, blog, home)
            val outbox = Outbox({ home }, { blog }) { files, _ ->
                got.add(files.map { it.name })
                answer("a-draft")
            }
            assertEquals(1, outbox.sendAll(blog))
            assertEquals(listOf(listOf("waits.md")), got)
            assertEquals("held", WaitingRoom.one(held.id, blog, home)?.title)
        }
    }
}

/**
 * Posts on their way from the device to the blog. A mistake here is a
 * post sent twice, sent to the wrong blog, or lost on the way.
 */
class OutboxTest {
    /** A blog that answers as it is told to, and remembers what it was sent. */
    private class Site {
        val answers = ArrayDeque<Result<String>>()
        val got = mutableListOf<List<String>>()
        val whose = mutableListOf<String>()
        val markdown = mutableListOf<String>()

        fun take(files: List<DeliveryFile>, blog: String) {
            got.add(files.map { it.name })
            whose.add(blog)
            markdown.add(String(files.lastOrNull()?.data ?: ByteArray(0), Charsets.UTF_8))
        }

        fun deliver(files: List<DeliveryFile>, blog: String): ActionAnswer {
            take(files, blog)
            return answer((answers.removeFirstOrNull() ?: Result.success("a-draft")).getOrThrow())
        }
    }

    private fun refusal(error: String, message: String) = EngineError.Refused(Refusal(false, error, message))

    /** A post that arrived waits no longer, and went with its pictures first. */
    @Test
    fun aPostThatArrivedWaitsNoLonger() = room { home ->
        runBlocking {
            val id = id()
            val site = Site()
            val post = Waiting(title = "Hello", text = "![x](photo.jpg)", at = moment(0))
            WaitingRoom.put(post, listOf(Shot("photo.jpg", byteArrayOf(1), 1, 1)), id, home)
            val outbox = Outbox({ home }, { id }) { files, blog -> site.deliver(files, blog) }
            assertEquals(Outbox.Outcome.Sent("a-draft"), outbox.send(WaitingRoom.all(id, home)[0], id))
            assertEquals(listOf(listOf("photo.jpg", "hello.md")), site.got)
            assertTrue(WaitingRoom.all(id, home).isEmpty())
            assertNull(outbox.sending)
        }
    }

    /**
     * A silent server refuses nothing: the post still waits, with no
     * reason written on it, and the ones after it are not tried.
     */
    @Test
    fun aSilentServerLeavesEverythingWaiting() = room { home ->
        runBlocking {
            val id = id()
            val site = Site()
            site.answers.add(Result.failure(EngineError.Unreachable("one.example", "timeout")))
            WaitingRoom.put(Waiting(title = "one", at = moment(0)), emptyList(), id, home)
            WaitingRoom.put(Waiting(title = "two", at = moment(1)), emptyList(), id, home)
            val outbox = Outbox({ home }, { id }) { files, blog -> site.deliver(files, blog) }
            assertEquals(0, outbox.sendAll(id))
            assertEquals(listOf(listOf("one.md")), site.got)
            assertEquals(listOf("one", "two"), WaitingRoom.all(id, home).map { it.title })
            assertTrue(WaitingRoom.all(id, home).all { it.problem == null })
        }
    }

    /**
     * What the blog turned away is kept with its reason and not sent
     * again unasked; the posts after it go.
     */
    @Test
    fun aRefusedPostIsKeptWithItsReasonAndTheRestGo() = room { home ->
        runBlocking {
            val id = id()
            val site = Site()
            site.answers.add(Result.failure(refusal("too_large", "Too large.")))
            site.answers.add(Result.success("two"))
            WaitingRoom.put(Waiting(title = "one", at = moment(0)), emptyList(), id, home)
            WaitingRoom.put(Waiting(title = "two", at = moment(1)), emptyList(), id, home)
            val outbox = Outbox({ home }, { id }) { files, blog -> site.deliver(files, blog) }
            assertEquals(1, outbox.sendAll(id))
            val left = WaitingRoom.all(id, home)
            assertEquals(listOf("one"), left.map { it.title })
            assertEquals("Too large.", left[0].problem)
            // Asked again by itself, it leaves the refused one alone...
            assertEquals(0, outbox.sendAll(id))
            assertEquals(2, site.got.size)
            // ...and sent by hand, it goes.
            assertEquals(Outbox.Outcome.Sent("a-draft"), outbox.send(left[0], id))
            assertTrue(WaitingRoom.all(id, home).isEmpty())
        }
    }

    /** What was written for one blog is never sent with another open. */
    @Test
    fun nothingIsSentWithAnotherBlogOpen() = room { home ->
        runBlocking {
            val id = id()
            val site = Site()
            WaitingRoom.put(Waiting(title = "one", at = moment(0)), emptyList(), id, home)
            val outbox = Outbox({ home }, { id() }) { files, blog -> site.deliver(files, blog) }
            assertEquals(0, outbox.sendAll(id))
            assertTrue(site.got.isEmpty())
            assertEquals(1, WaitingRoom.all(id, home).size)
        }
    }

    /** They go in the order they were written. */
    @Test
    fun postsGoInTheOrderTheyWereWritten() = room { home ->
        runBlocking {
            val id = id()
            val site = Site()
            WaitingRoom.put(Waiting(title = "later", at = moment(60)), emptyList(), id, home)
            WaitingRoom.put(Waiting(title = "sooner", at = moment(0)), emptyList(), id, home)
            val outbox = Outbox({ home }, { id }) { files, blog -> site.deliver(files, blog) }
            assertEquals(2, outbox.sendAll(id))
            assertEquals(listOf(listOf("sooner.md"), listOf("later.md")), site.got)
        }
    }

    /** A delivery's answers: the last is the engine's own, a no among them is thrown. */
    @Test
    fun aDeliverysAnswersAreReadToTheLastOrTheFirstNo() {
        val fine = """{"ok":true}""".toByteArray()
        val made = """{"ok":true,"slug":"hello"}""".toByteArray()
        assertEquals("hello", Engine.made(listOf(fine, made)).slug)
        val no = """{"ok":false,"error":"too_large","message":"Too large."}""".toByteArray()
        assertTrue(runCatching { Engine.made(listOf(no, made)) }.exceptionOrNull() is EngineError.Refused)
        assertTrue(runCatching { Engine.made(emptyList()) }.exceptionOrNull() is EngineError)
    }

    /** Only the open blog is connected to, when a call says whose it is. */
    @Test
    fun aCallMeantForOneBlogFindsNoOther() {
        val notes = MemoryNotes()
        val blog = Blog(host = "one.example", user = "dan")
        BlogShelf.write(listOf(blog), blog.id, notes)
        assertEquals("one.example", ServerSettings.load(notes, only = blog.id)?.host)
        assertNull(ServerSettings.load(notes, only = id()))
        assertEquals("one.example", ServerSettings.load(notes)?.host)
    }

    /**
     * A post thrown away while an earlier one is on its way is not sent
     * from what the run remembered of the room.
     */
    @Test
    fun aPostThrownAwayWhileAnotherIsOnItsWayIsNotSent() = room { home ->
        runBlocking {
            val id = id()
            val site = Site()
            val one = Waiting(title = "one", at = moment(0))
            val two = Waiting(title = "two", at = moment(1))
            WaitingRoom.put(one, emptyList(), id, home)
            WaitingRoom.put(two, emptyList(), id, home)
            val gate = CompletableDeferred<Unit>()
            val outbox = Outbox({ home }, { id }) { files, blog ->
                site.take(files, blog)
                // The first upload takes its time: somebody is in the list meanwhile.
                if (site.got.size == 1) gate.await()
                answer("a-draft")
            }
            val run = async { outbox.sendAll(id) }
            while (site.got.isEmpty()) yield()
            assertEquals(one.id, outbox.sending)
            // The list's "Throw away", after "It was never sent; nothing of it is kept."
            WaitingRoom.remove(two.id, id, home)
            gate.complete(Unit)
            assertEquals(1, run.await())
            assertEquals(listOf(listOf("one.md")), site.got)
        }
    }

    /**
     * A blog opened while another blog's post is on its way has its own
     * posts sent, after it: nobody else would ask again.
     */
    @Test
    fun aBlogOpenedWhileAnothersPostIsOnItsWayHasItsOwnPostsSent() = room { home ->
        runBlocking {
            val a = id()
            val b = id()
            val site = Site()
            var current = a
            WaitingRoom.put(Waiting(title = "of a", at = moment(0)), emptyList(), a, home)
            WaitingRoom.put(Waiting(title = "of b", at = moment(1)), emptyList(), b, home)
            val gate = CompletableDeferred<Unit>()
            val outbox = Outbox({ home }, { current }) { files, blog ->
                site.take(files, blog)
                if (site.got.size == 1) gate.await()
                answer("a-draft")
            }
            val first = async { outbox.sendAll(a) }
            while (site.got.isEmpty()) yield()
            current = b
            val second = async { outbox.sendAll(b) }
            repeat(50) { yield() }
            gate.complete(Unit)
            first.await()
            second.await()
            assertEquals(listOf(a, b), site.whose)
            assertTrue(WaitingRoom.all(a, home).isEmpty())
            assertTrue(WaitingRoom.all(b, home).isEmpty())
        }
    }

    /**
     * The server has the whole delivery and the app is gone before the
     * answer. The next launch sends the post again -- under the same
     * receipt, by which the engine knows it for the one it has.
     */
    @Test
    fun aPostTheServerTookBeforeTheAppDiedGoesAgainUnderTheSameReceipt() = room { home ->
        runBlocking {
            val id = id()
            val site = Site()
            WaitingRoom.put(Waiting(title = "one", text = "Words.", at = moment(0)), emptyList(), id, home)
            val gate = CompletableDeferred<Unit>()
            // The run that was cut off: everything was written, the answer never came.
            val before = Outbox({ home }, { id }) { files, blog ->
                site.take(files, blog)
                gate.await()
                throw CancellationException()
            }
            val cut = async { before.sendAll(id) }
            while (site.got.isEmpty()) yield()
            // The next launch.
            val after = Outbox({ home }, { id }) { files, blog ->
                site.take(files, blog)
                answer("one")
            }
            after.sendAll(id)
            gate.complete(Unit)
            cut.await()
            val receipts = site.markdown.map { text -> text.lines().firstOrNull { it.startsWith("receipt: ") }?.drop(9) }
            assertEquals(2, receipts.size)
            assertTrue(receipts[0] != null && receipts[0] == receipts[1])
            assertTrue(Receipt.isOne(receipts[0] ?: ""))
        }
    }

    /**
     * The blog is busy -- another delivery is being taken, the site is
     * being built: that is not a no to the post. It waits without a
     * reason written on it, and goes with the next asking.
     */
    @Test
    fun aBusyBlogLeavesThePostWaiting() = room { home ->
        runBlocking {
            val id = id()
            val site = Site()
            WaitingRoom.put(Waiting(title = "one", at = moment(0)), emptyList(), id, home)
            var busy = true
            val outbox = Outbox({ home }, { id }) { files, blog ->
                site.take(files, blog)
                if (busy) throw refusal("busy", "Another delivery is being taken.")
                answer("one")
            }
            assertEquals(0, outbox.sendAll(id))
            assertEquals(listOf<String?>(null), WaitingRoom.all(id, home).map { it.problem })
            busy = false
            assertEquals(1, outbox.sendAll(id))
            assertTrue(WaitingRoom.all(id, home).isEmpty())
        }
    }
}

/** Whether a blog is within reach: what the engine's calls said of its server. */
class ReachTest {
    private fun blog(host: String, port: Int = 22) = Blog(host = host, port = port, user = "dan")

    /** A server nobody answered at is silent until a call gets through again. */
    @Test
    fun aSilentServerIsOfflineUntilItIsHeardFrom() {
        val reach = Reach()
        val one = blog("one.example")
        assertFalse(reach.isOffline(one))
        reach.nothing(Reach.server("one.example", 22))
        assertTrue(reach.isOffline(one))
        reach.heard(Reach.server("one.example", 22))
        assertFalse(reach.isOffline(one))
    }

    /**
     * The server is what is silent: two blogs behind one address share
     * it, a blog elsewhere -- or on another port of the same host -- does not.
     */
    @Test
    fun blogsOnOneServerShareItsSilence() {
        val reach = Reach()
        reach.nothing(Reach.server("one.example", 202))
        assertTrue(reach.isOffline(blog("one.example", 202)))
        assertTrue(reach.isOffline(blog(" One.Example ", 202)))
        assertFalse(reach.isOffline(blog("one.example")))
        assertFalse(reach.isOffline(blog("two.example", 202)))
        assertFalse(reach.isOffline(null))
    }

    /** A blog whose port was never written down is on ssh's own. */
    @Test
    fun noPortIsTheUsualOne() {
        assertEquals(Reach.server("one.example", 22), Reach.server("one.example", 0))
    }

    /** What is said of a silent server names it, and is not the library's English. */
    @Test
    fun aSilentServerIsNamedInPlainWords() {
        English.speak()
        val words = EngineError.Unreachable("one.example", "java.net.ConnectException: Connection refused").message
        assertTrue(words, words.contains("one.example"))
        assertFalse(words, words.contains("ConnectException"))
    }

    /** The library saying it needs the network is that, whatever the device thinks of its own. */
    @Test
    fun theLibrarysOwnWordForTheNetworkIsBelieved() {
        assertEquals(Media.Unread.NeedsNetwork, Media.unread(java.net.UnknownHostException("photos.example"), online = true))
        val wrapped = java.io.FileNotFoundException("no such file").apply { initCause(java.net.SocketTimeoutException("timeout")) }
        assertEquals(Media.Unread.NeedsNetwork, Media.unread(wrapped, online = true))
        assertEquals(Media.Unread.NeedsNetwork, Media.unread(java.net.ConnectException("refused"), online = true))
    }

    /**
     * Any failure on a device with no network is most likely the same
     * thing; with a network, it is a picture that could not be read and
     * no more.
     */
    @Test
    fun withoutANetworkAFailureIsTakenForIt() {
        val other = java.io.FileNotFoundException("no such file")
        assertEquals(Media.Unread.NeedsNetwork, Media.unread(other, online = false))
        assertEquals(Media.Unread.NeedsNetwork, Media.unread(null, online = false))
        assertEquals(Media.Unread.Unreadable, Media.unread(other, online = true))
        assertEquals(Media.Unread.Unreadable, Media.unread(null, online = true))
    }
}
