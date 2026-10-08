package app.blogsh.android.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.blogsh.android.R
import app.blogsh.android.model.Delivery
import app.blogsh.android.model.Kept
import app.blogsh.android.model.Marks
import app.blogsh.android.model.Pictures
import app.blogsh.android.model.Shot
import app.blogsh.android.model.TagStore

/**
 * A text and where the caret is in it, held where a screen can reach both:
 * the marks go where the caret is, and a picture's line is put in there too.
 */
class EditorState(text: String = "") {
    var value by mutableStateOf(TextFieldValue(text))

    /** False until the text has been touched: a mark then goes to its end. */
    var touched by mutableStateOf(false)

    val text: String get() = value.text

    /** The text from outside -- read from the server, or changed by the screen: the caret stays where it can. */
    fun set(text: String) {
        if (text == value.text) return
        val at = minOf(value.selection.end, text.length)
        value = TextFieldValue(text, TextRange(at))
    }

    /** Where a mark or a line would go: the selection, or the end of a text never touched. */
    fun range(): Marks.Range {
        if (!touched) return Marks.Range(value.text.length, 0)
        return Marks.Range(value.selection.min, value.selection.max - value.selection.min)
    }

    fun mark(kind: Marks.Kind, words: Marks.Words) {
        val out = Marks.apply(value.text, range(), kind, words)
        value = TextFieldValue(out.value, TextRange(out.selection.location, out.selection.location + out.selection.length))
        touched = true
    }

    /**
     * A paragraph of its own where the caret is -- at the end of a text
     * never touched: a picture's line, with a blank line before it and
     * after it, as the blog wants it, and none of them doubled. The caret
     * goes on after it.
     */
    fun insertParagraph(line: String) {
        val put = Kept.placed(line, value.text, if (touched) value.selection.max else null)
        value = TextFieldValue(put.text, TextRange(put.caret))
        touched = true
    }
}

@Composable
fun rememberEditorState(text: String = ""): EditorState = remember { EditorState(text) }

/**
 * The text of a post, wherever it is typed: markdown, the raw material
 * the engine reads, in the typewriter face -- and over it the marks the
 * /write/ page offers, as keys. The last key opens the text over the whole
 * screen, for writing with nothing else in sight; the same key there
 * closes it again. Both are the one text.
 */
