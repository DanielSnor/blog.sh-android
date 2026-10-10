package app.blogsh.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.ActionAnswer
import app.blogsh.android.model.BlogShelf
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Delivery
import app.blogsh.android.model.DeliveryFile
import app.blogsh.android.model.Desk
import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.Kept
import app.blogsh.android.model.Markdown
import app.blogsh.android.model.Media
import app.blogsh.android.model.Now
import app.blogsh.android.model.Preview
import app.blogsh.android.model.Reach
import app.blogsh.android.model.Receipt
import app.blogsh.android.model.Shot
import app.blogsh.android.model.TagStore
import app.blogsh.android.model.Unsent
import app.blogsh.android.model.Waiting
import app.blogsh.android.model.WaitingRoom
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.plain
import app.blogsh.android.model.said
import app.blogsh.android.ui.BroughtBack
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.DeliveryNote
import app.blogsh.android.ui.EditorState
import app.blogsh.android.ui.EngineLabel
import app.blogsh.android.ui.FieldRow
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.Mark
import app.blogsh.android.ui.MenuEntry
import app.blogsh.android.ui.PaperEditor
import app.blogsh.android.ui.PaperScreen
import app.blogsh.android.ui.PlainField
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.PrimaryButton
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.RowDate
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.TagSuggestions
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.gap
import app.blogsh.android.ui.ui
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "New post": what /write/ offers, as a form -- a title, the text, the
 * tags, photographs each with its description -- sent the way the page
 * sends it: the pictures first, the markdown last, one delivery. The
 * post arrives as a draft with a preview; publishing is its properties'
 * decision, the way it is at the desk.
 *
 * What is written is kept on the device at every letter (`Unsent`) and
 * is back in the form the next time it opens, until it is sent.
 */
