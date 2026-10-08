package app.blogsh.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import app.blogsh.android.R
import app.blogsh.android.model.AppLanguage
import app.blogsh.android.model.BuildStamp
import app.blogsh.android.model.Colouring
import app.blogsh.android.model.Hsv
import app.blogsh.android.model.Reading
import app.blogsh.android.model.Shades
import app.blogsh.android.model.TextSize
import app.blogsh.android.model.Tones
import app.blogsh.android.ui.DialogKey
import app.blogsh.android.ui.EngineLabel
import app.blogsh.android.ui.Faces
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.Mark
import app.blogsh.android.ui.PaperSheet
import app.blogsh.android.ui.PlainField
import app.blogsh.android.ui.PlainGround
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.RowDate
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.Typed
import app.blogsh.android.ui.blog
import app.blogsh.android.ui.gap
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.tone
import app.blogsh.android.ui.ui
import app.blogsh.android.ui.worn
import java.time.Instant

/**
 * What is the app's own and no blog's: how it is read on this device.
 * Where a blog is and the key to it are that blog's, and are kept with
 * it, behind its row in the list of blogs.
 */
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val reading = Reading.shared
    PaperSheet(onDismiss, name = stringResource(R.string.settings), actions = { DialogKey(stringResource(R.string.done), onClick = onDismiss) }) {
        SectionLabel(stringResource(R.string.language))
        val system = stringResource(R.string.as_the_system_has_it)
        Plate {
            for (choice in AppLanguage.entries) {
                row {
                    // A language is named in itself, so it is found by
                    // somebody who cannot read the one the app is in.
                    ChoiceLine(choice.title ?: system, chosen = choice == reading.language) { reading.speak(choice) }
                }
            }
        }
        Hint(stringResource(R.string.the_app_speaks_the_chosen_language_after))

        ColoursSection()

        SectionLabel(stringResource(R.string.text_size))
        TextSizePicker()
        Hint(stringResource(R.string.the_first_is_the_size_the_system))

        BuildMark(Modifier.gap(36, bottom = 8))
    }
}

/** One of a few to choose from: its name, and a mark on the one that is chosen. */
@Composable
private fun ChoiceLine(name: String, chosen: Boolean, choose: () -> Unit) {
    Pressable(choose, modifier = Modifier.semantics { selected = chosen }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Theme.ink, style = ui(15f), modifier = Modifier.weight(1f))
            if (chosen) Mark(Symbols.checkmark, 18.dp)
        }
    }
}

/** One well of the table: which colour, of which scheme. */
private data class Pick(val part: Shades.Part, val dark: Boolean)

/**
 * Whose colours the app wears -- the open blog's, its own, or the ones
 * chosen here -- and, for the last, the table they are chosen in: five
 * colours by day and five by night, each a well that opens a picker.
 */
