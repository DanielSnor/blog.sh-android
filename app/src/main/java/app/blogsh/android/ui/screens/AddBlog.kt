package app.blogsh.android.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.blogsh.android.R
import app.blogsh.android.model.Blog
import app.blogsh.android.model.BlogShelf
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Herald
import app.blogsh.android.model.KeyStore
import app.blogsh.android.model.Pairing
import app.blogsh.android.model.PairingCode
import app.blogsh.android.model.PairingError
import app.blogsh.android.model.TrustOnFirstUse
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.InfoRow
import app.blogsh.android.ui.Mark
import app.blogsh.android.ui.PaperScreen
import app.blogsh.android.ui.PlainField
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.PrimaryButton
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.SheetBars
import app.blogsh.android.ui.SheetFrame
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.Typed
import app.blogsh.android.ui.gap
import app.blogsh.android.ui.ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Add a blog": two ways in. With a code -- `./blog.sh pair` on the
 * server shows one, the app reads it with the camera (or is given its
 * line of text) and is connected, and nobody types an address, a user
 * name or a key -- or by hand, as before: the blog's settings, a key
 * made here and its line written into the server's authorized_keys by
 * whoever keeps that file.
 *
 * `initialCode`: a code that came with the way in -- a link opened from
 * elsewhere. `onBack` leaves this screen; `onDone` closes what it stands in.
 */
