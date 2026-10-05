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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blogsh.android.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How the app looks: paper and ink by day, ink and paper on black by
 * night, one accent -- the blog's own -- and three voices of type: a
 * display face set in lower case for what a screen is, a plain sans for
 * what it holds, and a typewriter face for what the engine says (a
 * version, a count, a date, a key).
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
    /** Text on a pill filled with ink. */
    val onInk: Color,
    /**
     * What cannot be taken back: a delete, a refusal. The one colour
     * beside the accent, and never a fill.
     */
    val danger: Color,
)

private val Day = Palette(
    paper = Color(0xFFEAE9E3), ink = Color(0xFF1E1D1C), muted = Color(0xFF6B6862),
    line = Color(0xFF1E1D1C).copy(alpha = 0.18f), card = Color(0xFF1E1D1C).copy(alpha = 0.03f),
    onInk = Color(0xFFEAE9E3), danger = Color(0xFFA81800),
)

private val Night = Palette(
    paper = Color(0xFF000000), ink = Color(0xFFEAE9E3), muted = Color(0xFF9A9993),
    line = Color(0xFFEAE9E3).copy(alpha = 0.16f), card = Color(0xFFEAE9E3).copy(alpha = 0.05f),
    onInk = Color(0xFF14110F), danger = Color(0xFFFF7A5C),
)

private val LocalPalette = staticCompositionLocalOf { Day }
private val LocalAccent = staticCompositionLocalOf { Theme.ember }

object Theme {
    val paper: Color @Composable get() = LocalPalette.current.paper
    val ink: Color @Composable get() = LocalPalette.current.ink
    val muted: Color @Composable get() = LocalPalette.current.muted
    val line: Color @Composable get() = LocalPalette.current.line
    val card: Color @Composable get() = LocalPalette.current.card
    val onInk: Color @Composable get() = LocalPalette.current.onInk
    val danger: Color @Composable get() = LocalPalette.current.danger

    /** The blog's own accent: what the iOS app calls the tint. */
    val accent: Color @Composable get() = LocalAccent.current

    /** The accent before a blog has said its own. */
    val ember = Color(0xFFFF2E00)

    val corner = 14.dp
    val gutter = 20.dp
}

/**
 * `#rrggbb` or `#rgb`, the way a palette writes a colour; anything
 * else -- rgb(), a name, nothing -- is null.
 */
fun hexColor(hex: String?): Color? {
    var digits = (hex ?: "").trim()
    if (!digits.startsWith("#")) return null
    digits = digits.drop(1)
    if (digits.length == 3) digits = digits.map { "$it$it" }.joinToString("")
    if (digits.length != 6) return null
    val value = digits.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
}

/**
 * The look, around everything: the palette of the hour, the blog's accent,
 * and the system's own controls -- a switch, a menu, a dialog -- dressed in both.
 */
@Composable
fun BlogshTheme(accentLight: String?, accentDark: String?, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) Night else Day
    val accent = hexColor(if (dark) accentDark else accentLight) ?: Theme.ember
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = Color.White, secondary = accent, tertiary = accent,
            primaryContainer = accent.copy(alpha = 0.18f), onPrimaryContainer = palette.ink,
            secondaryContainer = accent.copy(alpha = 0.18f), onSecondaryContainer = palette.ink,
            tertiaryContainer = accent.copy(alpha = 0.18f), onTertiaryContainer = palette.ink,
            background = palette.paper, onBackground = palette.ink, surface = palette.paper, onSurface = palette.ink,
            surfaceVariant = Color(0xFF1A1917), onSurfaceVariant = palette.muted, outline = palette.muted, outlineVariant = palette.line,
            surfaceContainer = Color(0xFF151413), surfaceContainerHigh = Color(0xFF1C1B19), surfaceContainerHighest = Color(0xFF242321),
            surfaceContainerLow = Color(0xFF0F0E0D), surfaceContainerLowest = Color(0xFF000000), error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = Color.White, secondary = accent, tertiary = accent,
            primaryContainer = accent.copy(alpha = 0.18f), onPrimaryContainer = palette.ink,
            secondaryContainer = accent.copy(alpha = 0.18f), onSecondaryContainer = palette.ink,
            tertiaryContainer = accent.copy(alpha = 0.18f), onTertiaryContainer = palette.ink,
            background = palette.paper, onBackground = palette.ink, surface = palette.paper, onSurface = palette.ink,
            surfaceVariant = Color(0xFFDFDED7), onSurfaceVariant = palette.muted, outline = palette.muted, outlineVariant = palette.line,
            surfaceContainer = Color(0xFFE4E3DD), surfaceContainerHigh = Color(0xFFF1F0EB), surfaceContainerHighest = Color(0xFFF6F5F1),
            surfaceContainerLow = Color(0xFFE7E6E0), surfaceContainerLowest = Color(0xFFFFFFFF), error = palette.danger,
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
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
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
        text.lowercase(Locale.getDefault()), modifier = modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
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
fun Card(modifier: Modifier = Modifier, highlighted: Boolean = false, capsule: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val shape: Shape = if (capsule) CircleShape else RoundedCornerShape(Theme.corner)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (highlighted) Theme.accent.copy(alpha = 0.12f) else Theme.card)
            .border(1.dp, if (highlighted) Theme.accent else Theme.line, shape)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** How many: the accent, filled, with the number in the engine's voice. */
@Composable
fun CountBadge(count: Int) {
    Text(
        "$count", color = Color.White, style = mono(12f),
        modifier = Modifier.clip(CircleShape).background(Theme.accent).padding(horizontal = 9.dp, vertical = 2.dp),
    )
}

/** A key of the terminal, kept as a mark: a digit in a small square. */
@Composable
fun KeyChip(text: String) {
    Box(Modifier.size(26.dp).border(1.dp, Theme.line, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Text(text, color = Theme.muted, style = mono(13f))
    }
}

/** A filter: an outline at rest, filled with ink when it is the one on. */
@Composable
fun FilterPill(label: String, selected: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        label.lowercase(Locale.getDefault()), maxLines = 1, color = if (selected) Theme.onInk else Theme.muted,
        style = mono(11f, bold = selected).copy(letterSpacing = 0.6.sp),
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) Theme.ink else Color.Transparent)
            .border(1.dp, if (selected) Theme.ink else Theme.line, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * The head of a screen: its name in the display face, and beside it how
 * many it holds.
 */
@Composable
fun ScreenHeader(title: String, count: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.Bottom) {
        Text(title.lowercase(Locale.getDefault()), color = Theme.ink, style = display(29f), modifier = Modifier.alignByBaseline())
        if (count != null) {
            Spacer(Modifier.width(10.dp))
            Text(count, color = Theme.accent, style = mono(12f), modifier = Modifier.alignByBaseline())
        }
    }
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

    /** A day and its hour, whole: what a properties screen says a post's date is. */
    fun full(date: Instant): String = pattern("yMMMdjm").format(date.atZone(ZoneId.systemDefault()))
}
