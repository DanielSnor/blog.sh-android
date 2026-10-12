package app.blogsh.android

import app.blogsh.android.model.Shot
import app.blogsh.android.model.UnsentShots
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The pictures of a post being written, kept in files beside its words.
 * A mistake here is a form that comes back without the pictures chosen
 * for it, or megabytes left on the device for a post long sent.
 */
class UnsentShotsTest {
    private val home: File = Files.createTempDirectory("unsent").toFile()
    private val blog = "11111111-2222-3333-4444-555555555555"

    @After
    fun clear() {
        home.deleteRecursively()
    }

    private fun picture(name: String, alt: String = "", bytes: Int = 4) = Shot(name, ByteArray(bytes) { 7 }, 640, 480, alt)

    private fun video(name: String) = Shot(name, ByteArray(9) { 3 }, 0, 0, kind = Shot.Kind.Video, poster = ByteArray(2) { 5 }, converted = false)

    /** In their order, with their descriptions -- the ones not yet put into the text too -- and a video with its frame. */
    @Test
    fun whatTheFormHeldComesBackAsItWas() {
        UnsentShots.keep(listOf(picture("b.jpg", "Druhá, ale první v pořadí"), video("klip.mov"), picture("a.jpg")), blog, home = home)
        val back = UnsentShots.shots(blog, home = home)
        assertEquals(listOf("b.jpg", "klip.mov", "a.jpg"), back.map { it.name })
        assertEquals(listOf("Druhá, ale první v pořadí", "", ""), back.map { it.alt })
        assertEquals(listOf(Shot.Kind.Picture, Shot.Kind.Video, Shot.Kind.Picture), back.map { it.kind })
        assertEquals(640 to 480, back[0].width to back[0].height)
        assertArrayEquals(ByteArray(4) { 7 }, back[0].data)
        assertArrayEquals(ByteArray(9) { 3 }, back[1].data)
        assertArrayEquals(ByteArray(2) { 5 }, back[1].poster)
        assertFalse(back[1].converted)
        assertNull(back[0].poster)
        assertNotNull(UnsentShots.kept(blog, home = home))
    }

    /** A description is typed a letter at a time: it is kept, and the picture is not written again for it. */
    @Test
    fun aFileIsWrittenOnceAndADescriptionEachTime() {
        UnsentShots.keep(listOf(picture("a.jpg")), blog, home = home)
        val file = File(home, "$blog/new/media/a.jpg")
        assertTrue(file.isFile)
        // The same name with other bytes: the file that is there stays.
        UnsentShots.keep(listOf(Shot("a.jpg", ByteArray(4) { 9 }, 640, 480, "popis")), blog, home = home)
        assertArrayEquals(ByteArray(4) { 7 }, file.readBytes())
        assertEquals("popis", UnsentShots.shots(blog, home = home).single().alt)
    }

    /** A picture taken out of the form goes from the device; with nothing in the form, nothing is kept. */
    @Test
    fun whatLeavesTheFormLeavesTheDevice() {
        UnsentShots.keep(listOf(picture("a.jpg"), video("klip.mov")), blog, home = home)
        UnsentShots.keep(listOf(picture("a.jpg")), blog, home = home)
        assertEquals(listOf("a.jpg"), File(home, "$blog/new/media").list()!!.toList())
        assertEquals(emptyList<String>(), File(home, "$blog/new/posters").list()!!.toList())
        UnsentShots.keep(emptyList(), blog, home = home)
        assertFalse(File(home, "$blog/new").exists())
        assertTrue(UnsentShots.shots(blog, home = home).isEmpty())
        assertNull(UnsentShots.kept(blog, home = home))
    }

    /** One whose file does not read is left out, and the others come back. */
    @Test
    fun aPictureWhoseFileIsGoneIsLeftOut() {
        UnsentShots.keep(listOf(picture("a.jpg"), picture("b.jpg")), blog, home = home)
        File(home, "$blog/new/media/a.jpg").delete()
        assertEquals(listOf("b.jpg"), UnsentShots.shots(blog, home = home).map { it.name })
    }

    /**
     * The new post has one room and each post being changed its own: a
     * picture added to one is not another's, and forgetting one leaves
     * the others.
     */
    @Test
    fun thePostBeingChangedKeepsItsOwn() {
        UnsentShots.keep(listOf(picture("novy.jpg")), blog, home = home)
        UnsentShots.keep(listOf(picture("k-textu.jpg")), blog, "sobota-v-lese", home)
        UnsentShots.keep(listOf(picture("jiny.jpg")), blog, "jiny-post", home)
        assertEquals(listOf("novy.jpg"), UnsentShots.shots(blog, home = home).map { it.name })
        assertEquals(listOf("k-textu.jpg"), UnsentShots.shots(blog, "sobota-v-lese", home).map { it.name })
        UnsentShots.forget(blog, "sobota-v-lese", home)
        assertTrue(UnsentShots.shots(blog, "sobota-v-lese", home).isEmpty())
        assertEquals(listOf("jiny.jpg"), UnsentShots.shots(blog, "jiny-post", home).map { it.name })
        UnsentShots.forget(blog, home = home)
        assertTrue(UnsentShots.shots(blog, home = home).isEmpty())
        assertEquals(listOf("jiny.jpg"), UnsentShots.shots(blog, "jiny-post", home).map { it.name })
    }

    /** Renamed, a post takes what was kept for it along; what stood under the new name gives way. */
    @Test
    fun whatIsKeptFollowsAPostToItsNewName() {
        UnsentShots.keep(listOf(picture("a.jpg", "popis")), blog, "stary", home)
        UnsentShots.keep(listOf(picture("zbytek.jpg")), blog, "novy", home)
        UnsentShots.move(blog, "stary", "novy", home)
        assertTrue(UnsentShots.shots(blog, "stary", home).isEmpty())
        assertEquals(listOf("a.jpg" to "popis"), UnsentShots.shots(blog, "novy", home).map { it.name to it.alt })
        // Nothing kept under the old name: nothing moves, and nothing is lost.
        UnsentShots.move(blog, "neni", "novy", home)
        assertEquals(1, UnsentShots.shots(blog, "novy", home).size)
    }

    /** A blog that leaves the app takes all of it along, and no other blog's. */
    @Test
    fun aBlogThatLeavesTakesItsPicturesAlong() {
        val other = "99999999-2222-3333-4444-555555555555"
        UnsentShots.keep(listOf(picture("a.jpg")), blog, home = home)
        UnsentShots.keep(listOf(picture("b.jpg")), blog, "post", home)
        UnsentShots.keep(listOf(picture("c.jpg")), other, home = home)
        UnsentShots.forgetAll(blog, home)
        assertFalse(File(home, blog).exists())
        assertEquals(listOf("c.jpg"), UnsentShots.shots(other, home = home).map { it.name })
    }

    /** Whatever a slug or a name holds, the files stay inside the blog's own directory. */
    @Test
    fun nothingIsWrittenOutsideTheBlogsDirectory() {
        UnsentShots.keep(listOf(picture("../../venku.jpg")), blog, "../../jinde", home)
        val all = home.walkTopDown().filter { it.isFile }.map { it.relativeTo(home).path }.toList()
        assertTrue(all.toString(), all.isNotEmpty() && all.all { it.startsWith("$blog/edits/") })
        assertFalse(File(home.parentFile, "venku.jpg").exists())
        assertEquals(listOf("../../venku.jpg"), UnsentShots.shots(blog, "../../jinde", home).map { it.name })
    }
}
