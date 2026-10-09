package app.blogsh.android

import app.blogsh.android.model.BuildStamp
import app.blogsh.android.model.Colouring
import app.blogsh.android.model.Colours
import app.blogsh.android.model.Hsv
import app.blogsh.android.model.Shades
import app.blogsh.android.model.Tones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Whose colours the app wears, and the ones chosen on the device. A
 * mistake here is an app in somebody else's colours, or one that cannot
 * be read to be put right.
 */
class ColoursTest {
    private val cream = Tones("#fff7eb", "#1e1d1c", "#6b6862", "#d7d0c6")
    private val night = Tones("#000000", "#e6dccb", "#a1988a", "#3c3935")
    private val blog = Colours.said(cream, night, "#a81800", "#ff7a5c")

    @Test
    fun theChoiceIsReadBackAndTheOldSwitchWithIt() {
        assertEquals(Colouring.Chosen, Colouring.kept("chosen", switchedToOwn = false))
        assertEquals(Colouring.Own, Colouring.kept("own", switchedToOwn = false))
        assertEquals(Colouring.Blog, Colouring.kept("blog", switchedToOwn = true))
        // From before there were three: the switch, on or off.
        assertEquals(Colouring.Own, Colouring.kept(null, switchedToOwn = true))
        assertEquals(Colouring.Blog, Colouring.kept(null, switchedToOwn = false))
        assertEquals(Colouring.Blog, Colouring.kept("rainbow", switchedToOwn = false))
    }

    @Test
    fun aBlogSaysItsPaletteAndItsAccents() {
        assertEquals(0xFFF7EBL, blog.light.bg)
        assertEquals(0xE6DCCBL, blog.dark.text)
        assertEquals(0xA81800L, blog.light.accent)
        assertEquals(0xFF7A5CL, blog.dark.accent)
    }

    /**
     * Each on its own, as before: a blog with an accent and no palette
     * wears its accent on the app's ground, and the other way round.
     */
    @Test
    fun whatABlogDidNotSayIsTheAppsOwn() {
        val accentOnly = Colours.said(null, null, "#a81800", "")
        assertEquals(Tones.ownLight.bg, accentOnly.light.bg)
        assertEquals(0xA81800L, accentOnly.light.accent)
        assertEquals(Tones.ownAccentDark, accentOnly.dark.accent)
        val silent = Colours.said(null, null, "", "teal")
        assertEquals(Colours.own, silent)
    }

    @Test
    fun whatIsWornGoesByTheChoice() {
        val mine = Colours.own.with(dark = false, Shades.Part.Bg, 0x123456)
        assertEquals(blog, Colours.worn(Colouring.Blog, mine, blog))
        assertEquals(Colours.own, Colours.worn(Colouring.Own, mine, blog))
        assertEquals(mine, Colours.worn(Colouring.Chosen, mine, blog))
        // Asked for the chosen ones before any were: the blog's.
        assertEquals(blog, Colours.worn(Colouring.Chosen, null, blog))
    }

    @Test
    fun theChosenOnesAreKeptAndReadBack() {
        val mine = blog.with(dark = true, Shades.Part.Accent, 0x00FF88)
        assertEquals(0x00FF88L, mine.dark.accent)
        assertEquals(blog.light, mine.light)
        assertEquals(mine, Colours.kept(mine.kept))
        assertNull(Colours.kept(null))
        assertNull(Colours.kept("{}"))
        assertNull(Colours.kept("not json"))
    }

