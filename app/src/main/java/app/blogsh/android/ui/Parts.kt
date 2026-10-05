package app.blogsh.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.core.view.WindowCompat
import app.blogsh.android.R
import kotlinx.coroutines.launch
import java.util.Locale

// ---- A screen

/** A key of the bar over a screen: a mark, and what it is called for one who cannot see it. */
@Composable
fun BarKey(symbol: Int, label: String, enabled: Boolean = true, tint: Color = Theme.accent, onClick: () -> Unit) {
    Pressable(onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
        Box(Modifier.size(44.dp).alpha(if (enabled) 1f else 0.35f), contentAlignment = Alignment.Center) { Mark(symbol, 24.dp, tint) }
    }
}

/**
 * A screen on paper: the bar over it keeps its keys -- the way back, and
 * what the screen itself offers -- and gives up a title; a screen says
 * its own name, in its own face, at the head of what it holds.
 */
@Composable
fun PaperScaffold(
    onBack: (() -> Unit)? = null,
    close: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Theme.paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                BarKey(
                    if (close) Symbols.xmark else Symbols.chevronLeft,
                    stringResource(if (close) R.string.android_close else R.string.android_back), onClick = onBack,
                )
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        content()
    }
}

/**
 * A screen of fields, facts and actions: everything it holds in one
 * column on paper, between the two gutters.
 */
@Composable
fun PaperScreen(
    onBack: (() -> Unit)? = LocalNav.current.let { nav -> { nav.pop() } },
    close: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    PaperScaffold(onBack, close, actions) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Theme.gutter).padding(top = 2.dp, bottom = 28.dp),
            content = content,
        )
    }
}

/**
 * What iOS shows as a sheet: a screen over the one that asked for it,
 * closed by its own key, with the one under it kept as it was.
 */
@Composable
fun PaperSheet(
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    scrolls: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SheetBars()
        if (scrolls) PaperScreen(onBack = onDismiss, close = true, actions = actions, content = content)
        else PaperScaffold(onBack = onDismiss, close = true, actions = actions, content = content)
    }
}

/**
 * A sheet is a window of its own, and the system draws its bars' marks
 * light unless told otherwise: on paper they have to be dark, by night light.
 */
@Composable
fun SheetBars() {
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/** A hairline: the rule between two rows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Theme.line))
}

/**
 * A row of a list on paper: the ground shows through, a hairline under it
 * from one gutter to the other.
 */
@Composable
fun PaperRow(modifier: Modifier = Modifier, rule: Boolean = true, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = Theme.gutter)) { content() }
        if (rule) Hairline(Modifier.padding(horizontal = Theme.gutter))
    }
}

/** Something is being asked of the server. */
@Composable
fun Busy(size: Dp = 20.dp, color: Color = Theme.accent, modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier.size(size), color = color, strokeWidth = 2.dp)
}

// ---- What a screen holds

/**
 * A post at the head of its own screen. Its title is its own words, so
 * it keeps its capitals and the plain face; under it, in the engine's
 * voice, what the engine calls it.
 */
@Composable
fun PostHeading(title: String, detail: String? = null) {
    Column(Modifier.padding(top = 4.dp).semantics(mergeDescendants = true) { heading() }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Theme.ink, style = ui(22f, FontWeight.Bold))
        if (!detail.isNullOrEmpty()) {
            SelectionContainer { Text(detail, color = Theme.muted, style = mono(12f, bold = false)) }
        }
    }
}

/** What the rows under it are, in the engine's voice. */
@Composable
fun SectionLabel(text: String) {
    EngineLabel(text, Modifier.padding(top = 22.dp, bottom = 8.dp).semantics { heading() })
}

/** A word of explanation under a plate. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Theme.muted, style = ui(13f), modifier = modifier.padding(top = 8.dp))
}

/** What went wrong, where it went wrong. */
@Composable
fun ProblemLine(text: String) {
    SelectionContainer { Text(text, color = Theme.danger, style = ui(14f), modifier = Modifier.padding(top = 10.dp)) }
}

