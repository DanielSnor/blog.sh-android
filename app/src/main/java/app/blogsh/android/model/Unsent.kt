package app.blogsh.android.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable

// What is being written on this device and has not gone to the blog yet.
// Android may stop an app that is out of sight at any moment, and a hand
// may take the way back by mistake: so the words are kept at every
// letter, under the blog they are written for, and are back in their
// form the next time it opens. Times are milliseconds since 1970.

/**
 * A post being written and not sent yet: its title, its tags, its text.
 *
 * The pictures are not kept: they are large, and come back from the
 * library in a moment. The marks the text has for them are text, and
 * stay; a picture chosen again takes its name and its description back
 * from its mark.
 */
@Serializable
data class Unsent(
    val title: String = "",
    val tags: String = "",
    val text: String = "",
    /** When it was last written in. */
    val at: Long = 0,
) {
    /** Nothing worth keeping: spaces and line breaks are not writing. */
    val isEmpty: Boolean get() = listOf(title, tags, text).all { it.isBlank() }

    /**
     * What to call it where it is listed: its title, or -- written
     * without one -- the first words of its text.
     */
    val headline: String
        get() {
            val named = title.trim()
            if (named.isNotEmpty()) return named
            val first = text.lines().map { it.trim(' ', '\t') }.firstOrNull { it.isNotEmpty() && !it.startsWith("!") } ?: ""
            return first.dropWhile { it == '#' || it == ' ' }.take(60)
        }

    /** The text names a picture or a video, which was not kept with it. */
    val namesPictures: Boolean get() = Regex("""!{1,2}\[[^\n]*\]\([^)\s]+\)""").containsMatchIn(text)

    /**
     * Keeps it for this blog -- or, emptied, keeps nothing: a form that
     * was sent, or cleared by hand, leaves no post behind to bring back.
     * What is kept already, word for word, is left as it is and keeps
     * its time: a form that was only opened was not written in.
     */
    fun keep(blog: String, notes: Notes) {
        if (isEmpty) {
            notes.write(key(blog), null)
            return
        }
        val kept = kept(blog, notes)
        if (kept != null && kept.title == title && kept.tags == tags && kept.text == text) return
        notes.write(key(blog), EngineJson.encodeToString(serializer(), this))
    }

    companion object {
        fun key(blog: String): String = "unsent.$blog"

        /** What is kept for this blog; nothing where nothing is, or where what is there does not read. */
        fun kept(blog: String, notes: Notes): Unsent? {
            val written = notes.read(key(blog)) ?: return null
            return runCatching { EngineJson.decodeFromString(serializer(), written) }.getOrNull()?.takeIf { !it.isEmpty }
        }

        fun forget(blog: String, notes: Notes) = notes.write(key(blog), null)

        /**
         * What the form kept under its old name, before it was kept here:
         * read once and moved, so an update of the app loses nobody's words.
         */
        fun carriedOver(blog: String, notes: Notes, now: Long): Unsent? {
            val old = "compose.draft.$blog"
            val written = notes.read(old) ?: return null
            notes.write(old, null)
            val kept = runCatching { EngineJson.decodeFromString(serializer(), written) }.getOrNull()?.copy(at = now) ?: return null
            if (kept.isEmpty) return null
            kept.keep(blog, notes)
            return kept
        }
    }
}

/**
 * Changes to a post the blog already has -- its text, or its words in
 * another language -- written and not saved yet. Kept as the new post's
 * writing is kept, for the same reasons; and with the version of the
 * post they were written over, so that the screen can say when the post
 * has moved on under them.
 */
