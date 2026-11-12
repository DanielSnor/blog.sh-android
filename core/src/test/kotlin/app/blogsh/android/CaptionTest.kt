package app.blogsh.android

import app.blogsh.android.model.Kept
import app.blogsh.android.model.Preview
import app.blogsh.android.model.Shot
import app.blogsh.android.model.Unsaved
import app.blogsh.android.model.Unsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A picture's mark with a caption after the name. A mistake here is a
 * picture the app calls deleted when it is not, does not send when it
 * should, or draws as a line of text.
 */
class CaptionTest {
    /**
     * A picture with a caption after its name -- `![...](file "caption")`,
     * which the engine writes back for every picture that has one -- is
     * a picture the text names. Read as not named, a draft's four
     * pictures were all called "deleted on save".
     */
    @Test
    fun aPictureWithACaptionIsNamed() {
        val text = """
            Text.

            ![Titulka blogsh.app ve světlém režimu v Minimalu: modrý filtr „all“.](01.png "Titulka ve dne. Stejné řádky, jen místo oranžové modrá.")

            ![Tatáž titulka v tmavém režimu.](02.png „V noci. Tady je modrá možná ještě lepší…d8-D“)

            ![The page of a post.](03.png “A post. The date in the accent.”)

            ![Bez popisku](04.png)

            ![An escaped quote](05.png "He said \"no\" and left.")
        """.trimIndent()
        val media = listOf("01.png", "02.png", "03.png", "04.png", "05.png")
        for (name in media) assertTrue(name, Kept.named(name, text))
        assertTrue(Kept.dropped(media, text).isEmpty())
        assertFalse(Kept.asksBeforeSaving(media, text))
        // What is really gone is still seen to be gone.
        assertEquals(listOf("06.png"), Kept.dropped(media + "06.png", text))
        // A name that only begins or ends another's is not that other.
        assertFalse(Kept.named("1.png", "![a](01.png \"x\")"))
        assertFalse(Kept.named("03.pn", text))
    }

    /**
     * A new picture whose mark was given a caption by hand still travels
     * with the text, and its card and its mark still say the same.
     */
    @Test
    fun aCaptionDoesNotPartAShotFromItsMark() {
        val shot = Shot("photo-1.jpg", ByteArray(0), 1, 1, "A hill")
        val text = "Before.\n\n![A hill](photo-1.jpg \"Seen from the road.\")\n\nAfter."
        assertEquals(listOf("photo-1.jpg"), Kept.sent(listOf(shot), text).map { it.name })
        assertEquals("A hill", Kept.described("photo-1.jpg", text))
        // Written on the card: the description changes, the caption stays.
        assertEquals(
            "Before.\n\n![A green hill](photo-1.jpg \"Seen from the road.\")\n\nAfter.",
            Kept.typed(text, "photo-1.jpg", "A green hill"),
        )
        // Removed: the whole mark goes, caption and all.
        assertEquals("![A hill](photo-1.jpg \"Seen from the road.\")", shot.markPattern.find(text)?.value)
        // Without a caption everything is as it was.
        assertEquals("![A \$1 hill](photo-1.jpg)", Kept.typed("![A hill](photo-1.jpg)", "photo-1.jpg", "A \$1 hill"))
    }

    /**
     * The preview draws a picture with a caption as a picture, with its
     * caption under it -- not as a line of text.
     */
    @Test
    fun thePreviewDrawsAPictureWithItsCaption() {
        val shots = mapOf<String, Preview.Shown>("01.png" to Preview.Shown.Picture("x://01.png"))
        val html = Preview.render("![Alt words](01.png \"The caption.\")", shots)
        assertTrue(html, html.contains("<img src=\"x://01.png\" alt=\"Alt words\">"))
        assertTrue(html, html.contains("<figcaption>The caption.</figcaption>"))
        assertFalse(html, html.contains("![Alt"))
        val typed = Preview.render("![Alt](01.png „Popisek.“)", shots)
        assertTrue(typed, typed.contains("<figcaption>Popisek.</figcaption>"))
        val escaped = Preview.render("![Alt](01.png \"He said \\\"no\\\".\")", shots)
        assertTrue(escaped, escaped.contains("<figcaption>He said &quot;no&quot;.</figcaption>"))
        val bare = Preview.render("![Alt](01.png)", shots)
        assertTrue(bare, bare.contains("<img") && !bare.contains("figcaption"))
    }

    /**
     * Changes kept on the device that name only the post's own pictures,
     * captions and all, name no picture that was not kept.
     */
    @Test
    fun keptChangesWithCaptionsNameNoPictureBeyondThePosts() {
        val kept = Unsaved("![a](01.png \"One.\")\n\n![b](02.png „Dva.“)", "K0", 1, null)
        assertFalse(kept.namesPictures(listOf("01.png", "02.png")))
        assertTrue(kept.namesPictures(listOf("01.png")))
        assertTrue(Unsent(text = "![a](photo-1.jpg \"One.\")").namesPictures)
    }
    /**
     * A save that would delete a picture is asked about every time --
     * also where the post is left with fewer pictures than it had, which
     * the blog used to refuse and now takes.
     */
    @Test
    fun aSaveThatDeletesAPictureIsAlwaysAskedAbout() {
        val media = listOf("01.jpg", "02.jpg")
        assertTrue(Kept.asksBeforeSaving(media, "![a](01.jpg)"))
        assertTrue(Kept.asksBeforeSaving(media, "no pictures at all"))
        assertTrue(Kept.asksBeforeSaving(media, "![a](01.jpg) ![c](photo-3.jpg)"))
        assertFalse(Kept.asksBeforeSaving(media, "![a](01.jpg)\n\n![b](02.jpg)"))
        assertFalse(Kept.asksBeforeSaving(emptyList(), "text"))
    }
}