/** The rows of a plate, as they are said: one that is not said takes no place and leaves no rule behind. */
class PlateScope {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows.add(content)
    }

    /** A fact; one with nothing to say is not drawn, as on the terminal. */
    fun info(label: String, value: String?, mono: Boolean = false) {
        if (!value.isNullOrEmpty()) rows.add { InfoRow(label, value, mono) }
    }
}

/**
 * Several rows that belong together, on one card: a hairline around
 * them and one between each two.
 */
@Composable
fun Plate(modifier: Modifier = Modifier, build: PlateScope.() -> Unit) {
    val rows = PlateScope().apply(build).rows
    if (rows.isEmpty()) return
    val shape = RoundedCornerShape(Theme.corner)
    Column(modifier.fillMaxWidth().clip(shape).background(Theme.card).border(1.dp, Theme.line, shape)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) Hairline()
            Box(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp)) { row() }
        }
    }
}

/** A fact: what it is in the engine's voice, what it says beside it. */
@Composable
fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EngineLabel(label, Modifier.alignByBaseline())
        // The value has the row: it wraps rather than being cut, and the
        // label keeps to its own width.
        SelectionContainer(Modifier.weight(1f).alignByBaseline()) {
            Text(
                value, color = Theme.ink, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
                style = if (mono) mono(13f, bold = false) else ui(15f),
            )
        }
    }
}

/** A short value to type, named in the engine's voice at its side. */
@Composable
fun FieldRow(
    label: String,
    text: String,
    onText: (String) -> Unit,
    prompt: String = "",
    mono: Boolean = false,
    /** The width the labels of one plate share, so their fields line up. */
    labelWidth: Dp = 72.dp,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EngineLabel(label, Modifier.width(labelWidth).alignByBaseline())
        PlainField(text, onText, prompt, mono, keyboard, enabled, Modifier.weight(1f).alignByBaseline())
    }
}

/** A line to type on, with nothing around it: the plate it stands on is its frame. */
@Composable
fun PlainField(
    text: String,
    onText: (String) -> Unit,
    prompt: String = "",
    mono: Boolean = false,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    size: Float = if (mono) 15f else 16f,
    weight: FontWeight = FontWeight.Normal,
    singleLine: Boolean = true,
    actions: KeyboardActions = KeyboardActions.Default,
) {
    val style = if (mono) mono(size, bold = false) else ui(size, weight)
    BasicTextField(
        value = text, onValueChange = onText, modifier = modifier, enabled = enabled, singleLine = singleLine,
        textStyle = style.copy(color = Theme.ink), cursorBrush = SolidColor(Theme.accent), keyboardOptions = keyboard, keyboardActions = actions,
        decorationBox = { inner ->
            Box {
                if (text.isEmpty() && prompt.isNotEmpty()) Text(prompt, color = Theme.muted, style = style, maxLines = 1)
                inner()
            }
        },
    )
}

/**
 * Something the screen can do or lead to: its mark in the accent, its
 * name, and an arrow when it leads somewhere. What cannot be taken back
 * says so by its colour.
 */
@Composable
fun CommandRow(label: String, symbol: Int, danger: Boolean = false, leads: Boolean = false, busy: Boolean = false, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f),
        horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            if (busy) Busy(16.dp) else Mark(symbol, 20.dp, if (danger) Theme.danger else Theme.accent)
        }
        Text(label, color = if (danger) Theme.danger else Theme.ink, style = ui(15f, FontWeight.Medium), modifier = Modifier.weight(1f))
        if (leads) Mark(Symbols.chevronRight, 16.dp, Theme.muted)
    }
}

/** A command row that is a button: it dims under the finger and does its one thing. */
@Composable
fun Command(
    label: String, symbol: Int, danger: Boolean = false, leads: Boolean = false, busy: Boolean = false, enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Pressable(onClick, enabled = enabled && !busy) { CommandRow(label, symbol, danger, leads, busy, enabled) }
}

/**
 * The one thing a screen is for: the accent, filled, the words in the
 * engine's voice. One of these to a screen.
 */