@Serializable
data class Unsaved(
    val text: String,
    /** The digest the post had when the changes were begun. */
    val base: String,
    /** When they were last written in. */
    val at: Long,
    /** The post's title, for where the changes are listed by name. */
    val title: String? = null,
) {
    /** Which of a post's texts: its own, or one language of it. */
    sealed class What {
        data object Text : What()
        data class Language(val code: String) : What()

        internal val word: String
            get() = when (this) {
                Text -> "text"
                is Language -> "lang-$code"
            }

        companion object {
            internal fun of(word: String): What? = when {
                word == "text" -> Text
                word.startsWith("lang-") && word.length > 5 -> Language(word.drop(5))
                else -> null
            }
        }
    }

    /** One of a blog's kept changes: which post, which of its texts. */
    class Entry(val slug: String, val what: What, val kept: Unsaved)

    /** Kept, unless the same words are kept already -- those keep their time. */
    fun keep(blog: String, slug: String, what: What, notes: Notes) {
        if (kept(blog, slug, what, notes)?.text == text) return
        notes.write(key(blog, slug, what), EngineJson.encodeToString(serializer(), this))
    }

    /**
     * The text names a picture the post does not have: one that was
     * chosen on the device and, like every picture, not kept.
     */
    fun namesPictures(beyond: List<String>): Boolean =
        Regex("""!{1,2}\[[^\n]*\]\(([^)\s]+)\)""").findAll(text).any { it.groupValues[1] !in beyond }

    companion object {
        private fun prefix(blog: String): String = "unsaved.$blog."

        fun key(blog: String, slug: String, what: What): String = prefix(blog) + what.word + "." + slug

        fun kept(blog: String, slug: String, what: What, notes: Notes): Unsaved? {
            val written = notes.read(key(blog, slug, what)) ?: return null
            return runCatching { EngineJson.decodeFromString(serializer(), written) }.getOrNull()
        }

        /** Every change kept for a blog: which post, which of its texts. */
        fun all(blog: String, notes: Notes): List<Entry> {
            val prefix = prefix(blog)
            return notes.keys().mapNotNull { key ->
                if (!key.startsWith(prefix)) return@mapNotNull null
                val rest = key.drop(prefix.length)
                val dot = rest.indexOf('.')
                if (dot < 0) return@mapNotNull null
                val what = What.of(rest.substring(0, dot)) ?: return@mapNotNull null
                // A slug is whatever the blog made it; one with a dot in it is still one slug.
                val slug = rest.substring(dot + 1)
                if (slug.isEmpty()) return@mapNotNull null
                val kept = kept(blog, slug, what, notes) ?: return@mapNotNull null
                Entry(slug, what, kept)
            }
        }

        fun forget(blog: String, slug: String, what: What, notes: Notes) = notes.write(key(blog, slug, what), null)

        /** Everything kept for a blog that leaves the app. */
        fun forgetAll(blog: String, notes: Notes) {
            val prefix = prefix(blog)
            for (key in notes.keys()) if (key.startsWith(prefix)) notes.write(key, null)
        }
    }
}

/**
 * One thing begun on this device for a blog and not finished: a new post
 * not sent, a post's text or one of its languages changed and not saved.
 * What the first screen lists, so that writing kept from the last time is
 * seen without opening the form it waits in.
 */
data class Begun(
    val what: What,
    /** What to call it: the post's title, or what stands for one. */
    val title: String,
    val at: Long,
) {
    sealed class What {
        data object New : What()
        data class Text(val slug: String) : What()
        data class Language(val slug: String, val lang: String) : What()
    }

    companion object {
        /** All of them for a blog, the last written first. */
        fun all(blog: String, notes: Notes): List<Begun> {
            val all = mutableListOf<Begun>()
            Unsent.kept(blog, notes)?.let { all.add(Begun(What.New, it.headline, it.at)) }
            for (entry in Unsaved.all(blog, notes)) {
                val title = entry.kept.title?.takeIf { it.isNotEmpty() } ?: entry.slug
                when (val what = entry.what) {
                    Unsaved.What.Text -> all.add(Begun(What.Text(entry.slug), title, entry.kept.at))
                    is Unsaved.What.Language -> all.add(Begun(What.Language(entry.slug, what.code), title, entry.kept.at))
                }
            }
            return all.sortedWith(compareByDescending<Begun> { it.at }.thenBy { it.title })
        }
    }
}

/**
 * What is being written on this device, as far as the first screen
 * needs to know: that it changed, so the list of things begun is read again.
 */
object Desk {
    var changes by mutableIntStateOf(0)
        private set

    fun changed() {
        changes += 1
    }
}
