package app.blogsh.android

import app.blogsh.android.model.Delivery
import app.blogsh.android.model.Kept
import app.blogsh.android.model.Markdown
import app.blogsh.android.model.Pictures
import app.blogsh.android.model.Shot
import app.blogsh.android.model.encodedSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A post on its way to the blog: its file, its pictures' names, and how
 * much all of it weighs against the server's limit.
 */
class PostTest {
    // the markdown file

    @Test
    fun theHeaderHoldsWhatTheFormHasFieldsFor() {
        assertEquals("---\ntitle: Hello\ntags: a, b\n---\n\n", Markdown.frontMatter(" Hello ", "a, b"))
        assertEquals("---\ntitle: Hello\npublish: yes\n---\n\n", Markdown.frontMatter("Hello", "", publish = true))
    }

    @Test
    fun nothingFilledInIsNoHeader() = assertEquals("", Markdown.frontMatter("  ", " , "))

    /**
     * A title in quotes or tags written the YAML way would be read by the
     * engine as something else than the words.
     */
    @Test
    fun quotesBracketsAndHashesAreTakenOff() {
        assertEquals("---\ntitle: Hello\n---\n\n", Markdown.frontMatter("\"Hello\"", ""))
        assertEquals("---\ntags: ruby, blog.sh, x\n---\n\n", Markdown.frontMatter("", "#ruby, [blog.sh], 'x'"))
        assertEquals("---\ntags: a, b\n---\n\n", Markdown.frontMatter("", "[a, b]"))
    }

    @Test
    fun theFileIsItsHeaderAndItsTextWithOneNewlineAtTheEnd() {
        assertEquals("---\ntitle: T\n---\n\ntext\n", Markdown.file("T", "", "\n  text \n\n"))
        assertEquals("text\n", Markdown.file("", "", "text"))
    }

    /** A text that itself opens with --- would be read as a header. */
    @Test
    fun aTextOpeningWithDashesGetsAnEmptyHeaderBeforeIt() {
        assertEquals("---\n---\n\n--- and so on\n", Markdown.file("", "", "--- and so on"))
    }

    @Test
    fun theFileIsNamedByItsTitleFolded() {
        assertEquals("zlutoucky-kun-upel.md", Markdown.fileName("Žluťoučký kůň úpěl", "x"))
        assertEquals("one-two-three-four-five-six.md", Markdown.fileName("", "one two three four five six seven"))
        assertEquals("post.md", Markdown.fileName("", ""))
        assertEquals("post.md", Markdown.fileName("!!!", ""))
        assertEquals(43, Markdown.fileName("abcdefghij ".repeat(6), "").length)
    }

    // names of pictures and videos

    @Test
    fun aPictureKeepsItsOwnNameFolded() {
        assertEquals("img-1234.jpg", Pictures.safeName("IMG 1234.HEIC", 1))
        assertEquals("zaba-na-prameni.jpg", Pictures.safeName("Žába na prameni.png", 1))
        assertEquals("a-b-c.jpg", Pictures.safeName("a.b.c.jpeg", 1))
    }

    @Test
    fun aPictureWithoutANameIsNumbered() {
        assertEquals("photo-3.jpg", Pictures.safeName(null, 3))
        assertEquals("photo-2.jpg", Pictures.safeName("???.jpg", 2))
        assertEquals("video-1.mp4", Pictures.safeName(null, 1, stem = "video", ext = "mp4"))
    }

    @Test
    fun twoPicturesFoldingToOneNameAreToldApart() {
        assertEquals("a.jpg", Pictures.freeName("a.jpg", emptyList()))
        assertEquals("a-2.jpg", Pictures.freeName("a.jpg", listOf("a.jpg")))
        assertEquals("a-3.jpg", Pictures.freeName("a.jpg", listOf("a.jpg", "a-2.jpg")))
    }

    // the mark of a shot in the text

    private fun shot(name: String, bytes: Int = 10, alt: String = "", kind: Shot.Kind = Shot.Kind.Picture) =
        Shot(name, ByteArray(bytes), 10, 10, alt, kind)

    @Test
    fun aPictureIsOneMarkAVideoTwo() {
        assertEquals("![a cat](cat.jpg)", shot("cat.jpg", alt = " a cat ").mark)
        assertEquals("!![](clip.mp4)", shot("clip.mp4", kind = Shot.Kind.Video).mark)
    }

    @Test
    fun aShotsMarkIsFoundWhateverItsDescriptionSays() {
        val pattern = shot("cat.jpg").markPattern
        assertTrue(pattern.containsMatchIn("before ![a cat](cat.jpg) after"))
        assertTrue(pattern.containsMatchIn("!![](cat.jpg)"))
        assertFalse(pattern.containsMatchIn("![a cat](cat2.jpg)"))
        // The dot of the name is a dot, not any letter.
        assertFalse(pattern.containsMatchIn("![a cat](catxjpg)"))
    }

    // what an edit may do to a post's pictures

