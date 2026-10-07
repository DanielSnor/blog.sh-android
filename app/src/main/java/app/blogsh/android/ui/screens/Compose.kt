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
import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.EngineJson
import app.blogsh.android.model.Kept
import app.blogsh.android.model.Markdown
import app.blogsh.android.model.Media
import app.blogsh.android.model.Preview
import app.blogsh.android.model.Shot
import app.blogsh.android.model.TagStore
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.plain
import app.blogsh.android.model.said
import app.blogsh.android.ui.Asks
import app.blogsh.android.ui.Choice
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.DeliveryNote
import app.blogsh.android.ui.EditorState
import app.blogsh.android.ui.EngineLabel
import app.blogsh.android.ui.FieldRow
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.Mark
import app.blogsh.android.ui.PaperEditor
import app.blogsh.android.ui.PaperScreen
import app.blogsh.android.ui.PlainField
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.PrimaryButton
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.ScreenBack
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.TagSuggestions
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * What is typed into a new post -- its title, its tags, its text; not its
 * shots -- as the app keeps it between two runs. Android may close an app
 * that is out of sight, and a post half written would be gone with it:
 * so the words are written down as they change, under the blog they were
 * written for, and are there when the form is opened again.
 */
@Serializable
private data class KeptWords(val title: String = "", val tags: String = "", val text: String = "") {
    val none: Boolean get() = title.isEmpty() && tags.isEmpty() && text.isEmpty()

    companion object {
        private fun place(blog: String) = "compose.draft.$blog"

        fun read(blog: String?): KeptWords? {
            val written = BlogShelf.notes.read(place(blog ?: return null)) ?: return null
            return runCatching { EngineJson.decodeFromString<KeptWords>(written) }.getOrNull()?.takeIf { !it.none }
        }

        /** Nothing to keep is nothing kept: the note is taken away. */
        fun write(blog: String?, words: KeptWords?) {
            BlogShelf.notes.write(place(blog ?: return), if (words == null || words.none) null else EngineJson.encodeToString(words))
        }
    }
}

/**
 * What a delivery answered. A refusal anywhere among the answers is the
 * answer -- a picture the receiver would not take ends the delivery; with
 * none, the last one counts, the engine's own for the markdown.
 */
internal fun delivered(answers: List<ByteArray>): ActionAnswer {
    for (answer in answers) {
        try {
            Engine.element(answer)
        } catch (e: EngineError.Unreadable) {
            // Not an object, so not a refusal either.
        }
    }
    val last = answers.lastOrNull() ?: throw EngineError.Unreadable("")
    return Engine.decode<ActionAnswer>(last)
}

/**
 * Android has a back key, and the bar an arrow: either would take unsent
 * words with it. So before it does, the screen asks.
 */
@Composable
internal fun AsksToLeave(onLeave: () -> Unit, onDismiss: () -> Unit) {
    Asks(
        stringResource(R.string.android_leave_unsaved_title), stringResource(R.string.android_leave_unsaved_text),
        choices = listOf(Choice(stringResource(R.string.android_leave), danger = true, run = onLeave)),
        cancel = stringResource(R.string.android_stay), onDismiss = onDismiss,
    )
}