    @Test
    fun everyPartOfASchemeIsOneOfItsFive() {
        var shades = Colours.own.light
        Shades.Part.entries.forEachIndexed { step, part -> shades = shades.with(part, step + 1L) }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), listOf(shades.bg, shades.text, shades.metaText, shades.border, shades.accent))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), Shades.Part.entries.map { shades[it] })
    }

    /** The line under which the settings themselves could not be read. */
    @Test
    fun writingThatCannotBeToldFromItsGroundIsNotLegible() {
        var shades = Colours.own.light
        assertTrue(shades.legible)
        assertTrue(Colours.own.dark.legible)
        shades = shades.copy(text = shades.bg)
        assertEquals(1.0, shades.contrast, 0.0)
        assertFalse(shades.legible)
        shades = shades.copy(bg = 0xFFFFFF, text = 0xF0F0F0)
        assertFalse(shades.legible)
        // Faint, and still there to be read.
        shades = shades.copy(text = 0x999999)
        assertTrue(shades.legible)
        shades = shades.copy(bg = 0x000000, text = 0xFFFFFF)
        assertTrue(abs(shades.contrast - 21) < 0.001)
    }

    /**
     * The picker moves a colour around the wheel, away from grey and up
     * from black, and what it comes to is the number that was put in.
     */
    @Test
    fun aColourGoesThroughThePickerAndComesBackTheSame() {
        for (number in listOf(0x000000L, 0xFFFFFFL, 0x808080L, 0x1DA1F2L, 0xA81800L, 0xFFF7EBL, 0x00FF88L, 0x123456L, 0xFF0000L, 0x00FF00L, 0x0000FFL, 0xFF00FFL)) {
            assertEquals(number, Hsv.of(number).number)
        }
        for (number in 0L..0xFFFFFFL step 4099) assertEquals(number, Hsv.of(number).number)
        assertEquals(Hsv(0f, 1f, 1f), Hsv.of(0xFF0000))
        assertEquals(120f, Hsv.of(0x00FF00).hue, 0.001f)
        assertEquals(240f, Hsv.of(0x0000FF).hue, 0.001f)
        // A grey is nowhere on the wheel, and a black is black wherever it stood.
        assertEquals(0f, Hsv.of(0x808080).saturation, 0f)
        assertEquals(0L, Hsv(200f, 0.5f, 0f).number)
        assertEquals(0xFFFFFFL, Hsv(200f, 0f, 1f).number)
        // The wheel closes: its end is its beginning.
        assertEquals(Hsv(0f, 1f, 1f).number, Hsv(360f, 1f, 1f).number)
    }
}

/**
 * The lines in the settings that say which build this is. A mistake here
 * is a version nobody can name.
 */
class BuildStampTest {
    @Test
    fun theTwoLinesAreTheCommitAndTheTime() {
        val stamp = BuildStamp.of("e838f47\n2026-10-07T15:10:00Z\n")
        assertEquals("e838f47", stamp.commit)
        assertEquals(1_791_385_800_000L, stamp.built)
    }

    /** Built from a tree with changes not committed: the commit says so. */
    @Test
    fun aTreeWithChangesIsMarked() {
        assertEquals("e838f47+", BuildStamp.of("e838f47+\n2026-10-07T15:10:00Z").commit)
    }

    @Test
    fun whatIsNotAStampIsNone() {
        assertEquals(BuildStamp(null, null), BuildStamp.of(""))
        assertNull(BuildStamp.of("fatal: not a git repository\nyesterday").commit)
        assertNull(BuildStamp.of("fatal: not a git repository\nyesterday").built)
        // A build outside a repository: no commit, the time all the same.
        val bare = BuildStamp.of("\n2026-10-07T15:10:00Z\n")
        assertNull(bare.commit)
        assertNotNull(bare.built)
    }

    /**
     * What is out of reach is drawn in one tone: the ink mixed with the
     * ground, 45 to 55 -- between the muted tone and a hairline, on a
     * light ground and on a dark one.
     */
    @Test
    fun whatIsOutOfReachIsTheInkMixedIntoTheGround() {
        val day = Colours.own.light.copy(text = 0x1E1D1C, bg = 0xFFF7EB)
        assertEquals(0x9A958EL, day.faded)
        val night = Colours.own.dark.copy(text = 0xE6DCCB, bg = 0x000000)
        // 230 x 0.45 is 103.5: a half goes up, as a browser rounds it.
        assertEquals(0x68635BL, night.faded)
        // The same ink and ground: nothing to mix.
        assertEquals(0x000000L, night.copy(text = 0x000000).faded)
    }
}