@Composable
fun ComposeScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val links = LocalUriHandler.current
    // The receiver's ceiling on one delivery, as the blog last said it.
    val maxMb = Blogs.current?.maxMb ?: 24
    // The blog the form was opened for: what is kept is kept under it.
    val blog = remember { Blogs.currentId }
    // Once, when the form opens: what was written for this blog and not
    // sent is put back.
    // A post that waited to be sent and was taken back to be written
    // on: it is the form's now and waits no longer -- but its files stay
    // where they are, held, for as long as the form has it. The form
    // keeps pictures in memory only; left, or stopped with the app, it
    // would take the post's pictures along.
    val handed = remember { if (blog == null) null else Desk.takeHanded()?.let { it to WaitingRoom.shots(it, blog) } }
    val kept = remember {
        if (handed != null) null
        else blog?.let { Unsent.kept(it, BlogShelf.notes) ?: Unsent.carriedOver(it, BlogShelf.notes, Now.instant().toEpochMilli()) }
    }
    var title by remember { mutableStateOf(handed?.first?.title ?: kept?.title ?: "") }
    var tags by remember { mutableStateOf(handed?.first?.tags ?: kept?.tags ?: "") }
    val state = remember { EditorState(handed?.first?.text ?: kept?.text ?: "") }
    // What was brought back when the form opened, for the line that says so.
    var broughtBack by remember { mutableStateOf(kept) }
    // What was taken back from the device has its pictures there still.
    val keptHeld = remember { if (blog == null) null else kept?.from?.let { WaitingRoom.one(it, blog) } }
    // The post that waited on the device and was taken back into this
    // form: its files are held until the form has sent it, put it by
    // again or been emptied.
    var from by remember { mutableStateOf(handed?.first?.id ?: keptHeld?.id) }
    var shots by remember {
        mutableStateOf(handed?.second ?: (if (blog != null && keptHeld != null) WaitingRoom.shots(keptHeld, blog) else emptyList()))
    }
    // The name this post's delivery goes under, however often it is
    // tried: kept with the writing, and another for the next post.
    var receipt by remember { mutableStateOf((handed?.first?.receipt ?: kept?.receipt)?.takeIf { Receipt.isOne(it) } ?: Receipt.mint()) }
    // The post was put by on the device: said where an answer would be.
    var putBy by remember { mutableStateOf(false) }
    // Why the last picture chosen is not among the shots.
    var unread by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var made by remember { mutableStateOf<ActionAnswer?>(null) }
    // Counted when a sending has answered: the page goes to the answer.
    var answered by remember { mutableStateOf(0) }
    var previewing by remember { mutableStateOf(false) }
    var looking by remember { mutableStateOf<String?>(null) }
    val unreadable = stringResource(R.string.one_picture_could_not_be_read)
    val needsNetwork = stringResource(R.string.android_picture_needs_network)
    val couldNotKeep = stringResource(R.string.the_post_could_not_be_kept_on)

    // The open blog's server did not answer the last time it was asked.
    val offline = Reach.shared.isOffline(Blogs.current)
    // A new post for this blog is on its way: from here, or from a form left before it arrived.
    val busy = sending || (blog != null && blog in Desk.sending)

    val text = state.text
    val textBytes = remember(text) { text.toByteArray(Charsets.UTF_8).size }
    // The shots the text names: only those go.
    val sent = Kept.sent(shots, text)
    val overweight = Delivery.over(sent, textBytes, maxMb)

    // ---- Pictures

    suspend fun load(items: List<Uri>) {
        if (items.isEmpty()) return
        importing = true
        unread = null
        try {
            for (item in items) {
                try {
                    shots = shots + Media.shot(context, item, shots.size + 1, shots.map { it.name })
                } catch (e: Media.NotRead) {
                    // Said at the key that was pressed, not at the form's end.
                    unread = if (e.why == Media.Unread.NeedsNetwork) needsNetwork else unreadable
                }
            }
        } finally {
            importing = false
        }
    }

    // A blank line on each side: the blog renders a picture only as a
    // paragraph of its own. It goes in where the caret is.
    fun insert(shot: Shot) = state.insertParagraph(shot.mark)

    fun remove(shot: Shot) {
        shots = shots.filter { it.id != shot.id }
        state.set(Regex("\n*" + shot.markPattern.pattern + "\n*").replace(state.text, "\n\n"))
    }

    // A picture's description is one thing with two places to write it:
    // typed on its card -- or under the shot seen large -- it is in the
    // mark the text has for it, at every letter...
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

    // ---- Sending

    /**
     * The form is the next post's: empty, and under a name of its own.
     * What it held of a post taken back from the device goes with it --
     * the post was sent, or put by anew, or thrown away here.
     */
    fun startEmpty() {
        val held = from
        if (held != null && blog != null) {
            WaitingRoom.remove(held, blog)
            Desk.changed()
        }
        from = null
        title = ""
        tags = ""
        state.set("")
        shots = emptyList()
        broughtBack = null
        receipt = Receipt.mint()
    }

    suspend fun send() {
        val to = blog ?: return
        if (to in Desk.sending) return
        sending = true
        putBy = false
        Desk.began(to)
        var came = false
        val going = Unsent(title, tags, state.text)
        val held = from
        try {
            problem = null
            val body = state.text
            // The text is what goes: a description typed on a card is in it
            // already, one typed into the text itself was never the card's.
            val markdown = Markdown.file(title, tags, body, receipt = receipt)
            val files = Kept.sent(shots, body).map { DeliveryFile(it.name, it.data) } +
                DeliveryFile(Markdown.fileName(title, body), markdown.toByteArray(Charsets.UTF_8))
            made = Engine.made(Engine.deliver(files, to = to))
            // Forgotten here, not by the form's own noticing that it was
            // emptied: a form left while its post was going notices nothing.
            Unsent.forget(to, going, BlogShelf.notes)
            // ...and so are the files it was taken back from: it has arrived.
            held?.let { WaitingRoom.remove(it, to) }
            Desk.changed()
            came = true
            // The form is the next post's now, and nothing is left to bring back.
            startEmpty()
            answered += 1
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
            answered += 1
        } finally {
            sending = false
            Desk.ended(to, came)
        }
    }

    /**
     * Put by on the device, whole: the text and the shots it names. The
     * form is the next post's, as after a sending.
     */
    suspend fun keepOnDevice() {
        val to = blog ?: return
        sending = true
        problem = null
        made = null
        try {
            val post = Waiting(title = title, tags = tags, text = state.text, at = Now.instant().toEpochMilli(), receipt = receipt)
            val going = Kept.sent(shots, state.text)
            withContext(Dispatchers.IO) { WaitingRoom.put(post, going, to) }
            startEmpty()
            putBy = true
            Desk.changed()
        } catch (e: Exception) {
            if (e.isCalledOff) throw e
            problem = couldNotKeep.format(e.said)
        } finally {
            sending = false
        }
        answered += 1
    }

    // What the form took back from the posts that waited is the post
    // being written from here on: held, and remembered by the writing.
    // Every other held post waits again -- with nothing being written,
    // no post is the form's to hold.
    LaunchedEffect(Unit) {
        if (blog == null) return@LaunchedEffect
        val post = handed?.first
        if (post != null) {
            WaitingRoom.hold(post.id, blog)
            Unsent(title, tags, state.text, Now.instant().toEpochMilli(), receipt, from).keep(blog, BlogShelf.notes)
        }
        if (WaitingRoom.release(blog, except = from) || post != null) Desk.changed()
    }

    // The post this form opened with has arrived, sent from a form
    // that was left meanwhile: it is not this form's to send again.
    val arrivedBefore = remember { Desk.arrived }
    val arrived = Desk.arrived
    LaunchedEffect(arrived) {
        if (arrived != arrivedBefore && !sending && Desk.arrivedAt == blog) startEmpty()
    }

    // Kept at every letter: there is no moment at which an app is told
    // it is about to be closed. Not when the form opens -- a form that
    // was only opened was not written in.
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(title, tags, state.text) }.drop(1).collect { (title, tags, text) ->
            blog?.let { Unsent(title, tags, text, Now.instant().toEpochMilli(), receipt, from).keep(it, BlogShelf.notes) }
            Desk.changed()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { items ->
        scope.launch { load(items) }
    }

    fun forget() {
        made = null
    }

    PaperScreen(name = stringResource(R.string.new_post), answered = answered, symbol = MenuEntry.Add.symbol) {
        broughtBack?.let { back ->
            // Said, because it was not asked for: the form opens with
            // something in it that was not typed just now.
            val words = stringResource(R.string.back_in_the_form_what_was_written, RowDate.spoken(Instant.ofEpochMilli(back.at))) +
                if (back.namesPictures(beyond = shots.map { it.name })) " " + stringResource(R.string.its_pictures_were_not_kept_add_them) else ""
            BroughtBack(words, stringResource(R.string.start_with_an_empty_form)) { startEmpty() }
        }
        Plate(Modifier.gap(if (broughtBack != null) 14 else 0)) {
            row {
                PlainField(
                    title, { title = it }, prompt = stringResource(R.string.title),
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    // Left alone while the post is on its way -- from this form
                    // or from one that was left with its post still going.
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(), size = 18f, weight = FontWeight.SemiBold,
                )
            }
            row { PaperEditor(state, minHeight = 200.dp, enabled = !busy) }
            row {
                LaunchedEffect(Unit) { TagStore.loadIfNeeded() }
                FieldRow(
                    stringResource(R.string.tags), tags, { tags = it }, prompt = stringResource(R.string.comma_separated),
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    enabled = !busy,
                )
            }
            // A row only while there is a tag to offer: an empty one would leave its rule behind.
            val (done, typing) = TagStore.parts(tags)
            if (TagStore.suggest(typing, done, from = TagStore.tags).isNotEmpty()) {
                row { TagSuggestions(tags, { tags = it }) }
            }
        }
        // Said as it is typed: the marks in the sentence are examples, not marks.
        Hint(stringResource(R.string.markdown_a_picture_goes_in_as_description))
        Plate(Modifier.gap(10)) {
            row { Command(stringResource(R.string.preview), Symbols.eye) { previewing = true } }
        }

        SectionLabel(stringResource(R.string.pictures_and_video))
        Plate {
            for (shot in shots) {
                row {
                    key(shot.id) {
                        ShotCard(
                            shot, onShot = { describe(it) },
                            inText = Kept.named(shot.name, text),
                            insert = { insert(shot) }, remove = { remove(shot) }, look = { looking = shot.id },
                        )
                    }
                }
            }
            // A video takes its time to convert: the row says it is being read, and waits.
            row {
                Command(
                    stringResource(if (importing) R.string.reading else R.string.add_a_picture_or_video), Symbols.photoOnRectangle,
                    busy = importing, enabled = !busy,
                ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
            }
        }
        unread?.let { ProblemLine(it) }
        DeliveryNote(sent, textBytes, maxMb)

        // With the server silent the same key keeps the post on the
        // device instead, whole, to go when the server answers.
        PrimaryButton(
            stringResource(if (busy) R.string.sending else if (offline) R.string.keep_on_the_device else R.string.send_to_the_blog_as_a_draft),
            modifier = Modifier.gap(22),
            // A title alone is not a post: the blog refuses one with nothing under its header.
            enabled = !(busy || importing || !Unsent(title, tags, text).canBeSent || overweight),
            busy = busy,
        ) {
            // Neither ends with the form: a post on its way arrives, and is
            // forgotten here, whether or not anybody is still looking.
            Desk.outliving.launch { if (offline) keepOnDevice() else send() }
        }
        if (offline) Hint(stringResource(R.string.the_blog_s_server_cannot_be_reached))
        problem?.let { ProblemLine(it) }

        if (putBy) {
            SectionLabel(stringResource(R.string.done))
            Plate {
                row { Text(stringResource(R.string.kept_on_the_device_it_goes_to), color = Theme.ink, style = ui(15f)) }
            }
        }

        made?.let { made ->
            SectionLabel(stringResource(R.string.done))
            Plate {
                row { Text(stringResource(R.string.draft_written, made.slug), color = Theme.ink, style = ui(15f)) }
                made.warnings?.plain?.forEach { warning ->
                    row { Text(warning, color = Theme.muted, style = ui(13f)) }
                }
                val url = made.url
                if (!url.isNullOrEmpty()) {
                    row { Command(stringResource(R.string.open_the_preview), Symbols.safari) { runCatching { links.openUri(url) } } }
                }
                row {
                    // Deleted from there, the post is not this form's to point at any more.
                    Command(stringResource(R.string.its_properties_and_the_actions_on_it), Symbols.sliderHorizontal3, leads = true) {
                        val slug = made.slug
                        nav.push { PropsScreen(slug, gone = { forget() }) }
                    }
                }
            }
        }
    }

    if (previewing) {
        val marked = remember { state.text }
        val shown = remember { Preview.shown(shots) }
        PreviewSheet(title, marked, shown) { previewing = false }
    }
    looking?.let { one ->
        ShotsViewer(
            shots, current = one, onShot = { describe(it) },
            onDismiss = { looking = null },
        )
    }
}

/**
 * One shot's card: what it looks like, its name as the text names it
 * and what it weighs, its description, and the way into the text.
 * `look` shows the shot large, with its description under it.
 */
@Composable
fun ShotCard(shot: Shot, onShot: (Shot) -> Unit, inText: Boolean, insert: () -> Unit, remove: () -> Unit, look: () -> Unit = {}) {
    val video = shot.kind == Shot.Kind.Video
    // Decoded once for the shot, not with every letter typed beside it.
    val image = remember(shot.id) { (shot.thumb ?: if (video) shot.poster else shot.data)?.let { pictureOf(it, 480) } }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        // The small picture is a key: behind it the shot is large enough
        // to tell from the next one, and to describe.
        val name = stringResource(R.string.look_at_the_picture)
        val corner = RoundedCornerShape(10.dp)
        Pressable(look, modifier = Modifier.semantics { contentDescription = name }) {
            if (image != null) {
                Box(Modifier.size(84.dp).clip(corner)) {
                    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (video) {
                        Box(Modifier.align(Alignment.BottomStart).padding(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(5.dp)) {
                            Mark(Symbols.playFill, 10.dp, Color.White)
                        }
                    }
                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(5.dp)) {
                        Mark(Symbols.arrowUpLeftAndArrowDownRight, 9.dp, Color.White)
                    }
                }
            } else {
                Box(Modifier.size(84.dp).border(1.dp, Theme.line, corner), contentAlignment = Alignment.Center) {
                    Mark(if (video) Symbols.film else Symbols.photoOnRectangle, 20.dp, Theme.muted)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.weight(1f).alignByBaseline(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        shot.name, color = Theme.muted, style = mono(12f, bold = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                    )
                    if (video) EngineLabel(stringResource(R.string.video), Modifier.alignByBaseline(), size = 11f, color = Theme.accent)
                }
                Text(Delivery.size(shot.data.size), color = Theme.muted, style = mono(11f, bold = false), modifier = Modifier.alignByBaseline())
            }
            // The same words as in the picture's mark in the text: written
            // here or there, they are one description.
            PlainField(
                shot.alt, { onShot(shot.withAlt(it)) }, prompt = stringResource(R.string.no_description_yet),
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(), size = 15f,
            )
            // Two keys for a finger, not two words: each is as tall as a
            // finger needs and takes its half of the row.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pressable(insert, enabled = !inText, modifier = Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 40.dp), contentAlignment = Alignment.CenterStart) {
                        EngineLabel(
                            stringResource(if (inText) R.string.used_in_the_text else R.string.insert_into_text),
                            size = 12f, color = if (inText) Theme.muted else Theme.accent,
                        )
                    }
                }
                Pressable(remove) {
                    Box(Modifier.widthIn(min = 88.dp).heightIn(min = 40.dp), contentAlignment = Alignment.CenterEnd) {
                        EngineLabel(stringResource(R.string.remove), size = 12f, color = Theme.danger)
                    }
                }
            }
            // Said on the card, before the post goes: what the text
            // does not name stays behind.
            if (!inText) {
                Text(stringResource(R.string.not_in_the_text_it_will_not), color = Theme.danger, style = ui(12f))
            }
        }
    }
}
