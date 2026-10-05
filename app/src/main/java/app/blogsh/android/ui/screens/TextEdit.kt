package app.blogsh.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.ActionAnswer
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Delivery
import app.blogsh.android.model.DeliveryFile
import app.blogsh.android.model.EditAnswer
import app.blogsh.android.model.EditEntry
import app.blogsh.android.model.Engine
import app.blogsh.android.model.Kept
import app.blogsh.android.model.Media
import app.blogsh.android.model.PostState
import app.blogsh.android.model.Preview
import app.blogsh.android.model.Shot
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.plain
import app.blogsh.android.model.said
import app.blogsh.android.ui.Asks
import app.blogsh.android.ui.Busy
import app.blogsh.android.ui.Choice
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.DeliveryNote
import app.blogsh.android.ui.EditorState
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.PaperEditor
import app.blogsh.android.ui.PaperScreen
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.PostHeading
import app.blogsh.android.ui.PrimaryButton
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.ScreenBack
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import kotlinx.coroutines.launch

/**
 * "the text": what `./blog.sh edit` opens in the editor, opened here.
 * `edit <slug> --json` hands the text out with the post's pictures by
 * bare name and a digest; the save goes back the way a post from the
 * phone goes -- new pictures first, then the text as a file whose header
 * says which post it edits and which version it started from. A draft
 * gets its preview rebuilt; a published post is rebuilt and deployed.
 *
 * `loaded` is the text already handed out to the screen before this one,
 * so the first look need not ask the engine again.
 */