@Composable
fun ColoursSection() {
    val reading = Reading.shared
    val dark = isSystemInDarkTheme()
    var picking by remember { mutableStateOf<Pick?>(null) }
    // The chosen colours cannot be read on this screen as it is now: the
    // section then keeps to the app's own, so there is always a way back.
    val unreadable = reading.wearing == Colouring.Chosen && !reading.worn.scheme(dark).legible

    val partNames = mapOf(
        Shades.Part.Bg to stringResource(R.string.background), Shades.Part.Text to stringResource(R.string.text),
        Shades.Part.MetaText to stringResource(R.string.muted_text), Shades.Part.Border to stringResource(R.string.dividers),
        Shades.Part.Accent to stringResource(R.string.accent),
    )
    val light = stringResource(R.string.light)
    val night = stringResource(R.string.dark)
    // What a well is called, where it has to say so by itself: "Text, Dark".
    fun title(pick: Pick): String = "${partNames.getValue(pick.part)}, ${if (pick.dark) night else light}"
    fun value(pick: Pick): Long = (reading.chosen ?: reading.worn).scheme(pick.dark)[pick.part]
    fun set(pick: Pick, value: Long) = reading.choose((reading.chosen ?: reading.worn).with(pick.dark, pick.part, value))

    val section: @Composable () -> Unit = {
        SectionLabel(stringResource(R.string.colours))
        Plate {
            for (choice in Colouring.entries) {
                row {
                    val name = when (choice) {
                        Colouring.Blog -> R.string.as_the_blog_has_them
                        Colouring.Own -> R.string.the_default_scheme
                        Colouring.Chosen -> R.string.your_own
                    }
                    ChoiceLine(stringResource(name), chosen = choice == reading.wearing) {
                        if (choice == Colouring.Chosen) reading.wearChosen(reading.worn) else reading.wear(choice)
                    }
                }
            }
        }
        Hint(stringResource(R.string.the_app_wears_the_colours_of_the))

        if (reading.wearing == Colouring.Chosen) {
            // The columns of the wells grow with their headings' type.
            val column = 52.dp * LocalDensity.current.fontScale.coerceIn(1f, 2.4f)
            Plate(Modifier.gap(14)) {
                row {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f))
                            for (heading in listOf(light, night)) {
                                Box(Modifier.width(column), contentAlignment = Alignment.Center) { EngineLabel(heading, size = 11f, bold = false, maxLines = 1) }
                            }
                        }
                        for (part in Shades.Part.entries) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(partNames.getValue(part), color = Theme.ink, style = ui(15f), modifier = Modifier.weight(1f))
                                for (scheme in listOf(false, true)) {
                                    val pick = Pick(part, scheme)
                                    Box(Modifier.width(column), contentAlignment = Alignment.Center) {
                                        ColourWell(value(pick), title(pick)) { picking = pick }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Hint(stringResource(if (unreadable) R.string.the_chosen_colours_cannot_be_read_here else R.string.tap_a_colour_to_change_it_it))
            Plate(Modifier.gap(14)) {
                row {
                    Pressable({ reading.choose(reading.blog) }) {
                        Text(stringResource(R.string.start_again_from_the_blog_s_colours), color = Theme.accent, style = ui(15f), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    if (unreadable) {
        // In its own colours the section brings its ground with it: the
        // page under it is in the ones that cannot be read.
        PlainGround {
            Column(Modifier.padding(top = 14.dp).clip(RoundedCornerShape(Theme.corner)).background(Theme.paper).padding(12.dp)) { section() }
        }
    } else {
        Column { section() }
    }
    picking?.let { pick ->
        ColourPicker(title(pick), value(pick), { set(pick, it) }) { picking = null }
    }
}

/** One colour, shown: a swatch that is a key. */
@Composable
private fun ColourWell(value: Long, label: String, pick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Pressable(pick, modifier = Modifier.semantics { contentDescription = label }) {
        Box(Modifier.size(52.dp, 32.dp).clip(shape).background(tone(value)).border(1.dp, Theme.muted.copy(alpha = 0.55f), shape))
    }
}

/**
 * A picker for one colour, where the system has none: around the wheel,
 * away from grey, up from black -- or the number itself, as a palette
 * writes it. Every move in it is the colour at once, and it covers only
 * the foot of the screen, so the app is seen changing over it. In the
 * app's own colours whatever is worn, as a system's picker would be:
 * what is being chosen may well be what it would be drawn in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColourPicker(title: String, value: Long, onChange: (Long) -> Unit, onDismiss: () -> Unit) {
    var hsv by remember { mutableStateOf(Hsv.of(value)) }
    var number by remember { mutableStateOf(written(value)) }
    fun move(to: Hsv) {
        hsv = to
        number = written(to.number)
        onChange(to.number)
    }
    PlainGround {
        ModalBottomSheet(
            onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Theme.paper, scrimColor = Color.Transparent,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Theme.muted) },
        ) {
            // Nothing is laid over the screen behind: what is being chosen is seen on it as it is.
            val view = LocalView.current
            SideEffect {
                generateSequence(view.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window?.setDimAmount(0f)
            }
            Typed {
                Column(Modifier.fillMaxWidth().padding(horizontal = Theme.gutter).padding(bottom = 16.dp).navigationBarsPadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EngineLabel(title, Modifier.weight(1f), color = Theme.ink)
                        DialogKey(stringResource(R.string.done), onClick = onDismiss)
                    }
                    val shape = RoundedCornerShape(8.dp)
                    val named = stringResource(R.string.android_colour_number)
                    Row(Modifier.padding(top = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp, 32.dp).clip(shape).background(tone(hsv.number)).border(1.dp, Theme.muted.copy(alpha = 0.55f), shape))
                        PlainField(
                            number,
                            { typed ->
                                number = typed.take(7)
                                // Only a whole number is a colour: half of one is still being typed.
                                if (typed.length == 7) Tones.value(typed)?.let {
                                    hsv = Hsv.of(it)
                                    onChange(it)
                                }
                            },
                            mono = true, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            modifier = Modifier.weight(1f).semantics { contentDescription = named },
                        )
                    }
                    Track(
                        stringResource(R.string.android_hue), hsv.hue / 360f,
                        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it, 1f, 1f) },
                    ) { move(hsv.copy(hue = it * 360f)) }
                    Track(
                        stringResource(R.string.android_saturation), hsv.saturation,
                        listOf(Color.hsv(hsv.hue, 0f, hsv.value), Color.hsv(hsv.hue, 1f, hsv.value)),
                    ) { move(hsv.copy(saturation = it)) }
                    Track(
                        stringResource(R.string.android_brightness), hsv.value,
                        listOf(Color.Black, Color.hsv(hsv.hue, hsv.saturation, 1f)),
                    ) { move(hsv.copy(value = it)) }
                }
            }
        }
    }
}

/** A colour as a palette writes it. */
private fun written(value: Long): String = "#%06x".format(value and 0xffffff)

/** One measure of a colour, from its one end to its other: a strip of what it would be, and a knob where it is. */
@Composable
private fun Track(name: String, at: Float, colours: List<Color>, onMove: (Float) -> Unit) {
    val move by rememberUpdatedState(onMove)
    val knob = 26.dp
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(44.dp)
            .semantics {
                contentDescription = name
                progressBarRangeInfo = ProgressBarRangeInfo(at, 0f..1f)
                setProgress {
                    move(it.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                val half = knob.toPx() / 2
                detectTapGestures { move(((it.x - half) / (size.width - 2 * half)).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                val half = knob.toPx() / 2
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    move(((change.position.x - half) / (size.width - 2 * half)).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 5.dp).height(16.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(colours)).border(1.dp, Theme.line, CircleShape)
        )
        Box(
            Modifier.offset(x = (maxWidth - knob) * at.coerceIn(0f, 1f)).size(knob).clip(CircleShape)
                .background(Color.White).border(2.dp, Color.Black.copy(alpha = 0.45f), CircleShape)
        )
    }
}

/**
 * Which build this is, at the foot of the settings: the engine's mark,
 * when the app was built and from which commit. Nothing to set -- what
 * is read out when one copy has to be told from another.
 */
@Composable
fun BuildMark(modifier: Modifier = Modifier, stamp: BuildStamp = BuildStamp.own) {
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.engine_mark), contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
        stamp.built?.let { Text(RowDate.numeric(Instant.ofEpochMilli(it)), color = Theme.muted, style = mono(12f)) }
        stamp.commit?.let { SelectionContainer { Text(it, color = Theme.muted, style = mono(12f)) } }
    }
}

/**
 * How large the type is: the same two letters in five sizes, the first
 * of them the system's own. The letters keep their sizes whatever is
 * chosen -- they are the scale, not what is measured on it.
 */
@Composable
fun TextSizePicker() {
    val reading = Reading.shared
    val shape = RoundedCornerShape(Theme.corner)
    val names = listOf(
        R.string.as_the_system_has_it, R.string.one_step_larger, R.string.two_steps_larger,
        R.string.three_steps_larger, R.string.four_steps_larger,
    ).map { stringResource(it) }
    val density = LocalDensity.current
    val wide = Theme.wide
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(Theme.card).border(1.dp, Theme.line, shape)) {
        TextSize.entries.forEachIndexed { index, size ->
            val chosen = size == reading.textSize
            if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Theme.line))
            Box(
                // The chosen one is filled with the accent, as a chosen filter is.
                Modifier.weight(1f).heightIn(min = 52.dp).background(if (chosen) Theme.accent else Color.Transparent)
                    .clickable(role = Role.RadioButton) { reading.set(size) }
                    .semantics {
                        contentDescription = names[index]
                        selected = chosen
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Aa", color = if (chosen) Color.White else Theme.ink,
                    style = TextStyle(fontFamily = Faces.sans, fontWeight = FontWeight.Medium, fontSize = with(density) { size.sample(wide).dp.toSp() }),
                )
            }
        }
    }
}
