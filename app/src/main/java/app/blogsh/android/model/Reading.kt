package app.blogsh.android.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

// How the app is read on this device: the size of its type, the language
// it speaks, and whose colours it wears. The device's own and not a
// blog's -- eyes do not change with the blog.

/**
 * How large the app sets its type: as the system has it, or one to four
 * steps above that. The steps stand on the system's size: somebody who
 * already reads large gets larger, never smaller.
 */
enum class TextSize(
    /** How much larger every face is drawn, and a page of the blog with it. */
    val zoom: Float,
    /** The two letters that stand for it where it is chosen, in sp. */
    val sample: Float,
) {
    System(1f, 14f), One(1.12f, 16f), Two(1.24f, 18f), Three(1.35f, 21f), Four(1.65f, 25f);

    companion object {
        /** Where the choice is kept. */
        const val KEY = "textSize"

        /** What is kept, read back; anything else is the system's size. */
        fun kept(value: Int?): TextSize = entries.getOrNull(value ?: 0) ?: System
    }
}

/**
 * The language the app speaks: the system's, or one of the three it is
 * written in. Like a language chosen in the system's own settings, it is
 * taken up when the app is next started, not in the middle of a screen.
 */
enum class AppLanguage(
    /** Its name in itself, the way a list of languages says them; the system's has none of its own. */
    val title: String?,
) {
    System(null), Cs("Čeština"), De("Deutsch"), En("English");

    /** What is written down for it; nothing for the system's. */
    val kept: String? get() = if (this == System) null else name.lowercase(Locale.ROOT)

    /** The locale the app's words are read in; none for the system's. */
    val locale: Locale? get() = kept?.let { Locale.forLanguageTag(it) }

    companion object {
        const val KEY = "language"

        /**
         * What is kept, read back: the language, whatever region it
         * carries. Nothing, or a language the app is not written in, is the
         * system's.
         */
        fun kept(value: String?): AppLanguage {
            val code = (value ?: "").split('-', '_').firstOrNull()?.lowercase(Locale.ROOT) ?: ""
            return when (code) {
                "cs" -> Cs
                "de" -> De
                "en" -> En
                else -> System
            }
        }

        fun read(from: Notes): AppLanguage = kept(from.read(KEY))
    }

    fun write(to: Notes) = to.write(KEY, kept)
}

/**
 * Whose colours the app wears: the open blog's -- its ground, its ink,
 * its rules, as its pages have them -- or its own, when the blog has
 * said none or when it is asked to keep to its own. The last is for eyes
 * that a blog's palette does not serve: the app's own are always the
 * same, whatever a blog chose.
 */
class Reading(private val notes: Notes) {
    /** Keep to the app's own colours, the accent included. */
    var ownColours by mutableStateOf(notes.read(OWN_KEY) == "yes")
        private set

    var textSize by mutableStateOf(TextSize.kept(notes.read(TextSize.KEY)?.toIntOrNull()))
        private set

    /** What was chosen; what the app speaks now is what was chosen when it started. */
    var language by mutableStateOf(AppLanguage.read(notes))
        private set

    fun keepOwnColours(own: Boolean) {
        ownColours = own
        notes.write(OWN_KEY, if (own) "yes" else null)
    }

    fun set(size: TextSize) {
        textSize = size
        notes.write(TextSize.KEY, if (size == TextSize.System) null else size.ordinal.toString())
    }

    fun speak(chosen: AppLanguage) {
        language = chosen
        chosen.write(notes)
    }

    companion object {
        const val OWN_KEY = "ownColours"

        /** The one the app has. */
        val shared: Reading by lazy { Reading(BlogShelf.notes) }
    }
}
