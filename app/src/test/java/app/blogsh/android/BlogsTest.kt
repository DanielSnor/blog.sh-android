package app.blogsh.android

import app.blogsh.android.model.Blog
import app.blogsh.android.model.Tones
import app.blogsh.android.model.BlogList
import app.blogsh.android.model.BlogShelf
import app.blogsh.android.model.EngineJson
import app.blogsh.android.model.Facts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** The blogs the app holds, and how they are written down. */
class BlogsTest {
    @Test
    fun blogsWrittenDownAreReadBack() {
        val notes = MemoryNotes()
        val one = Blog(host = "one.example")
        val two = Blog(
            host = "two.example",
            facts = Facts(92, "2026", 41319, 3.4, 14, 33, 31_600_000, 10, 1_231_404, 15, 101_168),
        )
        BlogShelf.write(listOf(one, two), two.id, notes)
        val (blogs, current) = BlogShelf.read(notes)
        assertEquals(listOf(one, two), blogs)
        assertEquals(two.id, current)
        assertEquals(two, BlogShelf.current(notes))
        assertEquals(92, blogs[1].facts?.posts)
    }

    /** The blog that was open is gone from the list: the first one opens. */
    @Test
    fun anUnknownCurrentBlogFallsBackToTheFirst() {
        val notes = MemoryNotes()
        val one = Blog()
        BlogShelf.write(listOf(one), UUID.randomUUID().toString(), notes)
        assertEquals(one.id, BlogShelf.read(notes).second)
    }

    @Test
    fun nothingWrittenDownIsNoBlogs() {
        val (blogs, current) = BlogShelf.read(MemoryNotes())
        assertTrue(blogs.isEmpty())
        assertNull(current)
    }

    @Test
    fun eachBlogHasAKeyAccountOfItsOwn() {
        val one = Blog()
        val two = Blog()
        assertNotEquals(one.keyAccount, two.keyAccount)
        assertTrue(one.keyAccount.startsWith("blog-"))
    }

    /**
     * A blog written down by an earlier build, before a field existed, is
     * still that blog.
     */
    @Test
    fun aBlogFromAnEarlierBuildIsStillRead() {
        val id = UUID.randomUUID().toString()
        val json = """[{"id":"$id","host":"blog.example","port":202,"user":"dan","path":"/app/data/blog","through":"","keyAccount":"ssh-ed25519"}]"""
        val blogs = EngineJson.decodeFromString<List<Blog>>(json)
        assertEquals(1, blogs.size)
        assertEquals(id, blogs[0].id)
        assertEquals("", blogs[0].name)
        assertEquals("", blogs[0].claim)
        assertEquals(24, blogs[0].maxMb)
        assertNull(blogs[0].facts)
        assertNull(blogs[0].tonesLight)
        assertNull(blogs[0].tonesDark)
    }

    /** A blog's palette is written down with it and read back as it was. */
    @Test
    fun aBlogKeepsItsPalette() {
        val blog = Blog(
            tonesLight = Tones("#fff7eb", "#1e1d1c", "#6b6862", "#d7d0c6"),
            tonesDark = Tones("#000000", "#e6dccb", "#a1988a", "#3c3935"),
        )
        val back = EngineJson.decodeFromString<Blog>(EngineJson.encodeToString(blog))
        assertEquals(blog, back)
    }

    /**
     * What a blog is called in the list: its own name; before it has said
     * one, its directory -- two blogs on one server share a host.
     */
    @Test
    fun aBlogIsCalledByItsNameThenItsDirectoryThenItsHost() {
        var blog = Blog(host = "blog.example")
        assertEquals("blog.example", blog.label)
        blog = blog.copy(path = "/app/data/blog.sh")
        assertEquals("blog.sh", blog.label)
        blog = blog.copy(name = "./blog.sh")
        assertEquals("./blog.sh", blog.label)
    }

    /** A second blog starts beside the first: its server, not its directory or its key. */
    @Test
    fun aNewBlogStartsWithTheServerOfTheOneThatWasOpen() {
        val forgotten = mutableListOf<String>()
        val list = BlogList(MemoryNotes()) { forgotten.add(it) }
        val first = list.add()
        list.update { it.copy(host = "blog.example", port = 202, user = "dan", through = "sudo docker exec -i blog", path = "/app/one") }
        val second = list.add()
        assertEquals("blog.example", second.host)
        assertEquals(202, second.port)
        assertEquals("dan", second.user)
        assertEquals("sudo docker exec -i blog", second.through)
        assertEquals("", second.path)
        assertNotEquals(first.keyAccount, second.keyAccount)
        assertEquals(second.id, list.currentId)
        // Removing a blog removes its key from the device, and the one before it opens.
        list.remove(second.id)
        assertEquals(listOf(second.keyAccount), forgotten)
        assertEquals(first.id, list.currentId)
    }

    /**
     * The order somebody put the blogs in is the order they are kept in,
     * and which one is open does not change with it.
     */
    @Test
    fun blogsKeepTheOrderTheyWerePutIn() {
        val notes = MemoryNotes()
        val list = BlogList(notes) {}
        val one = Blog(host = "one.example")
        val two = Blog(host = "two.example")
        val three = Blog(host = "three.example")
        for (blog in listOf(one, two, three)) list.adopt(blog)
        list.select(two.id)
        // The last carried to the first place, a step at a time.
        list.shift(three.id, -1)
        list.shift(three.id, -1)
        // And nowhere past the end.
        list.shift(three.id, -1)

        val (blogs, current) = BlogShelf.read(notes)
        assertEquals(listOf("three.example", "one.example", "two.example"), blogs.map { it.host })
        assertEquals(two.id, current)
        assertEquals(two, BlogShelf.current(notes))
        assertEquals(two.id, list.currentId)
    }

