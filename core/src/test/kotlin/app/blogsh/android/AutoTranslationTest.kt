package app.blogsh.android

import app.blogsh.android.model.AutoTranslation
import app.blogsh.android.model.EngineJson
import app.blogsh.android.model.TranslationAnswer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server's translator as the app sees it (engine 1.12): whether it is
 * offered, what it is asked with, and what it says of its own text. The
 * answers are the engine's own, made on a trial site with a translator
 * that only marks the words.
 */
class AutoTranslationTest {
    private fun answer(name: String) = EngineJson.decodeFromString<TranslationAnswer>(Fixture.text(name))

    @Test
    fun aServerWithATranslatorOffersIt() {
        val auto = answer("translate-auto-available").auto!!
        assertTrue(auto.available)
        assertTrue(auto.offered)
        assertNull(auto.warnings)
    }

    /**
     * The translator's text comes in place of the language's, nothing
     * saved, with what looked wrong in it -- here a number it changed.
     */
    @Test
    fun theTranslatorsTextComesWithWhatLookedWrong() {
        val answer = answer("translate-auto-done")
        assertTrue(answer.post.text.contains("[EN]"))
        assertFalse(answer.post.written)
        val warnings = answer.auto!!.warnings!!
        assertEquals(listOf("numbers"), warnings.map { it.code })
        assertTrue(warnings.first().message.isNotEmpty())
    }

    /**
     * A server that does not let a paired device ask says so, and why, in
     * its own words -- which the screen shows in place of the key.
     */
    @Test
    fun aServerThatSaysNoSaysWhatToSet() {
        val auto = answer("translate-auto-denied").auto!!
        assertFalse(auto.available)
        assertEquals("translator_not_allowed", auto.problem)
        assertTrue(auto.message!!.contains("TRANSLATOR_REMOTE"))
        assertTrue(auto.offered)
    }

    /** A server that says no and gives no reason has nothing to show. */
    @Test
    fun aRefusalWithoutWordsShowsNothing() {
        assertFalse(AutoTranslation(available = false, problem = "translator_not_set").offered)
        assertFalse(AutoTranslation(available = false, message = "").offered)
    }

    /** An engine before 1.12 says nothing of a translator, and nothing is offered. */
    @Test
    fun anOlderEngineOffersNothing() {
        val whole = EngineJson.parseToJsonElement(Fixture.text("translate-auto-available")).jsonObject
        val older = JsonObject(whole - "auto")
        assertNull(EngineJson.decodeFromString<TranslationAnswer>(older.toString()).auto)
    }

    /**
     * Asked as the server's door lets it in, and asked again on purpose
     * only over words the language has already.
     */
    @Test
    fun itIsAskedAgainOnlyOverWordsAlreadyThere() {
        assertEquals(listOf("translate", "pet-kroku", "--lang", "en", "--auto"), AutoTranslation.args("pet-kroku", "en", written = false))
        assertEquals(listOf("translate", "pet-kroku", "--lang", "en", "--auto", "--force"), AutoTranslation.args("pet-kroku", "en", written = true))
    }
}
