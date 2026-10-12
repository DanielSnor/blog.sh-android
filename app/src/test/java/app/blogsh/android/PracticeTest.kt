package app.blogsh.android

import app.blogsh.android.model.ActionAnswer
import app.blogsh.android.model.Blog
import app.blogsh.android.model.BlogList
import app.blogsh.android.model.DiagnosisAnswer
import app.blogsh.android.model.EditAnswer
import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.EngineJson
import app.blogsh.android.model.HeldAnswer
import app.blogsh.android.model.ListAnswer
import app.blogsh.android.model.PairedDevices
import app.blogsh.android.model.Practice
import app.blogsh.android.model.PropsAnswer
import app.blogsh.android.model.QueueAnswer
import app.blogsh.android.model.Refusal
import app.blogsh.android.model.StatsAnswer
import app.blogsh.android.model.VersionAnswer
import app.blogsh.android.model.VersionsAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The practice blog answers what the app asks a blog, in the shapes the
 * engine gives them -- they were said by one -- and says no to whatever
 * would write. A mistake here is a first look at the app that ends in an
 * error, or a blog that says "published" over a list that stayed as it was.
 */
class PracticeTest {
    @Before
    fun words() = English.speak()

    private fun refused(data: ByteArray): Boolean =
        runCatching { EngineJson.decodeFromString(Refusal.serializer(), String(data, Charsets.UTF_8)) }.getOrNull()?.error == "practice"

    /**
     * Everything the first screen and the lists ask for is read as the
     * app reads a real blog's answers.
     */
    @Test
    fun whatTheScreensAskForIsAnswered() {
        val version = Engine.decode<VersionAnswer>(Practice.answer(listOf("version")))
        assertEquals("Practice blog", version.site.name)
        assertTrue(version.site.url.isEmpty())
        assertEquals(7, Engine.decode<ListAnswer>(Practice.answer(listOf("list"))).posts.size)
        assertEquals(2, Engine.decode<ListAnswer>(Practice.answer(listOf("list", "--drafts"))).posts.size)
        assertTrue(Engine.decode<QueueAnswer>(Practice.answer(listOf("queue"))).queue.isEmpty())
        Engine.decode<StatsAnswer>(Practice.answer(listOf("stats")))
        Engine.decode<HeldAnswer>(Practice.answer(listOf("empty", "trash")))
        Engine.decode<HeldAnswer>(Practice.answer(listOf("empty", "versions")))
        assertEquals(0, Engine.decode<DiagnosisAnswer>(Practice.answer(listOf("check"))).errors)
        assertEquals(1, Engine.decode<DiagnosisAnswer>(Practice.answer(listOf("doctor"))).findings.size)
        val devices = Engine.decode<PairedDevices>(Practice.answer(listOf("pair", "--list")))
        assertTrue(devices.devices.isEmpty() && !devices.mayLeave)
        // What the trash holds is listed too: one post was thrown away there.
        assertEquals(1, Engine.decode<app.blogsh.android.model.TrashAnswer>(Practice.answer(listOf("restore"))).trash.size)
    }

    /** Every post of the list opens: its properties, its text, its versions. */
    @Test
    fun everyPostOpens() {
        val list = Engine.decode<ListAnswer>(Practice.answer(listOf("list")))
        for (post in list.posts) {
            val props = Engine.decode<PropsAnswer>(Practice.answer(listOf("props", post.slug)))
            assertEquals(post.slug, props.slug)
            assertTrue(props.url.isEmpty())
            val edit = Engine.decode<EditAnswer>(Practice.answer(listOf("edit", post.slug)))
            assertFalse(post.slug, edit.post.text.isNullOrEmpty())
            Engine.decode<VersionsAnswer>(Practice.answer(listOf("props", post.slug, "--versions")))
        }
    }

    /** A search finds by what a row says of its post. */
    @Test
    fun aSearchFindsInTheList() {
        assertEquals(listOf("pictures-and-video"), Engine.decode<ListAnswer>(Practice.answer(listOf("list", "--search=pictures"))).posts.map { it.slug })
        assertTrue(Engine.decode<ListAnswer>(Practice.answer(listOf("list", "--search=zzz"))).posts.isEmpty())
        assertEquals(7, Engine.decode<ListAnswer>(Practice.answer(listOf("list", "--search="))).posts.size)
    }

    /**
     * Nothing is written: what would change the blog is said no to, in
     * a sentence, and a delivery is not taken.
     */
    @Test
    fun whatWouldWriteIsSaidNoTo() {
        for (args in listOf(
            listOf("publish", "writing-a-post", "--yes"), listOf("delete", "writing-a-post", "--yes"), listOf("rebuild"),
            listOf("props", "writing-a-post", "--set", "pinned=yes"), listOf("empty", "trash", "--yes"), listOf("restore", "thrown-away"),
            listOf("schedule", "a-draft-about-autumn", "--at", "2027-01-01T10:00:00+01:00"), listOf("pair", "--revoke"), listOf("props", "no-such-post"),
            listOf("translate", "writing-a-post", "--lang", "cs"), emptyList(),
        )) assertTrue(args.toString(), refused(Practice.answer(args)))
        try {
            Engine.made(Practice.delivered)
            fail("a delivery taken")
        } catch (e: EngineError.Refused) {
            assertEquals("practice", e.refusal.error)
            assertEquals(Practice.refusal, e.message)
        }
        try {
            Engine.decode<ActionAnswer>(Practice.answer(listOf("rebuild")))
            fail("a build that ran")
        } catch (e: EngineError.Refused) {
            assertEquals(Practice.refusal, e.refusal.words)
            assertTrue(e.refusal.words.startsWith("This is the practice blog"))
        }
    }

    /** Nothing of the machine the answers were made on is carried along. */
    @Test
    fun theAnswersNameNoMachine() {
        val text = Practice.carried
        assertTrue(text.length > 1000)
        for (trace in listOf("/var/folders", "/Users/", "example.com", "localhost")) assertFalse(trace, text.contains(trace))
    }

    /** The record of the practice blog is kept and read back as one, and the app has one of it at most. */
    @Test
    fun thePracticeBlogIsKeptAsOne() {
        val back = EngineJson.decodeFromString(Blog.serializer(), EngineJson.encodeToString(Blog.serializer(), Blog(practice = true)))
        assertTrue(back.isPractice)
        assertFalse(EngineJson.decodeFromString(Blog.serializer(), EngineJson.encodeToString(Blog.serializer(), Blog())).isPractice)

        val notes = MemoryNotes()
        val blogs = BlogList(notes) {}
        val own = Blog(host = "blog.example", user = "me", name = "Můj blog")
        blogs.adopt(own)
        blogs.openPractice("Practice blog")
        assertEquals(listOf(false, true), blogs.all.map { it.isPractice })
        assertEquals("Practice blog", blogs.current?.name)
        // Asked for again from another blog, it is the one there is, open.
        blogs.select(own.id)
        blogs.openPractice("Practice blog")
        assertEquals(2, blogs.all.size)
        assertTrue(blogs.current?.isPractice == true)
        // And it leaves the app as any blog does.
        blogs.remove(blogs.current!!.id)
        assertEquals(listOf(own.id), blogs.all.map { it.id })
    }
}
