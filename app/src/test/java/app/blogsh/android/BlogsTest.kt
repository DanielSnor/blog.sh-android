package app.blogsh.android

import app.blogsh.android.model.Blog
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
}
