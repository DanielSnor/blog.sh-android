package app.blogsh.android

import app.blogsh.android.model.Kept
import app.blogsh.android.model.TextFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset

/**
 * A finished text taken from a file: all of it is the post's text, and
 * only a header says a title and tags. A mistake here is a text that
 * arrives in the wrong letters, a first line taken for a title nobody
 * gave, or a title somebody typed overwritten by a file's.
 */
class TextFileTest {
    @Before
    fun words() = English.speak()

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun refused(data: ByteArray): TextFile.Failure? = try {
        TextFile.read(data)
        null
    } catch (e: TextFile.NotTaken) {
        e.why
    }

    /** The first line stays a line of the text: a title is not guessed. */
    @Test
    fun allOfAPlainTextIsTheBody() {
        val read = TextFile.read("Sobota v lese\n\nŠli jsme ráno.\nBylo mokro.\n".toByteArray())
        assertNull(read.title)
        assertNull(read.tags)
        assertEquals("Sobota v lese\n\nŠli jsme ráno.\nBylo mokro.", read.body)
    }

    /**
     * The title and the tags are read out of a header; the rest of it
     * is left behind, and none of it stays in the text.
     */
    @Test
    fun aHeaderGivesItsTitleAndTags() {
        val file = "---\ntitle: \"Sobota v lese\"\ndate: 2026-10-12\ntags: [les, #houby, 'ráno']\npublish: yes\n---\n\nŠli jsme ráno.\n"
        val read = TextFile.read(file.toByteArray())
        assertEquals("Sobota v lese", read.title)
        assertEquals("les, houby, ráno", read.tags)
        assertEquals("Šli jsme ráno.", read.body)
    }

    @Test
    fun tagsAreReadHoweverTheHeaderListsThem() {
        assertEquals("les, houby", TextFile.read("---\ntags: les, houby\n---\ntext").tags)
        val listed = TextFile.read("---\ntags:\n  - les\n  - \"houby\"\ntitle: T\n---\ntext")
        assertEquals("les, houby", listed.tags)
        assertEquals("T", listed.title)
        val none = TextFile.read("---\ntags: []\ntitle:\n---\ntext")
        assertNull(none.tags)
        assertNull(none.title)
        // A longer key that begins the same is another key.
        val others = TextFile.read("---\ntitles: no\ntagset: a\n---\ntext")
        assertNull(others.title)
        assertNull(others.tags)
        assertEquals("text", others.body)
    }

    /**
     * A field written in is left as it is; only an empty one takes
     * what the header said.
     */
    @Test
    fun aHeaderFillsOnlyEmptyFields() {
        val read = TextFile.Read("Ze souboru", "les, houby", "text")
        assertEquals("Ze souboru", read.filling("", read.title))
        assertEquals("Ze souboru", read.filling("  \n", read.title))
        assertEquals("Můj titulek", read.filling("Můj titulek", read.title))
        assertEquals("les, houby", read.filling("", read.tags))
        assertEquals("ráno", read.filling("ráno", read.tags))
        // A file with no header changes neither.
        val plain = TextFile.Read(body = "text")
        assertEquals("", plain.filling("", plain.title))
        assertEquals("ráno", plain.filling("ráno", plain.tags))
    }

    /** A rule at the top of a text that nothing closes is the text's own. */
    @Test
    fun aRuleThatNothingClosesIsNoHeader() {
        val read = TextFile.read("---\n\nPo čáře.\n")
        assertNull(read.title)
        assertEquals("---\n\nPo čáře.", read.body)
        // And one further down is a rule, not a header's end.
        assertEquals("Nad čarou.\n\n---\n\nPod čarou.", TextFile.read("Nad čarou.\n\n---\n\nPod čarou.\n").body)
    }

