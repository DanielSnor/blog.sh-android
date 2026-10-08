package app.blogsh.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blogsh.android.BlogshApp
import app.blogsh.android.R
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Colours
import app.blogsh.android.model.Reading
import app.blogsh.android.model.Shades
import app.blogsh.android.model.Tones
import app.blogsh.android.model.engineInstant
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How the app looks: a ground and the ink on it, by day and by night,
 * and one accent -- all of them the open blog's own, as its pages have
 * them, or the app's own -- and three voices of type: a terminal's face
 * set in lower case for what a screen is, a plain sans for what it
 * holds, and a typewriter face for what the engine says (a version, a
 * count, a date, a key).
 */
class Palette(
    /** The ground everything sits on. */
    val paper: Color,
    /** What is written on it. */
    val ink: Color,
    /** What is written beside it: tags, dates, hints. */
    val muted: Color,
    /** A hairline: the edge of a card, the rule between two rows. */
    val line: Color,
    /** The inside of a card, barely off the ground. */
    val card: Color,
    /**
     * What cannot be taken back: a delete, a refusal. The one colour
     * beside the accent, and never a fill.
     */
    val danger: Color,
)

/** A colour of a palette, `0xRRGGBB`, as one to draw with. */
fun tone(value: Long): Color = Color(0xFF000000 or value)

private fun mixed(from: Color, to: Color, part: Float): Color = Color(
    red = from.red + (to.red - from.red) * part,
    green = from.green + (to.green - from.green) * part,
    blue = from.blue + (to.blue - from.blue) * part,
)

private fun palette(shades: Shades, dark: Boolean): Palette {
    val paper = tone(shades.bg)
    val ink = tone(shades.text)
    return Palette(
        paper = paper, ink = ink, muted = tone(shades.metaText), line = tone(shades.border),
        card = ink.copy(alpha = if (dark) 0.05f else 0.03f),
        danger = if (dark) Color(0xFFFF7A5C) else Color(0xFFA81800),
    )
}

private val LocalPalette = staticCompositionLocalOf { palette(Colours.own.light, dark = false) }
private val LocalAccent = staticCompositionLocalOf { tone(Colours.own.light.accent) }

/** What the open blog said of itself. */
val Reading.blog: Colours
    get() = Blogs.current.let { Colours.said(it?.tonesLight, it?.tonesDark, it?.accentLight ?: "", it?.accentDark ?: "") }

/** The two schemes everything is drawn in now. */
val Reading.worn: Colours get() = Colours.worn(wearing, chosen, blog)

/**
 * A part of a screen that does not wear the worn colours but the app's
 * own: the place the colours are chosen in keeps to them once the chosen
 * ones cannot be read, so there is always a way back.
 */
@Composable
fun PlainGround(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val shades = Colours.own.scheme(dark)
    val palette = remember(dark) { palette(shades, dark) }
    CompositionLocalProvider(LocalPalette provides palette, LocalAccent provides tone(shades.accent), content = content)
}

object Theme {
    val paper: Color @Composable get() = LocalPalette.current.paper
    val ink: Color @Composable get() = LocalPalette.current.ink
    val muted: Color @Composable get() = LocalPalette.current.muted
    val line: Color @Composable get() = LocalPalette.current.line
    val card: Color @Composable get() = LocalPalette.current.card
    val danger: Color @Composable get() = LocalPalette.current.danger

    /** The one accent: every control of the app, its links, its counts. What the iOS app calls the tint. */
    val accent: Color @Composable get() = LocalAccent.current

    /**
     * Type so large that a name and its value no longer share a row: the
     * value stands under its name and has the whole width.
     */
    val crowded: Boolean @Composable get() = LocalDensity.current.fontScale >= 1.6f

    /**
     * A screen with room for longer steps of type: a tablet, read from
     * further away than a phone.
     */
    val wide: Boolean @Composable get() = LocalConfiguration.current.smallestScreenWidthDp >= 600

    val corner = 14.dp
    val gutter = 20.dp
}

/**
 * The engine's voice and the names of screens are set in lower case --
 * except in German, which reads its nouns by their capitals: there the
 * words stand as they are written.
 */
fun voiced(text: String): String = BlogshApp.spoken.let { if (it.language == "de") text else text.lowercase(it) }

/**
 * `#rrggbb` or `#rgb`, the way a palette writes a colour; anything
 * else -- rgb(), a name, nothing -- is null.
 */
fun hexColor(hex: String?): Color? = Tones.value(hex ?: "")?.let { tone(it) }

/**
 * The distance between two parts of a screen -- a section and the one
 * before it, a plate and its hint. It grows with the type: a gap that
 * tells two groups apart at the usual size is lost between lines twice
 * as tall, and a screen of large type reads as one unbroken column.
 */