@Composable
fun TextEditScreen(slug: String, loaded: EditEntry? = null) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tookLoaded by remember { mutableStateOf(false) }
    // The receiver's ceiling on one delivery, as the blog last said it.
    val maxMb = Blogs.current?.maxMb ?: 24
    var entry by remember { mutableStateOf<EditEntry?>(null) }
    val state = remember { EditorState() }
    var shots by remember { mutableStateOf(emptyList<Shot>()) }
    var importing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<ActionAnswer?>(null) }
    var confirmingLoss by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var looking by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    val unreadable = stringResource(R.string.one_picture_could_not_be_read)
    val textMissing = stringResource(R.string.the_text_did_not_come_with_the)

    val text = state.text
    val textBytes = remember(text) { text.toByteArray(Charsets.UTF_8).size }
    val media = entry?.media ?: emptyList()
    val isDraft = entry?.preview?.startsWith("/draft/") ?: true
    // Pictures the post has that the text stops naming.
    val dropped = Kept.dropped(media, text)
    // The save would leave the post with fewer pictures than it had.
    val fewer = Kept.fewer(media, shots, text)
    val sent = Kept.sent(shots, text)
    // What the engine does not have yet: words changed since it handed them out, or a shot picked here.
    val unsent = entry.let { it != null && (text != (it.text ?: "") || shots.isNotEmpty()) }

    suspend fun load() {
        // Once: after a save the text is asked for afresh, with its new version.
        val handed = loaded?.text
        if (loaded != null && !tookLoaded && handed != null) {
            tookLoaded = true
            entry = loaded
            state.set(handed)
            return
        }
        tookLoaded = true
        try {
            val answer = Engine.call<EditAnswer>("edit", slug)
            entry = answer.post
            state.set(answer.post.text ?: "")
            problem = if (answer.post.text == null) textMissing else null
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        }
    }

    suspend fun loadPictures(items: List<Uri>) {
        if (items.isEmpty()) return
        importing = true
        try {
            for (item in items) {
                val taken = (entry?.media ?: emptyList()) + shots.map { it.name }
                val shot = Media.shot(context, item, taken.size + 1, taken)
                if (shot == null) {
                    problem = unreadable
                    continue
                }
                shots = shots + shot
            }
        } finally {
            importing = false
        }
    }

    // A paragraph of its own, where the caret is.
    fun insert(shot: Shot) = state.insertParagraph(shot.mark)

    // The text with every new shot's mark carrying its description as it stands now.
    fun described(text: String): String {
        var marked = text
        for (shot in shots) marked = shot.markPattern.replace(marked, Regex.escapeReplacement(shot.mark))
        return marked
    }

    // The header gets the two lines of the delivery: which post, which version.
    fun fileText(): String {
        val post = entry ?: return state.text
        val marked = described(state.text)
        val lines = "edits: ${post.slug}\nbase: ${post.base}\n"
        if (marked.startsWith("---\n")) return "---\n" + lines + marked.drop(4)
        return "---\n" + lines + "---\n\n" + marked
    }

    suspend fun save() {
        saving = true
        try {
            problem = null
            val files = Kept.sent(shots, state.text).map { DeliveryFile(it.name, it.data) } +
                DeliveryFile("$slug.md", fileText().toByteArray(Charsets.UTF_8))
            saved = delivered(Engine.deliver(files))
            shots = emptyList()
            load()
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        } finally {
            saving = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { items ->
        scope.launch { loadPictures(items) }
    }

    fun leave() {
        if (unsent) asking = true else nav.pop()
    }
    ScreenBack(enabled = unsent) { asking = true }

    Box(Modifier.fillMaxSize()) {
        PaperScreen(onBack = { leave() }) {
            val post = entry
            if (post != null) {
                PostHeading(post.title, post.slug)
                if (!post.editable) {
                    Hint(
                        post.problem?.let { stringResource(R.string.this_post_cannot_be_edited_here_at, it) }
                            ?: stringResource(R.string.this_post_cannot_be_edited_here)
                    )
                }
                Plate(Modifier.padding(top = 14.dp)) {
                    row { PaperEditor(state, minHeight = 320.dp, enabled = post.editable) }
                }
                Hint(stringResource(R.string.the_header_and_the_text_as_the))
                Plate(Modifier.padding(top = 10.dp)) {
                    row { Command(stringResource(R.string.preview), Symbols.eye) { previewing = true } }
                }

                if (post.media.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.pictures_on_the_blog))
                    Plate {
                        for (name in post.media) {
                            row {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(name, color = Theme.ink, style = mono(13f, bold = false), modifier = Modifier.weight(1f).alignByBaseline())
                                    if (Kept.named(name, text)) {
                                        Text(stringResource(R.string.in_the_text), color = Theme.muted, style = ui(13f), modifier = Modifier.alignByBaseline())
                                    } else if (fewer) {
                                        // Not a promise the blog would not keep: see the hint under the list.
                                        Text(stringResource(R.string.not_in_the_text), color = Theme.danger, style = ui(13f), modifier = Modifier.alignByBaseline())
                                    } else {
                                        Text(stringResource(R.string.not_named_deleted_on_save), color = Theme.danger, style = ui(13f), modifier = Modifier.alignByBaseline())
                                    }
                                }
                            }
                        }
                    }
                    // Said before the save, not after it: what the blog will answer.
                    if (fewer) Hint(stringResource(R.string.the_text_names_fewer_pictures_than_the))
                }

                SectionLabel(stringResource(R.string.pictures_and_video))
                Plate {
                    for (shot in shots) {
                        row {
                            key(shot.id) {
                                ShotCard(
                                    shot, onShot = { changed -> shots = shots.map { if (it.id == changed.id) changed else it } },
                                    inText = text.contains("(${shot.name})"),
                                    insert = { insert(shot) }, remove = { shots = shots.filter { it.id != shot.id } }, look = { looking = shot.id },
                                )
                            }
                        }
                    }
                    // A video takes its time to convert: the row says it is being read, and waits.
                    row {
                        Command(
                            stringResource(if (importing) R.string.reading else R.string.add_a_picture_or_video), Symbols.photoOnRectangle,
                            busy = importing, enabled = post.editable,
                        ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                    }
                }
                DeliveryNote(sent, textBytes, maxMb)

                PrimaryButton(
                    stringResource(
                        if (saving) R.string.saving
                        else if (post.scheduled || isDraft) R.string.save_the_draft
                        else R.string.save_and_publish_the_change
                    ),
                    modifier = Modifier.padding(top = 22.dp),
                    enabled = !(saving || importing || !post.editable || text == post.text || Delivery.over(sent, textBytes, maxMb)),
                    busy = saving,
                ) {
                    // The question is asked where the answer counts: a swap deletes
                    // the picture it replaces. Fewer than before is the blog's to
                    // refuse, and the hint above has said so.
                    if (dropped.isEmpty() || fewer) scope.launch { save() } else confirmingLoss = true
                }
                val wrong = problem
                if (wrong != null) {
                    ProblemLine(wrong)
                } else if (!isDraft) {
                    Hint(stringResource(R.string.the_post_is_live_saving_rebuilds_and))
                }

                saved?.let { saved ->
                    SectionLabel(stringResource(R.string.saved))
                    Plate {
                        row {
                            Text(
                                "${saved.slug}: " + stringResource(if (saved.state == PostState.Published) R.string.saved_published else R.string.saved_draft),
                                color = Theme.ink, style = ui(15f),
                            )
                        }
                        saved.warnings?.plain?.forEach { warning ->
                            row { Text(warning, color = Theme.muted, style = ui(13f)) }
                        }
                        // The editor closes on a save; here the way back is a key.
                        row { Command(stringResource(R.string.back_to_the_post), Symbols.arrowLeft) { leave() } }
                    }
                }
            } else {
                problem?.let { ProblemLine(it) }
            }
        }
        if (entry == null && problem == null) Busy(modifier = Modifier.align(Alignment.Center))
    }

    if (previewing) {
        // The post's own media from beside its page on the blog; what
        // was picked here from the device.
        val parts = remember { Preview.parts(described(state.text)) }
        val shown = remember { Preview.shown(entry?.media ?: emptyList(), entry?.preview ?: "/") + Preview.shown(shots) }
        PreviewSheet(parts.first, parts.second, shown) { previewing = false }
    }
    looking?.let { one ->
        ShotsViewer(
            shots, current = one, onShot = { changed -> shots = shots.map { if (it.id == changed.id) changed else it } },
            onDismiss = { looking = null },
        )
    }
    if (confirmingLoss) {
        Asks(
            stringResource(R.string.pictures_the_text_no_longer_names_are, dropped.joinToString(", ")),
            choices = listOf(Choice(stringResource(R.string.save_and_delete_them), danger = true) { scope.launch { save() } }),
            onDismiss = { confirmingLoss = false },
        )
    }
    if (asking) AsksToLeave(onLeave = { nav.pop() }, onDismiss = { asking = false })
}
