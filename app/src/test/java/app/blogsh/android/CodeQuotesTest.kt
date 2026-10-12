package app.blogsh.android

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.blogsh.android.model.CodeQuotes
import app.blogsh.android.model.Marks
import app.blogsh.android.ui.EditorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quotes in code stay the straight ones; prose keeps the typographic
 * ones. A mistake here is a command in a post that is no command, or a
 * post merely opened that reads as changed.
 */
class CodeQuotesTest {
    private fun all(text: String) = CodeQuotes.straightened(text, 0 until text.length)

    @Test
    fun proseKeepsItsQuotes() {
        val prose = "Řekl „ahoj“ a šel. She said “hi”, didn’t she?"
        assertEquals(prose, all(prose))
        assertTrue(CodeQuotes.code(prose).isEmpty())
    }

    @Test
    fun codeOnALineHasStraightQuotes() {
        assertEquals("Spusť `echo \"ahoj\"` a pak „hotovo“.", all("Spusť `echo “ahoj”` a pak „hotovo“."))
        assertEquals("`a 'b'` a `c \"d\"`", all("`a ‘b’` a `c „d“`"))
        // Two backticks around a text that has one of its own.
        assertEquals("``it's `x` \"y\"`` a “z”", all("``it’s `x` “y”`` a “z”"))
    }

    /**
     * A span runs across a line break within its paragraph, as the blog
     * reads it, and not across an empty line.
     */
    @Test
    fun aSpanCrossesALineAndNotAnEmptyOne() {
        assertEquals("Spusť `echo \"a\"\na \"b\"` hned.", all("Spusť `echo “a”\na “b”` hned."))
        assertEquals("Jeden ` a “a”\n\ndruhý ` a “b”", all("Jeden ` a “a”\n\ndruhý ` a “b”"))
        // The first run of as many closes, the rest of it stays text.
        assertEquals("`\"a\"`` “b”", all("`“a”`` “b”"))
    }

    /** A backtick nothing answers makes no code: the quote after it is prose's. */
    @Test
    fun anUnclosedBacktickIsNoCode() {
        val text = "Cena je 5` a “víc”"
        assertEquals(text, all(text))
    }

    @Test
    fun aBlockBetweenFencesIsCode() {
        assertEquals(
            "Úvod s “uvozovkami”.\n\n```sh\necho \"a\" 'b'\n```\n\nZávěr s “uvozovkami”.",
            all("Úvod s “uvozovkami”.\n\n```sh\necho “a” ‘b’\n```\n\nZávěr s “uvozovkami”."),
        )
        // A block nobody closed runs to the end, as the blog reads it.
        assertEquals("```\nx = \"1\"\ny = \"2\"", all("```\nx = “1”\ny = “2”"))
        // Tildes fence nothing on the blog: its lines are prose.
        assertEquals("~~~\n“a”\n~~~", all("~~~\n“a”\n~~~"))
        // However far the fence is indented; four spaces alone make no code.
        assertEquals("      ```\n\"a\"\n\t```\n“b”", all("      ```\n“a”\n\t```\n“b”"))
        assertEquals("    x = “a”", all("    x = “a”"))
        // A longer run closes, a shorter one or one with words after it does not.
        assertEquals("````\n\"a\"\n```\n\"b\"\n`````\n“c”", all("````\n“a”\n```\n“b”\n`````\n“c”"))
        assertEquals("```\n\"a\"\n``` konec\n\"b\"", all("```\n“a”\n``` konec\n“b”"))
    }

    /** The header of a post is not code: a title's quotes are prose. */
    @Test
    fun aHeaderIsLeftAlone() {
        val text = "---\ntitle: Jak jsem „skoro“ vyhrál\n---\n\nText."
        assertEquals(text, all(text))
    }

    /** The text is as long as it was: one mark stands for one. */
    @Test
    fun theTextKeepsItsLength() {
        val text = "`“a” ‘b’ „c“`"
        assertEquals(text.length, all(text).length)
    }

    /** Only the lines just written into are looked over. */
    @Test
    fun onlyTheTouchedLinesAreLookedOver() {
        val text = "`“stará”`\n`“nová”`"
        val at = text.indexOf("nová")
        assertEquals("`“stará”`\n`\"nová\"`", CodeQuotes.straightened(text, at until at + 4))
        // A caret alone, with nothing chosen, still says which line.
        assertEquals("`“stará”`\n`\"nová\"`", CodeQuotes.straightened(text, IntRange(at, at - 1)))
        assertEquals(text, CodeQuotes.straightened(text, IntRange(999, 998)))
    }

    /** A new post is its writer's whole: all its code is put right. */
    @Test
    fun aNewTextIsSettledWhole() {
        assertEquals(
            "Řekl „ahoj“ a `echo \"ahoj\"`.\n\n```\nx = '1'\n```",
            CodeQuotes.settled("Řekl „ahoj“ a `echo “ahoj”`.\n\n```\nx = ‘1’\n```"),
        )
        assertEquals("Jen „próza“.", CodeQuotes.settled("Jen „próza“."))
        assertEquals("", CodeQuotes.settled(""))
    }

    /**
     * Over a post from the blog, only what was written here is put
     * right: a post merely opened does not read as changed.
     */
    @Test
    fun aPostFromTheBlogIsSettledOnlyWhereItWasWritten() {
        val blogs = "Starý řádek s `“kódem”`.\n\n```\na = “1”\n```"
        assertEquals(blogs, CodeQuotes.settled(blogs, blogs))
        val edited = "Starý řádek s `“kódem”`.\nNový s `echo “x”`.\n\n```\na = “1”\nb = “2”\n```"
        assertEquals("Starý řádek s `“kódem”`.\nNový s `echo \"x\"`.\n\n```\na = “1”\nb = \"2\"\n```", CodeQuotes.settled(edited, blogs))
    }

    /**
     * The editor left: its code has code's quotes, and the caret is
     * where it was -- the text is as long as before. Over a post from
     * the blog, a text nobody wrote in stays the blog's.
     */
    @Test
    fun theEditorSettlesItsTextAndKeepsTheCaret() {
        val state = EditorState()
        state.value = TextFieldValue("Spusť `echo “a”` teď", TextRange(7))
        state.settle()
        assertEquals("Spusť `echo \"a\"` teď", state.text)
        assertEquals(TextRange(7), state.value.selection)

        val opened = EditorState("`“jak to blog má”`")
        opened.original = "`“jak to blog má”`"
        opened.settle()
        assertEquals("`“jak to blog má”`", opened.text)
    }

    /** A selection made code by the key for it gets code's quotes at once; one made bold does not. */
    @Test
    fun whatIsMadeCodeGetsCodesQuotes() {
        val words = Marks.Words(text = "text", url = "https://")
        val text = "Spusť echo “a” teď"
        val from = text.indexOf("echo")
        val code = EditorState()
        code.value = TextFieldValue(text, TextRange(from, from + "echo “a”".length))
        code.touched = true
        code.mark(Marks.Kind.Code, words)
        assertEquals("Spusť `echo \"a\"` teď", code.text)

        val bold = EditorState()
        bold.value = TextFieldValue(text, TextRange(from, from + "echo “a”".length))
        bold.touched = true
        bold.mark(Marks.Kind.Bold, words)
        assertEquals("Spusť **echo “a”** teď", bold.text)
    }
}
