package app.blogsh.android

import app.blogsh.android.model.AppLanguage
import app.blogsh.android.model.Reading
import app.blogsh.android.model.TextSize
import app.blogsh.android.model.Tones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colours a blog says and the ones the app wears. A mistake here is
 * a screen in half a palette, or one that cannot be read at all.
 */
class TonesTest {
    private val cream = Tones("#fff7eb", "#1e1d1c", "#6b6862", "#d7d0c6")
    private val night = Tones("#000000", "#e6dccb", "#a1988a", "#3c3935")

    @Test
    fun aColourIsReadTheWayAPaletteWritesIt() {
        assertEquals(0xFFF7EBL, Tones.value("#fff7eb"))
        assertEquals(0xFFF7EBL, Tones.value("#FFF7EB"))
        assertEquals(0L, Tones.value(" #000000 "))
        assertEquals(0xFFCC00L, Tones.value("#fc0"))
    }

    @Test
    fun whatIsNotAColourIsNone() {
        for (word in listOf("fff7eb", "#fff7e", "#gggggg", "red", "rgb(1, 2, 3)", "")) assertNull(word, Tones.value(word))
    }

    @Test
    fun aPaletteReadsWholeOrNotAtAll() {
        assertEquals(Tones.Scheme(0xFFF7EB, 0x1E1D1C, 0x6B6862, 0xD7D0C6), cream.values)
        assertNull(cream.copy(border = "papayawhip").values)
    }

    @Test
    fun theBlogsPaletteIsWornWhenItHasSaidBothSchemes() {
        val worn = Tones.worn(own = false, light = cream, dark = night)
        assertEquals(0xFFF7EBL, worn.first.bg)
        assertEquals(0xE6DCCBL, worn.second.text)
    }

    /** Half a palette is none: a cream day with the app's own night would be two looks in one app. */
    @Test
    fun withoutBothSchemesTheAppWearsItsOwn() {
        val own = Tones.ownLight to Tones.ownDark
        assertEquals(own, Tones.worn(own = false, light = null, dark = null))
        assertEquals(own, Tones.worn(own = false, light = cream, dark = null))
        assertEquals(own, Tones.worn(own = false, light = null, dark = night))
        assertEquals(own, Tones.worn(own = false, light = cream, dark = night.copy(bg = "black")))
    }

    @Test
    fun askedToKeepToItsOwnTheAppDoesWhateverTheBlogSaid() {
        assertEquals(Tones.ownLight to Tones.ownDark, Tones.worn(own = true, light = cream, dark = night))
    }

    /** The app's own is the palette the engine ships with, to the last hex. */
    @Test
    fun theAppsOwnIsTheEnginesShippedPalette() {
        assertEquals(Tones.Scheme(0xF5F8FA, 0x444A5A, 0x657784, 0xE1E8ED), Tones.ownLight)
        assertEquals(Tones.Scheme(0x111111, 0xFFFFFF, 0x6A7F8C, 0x263340), Tones.ownDark)
        assertEquals(0x1DA1F2L, Tones.ownAccentLight)
        assertEquals(0x4AB3F4L, Tones.ownAccentDark)
    }
}

/**
 * The size of the type chosen in the settings. A mistake here is type
 * that grows the wrong way, or a kept choice that no longer reads.
 */
class TextSizeTest {
    @Test
    fun aChoiceThatIsNotOneIsTheSystems() {
        assertEquals(TextSize.System, TextSize.kept(0))
        assertEquals(TextSize.Four, TextSize.kept(4))
        assertEquals(TextSize.System, TextSize.kept(5))
        assertEquals(TextSize.System, TextSize.kept(-1))
        assertEquals(TextSize.System, TextSize.kept(null))
    }

    /** The type, a page of the blog and the letters in the picker grow with the steps. */
    @Test
    fun everyStepIsLargerThanTheOneBefore() {
        val all = TextSize.entries
        assertEquals(1f, TextSize.System.zoom)
        for ((smaller, larger) in all.zip(all.drop(1))) {
            assertTrue(smaller.zoom < larger.zoom)
            assertTrue(smaller.sample < larger.sample)
        }
    }

    /** The steps stand on what the system has: somebody who already reads large gets larger, never smaller. */
    @Test
    fun noStepMakesTheTypeSmaller() {
        for (step in TextSize.entries) assertTrue(step.zoom >= 1f)
    }
}

/**
 * The language chosen in the settings. A mistake here is an app that
 * starts in a language nobody chose, or cannot be given back to the system.
 */
class AppLanguageTest {
    @Test
    fun nothingKeptIsTheSystems() {
        assertEquals(AppLanguage.System, AppLanguage.kept(null))
        assertEquals(AppLanguage.System, AppLanguage.kept(""))
    }

    @Test
    fun aLanguageIsReadBack() {
        assertEquals(AppLanguage.De, AppLanguage.kept("de"))
        assertEquals(AppLanguage.Cs, AppLanguage.kept("cs"))
        assertEquals(AppLanguage.En, AppLanguage.kept("en"))
    }

    /** The system writes a language with its region. */
    @Test
    fun aRegionDoesNotMakeItAnotherLanguage() {
        assertEquals(AppLanguage.Cs, AppLanguage.kept("cs-CZ"))
        assertEquals(AppLanguage.De, AppLanguage.kept("de_AT"))
        assertEquals(AppLanguage.En, AppLanguage.kept("en-GB"))
        assertEquals(AppLanguage.En, AppLanguage.kept("EN"))
    }

    @Test
    fun aLanguageTheAppIsNotWrittenInIsTheSystems() {
        assertEquals(AppLanguage.System, AppLanguage.kept("fr"))
        assertEquals(AppLanguage.System, AppLanguage.kept("system"))
    }

    @Test
    fun whatIsWrittenDownReadsBackAsItself() {
        for (language in AppLanguage.entries) assertEquals(language, AppLanguage.kept(language.kept))
        assertNull(AppLanguage.System.kept)
    }

    @Test
    fun onlyTheSystemsHasNoNameOfItsOwn() {
        assertNull(AppLanguage.System.title)
        for (language in AppLanguage.entries) if (language != AppLanguage.System) assertFalse(language.title.isNullOrEmpty())
    }

    /** Kept among the app's own settings, and taken out again for the system's. */
    @Test
    fun itIsKeptAndGivenBack() {
        val notes = MemoryNotes()
        assertEquals(AppLanguage.System, AppLanguage.read(notes))
        AppLanguage.De.write(notes)
        assertEquals(AppLanguage.De, AppLanguage.read(notes))
        AppLanguage.Cs.write(notes)
        assertEquals(AppLanguage.Cs, AppLanguage.read(notes))
        AppLanguage.System.write(notes)
        assertEquals(AppLanguage.System, AppLanguage.read(notes))
        assertNull(notes.read(AppLanguage.KEY))
    }

    /** What is chosen for reading is kept for the device, and read back when the app next starts. */
    @Test
    fun howTheAppIsReadIsKept() {
        val notes = MemoryNotes()
        val reading = Reading(notes)
        assertFalse(reading.ownColours)
        assertEquals(TextSize.System, reading.textSize)
        reading.keepOwnColours(true)
        reading.set(TextSize.Three)
        reading.speak(AppLanguage.De)
        val again = Reading(notes)
        assertTrue(again.ownColours)
        assertEquals(TextSize.Three, again.textSize)
        assertEquals(AppLanguage.De, again.language)
        again.keepOwnColours(false)
        again.set(TextSize.System)
        assertFalse(Reading(notes).ownColours)
        assertEquals(TextSize.System, Reading(notes).textSize)
    }
}