    /**
     * A held row's "Up" and "Down": one place at a time, and nowhere
     * past either end of the list.
     */
    @Test
    fun aBlogStepsUpAndDownTheListButNotOffIt() {
        val one = Blog(host = "one.example")
        val two = Blog(host = "two.example")
        val three = Blog(host = "three.example")
        val blogs = listOf(one, two, three)

        assertEquals(listOf("one.example", "three.example", "two.example"), BlogShelf.shifted(blogs, three.id, -1).map { it.host })
        assertEquals(listOf("two.example", "one.example", "three.example"), BlogShelf.shifted(blogs, one.id, 1).map { it.host })
        assertEquals(blogs, BlogShelf.shifted(blogs, one.id, -1))
        assertEquals(blogs, BlogShelf.shifted(blogs, three.id, 1))
        assertEquals(blogs, BlogShelf.shifted(blogs, UUID.randomUUID().toString(), 1))
    }

    /**
     * What is remembered of a server is remembered under the host and
     * port the connection uses -- and the settings look there too, or
     * "forget the server's key" would look beside it.
     */
    @Test
    fun aServerIsRememberedUnderWhatTheConnectionUses() {
        val notes = MemoryNotes()
        val blog = Blog(host = "one.example ", port = 0, user = "dan")
        BlogShelf.write(listOf(blog), blog.id, notes)
        val settings = app.blogsh.android.model.ServerSettings.load(notes)
        assertEquals("one.example", blog.reached.first)
        assertEquals(22, blog.reached.second)
        assertEquals(blog.reached.first, settings?.host)
        assertEquals(blog.reached.second, settings?.port)
        assertEquals("hostkey.one.example:22", app.blogsh.android.model.TrustOnFirstUse.notesKey(blog.reached.first, blog.reached.second))
    }

    /**
     * A blog let in by a code remembers under which name the server
     * wrote this device down; one written down before that was kept --
     * or set up by hand -- has none.
     */
    @Test
    fun aBlogRemembersThatACodeLetItIn() {
        val notes = MemoryNotes()
        val paired = Blog(host = "one.example", pairedAs = "Pixel 9")
        val byHand = Blog(host = "two.example", path = "/home/me/blog")
        BlogShelf.write(listOf(paired, byHand), paired.id, notes)
        val (blogs, _) = BlogShelf.read(notes)
        assertEquals("Pixel 9", blogs[0].pairedAs)
        assertNull(blogs[1].pairedAs)
        // Written down by an earlier build: the field is not there, and is none.
        val earlier = EngineJson.decodeFromString(Blog.serializer(), "{\"id\":\"x\",\"host\":\"old.example\",\"user\":\"me\"}")
        assertNull(earlier.pairedAs)
    }

    /**
     * Let in again by a new code, a blog is where the code says and is
     * still that blog: its name, its numbers, what was written for it on
     * this device, and its place in the list.
     */
    @Test
    fun aBlogLetInAgainIsTheSameBlogSomewhereElse() {
        val notes = MemoryNotes()
        var hungUp = 0
        val list = BlogList(notes, hangUp = { hungUp += 1 }) {}
        val first = Blog(host = "first.example", user = "me")
        val moved = Blog(
            host = "10.0.0.39", port = 22, user = "pavel", path = "/home/pavel/blog", through = "sudo docker exec -i blog", name = "pokusy",
            facts = Facts(12, "2026", 128, 0.1, 18, 3, 1_200, 0, 0, 0, 0),
        )
        list.adopt(first)
        list.adopt(moved)
        app.blogsh.android.model.Unsent("Rozepsáno", "", "Text.", 1_791_400_000_000).keep(moved.id, notes)
        list.select(first.id)

        list.repaired(moved.id, "192.168.1.20", 2222, "pavel", "blog-new-key", "motorola edge")

        val now = list.all[1]
        assertEquals(moved.id, now.id)
        assertEquals("192.168.1.20", now.host)
        assertEquals(2222, now.port)
        assertEquals("blog-new-key", now.keyAccount)
        assertEquals("motorola edge", now.pairedAs)
        // What a hand-made line was composed from is the server's to say now.
        assertEquals("", now.path)
        assertEquals("", now.through)
        assertEquals("pokusy", now.name)
        assertEquals(12, now.facts?.posts)
        assertEquals(first.id, list.currentId)
        assertEquals("Rozepsáno", app.blogsh.android.model.Unsent.kept(moved.id, notes)?.title)
        assertEquals(1, hungUp)
        // Written down: the next launch finds it where it is now.
        assertEquals("192.168.1.20", BlogShelf.read(notes).first[1].host)
        // A blog that is not there is nobody's to move.
        list.repaired("nobody", "x", 1, "y", "z", "w")
        assertEquals(2, list.all.size)
        // What the blog called itself is kept; the code's word for it is a hint for a blog with none.
        list.repaired(moved.id, "192.168.1.20", 2222, "pavel", "blog-new-key", "motorola edge", site = "Jiný název")
        assertEquals("pokusy", list.all[1].name)
        list.repaired(first.id, "192.168.1.21", 22, "me", first.keyAccount, "", site = "Druhý blog")
        assertEquals("Druhý blog", list.all[0].name)
        assertEquals("", list.all[0].pairedAs)
    }
}
