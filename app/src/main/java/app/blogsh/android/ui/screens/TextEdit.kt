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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.ActionAnswer
import app.blogsh.android.model.BlogShelf
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Delivery
import app.blogsh.android.model.DeliveryFile
import app.blogsh.android.model.Desk
import app.blogsh.android.model.Herald
import app.blogsh.android.model.EditAnswer
import app.blogsh.android.model.EditEntry
import app.blogsh.android.model.Engine
import app.blogsh.android.model.Kept
import app.blogsh.android.model.Media
import app.blogsh.android.model.PostState
import app.blogsh.android.model.Preview
import app.blogsh.android.model.Shot
import app.blogsh.android.model.Unsaved
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.plain
import app.blogsh.android.model.said
import app.blogsh.android.ui.Asks
import app.blogsh.android.ui.BroughtBack
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
import app.blogsh.android.ui.PrimaryButton
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.Stranded
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.broughtBackWords
import app.blogsh.android.ui.gap
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * "the text": what `./blog.sh edit` opens in the editor, opened here.
 * `edit <slug> --json` hands the text out with the post's pictures by
 * bare name and a digest; the save goes back the way a post from the
 * phone goes -- new pictures first, then the text as a file whose header
 * says which post it edits and which version it started from. A draft
 * gets its preview rebuilt; a published post is rebuilt and deployed.
 *
 * Changes written and not saved are kept on the device (`Unsaved`) and
 * are back in the editor the next time the post is opened.
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
    // Counted when a save has answered: the page goes to the answer.
    var answered by remember { mutableStateOf(0) }
    var confirmingLoss by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var looking by remember { mutableStateOf<String?>(null) }
    // The changes brought back when the text was opened, for the line that says so.
    var broughtBack by remember { mutableStateOf<Unsaved?>(null) }
    // The blog the screen was opened for: what is kept is kept under it.
    val blog = remember { Blogs.currentId }
    val unreadable = stringResource(R.string.one_picture_could_not_be_read)
    val needsNetwork = stringResource(R.string.android_picture_needs_network)
    // Why the last picture chosen is not among the shots.
    var unread by remember { mutableStateOf<String?>(null) }
    val textMissing = stringResource(R.string.the_text_did_not_come_with_the)

    val text = state.text
    val textBytes = remember(text) { text.toByteArray(Charsets.UTF_8).size }
    val media = entry?.media ?: emptyList()
    val isDraft = entry?.preview?.startsWith("/draft/") ?: true
    // Pictures the post has that the text stops naming.
    val dropped = Kept.dropped(media, text)
    val sent = Kept.sent(shots, text)

    // ---- Kept until saved

    // The text the editor opens with: the blog's, or -- where changes to
    // it were written here and never saved -- those.
    fun opened(post: EditEntry, fresh: String): String {
        val kept = if (post.editable) blog?.let { Unsaved.kept(it, slug, Unsaved.What.Text, BlogShelf.notes) } else null
        if (kept == null || kept.text == fresh) {
            broughtBack = null
            return fresh
        }
        broughtBack = kept
        return kept.text
    }

    // At every letter; a text that is the blog's again is nothing to keep.
    fun keep(written: String) {
        val post = entry ?: return
        val fresh = post.text
        if (!post.editable || fresh == null || blog == null) return
        if (written == fresh) Unsaved.forget(blog, slug, Unsaved.What.Text, BlogShelf.notes)
        else Unsaved.now(written, post.title, post.base, broughtBack, System.currentTimeMillis()).keep(blog, slug, Unsaved.What.Text, BlogShelf.notes)
        Desk.changed()
    }

    fun takeTheBlogs() {
        broughtBack = null
        shots = emptyList()
        state.set(entry?.text ?: "")
        blog?.let { Unsaved.forget(it, slug, Unsaved.What.Text, BlogShelf.notes) }
    }

    suspend fun load() {
        // Once: after a save the text is asked for afresh, with its new version.
        val handed = loaded?.text
        if (loaded != null && !tookLoaded && handed != null) {
            tookLoaded = true
            entry = loaded
            state.set(opened(loaded, handed))
            return
        }
        tookLoaded = true
        try {
            val answer = Engine.call<EditAnswer>("edit", slug)
            entry = answer.post
            state.set(opened(answer.post, answer.post.text ?: ""))
            problem = if (answer.post.text == null) textMissing else null
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        }
    }

    suspend fun loadPictures(items: List<Uri>) {
        if (items.isEmpty()) return
        importing = true
        unread = null
        try {
            for (item in items) {
                val taken = (entry?.media ?: emptyList()) + shots.map { it.name }
                try {
                    shots = shots + Media.shot(context, item, taken.size + 1, taken)
                } catch (e: Media.NotRead) {
                    // Said at the key that was pressed, not at the form's end.
                    unread = if (e.why == Media.Unread.NeedsNetwork) needsNetwork else unreadable
                }
            }
        } finally {
            importing = false
        }
    }

    // A paragraph of its own, where the caret is.
    fun insert(shot: Shot) = state.insertParagraph(shot.mark)

    // A picture's description is one thing with two places to write it:
    // typed on its card it is in the mark the text has for it...
    fun describe(changed: Shot) {
        shots = shots.map { if (it.id == changed.id) changed else it }
        state.set(Kept.typed(state.text, changed.name, changed.alt))
    }

    // ...and typed into the mark in the text it is on the card. A picture
    // just chosen, whose mark the text already has, takes its words too.
    LaunchedEffect(text, shots.size) {
        val heard = Kept.heard(shots, text)
        if (heard.map { it.alt } != shots.map { it.alt }) shots = heard
    }

    // The header gets the two lines of the delivery: which post, which version.
    fun fileText(): String {
        val post = entry ?: return state.text
        // The text is what goes: a description typed on a card is in it
        // already, one typed into the text itself was never the card's.
        val marked = state.text
        val lines = "edits: ${post.slug}\nbase: ${post.base}\n"
        if (marked.startsWith("---\n")) return "---\n" + lines + marked.drop(4)
        return "---\n" + lines + "---\n\n" + marked
    }

    suspend fun save() {
        saving = true
        // Whose post it is and what goes, said before anything is waited for.
        val going = state.text
        try {
            problem = null
            val files = Kept.sent(shots, going).map { DeliveryFile(it.name, it.data) } +
                DeliveryFile("$slug.md", fileText().toByteArray(Charsets.UTF_8))
            val answer = Engine.made(Engine.deliver(files, to = blog))
            saved = answer
            shots = emptyList()
            // Saved: what went is not left to bring back.
            blog?.let { Unsaved.forget(it, slug, Unsaved.What.Text, going, BlogShelf.notes) }
            broughtBack = null
            Desk.changed()
            // Saving a published post builds the site: nothing is owed
            // after it -- to the blog the post is on, whichever is open by now.
            if (answer.state == PostState.Published) Herald.shared.settled(blog)
            load()
            answered += 1
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
            answered += 1
        } finally {
            saving = false
        }
    }

    LaunchedEffect(Unit) { load() }
    LaunchedEffect(Unit) { snapshotFlow { state.text }.drop(1).collect { keep(it) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { items ->
        scope.launch { loadPictures(items) }
    }

    fun leave() = nav.pop()

    Box(Modifier.fillMaxSize()) {
        PaperScreen(title = entry?.title, answered = answered) {
            val post = entry
            if (post != null) {
                broughtBack?.let { back ->
                    BroughtBack(
                        broughtBackWords(back, post.base, post.media), stringResource(R.string.take_the_text_as_the_blog_has),
                        Modifier.gap(0, bottom = 14),
                    ) { takeTheBlogs() }
                }
                if (!post.editable) {
                    Hint(
                        post.problem?.let { stringResource(R.string.this_post_cannot_be_edited_here_at, it) }
                            ?: stringResource(R.string.this_post_cannot_be_edited_here)
                    )
                }
                Plate(Modifier.padding(top = if (post.editable) 0.dp else 14.dp)) {
                    // Left alone while a save is on its way: what is typed
                    // then would be neither in what was saved nor kept.
                    row { PaperEditor(state, minHeight = 320.dp, enabled = post.editable && !saving) }
                }
                Hint(stringResource(R.string.the_header_and_the_text_as_the))
                Plate(Modifier.gap(10)) {
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
                                    } else {
                                        // Whether or not another takes its place: the
                                        // blog deletes a picture its text stopped naming.
                                        Text(stringResource(R.string.not_named_deleted_on_save), color = Theme.danger, style = ui(13f), modifier = Modifier.alignByBaseline())
                                    }
                                }
                            }
                        }
                    }
                }

                SectionLabel(stringResource(R.string.pictures_and_video))
                Plate {
                    for (shot in shots) {
                        row {
                            key(shot.id) {
                                ShotCard(
                                    shot, onShot = { describe(it) },
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
                unread?.let { ProblemLine(it) }
                DeliveryNote(sent, textBytes, maxMb)

                PrimaryButton(
                    stringResource(
                        if (saving) R.string.saving
                        else if (post.scheduled || isDraft) R.string.save_the_draft
                        else R.string.save_and_publish_the_change
                    ),
                    modifier = Modifier.gap(22),
                    enabled = !(saving || importing || !post.editable || text == post.text || Delivery.over(sent, textBytes, maxMb)),
                    busy = saving,
                ) {
                    // Asked whenever the save would delete a picture -- one
                    // swapped for another, or one simply taken out. The blog
                    // used to refuse a save that left the post with fewer, and
                    // the app left that case to it; it takes such a save now,
                    // and deletes the picture for good.
                    // The save does not end with the screen: what went is
                    // forgotten here whether or not anybody is still looking.
                    if (Kept.asksBeforeSaving(media, text)) confirmingLoss = true else Desk.outliving.launch { save() }
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
                // What is kept for this post while the post itself did not open.
                val stranded = remember(Desk.changes) { blog?.let { Unsaved.kept(it, slug, Unsaved.What.Text, BlogShelf.notes) } }
                if (problem != null && stranded != null) {
                    Stranded(stranded) {
                        blog?.let { Unsaved.forget(it, slug, Unsaved.What.Text, BlogShelf.notes) }
                        Desk.changed()
                    }
                }
            }
        }
        if (entry == null && problem == null) Busy(modifier = Modifier.align(Alignment.Center))
    }

    if (previewing) {
        // The post's own media from beside its page on the blog; what
        // was picked here from the device.
        val parts = remember { Preview.parts(state.text) }
        val shown = remember { Preview.shown(entry?.media ?: emptyList(), entry?.preview ?: "/") + Preview.shown(shots) }
        PreviewSheet(parts.first, parts.second, shown) { previewing = false }
    }
    looking?.let { one ->
        ShotsViewer(
            shots, current = one, onShot = { describe(it) },
            onDismiss = { looking = null },
        )
    }
    if (confirmingLoss) {
        Asks(
            stringResource(R.string.pictures_the_text_no_longer_names_are, dropped.joinToString(", ")),
            choices = listOf(Choice(stringResource(R.string.save_and_delete_them), danger = true) { Desk.outliving.launch { save() } }),
            onDismiss = { confirmingLoss = false },
        )
    }
}