@Composable
fun PaperEditor(state: EditorState, minHeight: Dp = 220.dp, enabled: Boolean = true) {
    var whole by remember { mutableStateOf(false) }
    // As tall as the text asks, up to what stays in sight: a text that
    // grew on down the page took its caret behind the keyboard, and the
    // page does not follow a caret. Past that height the text moves
    // inside its own frame, which does. With a keyboard up the text has
    // everything down to it -- what stands under the text, the tags, is
    // not needed while writing; without one, half the page. For more room
    // there is the whole screen, one key away.
    val density = LocalDensity.current
    val page = LocalPage.current
    val keyboardUp = WindowInsets.ime.getBottom(density) > 0
    // Where the text's own upper edge is in the window.
    var top by remember { mutableStateOf(0f) }
    val least = 180.dp
    val room = if (keyboardUp && page.bottom != Float.MAX_VALUE) {
        val down = with(density) { (page.bottom - top).toDp() } - 6.dp
        minOf(maxOf(least, down), maxOf(least, page.height * 0.9f))
    } else {
        maxOf(least, page.height * 0.5f)
    }
    Column {
        EditorKeys(state, Symbols.arrowUpLeftAndArrowDownRight, stringResource(R.string.editor_whole)) { whole = true }
        BasicTextField(
            value = state.value,
            onValueChange = { state.value = it },
            enabled = enabled,
            textStyle = mono(15f, bold = false).copy(color = Theme.ink, lineHeight = 21.sp),
            cursorBrush = SolidColor(Theme.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                .onGloballyPositioned { top = it.positionInWindow().y.roundToInt().toFloat() }
                .heightIn(min = minOf(minHeight, room), max = room)
                .onFocusChanged { if (it.isFocused) state.touched = true },
        )
    }
    if (whole) WritingScreen(state) { whole = false }
}

/**
 * Nothing but the text: the marks over it, the paper under it, the
 * keyboard up. The text has the whole width of the screen or the window,
 * however wide: how long a line is, is the writer's to say, by the size
 * of the window.
 */
@Composable
fun WritingScreen(state: EditorState, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SheetBars()
        val focus = remember { FocusRequester() }
        Typed {
            Column(Modifier.fillMaxSize().background(Theme.paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
                Box(Modifier.padding(horizontal = Theme.gutter, vertical = 12.dp)) {
                    EditorKeys(state, Symbols.arrowDownRightAndArrowUpLeft, stringResource(R.string.editor_back), onClose)
                }
                Hairline()
                BasicTextField(
                    value = state.value,
                    onValueChange = { state.value = it },
                    textStyle = mono(16f, bold = false).copy(color = Theme.ink, lineHeight = 24.sp),
                    cursorBrush = SolidColor(Theme.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxSize().padding(horizontal = Theme.gutter).padding(top = 12.dp)
                        .focusRequester(focus).onFocusChanged { if (it.isFocused) state.touched = true },
                )
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

/**
 * The marks, and at their end the key that opens or closes the whole
 * screen. The marks scroll under it; the key stays where the thumb left it.
 */
@Composable
fun EditorKeys(state: EditorState, symbol: Int, label: String, turn: () -> Unit) {
    val words = Marks.Words(text = stringResource(R.string.mark_link_text), url = "https://")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { MarkBar { state.mark(it, words) } }
        Pressable(turn, modifier = Modifier.semantics { contentDescription = label }) {
            Box(Modifier.size(34.dp, 30.dp).border(1.dp, Theme.keyLine, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Mark(symbol, 16.dp)
            }
        }
    }
}

/**
 * The marks as a row of keys over the text: the ones the /write/ page
 * has, in its order.
 */
@Composable
fun MarkBar(apply: (Marks.Kind) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (kind in Marks.Kind.entries) {
            val name = stringResource(
                when (kind) {
                    Marks.Kind.Bold -> R.string.bold
                    Marks.Kind.Italic -> R.string.italic
                    Marks.Kind.Strike -> R.string.strikethrough
                    Marks.Kind.Code -> R.string.code
                    Marks.Kind.Link -> R.string.link
                    Marks.Kind.H2 -> R.string.heading
                    Marks.Kind.Quote -> R.string.quote
                    Marks.Kind.Ul -> R.string.list
                    Marks.Kind.Ol -> R.string.numbered_list
                    Marks.Kind.Fence -> R.string.code_block
                }
            )
            Pressable({ apply(kind) }, modifier = Modifier.semantics { contentDescription = name }) {
                Box(Modifier.size(34.dp, 30.dp).border(1.dp, Theme.keyLine, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    // Keys, so in the accent like every other key.
                    when (kind) {
                        Marks.Kind.Bold -> Text("B", color = Theme.accent, style = ui(14f, FontWeight.Bold))
                        Marks.Kind.Italic -> Text("I", color = Theme.accent, style = ui(15f).copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic))
                        Marks.Kind.Strike -> Text("S", color = Theme.accent, style = ui(14f).copy(textDecoration = TextDecoration.LineThrough))
                        Marks.Kind.Code -> Text("</>", color = Theme.accent, style = mono(11f))
                        Marks.Kind.Link -> Mark(Symbols.link, 16.dp, Theme.accent)
                        Marks.Kind.H2 -> Text("H", color = Theme.accent, style = ui(14f, FontWeight.Bold))
                        Marks.Kind.Quote -> Text("❝", color = Theme.accent, style = ui(14f))
                        Marks.Kind.Ul -> Text("•", color = Theme.accent, style = ui(16f, FontWeight.Bold))
                        Marks.Kind.Ol -> Text("1.", color = Theme.accent, style = mono(12f))
                        Marks.Kind.Fence -> Text("```", color = Theme.accent, style = mono(11f))
                    }
                }
            }
        }
    }
}

/** The words the line under the pictures is made of, in the reader's language. */
@Composable
fun deliveryWords(): Delivery.Words = Delivery.Words(
    pictureOne = stringResource(R.string.batch_pictures_one),
    pictureFew = stringResource(R.string.batch_pictures_few),
    pictureMany = stringResource(R.string.batch_pictures_many),
    videoOne = stringResource(R.string.batch_videos_one),
    videoFew = stringResource(R.string.batch_videos_few),
    videoMany = stringResource(R.string.batch_videos_many),
    textOnly = stringResource(R.string.batch_text_only),
)

/**
 * What is on the way and whether the blog will take it: the line the
 * /write/ page keeps under its pictures, in its words, and its legend.
 */
@Composable
fun DeliveryNote(shots: List<Shot>, textBytes: Int, maxMb: Int) {
    val over = Delivery.over(shots, textBytes, maxMb)
    if (shots.isNotEmpty() || textBytes > 0) {
        val line = stringResource(R.string.on_the_way, Delivery.describe(shots, textBytes, deliveryWords()))
        val refusal = stringResource(R.string.once_encoded_for_the_wire_over_the, Delivery.size(Delivery.wireBytes(shots, textBytes)), maxMb)
        Text(
            if (over) "$line — $refusal" else line, color = if (over) Theme.danger else Theme.muted,
            style = ui(13f, if (over) FontWeight.Medium else FontWeight.Normal), modifier = Modifier.gap(8),
        )
    }
    Hint(stringResource(R.string.a_picture_is_shrunk_to_px_on, Pictures.MAX_EDGE.toString(), maxMb, (maxMb * 0.73).toInt()))
}

/**
 * The row of pills under a tags field: a tag is tapped rather than typed,
 * and typed as it was before, instead of the blog growing a second
 * spelling of it. The chosen tag replaces whatever was being typed, and
 * the comma after it invites the next one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSuggestions(text: String, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val (done, typing) = TagStore.parts(text)
    val offered = TagStore.suggest(typing, done, from = TagStore.tags)
    if (offered.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (tag in offered) {
            Pressable({ onText((done + tag.name).joinToString(", ") + ", ") }) {
                Row(
                    Modifier.border(1.dp, Theme.line, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(tag.name, color = Theme.ink, style = mono(11f, bold = false).copy(letterSpacing = 0.4.sp))
                    Text("${tag.count}", color = Theme.muted, style = mono(11f, bold = false).copy(letterSpacing = 0.4.sp))
                }
            }
        }
    }
}
