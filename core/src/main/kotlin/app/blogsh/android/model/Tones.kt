package app.blogsh.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The colours a blog's pages are set in, for one scheme: the ground,
 * what is written on it, what is written beside it, and its rules. As
 * the blog's config names them, in hex.
 */
@Serializable
data class Tones(
    val bg: String,
    val text: String,
    @SerialName("meta_text") val metaText: String,
    val border: String,
) {
    /** The four as numbers, `0xRRGGBB`. */
    data class Scheme(val bg: Long, val text: Long, val metaText: Long, val border: Long)

    /**
     * The four as numbers -- all of them or none: a palette with one
     * colour unreadable is not worn at all, rather than half worn.
     */
    val values: Scheme?
        get() {
            val ground = value(bg) ?: return null
            val ink = value(text) ?: return null
            val beside = value(metaText) ?: return null
            val rule = value(border) ?: return null
            return Scheme(ground, ink, beside, rule)
        }

    companion object {
        /**
         * A colour as a number, the way a palette writes one -- `#fff7eb`,
         * `#fc0` -- or nothing when the word is not that.
         */
        fun value(hex: String): Long? {
            var word = hex.trim(' ', '\t')
            if (!word.startsWith("#")) return null
            word = word.drop(1)
            if (word.length == 3) word = word.map { "$it$it" }.joinToString("")
            if (word.length != 6 || !word.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
            return word.toLong(16)
        }

        // The app's own colours: the palette the engine ships with, the blue
        // one -- what a blog looks like before anybody chose its colours, and
        // so what the app looks like before a blog has said its own.
        val ownLight = Scheme(0xF5F8FA, 0x444A5A, 0x657784, 0xE1E8ED)
        val ownDark = Scheme(0x111111, 0xFFFFFF, 0x6A7F8C, 0x263340)
        val ownAccentLight = 0x1DA1F2L
        val ownAccentDark = 0x4AB3F4L

        /**
         * The two schemes the app is drawn in: the blog's, when it has said
         * both and both read whole and the app is not asked to keep to its
         * own; the app's own otherwise.
         */
        fun worn(own: Boolean, light: Tones?, dark: Tones?): Pair<Scheme, Scheme> {
            val day = light?.values
            val night = dark?.values
            if (!own && day != null && night != null) return day to night
            return ownLight to ownDark
        }
    }
}
