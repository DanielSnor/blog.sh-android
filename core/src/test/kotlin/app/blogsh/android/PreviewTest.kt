package app.blogsh.android

import app.blogsh.android.model.EngineJson
import app.blogsh.android.model.Lede
import app.blogsh.android.model.Preview
import app.blogsh.android.model.Preview.Shown
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preview of a post while it is written. It is a port of the /write/
 * page's own `renderMarkdown`, and the two have to draw the same page.
 */
class PreviewTest {
    @Serializable
    private data class Case(val markdown: String, val html: String)

    private val shots: Map<String, Shown> = mapOf(
        "cat.jpg" to Shown.Picture("data:image/jpeg;base64,AAAA"),
        "clip.mp4" to Shown.Video("data:video/mp4;base64,BBBB"),
        "a&b.jpg" to Shown.Picture("data:x\"y"),
    )

    /**
     * Every case as the page's own JavaScript rendered it: the fixture is
     * its output, not this port's.
     */
    @Test
    fun everyCaseAsThePageRendersIt() {
        val cases = EngineJson.decodeFromString<List<Case>>(Fixture.text("preview-cases"))
        assertTrue(cases.size > 80)
        val wrong = cases.indices.filter { Preview.render(cases[it].markdown, shots) != cases[it].html }
        assertEquals(
            wrong.take(4).joinToString("\n") { "case $it: ${cases[it].markdown}\n  gave ${Preview.render(cases[it].markdown, shots)}\n  not  ${cases[it].html}" },
            0, wrong.size,
        )
    }

    /**
     * The one departure from the page: the line that cuts a post in two
     * is drawn as a hairline, not printed as words.
     */
    @Test
    fun theLineThatCutsAPostIsAHairline() {
        assertEquals("<p>Intro.</p>\n<hr class=\"teaser-end\">\n<p>Rest.</p>", Preview.render("Intro.\n\n//--more--//\n\nRest."))
        // Alone on its line is enough, as the engine reads it: no blank lines needed, spaces around it allowed.
        assertEquals("<p>Intro.</p>\n<hr class=\"teaser-end\">\n<p>Rest.</p>", Preview.render("Intro.\n  //--more--//\t\nRest."))
    }

    @Test
    fun theSameWordsElsewhereStayWords() {
        assertEquals("<p>see //--more--// here</p>", Preview.render("see //--more--// here"))
        assertEquals("<pre class=\"code-block\"><code>//--more--//</code></pre>", Preview.render("```\n//--more--//\n```"))
        assertTrue(Preview.document("", "", "en").contains("hr.teaser-end{"))
    }

    // pictures in a row

    private val three: Map<String, Shown> =
        mapOf("a.jpg" to Shown.Picture("A"), "b.jpg" to Shown.Picture("B"), "c.jpg" to Shown.Picture("C"), "clip.mp4" to Shown.Video("V"))