@Composable
fun AddBlogSheet(initialCode: String = "", onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initialCode) }
    var connecting by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var byHand by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    // The camera was asked for and the answer was no.
    var cameraRefused by remember { mutableStateOf(false) }
    val offered = remember { CodeScanner.isOffered(context) }

    val asking = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        cameraRefused = !allowed
        scanning = allowed
    }

    // What was pasted, read: a code, or why it is not one. Nothing while
    // nothing is there.
    val read: Result<PairingCode>? = remember(text) {
        if (text.isBlank()) null else runCatching { PairingCode(text) }
    }

    val spent = stringResource(R.string.the_code_is_no_longer_good_it)
    val wrongServer = stringResource(R.string.another_machine_answered_than_the_one_the)
    val noKey = stringResource(R.string.the_app_could_not_make_its_key)

    fun words(why: PairingError, code: PairingCode): String = when (why) {
        PairingError.Spent -> spent
        PairingError.WrongServer -> wrongServer
        // Said as what to check, not as what the network library reported.
        is PairingError.Unreachable -> context.getString(R.string.the_server_did_not_answer_is_this, code.host)
        is PairingError.Refused -> context.getString(R.string.the_blog_said_no, why.words)
        PairingError.NoKey -> noKey
    }

    // The exchange: a key of the app's own is made, handed in with the
    // code's, and the blog is the app's from then on. A code that did
    // not take leaves nothing behind -- no blog, no key.
    suspend fun connect(code: PairingCode) {
        connecting = true
        problem = null
        val blog = Blog(host = code.host, port = code.port, user = code.user, name = code.site ?: "")
        val device = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: ""
        try {
            val handed = withContext(Dispatchers.IO) {
                // The key and its kind, without the comment ssh-keygen would add.
                val line = try {
                    KeyStore.makeKey(blog.keyAccount)
                    KeyStore.publicKeyLine(blog.keyAccount).split(" ").take(2).joinToString(" ")
                } catch (e: Exception) {
                    throw PairingError.NoKey
                }
                Pairing.handIn(line, device, code)
            }
            // The server that answered is the one this blog's connections expect from now on.
            BlogShelf.notes.write(TrustOnFirstUse.notesKey(code.host, code.port), handed.server)
            Blogs.adopt(blog)
            Herald.shared.say(context.getString(R.string.connected, handed.device.ifEmpty { blog.label }))
            onDone()
        } catch (e: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { KeyStore.deleteKey(blog.keyAccount) } }
            if (e.isCalledOff) throw e
            problem = words(e as? PairingError ?: PairingError.NoKey, code)
        } finally {
            connecting = false
        }
    }

    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SheetBars()
        Typed {
            SheetFrame {
            PaperScreen(onBack = onBack, name = stringResource(R.string.add_a_blog)) {
                SectionLabel(stringResource(R.string.with_a_code))
                Plate {
                    // Where there is a camera, that is the first way; the
                    // line of text is for where there is none.
                    if (offered) {
                        row {
                            Command(stringResource(R.string.scan_the_code), Symbols.qrcodeViewfinder, enabled = !connecting) {
                                if (CodeScanner.isAllowed(context)) {
                                    cameraRefused = false
                                    scanning = true
                                } else {
                                    asking.launch(android.Manifest.permission.CAMERA)
                                }
                            }
                        }
                    }
                    row {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlainField(
                            text,
                            {
                                text = it
                                problem = null
                            },
                            prompt = "blogsh://pair?…", mono = true, size = 13f, singleLine = false, enabled = !connecting,
                            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f),
                        )
                        // What was pasted and is not a code is put away by one key.
                        if (text.isNotEmpty() && !connecting) {
                            val clear = stringResource(R.string.android_b_clear)
                            Pressable(
                                {
                                    text = ""
                                    problem = null
                                },
                                modifier = Modifier.semantics { contentDescription = clear },
                            ) {
                                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Mark(Symbols.xmarkCircle, 20.dp, Theme.muted) }
                            }
                        }
                        }
                    }
                    row {
                        Command(stringResource(R.string.android_paste), Symbols.docOnClipboard, enabled = !connecting) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let {
                                text = it
                                problem = null
                            }
                        }
                    }
                }
                if (cameraRefused) ProblemLine(stringResource(R.string.the_app_is_not_allowed_to_use))
                Hint(stringResource(if (offered) R.string.on_the_server_run_blog_sh_pair_2 else R.string.on_the_server_run_blog_sh_pair))

                read?.onSuccess { code ->
                    val hostLabel = stringResource(R.string.host)
                    val userLabel = stringResource(R.string.user)
                    Plate(Modifier.gap(14)) {
                        row { Text(stringResource(R.string.connect_to, code.site ?: code.host), color = Theme.ink, style = ui(15f, FontWeight.Medium)) }
                        info(hostLabel, "${code.host}:${code.port}", mono = true)
                        info(userLabel, code.user, mono = true)
                    }
                    PrimaryButton(
                        stringResource(if (connecting) R.string.connecting else R.string.connect),
                        modifier = Modifier.gap(22), enabled = !connecting, busy = connecting,
                    ) { scope.launch { connect(code) } }
                }?.onFailure { why ->
                    ProblemLine(
                        stringResource(
                            when ((why as? PairingCode.Unreadable)?.problem) {
                                PairingCode.Problem.AnotherVersion -> R.string.this_code_is_of_a_newer_kind
                                PairingCode.Problem.Incomplete -> R.string.the_code_is_not_whole_copy_the
                                else -> R.string.this_is_not_a_code_from_blog
                            }
                        )
                    )
                }
                problem?.let { ProblemLine(it) }

                SectionLabel(stringResource(R.string.by_hand))
                Plate {
                    row {
                        Command(stringResource(R.string.set_up_by_hand), Symbols.wrenchAndScrewdriver, leads = true, enabled = !connecting) {
                            Blogs.add()
                            byHand = true
                        }
                    }
                }
                Hint(stringResource(R.string.for_a_blog_that_lives_in_a))
            }
            }
        }
    }

    if (byHand) BlogSettingsSheet(onBack = { byHand = false }, onDone = { byHand = false; onDone() })
    // Whatever the camera read goes into the field: a code is then asked
    // about, and anything else is said not to be one.
    if (scanning) {
        CodeScannerScreen(
            onRead = {
                text = it
                problem = null
            },
            onDismiss = { scanning = false },
        )
    }
}