/**
 * "New post": what /write/ offers, as a form -- a title, the text, the
 * tags, photographs each with its description -- sent the way the page
 * sends it: the pictures first, the markdown last, one delivery. The
 * post arrives as a draft with a preview; publishing is its properties'
 * decision, the way it is at the desk.
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
    val kept = remember { KeptWords.read(blog) }
    var title by remember { mutableStateOf(kept?.title ?: "") }
    var tags by remember { mutableStateOf(kept?.tags ?: "") }
    val state = remember { EditorState(kept?.text ?: "") }
    // What is shown was picked up from the last time, and the form says so.
    var pickedUp by remember { mutableStateOf(kept != null) }
    // The author chose to leave: nothing more is written down behind them.
    var left by remember { mutableStateOf(false) }
    var shots by remember { mutableStateOf(emptyList<Shot>()) }
    var importing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var made by remember { mutableStateOf<ActionAnswer?>(null) }
    // Counted when a sending has answered: the page goes to the answer.
    var answered by remember { mutableStateOf(0) }
    var previewing by remember { mutableStateOf(false) }
    var looking by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    val unreadable = stringResource(R.string.one_picture_could_not_be_read)

    val text = state.text
    val textBytes = remember(text) { text.toByteArray(Charsets.UTF_8).size }
    // The shots the text names: only those go.
    val sent = Kept.sent(shots, text)
    val overweight = Delivery.over(sent, textBytes, maxMb)
    val unsent = title.isNotEmpty() || tags.isNotEmpty() || text.isNotEmpty() || shots.isNotEmpty()

    // ---- Pictures

    suspend fun load(items: List<Uri>) {
        if (items.isEmpty()) return
        importing = true
        try {
            for (item in items) {
                val shot = Media.shot(context, item, shots.size + 1, shots.map { it.name })
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

    suspend fun send() {
        sending = true
        try {
            problem = null
            val body = state.text
            // The text is what goes: a description typed on a card is in it
            // already, one typed into the text itself was never the card's.
            val markdown = Markdown.file(title, tags, body)
            val files = Kept.sent(shots, body).map { DeliveryFile(it.name, it.data) } +
                DeliveryFile(Markdown.fileName(title, body), markdown.toByteArray(Charsets.UTF_8))
            made = delivered(Engine.deliver(files))
            // The form is the next post's now, and nothing of this one is kept.
            title = ""
            tags = ""
            state.set("")
            shots = emptyList()
            pickedUp = false
            KeptWords.write(blog, null)
            answered += 1
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
            answered += 1
        } finally {
            sending = false
        }
    }

    // The words are written down a moment after the last of them; a form
    // emptied by its author keeps nothing, and says nothing of the last time.
    LaunchedEffect(title, tags, text) {
        val words = KeptWords(title, tags, text)
        if (words.none) {
            pickedUp = false
            KeptWords.write(blog, null)
            return@LaunchedEffect
        }
        delay(400)
        if (!left) KeptWords.write(blog, words)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { items ->
        scope.launch { load(items) }
    }

    fun leave() {
        if (unsent) asking = true else nav.pop()
    }

    fun forget() {
        made = null
    }
    ScreenBack(enabled = unsent) { asking = true }

    PaperScreen(onBack = { leave() }, name = stringResource(R.string.new_post), answered = answered) {
        if (pickedUp) Hint(stringResource(R.string.android_draft_kept))
        Plate(Modifier.padding(top = if (pickedUp) 14.dp else 0.dp)) {
            row {
                PlainField(
                    title, { title = it }, prompt = stringResource(R.string.title),
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(), size = 18f, weight = FontWeight.SemiBold,
                )
            }
            row { PaperEditor(state, minHeight = 200.dp) }
            row {
                LaunchedEffect(Unit) { TagStore.loadIfNeeded() }
                FieldRow(
                    stringResource(R.string.tags), tags, { tags = it }, prompt = stringResource(R.string.comma_separated),
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
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
        Plate(Modifier.padding(top = 10.dp)) {
            row { Command(stringResource(R.string.preview), Symbols.eye) { previewing = true } }
        }

        SectionLabel(stringResource(R.string.pictures_and_video))
        Plate {
            for (shot in shots) {
                row {
                    key(shot.id) {
                        ShotCard(
                            shot, onShot = { describe(it) },
                            inText = text.contains("(${shot.name})"),
                            insert = { insert(shot) }, remove = { remove(shot) }, look = { looking = shot.id },
                        )
                    }
                }
            }
            // A video takes its time to convert: the row says it is being read, and waits.
            row {
                Command(
                    stringResource(if (importing) R.string.reading else R.string.add_a_picture_or_video), Symbols.photoOnRectangle,
                    busy = importing,
                ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
            }
        }
        DeliveryNote(sent, textBytes, maxMb)

        PrimaryButton(
            stringResource(if (sending) R.string.sending else R.string.send_to_the_blog_as_a_draft),
            modifier = Modifier.padding(top = 22.dp),
            enabled = !(sending || importing || (title.isEmpty() && text.trim().isEmpty()) || overweight),
            busy = sending,
        ) { scope.launch { send() } }
        problem?.let { ProblemLine(it) }

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
    if (asking) {
        AsksToLeave(
            onLeave = {
                // Asked and answered: what was written is let go of here too.
                left = true
                KeptWords.write(blog, null)
                nav.pop()
            },
            onDismiss = { asking = false },
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
