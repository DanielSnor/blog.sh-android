package app.blogsh.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.blogsh.android.R
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Engine
import app.blogsh.android.model.Herald
import app.blogsh.android.model.KeyStore
import app.blogsh.android.model.PairedDevices
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.ui.Asks
import app.blogsh.android.ui.Busy
import app.blogsh.android.ui.Choice
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.EngineLabel
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.PaperScreen
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.SheetBars
import app.blogsh.android.ui.SheetFrame
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.Typed
import app.blogsh.android.ui.gap
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * The devices a code let in to the open blog, as `./blog.sh pair --list`
 * shows them at the terminal -- and the one thing a device may do about
 * them from here: take itself off. Another device is taken off on the
 * server, by whoever sits at it.
 */
@Composable
fun DevicesSheet(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val blog = Blogs.current
    var answer by remember { mutableStateOf<PairedDevices?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    // The blog is an older one, which does not show its devices to an app.
    var notYet by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    // This device was just taken off: said here, over who is left, for
    // as long as the screen stands.
    var gone by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    // The key the app holds for this blog, as the list writes keys.
    val key = remember(blog?.keyAccount) { blog?.let { runCatching { KeyStore.fingerprint(it.keyAccount) }.getOrNull() } }
    val takenOff = stringResource(R.string.this_device_was_taken_off_the_blog)

    suspend fun load() {
        loading = true
        problem = null
        notYet = false
        try {
            answer = Engine.call<PairedDevices>("pair", "--list")
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            answer = null
            if (PairedDevices.notKnownThere(e)) notYet = true else problem = PairedDevices.words(e)
        } finally {
            loading = false
        }
    }

    // `pair --revoke`: the blog takes off whoever asks, and nobody else.
    // The answer still comes over the connection that asked; the next
    // one the server turns away, so the kept one is hung up with it.
    suspend fun leave() {
        leaving = true
        problem = null
        try {
            // Who is left comes with the answer.
            answer = Engine.call<PairedDevices>("pair", "--revoke")
        } catch (e: Throwable) {
            if (e.isCalledOff) {
                leaving = false
                throw e
            }
            if (!PairedDevices.isGone(e)) {
                problem = PairedDevices.words(e)
                leaving = false
                return
            }
            // Taken off, and the answer lost on its way.
            answer = null
        }
        Engine.hangUp()
        gone = true
        leaving = false
        Herald.shared.say(takenOff)
    }

    LaunchedEffect(Blogs.currentId) {
        gone = false
        load()
    }

    val listed = answer
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SheetBars()
        Typed {
            SheetFrame {
                PaperScreen(
                    onBack = onBack, name = stringResource(R.string.paired_devices),
                    count = listed?.devices?.takeIf { it.isNotEmpty() }?.let { NumberFormat.getIntegerInstance().format(it.size) },
                ) {
                    // Which blog's: the one whose settings led here.
                    val label = blog?.label ?: ""
                    if (label.isNotEmpty()) Text(label, color = Theme.accent, style = mono(12f), modifier = Modifier.padding(top = 2.dp))

                    if (loading && listed == null) {
                        Box(Modifier.fillMaxWidth().gap(40), contentAlignment = Alignment.Center) { Busy() }
                    }
                    if (notYet) Hint(stringResource(R.string.this_blog_does_not_show_its_devices), Modifier.gap(10))
                    problem?.let { ProblemLine(it) }
                    if (gone) {
                        Plate(Modifier.gap(14)) {
                            row { Text(stringResource(R.string.this_device_was_taken_off_the_blog_2), color = Theme.ink, style = ui(15f)) }
                        }
                    }
                    if (listed != null) {
                        if (listed.devices.isEmpty()) {
                            Hint(stringResource(R.string.no_device_was_let_in_to_this), Modifier.gap(10))
                        } else {
                            Plate(Modifier.gap(14)) {
                                for (device in listed.devices) {
                                    row { DeviceRow(device, listed.isThis(device, key)) }
                                }
                            }
                            Hint(stringResource(R.string.the_devices_a_code_let_in_to))
                        }
                        if (listed.mayLeave) {
                            // What cannot be taken back, set apart and last.
                            Plate(Modifier.gap(28)) {
                                row {
                                    Command(stringResource(R.string.take_this_device_off_the_blog), Symbols.minusCircle, danger = true, busy = leaving) { confirming = true }
                                }
                            }
                        } else if (listed.holdsThis(key)) {
                            Hint(stringResource(R.string.the_blog_cannot_tell_yet_which_of))
                        }
                    }
                }
            }
        }
    }

    if (confirming) {
        Asks(
            stringResource(R.string.take_this_device_off_the_blog_the, blog?.label ?: ""),
            choices = listOf(Choice(stringResource(R.string.remove), danger = true) { scope.launch { leave() } }),
            onDismiss = { confirming = false },
        )
    }
}

/** One device: its name, the mark on this one, and the key the server knows it by. */
@Composable
private fun DeviceRow(device: PairedDevices.Device, own: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(device.name.ifEmpty { "—" }, color = Theme.ink, style = ui(15f, FontWeight.Medium), modifier = Modifier.alignByBaseline().weight(1f, fill = false))
            if (own) EngineLabel(stringResource(R.string.this_device), Modifier.alignByBaseline(), color = Theme.accent)
        }
        if (device.fingerprint.isNotEmpty()) {
            SelectionContainer { Text(device.fingerprint, color = Theme.muted, style = mono(12f, bold = false)) }
        }
    }
}
