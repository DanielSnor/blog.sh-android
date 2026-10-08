package app.blogsh.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Whose colours the app wears: the open blog's, the app's own -- the
 * same whatever a blog chose, for eyes a blog's palette does not serve --
 * or the ones chosen on this device, colour by colour.
 */
enum class Colouring {
    Blog, Own, Chosen;

    /** The word it is kept as. */
    val word: String get() = name.lowercase()

    companion object {
        /** Where the choice is kept, and where it was kept while it was a switch between the first two. */
        const val KEY = "colours"
        const val SWITCH_KEY = "ownColours"

        /**
         * What is kept, read back: the word, or -- from before there were
         * three -- the switch that stood for the second.
         */
        fun kept(word: String?, switchedToOwn: Boolean): Colouring =
            entries.firstOrNull { it.word == word } ?: if (switchedToOwn) Own else Blog
    }
}

/**
 * The colours of one scheme, as numbers: the ground, what is written on
 * it, what is written beside it, its rules, and the accent.
 */
@Serializable
data class Shades(
    val bg: Long,
    val text: Long,
    @SerialName("metaText") val metaText: Long,
    val border: Long,
    val accent: Long,
) {
    constructor(scheme: Tones.Scheme, accent: Long) : this(scheme.bg, scheme.text, scheme.metaText, scheme.border, accent)

    /** One of the five, by what it is for: a row of the table they are chosen in. */
    enum class Part { Bg, Text, MetaText, Border, Accent }

    operator fun get(part: Part): Long = when (part) {
        Part.Bg -> bg
        Part.Text -> text
        Part.MetaText -> metaText
        Part.Border -> border
        Part.Accent -> accent
    }

    fun with(part: Part, value: Long): Shades = when (part) {
        Part.Bg -> copy(bg = value)
        Part.Text -> copy(text = value)
        Part.MetaText -> copy(metaText = value)
        Part.Border -> copy(border = value)
        Part.Accent -> copy(accent = value)
    }

    /**
     * How far what is written stands from what it is written on: the
     * ratio of their luminances, from one (the same colour) to twenty-one
     * (black on white).
     */
    val contrast: Double
        get() {
            val a = luminance(text)
            val b = luminance(bg)
            return (max(a, b) + 0.05) / (min(a, b) + 0.05)
        }

    /**
     * The writing can be told from the ground at all. Not a verdict on a
     * palette -- the ratio is a poor judge of that -- only the line under
     * which a screen cannot be read to be put right.
     */
    val legible: Boolean get() = contrast >= 1.5

    private companion object {
        fun luminance(value: Long): Double {
            fun linear(part: Long): Double {
                val c = (part and 0xff).toDouble() / 255
                return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * linear(value shr 16) + 0.7152 * linear(value shr 8) + 0.0722 * linear(value)
        }
    }
}

/** The two schemes the app is drawn in, by day and by night. */
@Serializable
data class Colours(val light: Shades, val dark: Shades) {
    fun scheme(dark: Boolean): Shades = if (dark) this.dark else light

    fun with(dark: Boolean, part: Shades.Part, value: Long): Colours =
        if (dark) copy(dark = this.dark.with(part, value)) else copy(light = light.with(part, value))

    /** What is written down for them. */
    val kept: String get() = EngineJson.encodeToString(serializer(), this)

    companion object {
        /** Where the ones chosen on this device are kept. */
        const val KEY = "chosenColours"

        /** The app's own: the palette the engine ships with. */
        val own = Colours(Shades(Tones.ownLight, Tones.ownAccentLight), Shades(Tones.ownDark, Tones.ownAccentDark))

        /**
         * What a blog said of itself: its palette where it said both schemes
         * and both read whole, its accents where each is a colour -- and the
         * app's own for whatever it did not say.
         */
        fun said(light: Tones?, dark: Tones?, accentLight: String, accentDark: String): Colours {
            val tones = Tones.worn(own = false, light = light, dark = dark)
            return Colours(
                Shades(tones.first, Tones.value(accentLight) ?: Tones.ownAccentLight),
                Shades(tones.second, Tones.value(accentDark) ?: Tones.ownAccentDark),
            )
        }

        /** What is worn: by the choice, and with nothing chosen yet, the blog's. */
        fun worn(wearing: Colouring, chosen: Colours?, blog: Colours): Colours = when (wearing) {
            Colouring.Blog -> blog
            Colouring.Own -> own
            Colouring.Chosen -> chosen ?: blog
        }

        /** What is kept, read back; anything else is nothing chosen. */
        fun kept(written: String?): Colours? =
            written?.let { runCatching { EngineJson.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/**
 * Which build this is: the commit it was made from and when it was made.
 * Written into the app by the build itself (two lines: the commit, the
 * time), so one copy of the app can be told from another without asking
 * the machine that built it.
 */
data class BuildStamp(
    /** The short commit; a plus after it for a tree that had changes not committed. */
    val commit: String?,
    /** When it was built, in milliseconds since 1970. */
    val built: Long?,
) {
    companion object {
        /** This build's own, as its build wrote it into the app. */
        var own: BuildStamp
            get() = said ?: runCatching { of(app.blogsh.android.BlogshApp.context.assets.open("BuildStamp.txt").bufferedReader().use { it.readText() }) }
                .getOrDefault(BuildStamp(null, null)).also { said = it }
            /** Only a test says another: a picture of the settings is not to change with every build. */
            internal set(value) {
                said = value
            }

        @Volatile
        private var said: BuildStamp? = null

        /** The two lines of the stamp. Anything else is no stamp. */
        fun of(text: String): BuildStamp {
            val lines = text.split("\n").map { it.trim(' ', '\t', '\r') }
            val first = lines.firstOrNull() ?: ""
            val valid = first.isNotEmpty() && first.length <= 41 &&
                first.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == '+' }
            val built = lines.getOrNull(1)?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            return BuildStamp(if (valid) first else null, built)
        }
    }
}

/**
 * A colour as a picker moves it: around the wheel, away from grey, up
 * from black. Kept apart from the number it comes to -- a grey has no
 * place on the wheel, and a black neither that nor a distance from grey,
 * and a picker that asked the number each time would forget both.
 */
data class Hsv(
    /** Around the wheel, in degrees from red. */
    val hue: Float,
    /** From grey (0) to the pure colour (1). */
    val saturation: Float,
    /** From black (0) to the colour at its lightest (1). */
    val value: Float,
) {
    /** The number a palette writes for it. */
    val number: Long
        get() {
            val c = value * saturation
            val sixth = ((hue % 360 + 360) % 360) / 60
            val x = c * (1 - kotlin.math.abs(sixth % 2 - 1))
            val (r, g, b) = when (sixth.toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            fun part(of: Float): Long = Math.round((of + value - c) * 255).coerceIn(0, 255).toLong()
            return (part(r) shl 16) or (part(g) shl 8) or part(b)
        }

    companion object {
        fun of(number: Long): Hsv {
            val r = ((number shr 16) and 0xff) / 255f
            val g = ((number shr 8) and 0xff) / 255f
            val b = (number and 0xff) / 255f
            val most = max(r, max(g, b))
            val span = most - min(r, min(g, b))
            val hue = when {
                span == 0f -> 0f
                most == r -> 60 * (((g - b) / span + 6) % 6)
                most == g -> 60 * ((b - r) / span + 2)
                else -> 60 * ((r - g) / span + 4)
            }
            return Hsv(hue, if (most == 0f) 0f else span / most, most)
        }
    }
}