    /** A header alone still says its title; a file with nothing says so. */
    @Test
    fun whatHasNothingIsSaidEmpty() {
        assertEquals(TextFile.Read("Jen titulek", null, ""), TextFile.read("---\ntitle: Jen titulek\n---\n".toByteArray()))
        assertEquals(TextFile.Failure.Empty, refused(" \n\n".toByteArray()))
        assertEquals(TextFile.Failure.Empty, refused(ByteArray(0)))
        assertEquals("There is no text in the file.", TextFile.Failure.Empty.words)
    }

    /**
     * Whatever wrote the file: the mark at its head is no part of the
     * text, a Windows line end is a line end, and a file in two bytes to
     * a letter is read as what it is.
     */
    @Test
    fun theTextIsReadWhateverWroteIt() {
        val words = "Žluťoučký kůň\núpěl"
        assertEquals(words, TextFile.text(bytes(0xEF, 0xBB, 0xBF) + words.toByteArray()))
        assertEquals(words, TextFile.text("Žluťoučký kůň\r\núpěl".toByteArray()))
        assertEquals(words, TextFile.text("Žluťoučký kůň\rúpěl".toByteArray()))
        assertEquals(words, TextFile.text(bytes(0xFF, 0xFE) + words.toByteArray(Charsets.UTF_16LE)))
        assertEquals(words, TextFile.text(bytes(0xFE, 0xFF) + words.toByteArray(Charsets.UTF_16BE)))
        assertEquals(words, TextFile.text(words.toByteArray(Charsets.UTF_16LE)))
        assertEquals(words, TextFile.text(words.toByteArray(Charsets.UTF_16BE)))
        assertEquals(words, TextFile.text(words.toByteArray(Charset.forName("windows-1250"))))
        // A letter Central Europe's page has no byte for: the Western one is tried next.
        assertEquals("price ƒ 10", TextFile.text("price ƒ 10".toByteArray(Charset.forName("windows-1252"))))
        // A header behind the mark is still a header.
        val marked = bytes(0xEF, 0xBB, 0xBF) + "---\r\ntitle: T\r\n---\r\ntext\r\n".toByteArray()
        assertEquals(TextFile.Read("T", null, "text"), TextFile.read(marked))
    }

    /** A word processor's document is not text, under whatever name. */
    @Test
    fun whatIsNotTextIsTurnedAway() {
        assertEquals(TextFile.Failure.NotText, refused("""{\rtf1\ansi Sobota}""".toByteArray()))
        assertEquals(TextFile.Failure.NotText, refused(bytes(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00, 0x08, 0x00, 0x00, 0x00, 0x21)))
        assertEquals(TextFile.Failure.TooLarge, refused(ByteArray(TextFile.CEILING + 1) { 0x61 }))
        assertNull(refused(ByteArray(TextFile.CEILING) { 0x61 }))
        assertTrue(TextFile.Failure.NotText.words.startsWith("This file is not plain text."))
    }

    /**
     * The picker is asked for plain text and Markdown, and for a file of
     * no kind in particular -- which is what many keepers call a Markdown
     * file -- and for no document of a word processor.
     */
    @Test
    fun thePickerIsAskedForTextAndMarkdown() {
        val asked = TextFile.KINDS.toList()
        assertTrue("text/plain" in asked && "text/markdown" in asked && "application/octet-stream" in asked)
        assertFalse(asked.any { it.contains("*") })
        assertFalse(asked.any { it.contains("rtf") || it.contains("word") || it.contains("pdf") || it.startsWith("image/") })
    }

    /**
     * Into a text already begun, the file's text goes where the caret
     * is, as paragraphs of its own, and nothing written is lost.
     */
    @Test
    fun theTextGoesInWhereTheCaretIs() {
        val put = Kept.placed("Ze souboru.\n\nDruhý odstavec.", "Začátek.\n\nKonec.", "Začátek.".length)
        assertEquals("Začátek.\n\nZe souboru.\n\nDruhý odstavec.\n\nKonec.", put.text)
        assertEquals("Ze souboru.\n", Kept.placed("Ze souboru.", "", null).text)
        if (put.caret <= "Začátek.\n\nZe souboru.".length) fail("the caret did not go on after what was put in")
    }
}
