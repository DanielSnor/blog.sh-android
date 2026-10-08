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
enum class TextSize {
    System, One, Two, Three, Four;

    /**
     * How many sizes of the system's own scale it stands above the
     * system's: one after another on a phone, in longer strides on a
     * wide screen -- a tablet is read from further away and has the room,
     * so its last step is a good deal past where a phone's ends.
     */
    fun strides(wide: Boolean = false): Int = if (wide) listOf(0, 2, 4, 5, 6)[ordinal] else ordinal

    /**
     * How much larger every face is drawn, and a page of the blog with
     * it: what that many sizes of the system's scale come to, from its
     * usual one.
     */
    fun zoom(wide: Boolean = false): Float = listOf(1f, 1.12f, 1.24f, 1.35f, 1.65f, 1.94f, 2.35f)[strides(wide)]

    /** The two letters that stand for it where it is chosen, in sp. */
    fun sample(wide: Boolean = false): Float = (if (wide) listOf(14f, 18f, 23f, 27f, 32f) else listOf(14f, 16f, 18f, 21f, 25f))[ordinal]

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
 * How the app is read on this device, as it is chosen in the settings
 * and kept: whose colours it wears and the ones chosen here -- the open
 * blog's, the app's own, or the ones somebody chose on this device,
 * colour by colour -- the size of its type, and its language. The
 * device's, not a blog's: eyes do not change with the blog.
 */
class Reading(private val notes: Notes) {
    var wearing by mutableStateOf(Colouring.kept(notes.read(Colouring.KEY), switchedToOwn = notes.read(Colouring.SWITCH_KEY) == "yes"))
        private set

    /** The ones chosen here; null until somebody chose. */
    var chosen by mutableStateOf(Colours.kept(notes.read(Colours.KEY)))
        private set

    var textSize by mutableStateOf(TextSize.kept(notes.read(TextSize.KEY)?.toIntOrNull()))
        private set

    /** What was chosen; what the app speaks now is what was chosen when it started. */
    var language by mutableStateOf(AppLanguage.read(notes))
        private set

    fun wear(choice: Colouring) {
        wearing = choice
        notes.write(Colouring.KEY, choice.word)
    }

    /**
     * Wears the chosen ones -- which, the first time, are the ones worn
     * until then, so that choosing starts from a screen that reads.
     */
    fun wearChosen(worn: Colours) {
        if (chosen == null) choose(worn)
        wear(Colouring.Chosen)
    }

    fun choose(colours: Colours?) {
        chosen = colours
        notes.write(Colours.KEY, colours?.kept)
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
        /** The one the app has. */
        val shared: Reading by lazy { Reading(BlogShelf.notes) }
    }
}
