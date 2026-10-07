package app.blogsh.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.AppLanguage
import app.blogsh.android.model.Reading
import app.blogsh.android.model.TextSize
import app.blogsh.android.ui.DialogKey
import app.blogsh.android.ui.Faces
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.Mark
import app.blogsh.android.ui.PaperSheet
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.SwitchRow
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.ui

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
                    val chosen = choice == reading.language
                    Pressable({ reading.speak(choice) }, modifier = Modifier.semantics { selected = chosen }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            // A language is named in itself, so it is found by
                            // somebody who cannot read the one the app is in.
                            Text(choice.title ?: system, color = Theme.ink, style = ui(15f), modifier = Modifier.weight(1f))
                            if (chosen) Mark(Symbols.checkmark, 18.dp)
                        }
                    }
                }
            }
        }
        Hint(stringResource(R.string.the_app_speaks_the_chosen_language_after))

        SectionLabel(stringResource(R.string.colours))
        Plate {
            row { SwitchRow(stringResource(R.string.use_the_default_colour_scheme), reading.ownColours, { reading.keepOwnColours(it) }) }
        }
        Hint(stringResource(R.string.the_app_wears_the_colours_of_the))

        SectionLabel(stringResource(R.string.text_size))
        TextSizePicker()
        Hint(stringResource(R.string.the_first_is_the_size_the_system))
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
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(Theme.card).border(1.dp, Theme.line, shape)) {
        TextSize.entries.forEachIndexed { index, size ->
            val chosen = size == reading.textSize
            if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Theme.line))
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp).background(if (chosen) Theme.ink else Color.Transparent)
                    .clickable(role = Role.RadioButton) { reading.set(size) }
                    .semantics {
                        contentDescription = names[index]
                        selected = chosen
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Aa", color = if (chosen) Theme.onInk else Theme.ink,
                    style = TextStyle(fontFamily = Faces.sans, fontWeight = FontWeight.Medium, fontSize = with(density) { size.sample.dp.toSp() }),
                )
            }
        }
    }
}