@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Pressable(onClick, modifier = modifier, enabled = enabled && !busy) {
        Row(
            Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.35f).clip(CircleShape).background(Theme.accent).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) Busy(16.dp, Color.White)
            Text(label.lowercase(Locale.getDefault()), color = Color.White, style = mono(13f).copy(letterSpacing = 0.8.sp))
        }
    }
}

/**
 * A screen with nothing to show says so where the rows would be -- as
 * loudly as the room it stands in is large, and no louder: a screen of
 * its own -- an empty queue, an empty trash -- or a part of a screen,
 * where it stays a small remark.
 */
enum class Room { Welcome, Screen, Part }

@Composable
fun EmptyNote(symbol: Int, title: String, detail: String? = null, room: Room = Room.Screen, modifier: Modifier = Modifier) {
    val mark = when (room) { Room.Welcome -> 104.dp; Room.Screen -> 52.dp; Room.Part -> 30.dp }
    val words = when (room) { Room.Welcome -> 21f; Room.Screen -> 17f; Room.Part -> 16f }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.spacedBy(when (room) { Room.Welcome -> 22.dp; Room.Screen -> 14.dp; Room.Part -> 8.dp }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mark(symbol, mark, Theme.muted)
        Text(title, color = Theme.ink, style = ui(words, FontWeight.Medium), textAlign = TextAlign.Center)
        if (detail != null) {
            Text(detail, color = Theme.muted, style = ui(if (room == Room.Part) 14f else 15f), textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        }
    }
}

// ---- What a screen says and asks

/**
 * What a screen has to say after an action, and what it may ask with it:
 * one thing at a time, its question in it.
 */
class Said(
    val title: String = "",
    val text: String = "",
    val ask: Ask? = null,
    /** When it is put away without acting on it. */
    val after: (() -> Unit)? = null,
) {
    class Ask(val button: String, val cancel: String? = null, val danger: Boolean = false, val run: suspend () -> Unit)
}

/** The one place a screen says things through. `onClose` puts what was said away. */
@Composable
fun Says(said: Said?, onClose: () -> Unit) {
    // Before the way out: what is asked runs after the message is put away, and a
    // scope made after it would be gone with the message -- the action with it.
    val scope = rememberCoroutineScope()
    if (said == null) return
    val leave = {
        said.after?.invoke()
        onClose()
    }
    AlertDialog(
        onDismissRequest = leave,
        title = if (said.title.isEmpty()) null else ({ Text(said.title, style = ui(18f, FontWeight.SemiBold), color = Theme.ink) }),
        text = if (said.text.isEmpty()) null else ({ Text(said.text, style = ui(15f), color = Theme.ink) }),
        confirmButton = {
            val ask = said.ask
            if (ask != null) {
                DialogKey(ask.button, danger = ask.danger) {
                    onClose()
                    scope.launch { ask.run() }
                }
            } else {
                DialogKey(stringResource(R.string.ok), onClick = leave)
            }
        },
        dismissButton = said.ask?.let { ask -> { DialogKey(ask.cancel ?: stringResource(R.string.cancel), quiet = true, onClick = leave) } },
        containerColor = dialogGround(),
    )
}

@Composable
internal fun dialogGround(): Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
fun DialogKey(label: String, danger: Boolean = false, quiet: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, style = ui(15f, if (quiet) FontWeight.Normal else FontWeight.SemiBold), color = if (danger) Theme.danger else if (quiet) Theme.muted else Theme.accent)
    }
}

/** One of the things a question offers. */
class Choice(val label: String, val danger: Boolean = false, val run: () -> Unit)

/**
 * A question with several answers, asked over the screen: what iOS shows
 * as a sheet of actions rising from the bottom. Each answer is a line of
 * its own; the last one is always to leave things as they are.
 */
@Composable
fun Asks(title: String, message: String? = null, choices: List<Choice>, cancel: String = stringResource(R.string.cancel), onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(22.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(dialogGround()).padding(top = 22.dp, bottom = 8.dp)) {
            Text(title, style = ui(16f, FontWeight.SemiBold), color = Theme.ink, modifier = Modifier.padding(horizontal = 22.dp))
            if (!message.isNullOrEmpty()) {
                Text(message, style = ui(14f), color = Theme.muted, modifier = Modifier.padding(horizontal = 22.dp).padding(top = 8.dp))
            }
            Spacer(Modifier.height(14.dp))
            for (choice in choices) {
                Hairline()
                Pressable({ onDismiss(); choice.run() }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        choice.label, style = ui(15f, FontWeight.Medium), color = if (choice.danger) Theme.danger else Theme.accent,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                    )
                }
            }
            Hairline()
            Pressable(onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(cancel, style = ui(15f), color = Theme.muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp))
            }
        }
    }
}

