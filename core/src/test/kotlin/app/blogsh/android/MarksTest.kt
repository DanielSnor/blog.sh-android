package app.blogsh.android

import app.blogsh.android.model.EngineJson
import app.blogsh.android.model.Marks
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The marks over the text. They are a port of the /write/ page's own
 * `applyMark`, and the two have to agree: a post marked up on the phone
 * is the post the page would have made.
 */
class MarksTest {
    @Serializable
    private data class Case(val text: String, val start: Int, val end: Int, val kind: String)

    @Serializable
    private data class Expected(val value: String, val start: Int, val end: Int)

    private fun kind(name: String) = Marks.Kind.entries.first { it.name.lowercase() == name }

    private fun apply(text: String, start: Int, end: Int, kind: Marks.Kind): Expected {
        val out = Marks.apply(text, Marks.Range(start, end - start), kind)
        return Expected(out.value, out.selection.location, out.selection.location + out.selection.length)
    }

    /**
     * Every kind, on every text and every selection of the set the port
     * was first checked against. The expected answers are the page's own
     * for all but sixteen cases -- a line mark on an empty first line,
     * where the page was wrong and the port is not.
     */
    @Test
    fun everyCaseOfTheSet() {
        val cases = EngineJson.decodeFromString<List<Case>>(Fixture.text("marks-cases"))
        val expected = EngineJson.decodeFromString<List<Expected>>(Fixture.text("marks-expected"))
        assertEquals(cases.size, expected.size)
        assertTrue(cases.size > 1500)
        val wrong = cases.indices.filter { apply(cases[it].text, cases[it].start, cases[it].end, kind(cases[it].kind)) != expected[it] }
        assertEquals(
            wrong.take(5).joinToString("\n") { "case $it: ${cases[it]} gave ${apply(cases[it].text, cases[it].start, cases[it].end, kind(cases[it].kind))}, not ${expected[it]}" },
            0, wrong.size,
        )
    }

    @Test
    fun boldOnNothingLeavesTheCaretBetweenTheStars() = assertEquals(Expected("****", 2, 2), apply("", 0, 0, Marks.Kind.Bold))

    @Test
    fun aLinkOnNothingSelectsItsAddress() = assertEquals(Expected("[text](https://)", 7, 15), apply("", 0, 0, Marks.Kind.Link))

    @Test
    fun aHeadingStartsItsLine() = assertEquals(Expected("## ", 3, 3), apply("", 0, 0, Marks.Kind.H2))

    @Test
    fun aFenceOpensWithTheCaretInside() = assertEquals(Expected("```\n\n```", 4, 4), apply("", 0, 0, Marks.Kind.Fence))

    /**
     * A selection is counted the way the text field counts it, in UTF-16:
     * a letter with a hook is one, and the mark lands around the word.
     */
    @Test
    fun aSelectionWithCzechLettersIsWrappedWhole() {
        val text = "žluťoučký kůň"
        assertEquals("**žluťoučký kůň**", apply(text, 0, text.length, Marks.Kind.Bold).value)
    }
}
