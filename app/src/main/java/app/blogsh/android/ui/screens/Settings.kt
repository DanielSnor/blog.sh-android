package app.blogsh.android.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.Blog
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Engine
import app.blogsh.android.model.KeyLine
import app.blogsh.android.model.KeyStore
import app.blogsh.android.model.TrustOnFirstUse
import app.blogsh.android.model.VersionAnswer
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.said
import app.blogsh.android.ui.Asks
import app.blogsh.android.ui.Choice
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.DialogKey
import app.blogsh.android.ui.EngineLabel
import app.blogsh.android.ui.FieldRow
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.PaperSheet
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.PrimaryButton
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.ScreenHeader
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import kotlinx.coroutines.launch

private sealed class Probe {
    object Idle : Probe()
    object Running : Probe()
    class Answered(val answer: VersionAnswer) : Probe()
    class Failed(val reason: String) : Probe()
}

/**
 * The one thing the terminal never asks for: where the blog is, and the
 * key the app holds. Three fields, a key made on this device, and the
 * line for the server's authorized_keys -- with the forced command in
 * front of it, which is what keeps this key from ever getting a shell.
 */
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var publicKey by remember { mutableStateOf<String?>(null) }
    var probe by remember { mutableStateOf<Probe>(Probe.Idle) }
    var confirmingNewKey by remember { mutableStateOf(false) }
    var confirmingRemoval by remember { mutableStateOf(false) }
    // The server's remembered key is read again after it is forgotten or first seen.
    var keyTick by remember { mutableStateOf(0) }
    val blog = Blogs.current

    // The settings are a blog's: with none yet, they begin one.
    LaunchedEffect(Blogs.currentId) {
        if (Blogs.current == null) Blogs.add()
        publicKey = Blogs.current?.let { runCatching { KeyStore.publicKeyLine(it.keyAccount) }.getOrNull() }
        probe = Probe.Idle
    }

    fun makeKey() {
        val account = Blogs.current?.keyAccount ?: return
        try {
            KeyStore.makeKey(account)
            publicKey = KeyStore.publicKeyLine(account)
            probe = Probe.Idle
        } catch (e: Exception) {
            probe = Probe.Failed(e.said)
        }
    }

    // The fields are the open blog's own, written down as they are typed.
    fun set(change: (Blog) -> Blog) = Blogs.update(change)

    val plain = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii)
    val host = blog?.host ?: ""
    val user = blog?.user ?: ""
    val port = blog?.port ?: 22

    PaperSheet(onDismiss, actions = { DialogKey(stringResource(R.string.done), onClick = onDismiss) }) {
        ScreenHeader(stringResource(R.string.settings))

        SectionLabel(stringResource(R.string.server))
        Plate {
            row { FieldRow(stringResource(R.string.host), host, { v -> set { it.copy(host = v) } }, mono = true, keyboard = plain.copy(keyboardType = KeyboardType.Uri)) }
            row { FieldRow(stringResource(R.string.user), user, { v -> set { it.copy(user = v) } }, mono = true, keyboard = plain) }
            row {
                FieldRow(
                    stringResource(R.string.port), port.toString(),
                    { v -> set { it.copy(port = v.filter(Char::isDigit).toIntOrNull() ?: it.port) } },
                    mono = true, keyboard = plain.copy(keyboardType = KeyboardType.Number),
                )
            }
            // Where on it the blog is, and what it is entered through.
            row { FieldRow(stringResource(R.string.path), blog?.path ?: "", { v -> set { it.copy(path = v) } }, prompt = "/home/you/blog", mono = true, keyboard = plain) }
            row { FieldRow(stringResource(R.string.through), blog?.through ?: "", { v -> set { it.copy(through = v) } }, prompt = "sudo docker exec -i blog", mono = true, keyboard = plain) }
        }
        Hint(stringResource(R.string.the_line_for_the_server_s_ssh))

        SectionLabel(stringResource(R.string.key))
        val key = publicKey
        if (key != null) {
            // No line until it can be a true one: a made-up path in it is a
            // line somebody copies.
            val line = KeyLine.compose(key, blog?.path ?: "", blog?.through ?: "")
            Plate {
                row { SelectionContainer { Text(key, color = Theme.ink, style = mono(12f, bold = false)) } }
                if (line != null) {
                    row { SelectionContainer { Text(line, color = Theme.ink, style = mono(12f, bold = false)) } }
                    row {
                        Command(stringResource(R.string.copy_the_authorized_keys_line), Symbols.docOnDoc) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("authorized_keys", line))
                        }
                    }
                }
                row { Command(stringResource(R.string.make_a_new_key), Symbols.key, danger = true) { confirmingNewKey = true } }
            }
        } else {
            Plate { row { Command(stringResource(R.string.make_the_app_s_key), Symbols.key) { makeKey() } } }
        }
        Hint(stringResource(R.string.the_key_is_made_on_this_device))

        SectionLabel(stringResource(R.string.connection))
        PrimaryButton(
            stringResource(R.string.test_the_connection),
            enabled = host.isNotEmpty() && user.isNotEmpty() && key != null, busy = probe == Probe.Running,
        ) {
            scope.launch {
                probe = Probe.Running
                probe = try {
                    Probe.Answered(Engine.call<VersionAnswer>("version"))
                } catch (e: Throwable) {
                    if (e.isCalledOff) throw e
                    Probe.Failed(e.said)
                }
                keyTick += 1
            }
        }
        when (val now = probe) {
            is Probe.Answered -> {
                val answer = now.answer
                Plate(Modifier.padding(top = 12.dp)) {
                    row {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("./blog.sh ${answer.engine}", color = Theme.ink, style = mono(13f))
                            Text(
                                if (answer.site.claim.isEmpty()) answer.site.name else "${answer.site.name} — ${answer.site.claim.replace("\n", " ")}",
                                color = Theme.ink, style = ui(15f),
                            )
                            Text(answer.site.url, color = Theme.muted, style = mono(12f, bold = false))
                        }
                    }
                }
            }
            is Probe.Failed -> ProblemLine(now.reason)
            else -> {}
        }

        val known = remember(host, port, keyTick) { TrustOnFirstUse.known(host, port) }
        if (known != null) {
            Plate(Modifier.padding(top = 12.dp)) {
                row {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EngineLabel(stringResource(R.string.server_key))
                        SelectionContainer { Text(known, color = Theme.ink, style = mono(12f, bold = false)) }
                    }
                }
                row {
                    Command(stringResource(R.string.forget_the_server_s_key), Symbols.xmarkCircle, danger = true) {
                        TrustOnFirstUse.forget(host, port)
                        keyTick += 1
                    }
                }
            }
        }

        // The blog leaves the app; nothing on the server is touched.
        if (blog != null) {
            Plate(Modifier.padding(top = 28.dp)) {
                row { Command(stringResource(R.string.remove_this_blog), Symbols.minusCircle, danger = true) { confirmingRemoval = true } }
            }
        }
    }

    if (confirmingNewKey) {
        Asks(
            stringResource(R.string.make_a_new_key_the_server_will),
            choices = listOf(Choice(stringResource(R.string.make_a_new_key), danger = true) { makeKey() }),
            onDismiss = { confirmingNewKey = false },
        )
    }
    if (confirmingRemoval && blog != null) {
        Asks(
            stringResource(R.string.remove_from_the_app_its_key_is, blog.label),
            choices = listOf(Choice(stringResource(R.string.remove), danger = true) {
                Blogs.remove(blog.id)
                onDismiss()
            }),
            onDismiss = { confirmingRemoval = false },
        )
    }
}