    private fun fig(source: String, alt: String = "", span: Boolean = false) =
        "<figure${if (span) " class=\"span-2\"" else ""}><img src=\"$source\" alt=\"$alt\"></figure>"

    /**
     * The second departure from the page, and toward the blog: pictures
     * in a row are a gallery, in the markup the engine writes.
     */
    @Test
    fun twoPicturesInARowAreAGallery() {
        assertEquals("<div class=\"photo-grid\">" + fig("A", "x") + fig("B", "y") + "</div>", Preview.render("![x](a.jpg)\n\n![y](b.jpg)", three))
    }

    @Test
    fun anOddLastPictureSpansBothColumns() {
        assertEquals(
            "<div class=\"photo-grid\">" + fig("A") + fig("B") + fig("C", span = true) + "</div>",
            Preview.render("![](a.jpg)\n\n![](b.jpg)\n\n\n![](c.jpg)\n", three),
        )
    }

    @Test
    fun onePictureAloneIsNoGallery() {
        assertEquals(fig("A"), Preview.render("![](a.jpg)", three))
        assertEquals("<p>text</p>\n" + fig("A") + "\n<p>text</p>", Preview.render("text\n\n![](a.jpg)\n\ntext", three))
    }

    /**
     * Text between two pictures keeps them one under another, as the
     * engine's cheat sheet says; so does a video, which is no picture.
     */
    @Test
    fun textOrAVideoBetweenPicturesBreaksTheRow() {
        assertEquals(fig("A") + "\n<p>text</p>\n" + fig("B"), Preview.render("![](a.jpg)\n\ntext\n\n![](b.jpg)", three))
        val withClip = Preview.render("![](a.jpg)\n\n!![](clip.mp4)\n\n![](b.jpg)", three)
        assertFalse(withClip.contains("photo-grid"))
        assertTrue(withClip.contains("<video"))
    }

    @Test
    fun twoGalleriesStayTwo() {
        val html = Preview.render("![](a.jpg)\n\n![](b.jpg)\n\n## h\n\n![](c.jpg)\n\n![](a.jpg)", three)
        assertEquals(
            "<div class=\"photo-grid\">" + fig("A") + fig("B") + "</div>\n<h3>h</h3>\n<div class=\"photo-grid\">" + fig("C") + fig("A") + "</div>",
            html,
        )
    }

    /**
     * A picture the device cannot show still holds its place in the row;
     * one glued to a line of text is no part of it -- the blog would
     * refuse that post, and the box says so.
     */
    @Test
    fun aMissingPictureHoldsItsPlaceAGluedOneDoesNot() {
        val missing = Preview.render("![](a.jpg)\n\n![](nowhere.jpg)", three)
        assertTrue(missing.startsWith("<div class=\"photo-grid\">" + fig("A") + "<figure><div class=\"no-preview\">"))
        val glued = Preview.render("![](a.jpg)\n\n![](b.jpg)\ntext", three)
        assertFalse(glued.contains("photo-grid"))
        assertTrue(glued.startsWith(fig("A") + "\n<figure><div class=\"no-preview\">b.jpg: a picture has to stand"))
    }

    @Test
    fun thePageKnowsHowToLayAGalleryOutWithoutTheBlog() {
        val page = Preview.document("", "", "en")
        val own = page.indexOf(".photo-grid{display:grid")
        val blogs = page.indexOf("<link rel=\"stylesheet\"")
        assertTrue(own >= 0 && blogs >= 0)
        // Before the blog's stylesheets, so theirs has the last word.
        assertTrue(own < blogs)
        assertTrue(page.contains(".photo-grid .span-2{grid-column:1/-1}"))
    }

    @Test
    fun whatWasTypedCannotBecomeMarkup() {
        assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>", Preview.render("<script>alert(1)</script>"))
        assertFalse(Preview.render("[x](javascript:alert(1))").contains("href"))
        assertFalse(Preview.render("![a\" onerror=\"x](cat.jpg)", shots).contains("\" onerror=\"x\""))
    }

    /**
     * A picture glued to a line of text is a post the blog refuses; the
     * preview says so where the picture would stand.
     */
    @Test
    fun aPictureNotOnALineOfItsOwnIsSaidSo() {
        val html = Preview.render("text\n![a](cat.jpg)", shots)
        assertTrue(html.contains("no-preview"))
        assertTrue(html.contains("cat.jpg: a picture has to stand on a line of its own"))
        assertFalse(html.contains("<img"))
    }

    /** A video too large to hand a page is shown by one frame of it. */
    @Test
    fun aVideoMayBeShownByAFrame() {
        val html = Preview.render("!![a clip](clip.mp4)", mapOf("clip.mp4" to Shown.Frame("data:image/jpeg;base64,CCCC")))
        assertEquals("<figure><img src=\"data:image/jpeg;base64,CCCC\" alt=\"a clip\"><figcaption>a clip</figcaption></figure>", html)
    }

    @Test
    fun theSentencesComeInTheReadersLanguage() {
        val words = Preview.Words(missing = { "chybí $it" }, glued = { "přilepený $it" })
        assertTrue(Preview.render("![a](x.jpg)", words = words).contains("chybí x.jpg"))
        assertTrue(Preview.render("a\n![a](x.jpg)", words = words).contains("přilepený x.jpg"))
    }

    @Test
    fun thePageWearsTheBlogsStylesheetsAndItsTitle() {
        val page = Preview.document(" A <Title> ", "<p>x</p>", "cs")
        assertTrue(page.contains("<html lang=\"cs\">"))
        assertTrue(page.contains("<base href=\"/\">"))
        assertTrue(page.contains("<link rel=\"stylesheet\" href=\"/assets/css/colors.css\"><link rel=\"stylesheet\" href=\"/assets/css/site.css\">"))
        // Nested as the engine's post page is: the stylesheet's margins count on it.
        assertTrue(
            page.contains(
                "<main><div class=\"card\"><article><div class=\"post-header\"><div class=\"post-body\">" +
                    "<h1>A &lt;Title&gt;</h1><div class=\"content\"><p>x</p></div></div></div></article></div></main>"
            )
        )
        assertFalse(Preview.document("  ", "", "en").contains("<h1>"))
    }

    /**
     * A post opened for editing comes with its header; the preview shows
     * the title the header gives and the text under it.
     */
    @Test
    fun aTextWithAHeaderIsItsTitleAndItsBody() {
        val both = Preview.parts("---\ntitle: Nouzovka\ntags: a, b\n---\n\nText.\n")
        assertEquals("Nouzovka", both.first)
        assertEquals("\nText.\n", both.second)
        assertEquals("", Preview.parts("Just text.").first)
        assertEquals("Just text.", Preview.parts("Just text.").second)
        assertEquals("\nText.", Preview.parts("---\n---\n\nText.").second)
        assertEquals("---\nnever closed", Preview.parts("---\nnever closed").second)
    }
}