@Composable
fun Modifier.gap(top: Int, bottom: Int = 0): Modifier {
    val grown = LocalDensity.current.fontScale
    return padding(top = top.dp * grown, bottom = bottom.dp * grown)
}

/**
 * The type as large as was chosen in the settings: the system's size and
 * the steps above it. Said again inside every window the app opens -- a
 * sheet, a dialog and a menu are windows of their own, and each starts
 * from the system's size.
 */
@Composable
fun Typed(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val system = LocalConfiguration.current.fontScale
    val zoom = Reading.shared.textSize.zoom(Theme.wide)
    CompositionLocalProvider(LocalDensity provides Density(density.density, system * zoom), content = content)
}

/**
 * The look, around everything: whose colours the app wears -- the open
 * blog's, as its pages have them, its own, or the ones chosen on this
 * device -- the accent with them, and the system's own controls -- a
 * switch, a menu, a dialog -- dressed in both.
 */
@Composable
fun BlogshTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val reading = Reading.shared
    val shades = reading.worn.scheme(dark)
    // The same palette while the colours are the same: everything on the screen is drawn
    // again when it is another, and a blog says its numbers far more often than new colours.
    val palette = remember(shades, dark) { palette(shades, dark) }
    val accent = tone(shades.accent)
    val paper = palette.paper
    val ink = palette.ink
    // The system's own surfaces -- a dialog, a menu -- a step off the ground, towards the light.
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = Color.White, secondary = accent, tertiary = accent,
            primaryContainer = accent.copy(alpha = 0.18f), onPrimaryContainer = ink,
            secondaryContainer = accent.copy(alpha = 0.18f), onSecondaryContainer = ink,
            tertiaryContainer = accent.copy(alpha = 0.18f), onTertiaryContainer = ink,
            background = paper, onBackground = ink, surface = paper, onSurface = ink,
            surfaceVariant = mixed(paper, ink, 0.10f), onSurfaceVariant = palette.muted, outline = palette.muted, outlineVariant = palette.line,
            surfaceContainer = mixed(paper, ink, 0.08f), surfaceContainerHigh = mixed(paper, ink, 0.11f), surfaceContainerHighest = mixed(paper, ink, 0.14f),
            surfaceContainerLow = mixed(paper, ink, 0.06f), surfaceContainerLowest = paper, error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = Color.White, secondary = accent, tertiary = accent,
            primaryContainer = accent.copy(alpha = 0.18f), onPrimaryContainer = ink,
            secondaryContainer = accent.copy(alpha = 0.18f), onSecondaryContainer = ink,
            tertiaryContainer = accent.copy(alpha = 0.18f), onTertiaryContainer = ink,
            background = paper, onBackground = ink, surface = paper, onSurface = ink,
            surfaceVariant = mixed(paper, ink, 0.08f), onSurfaceVariant = palette.muted, outline = palette.muted, outlineVariant = palette.line,
            surfaceContainer = mixed(paper, ink, 0.04f), surfaceContainerHigh = mixed(paper, Color.White, 0.4f), surfaceContainerHighest = mixed(paper, Color.White, 0.65f),
            surfaceContainerLow = mixed(paper, ink, 0.02f), surfaceContainerLowest = Color.White, error = palette.danger,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette, LocalAccent provides accent) {
        // The system's own controls speak in the look's plain face, not in the platform's.
        val base = Typography()
        fun TextStyle.plain() = copy(fontFamily = Faces.sans)
        val type = Typography(
            displayLarge = base.displayLarge.plain(), displayMedium = base.displayMedium.plain(), displaySmall = base.displaySmall.plain(),
            headlineLarge = base.headlineLarge.plain(), headlineMedium = base.headlineMedium.plain(), headlineSmall = base.headlineSmall.plain(),
            titleLarge = base.titleLarge.plain(), titleMedium = base.titleMedium.plain(), titleSmall = base.titleSmall.plain(),
            bodyLarge = base.bodyLarge.plain(), bodyMedium = base.bodyMedium.plain(), bodySmall = base.bodySmall.plain(),
            labelLarge = base.labelLarge.plain(), labelMedium = base.labelMedium.plain(), labelSmall = base.labelSmall.plain(),
        )
        MaterialTheme(colorScheme = scheme, typography = type) { Typed(content) }
    }
}

/**
 * The faces the look is set in. They ride in the app as font files: a
 * terminal's face with a typewriter's feet for what a screen is, a plain
 * sans for what it holds, and a typewriter for what the engine says --
 * Courier Prime, where the iOS app has the system's Courier New.
 */
object Faces {
    val display = FontFamily(Font(R.font.ibm_plex_mono_medium, FontWeight.Medium))