/** A question answered by typing: a new slug, a position in the queue. */
@Composable
fun AsksFor(
    title: String,
    value: String,
    onValue: (String) -> Unit,
    prompt: String = "",
    confirm: String,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    mono: Boolean = false,
    message: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = ui(17f, FontWeight.SemiBold), color = Theme.ink) },
        text = {
            Column {
                if (!message.isNullOrEmpty()) Text(message, style = ui(14f), color = Theme.muted, modifier = Modifier.padding(bottom = 10.dp))
                val shape = RoundedCornerShape(10.dp)
                val focus = remember { FocusRequester() }
                Box(Modifier.fillMaxWidth().border(1.dp, Theme.line, shape).padding(horizontal = 12.dp, vertical = 11.dp)) {
                    PlainField(value, onValue, prompt, mono = mono, keyboard = keyboard, modifier = Modifier.fillMaxWidth().focusRequester(focus))
                }
                // The caret is in the field when the question opens, as it is in an alert on iOS.
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            }
        },
        confirmButton = { DialogKey(confirm) { onDismiss(); onConfirm() } },
        dismissButton = { DialogKey(stringResource(R.string.cancel), quiet = true, onClick = onDismiss) },
        containerColor = dialogGround(),
    )
}

/**
 * A site that publishes in more than one language refuses to publish or
 * schedule a post that has no words in one of them, unless it is told to
 * (`--allow-partial`). At the desk that is a flag typed after reading the
 * refusal; here it is the question the refusal becomes.
 */
object Partial {
    const val CODE = "partial_translation"
    val words: String @Composable get() = stringResource(R.string.this_site_publishes_in_more_than_one)

    /** The same words where nothing is being drawn: in the code that catches the refusal. */
    fun said(): String = app.blogsh.android.model.Spoken.say(R.string.this_site_publishes_in_more_than_one)
}

// ---- Choosing

/**
 * One of a few: the property named in the engine's voice, and beside it
 * what is chosen now -- which opens the others.
 */
@Composable
fun <T> ChoiceRow(label: String, chosen: String, selected: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        EngineLabel(label)
        Spacer(Modifier.weight(1f))
        Box {
            Pressable({ open = true }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(chosen, color = Theme.ink, style = ui(15f), maxLines = 1)
                    Mark(Symbols.chevronUpChevronDown, 16.dp)
                }
            }
            Menu(open, { open = false }) {
                for ((value, name) in options) {
                    MenuKey(name, if (value == selected) Symbols.checkmark else null) {
                        open = false
                        onSelect(value)
                    }
                }
            }
        }
    }
}

/** A menu opened from a key or from a row: its lines one under another. */
@Composable
fun Menu(open: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss, containerColor = dialogGround(), content = content)
}

@Composable
fun MenuKey(label: String, symbol: Int? = null, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = ui(15f), color = if (danger) Theme.danger else Theme.ink) },
        leadingIcon = symbol?.let { { Mark(it, 20.dp, if (danger) Theme.danger else Theme.accent) } },
        enabled = enabled, onClick = onClick,
    )
}

/** On or off: a sentence in the plain face, or a property in the engine's voice. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, property: Boolean = false, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (property) EngineLabel(label) else Text(label, color = Theme.ink, style = ui(15f))
        }
        // As tall as the rows beside it: the system's own switch asks for more room than a row of a plate has.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Switch(
            modifier = Modifier.scale(0.82f).height(26.dp),
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Theme.accent,
                uncheckedThumbColor = Theme.muted, uncheckedTrackColor = Theme.card, uncheckedBorderColor = Theme.line,
            ),
        )
        }
    }
}