/** How a post begins, where a post is picked. */
class LedeTest {
    @Test
    fun upToTheCutTheAuthorMade() {
        assertEquals("First.\n\nSecond.", Lede.of("---\ntitle: T\n---\n\nFirst.\n\nSecond.\n\n//--more--//\n\nThird.").words)
    }

    @Test
    fun withoutACutTheFirstParagraph() {
        assertEquals("First line still first.", Lede.of("First line\nstill first.\n\nSecond.").words)
    }

    @Test
    fun theMarksAreTakenOff() {
        assertEquals(
            "A bold link with code, em and gone",
            Lede.of("## A **bold** [link](https://x.cz/a_(b)) with `code`, *em* and ~~gone~~\n\nrest").words,
        )
        assertEquals("quoted words", Lede.of("> quoted words").words)
    }

    /**
     * A post that opens with its picture begins with its words all the
     * same; one that is only a picture is known by the description.
     */
    @Test
    fun aPictureIsNotWordsButItsDescriptionIsKept() {
        val both = Lede.of("![a cat](cat.jpg)\n\nThe words.")
        assertEquals("The words.", both.words)
        assertEquals("a cat", both.picture)
        val only = Lede.of("---\ntags: x\n---\n\n![](one.jpg)\n\n!![the waterfall](clip.mp4)\n")
        assertEquals("", only.words)
        assertEquals("the waterfall", only.picture)
    }

    @Test
    fun aPostWithNothingHasNothing() {
        assertEquals(Lede("", null), Lede.of("---\ntitle: T\n---\n\n"))
        assertEquals(Lede("", null), Lede.of("![](one.jpg)"))
    }

    @Test
    fun codeIsNotHowAPostBegins() {
        assertEquals("Then the words.", Lede.of("```\nputs 1\n```\n\nThen the words.").words)
    }

    /** A cut on the first line leaves nothing above it: the post begins after it. */
    @Test
    fun aCutAtTheVeryTopIsSteppedOver() {
        assertEquals("After the cut.", Lede.of("//--more--//\n\nAfter the cut.\n\nMore.").words)
    }

    @Test
    fun aLongBeginningEndsOnAWord() {
        val long = List(300) { "slovo" }.joinToString(" ")
        val words = Lede.of(long).words
        assertTrue(words.length <= Lede.LIMIT + 1)
        assertTrue(words.endsWith("slovo…"))
    }
}