    @OptIn(ExperimentalTextApi::class)
    val sans = FontFamily(
        listOf(400, 500, 600, 700).map { weight ->
            Font(R.font.work_sans_variable, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
        }
    )

    val mono = FontFamily(
        Font(R.font.courier_prime_regular, FontWeight.Normal),
        Font(R.font.courier_prime_bold, FontWeight.Bold),
    )
}

/** What a screen is: its name, the blog's name. */
fun display(size: Float): TextStyle = TextStyle(fontFamily = Faces.display, fontWeight = FontWeight.Medium, fontSize = size.sp)

/** What a screen holds. */
fun ui(size: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(fontFamily = Faces.sans, fontWeight = weight, fontSize = size.sp)

/** What the engine says. */
fun mono(size: Float, bold: Boolean = true): TextStyle =
    TextStyle(fontFamily = Faces.mono, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = size.sp)

/**
 * A line in the engine's voice: typewriter, lower case, a little air
 * between the letters.
 */
@Composable
fun EngineLabel(
    text: String,
    modifier: Modifier = Modifier,
    size: Float = 12f,
    bold: Boolean = true,
    color: Color = Theme.muted,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        voiced(text), modifier = modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = mono(size, bold).copy(letterSpacing = (size * 0.06f).sp),
    )
}

/** A mark: one of Symbols, as large as it is said and in the colour it is given. */
@Composable
fun Mark(symbol: Int, size: Dp = 20.dp, tint: Color = Theme.accent, modifier: Modifier = Modifier) {
    Icon(painterResource(symbol), contentDescription = null, modifier = modifier.size(size), tint = tint)
}

/** A press on a card or a tile: it dims, and comes back. */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .alpha(if (pressed) 0.55f else 1f)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
    ) { content() }
}

/** A row of its own on the ground: a hairline around, a large corner. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    capsule: Boolean = false,
    /**
     * Something that wants looking at before it is lost sight of: the
     * card stands on the colour of what cannot be taken back, thinned to
     * a wash -- told apart at a glance from the cards that only report.
     */
    warning: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val shape: Shape = if (capsule) CircleShape else RoundedCornerShape(Theme.corner)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (warning) Theme.danger.copy(alpha = 0.10f) else if (highlighted) Theme.accent.copy(alpha = 0.12f) else Theme.card)
            .border(1.dp, if (warning) Theme.danger.copy(alpha = 0.55f) else if (highlighted) Theme.accent else Theme.line, shape)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * How many: the number in the engine's voice and in ink. The accent on
 * such a card is its mark's; the number beside it is only a number, as
 * the counts beside the marks are on the blog's own pages.
 */
@Composable
fun CountBadge(count: Int) {
    Text("$count", color = Theme.ink, style = mono(13f), modifier = Modifier.padding(horizontal = 4.dp))
}

/** A key of the terminal, kept as a mark: a digit in a small square. */
@Composable
fun KeyChip(text: String) {
    Box(Modifier.size(26.dp).border(1.dp, Theme.line, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Text(text, color = Theme.muted, style = mono(13f))
    }
}

/**
 * A filter: an outline and a muted word at rest; the one that is on is
 * filled with the accent, as on the blog's own pages.
 */
@Composable
fun FilterPill(label: String, selected: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        voiced(label), maxLines = 1, color = if (selected) Color.White else Theme.muted,
        style = mono(11f, bold = selected).copy(letterSpacing = 0.6.sp),
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) Theme.accent else Color.Transparent)
            .border(1.dp, if (selected) Theme.accent else Theme.line, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * A date as a row says it: this year's by day and month, an older one
 * with its year; a time soon to come by its weekday and hour.
 */
object RowDate {
    private fun pattern(skeleton: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault())

    fun short(date: Instant, now: Instant = Instant.now()): String {
        val zone = ZoneId.systemDefault()
        val day = date.atZone(zone)
        val sameYear = day.year == now.atZone(zone).year
        return pattern(if (sameYear) "Md" else "yMd").format(day)
    }

    fun soon(date: Instant, now: Instant = Instant.now()): String {
        val ahead = Duration.between(now, date).seconds
        if (ahead <= -86_400 || ahead >= 6 * 86_400) return short(date, now)
        return pattern("EEEjm").format(date.atZone(ZoneId.systemDefault()))
    }

    /** A time a post goes out, as a sentence says it: the day, the date, the hour. */
    fun spoken(date: Instant): String = pattern("EEEdMMMjm").format(date.atZone(ZoneId.systemDefault()))

    /** A day and its hour in numbers: when a build was made. */
    fun numeric(date: Instant): String = pattern("yMdjm").format(date.atZone(ZoneId.systemDefault()))

    /** A day and its hour, whole: what a properties screen says a post's date is. */
    fun full(date: Instant): String = pattern("yMMMdjm").format(date.atZone(ZoneId.systemDefault()))
}

/** The engine's own date, as a person reads one; what cannot be read as a date stands as it came. */
fun humanDate(iso: String): String = engineInstant(iso)?.let { RowDate.full(it) } ?: iso