    /**
     * The engine refuses an edit from the app that leaves a post with
     * fewer pictures than it had; the app says so before the save.
     */
    @Test
    fun takingAPictureOutIsFewer() {
        val media = listOf("01.jpg", "02.jpg")
        assertFalse(Kept.fewer(media, emptyList(), "![a](01.jpg) ![b](02.jpg)"))
        assertTrue(Kept.fewer(media, emptyList(), "![a](01.jpg)"))
        assertTrue(Kept.fewer(media, emptyList(), "no pictures at all"))
        assertEquals(listOf("02.jpg"), Kept.dropped(media, "![a](01.jpg)"))
    }

    @Test
    fun oneOutAndAnotherInIsAsManyAsBefore() {
        assertFalse(Kept.fewer(listOf("01.jpg", "02.jpg"), listOf(shot("photo-3.jpg")), "![a](01.jpg) ![c](photo-3.jpg)"))
    }

    /** A picture chosen but not put into the text is not in the post. */
    @Test
    fun aNewPictureCountsOnlyOnceItIsNamed() {
        assertTrue(Kept.fewer(listOf("01.jpg", "02.jpg"), listOf(shot("photo-3.jpg")), "![a](01.jpg)"))
    }

    /**
     * The engine counts pictures and videos apart: a picture does not
     * stand in for a video taken out.
     */
    @Test
    fun aPictureDoesNotStandInForAVideo() {
        val picture = shot("photo-2.jpg")
        val video = shot("clip.mp4", kind = Shot.Kind.Video)
        assertTrue(Kept.fewer(listOf("01.mov"), listOf(picture), "![a](photo-2.jpg)"))
        assertFalse(Kept.fewer(listOf("01.mov"), listOf(video), "!![a](clip.mp4)"))
        assertTrue(Kept.isVideo("01.MOV") && Kept.isVideo("a.mp4") && !Kept.isVideo("a.jpg") && !Kept.isVideo("mov"))
    }

    /**
     * A shot picked and never put into the text would arrive, stand in no
     * post and lie in the blog's incoming/ for good: it does not go.
     */
    @Test
    fun onlyTheShotsTheTextNamesAreSent() {
        val a = shot("photo-1.jpg")
        val b = shot("photo-2.jpg")
        val clip = shot("video-3.mp4", kind = Shot.Kind.Video)
        assertEquals(listOf("photo-2.jpg", "video-3.mp4"), Kept.sent(listOf(a, b, clip), "![x](photo-2.jpg)\n\n!![y](video-3.mp4)").map { it.name })
        assertTrue(Kept.sent(listOf(a, b), "no marks").isEmpty())
        // The name has to be the whole of what the mark names.
        assertTrue(Kept.sent(listOf(a), "![x](other-photo-1.jpg)").isEmpty())
    }

    @Test
    fun aPostWithoutPicturesHasNothingToLose() {
        assertFalse(Kept.fewer(emptyList(), emptyList(), "text"))
        assertTrue(Kept.dropped(emptyList(), "text").isEmpty())
    }

    // the weight of a delivery

    @Test
    fun base64IsAThirdLargerAndCountsItsLineBreaks() {
        assertEquals(1, encodedSize(0))
        assertEquals(5, encodedSize(3))
        assertEquals(78, encodedSize(57))
    }

    @Test
    fun aDeliveryOfTextAloneStillWeighsItsEnvelope() = assertEquals(352, Delivery.wireBytes(emptyList(), 0))

    /**
     * The server measures the encoded stream: of a limit of one megabyte
     * about three quarters are room for files.
     */
    @Test
    fun theLimitIsMeasuredOnTheEncodedStream() {
        assertFalse(Delivery.over(listOf(shot("a.jpg", bytes = 700 * 1024)), 100, 1))
        assertTrue(Delivery.over(listOf(shot("a.jpg", bytes = 800 * 1024)), 100, 1))
        assertTrue(Delivery.over(listOf(shot("a.jpg", bytes = 400 * 1024), shot("b.jpg", bytes = 400 * 1024)), 100, 1))
    }

    @Test
    fun aServerThatNamesNoLimitRefusesNothing() {
        assertFalse(Delivery.over(listOf(shot("a.jpg", bytes = 5 * 1_048_576)), 100, 0))
    }

    @Test
    fun sizesAreSaidInKilobytesThenMegabytes() {
        assertEquals("0 kB", Delivery.size(0))
        assertEquals("1 kB", Delivery.size(1))
        assertEquals("2 kB", Delivery.size(1536))
        assertEquals("1023 kB", Delivery.size(1023 * 1024))
        // The decimal mark is the reader's own; the unit is not.
        assertTrue(Delivery.size(5 * 1_048_576).startsWith("5"))
        assertTrue(Delivery.size(5 * 1_048_576).endsWith(" MB"))
    }

    @Test
    fun whatIsOnTheWayEndsWithItsSize() {
        assertTrue(Delivery.describe(emptyList(), 10).endsWith(", 1 kB"))
        val said = Delivery.describe(
            listOf(shot("a.jpg", bytes = 1024), shot("b.jpg", bytes = 1024), shot("c.mp4", bytes = 1024, kind = Shot.Kind.Video)), 0,
        )
        assertTrue(said.startsWith("2 "))
        assertTrue(said.contains(", 1 "))
        assertTrue(said.endsWith(", 3 kB"))
    }
}
