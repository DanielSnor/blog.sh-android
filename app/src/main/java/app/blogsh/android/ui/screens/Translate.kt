package app.blogsh.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.ActionAnswer
import app.blogsh.android.model.BlogShelf
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.DeliveryFile
import app.blogsh.android.model.Desk
import app.blogsh.android.model.Engine
import app.blogsh.android.model.Preview
import app.blogsh.android.model.TranslationAnswer
import app.blogsh.android.model.TranslationEntry
import app.blogsh.android.model.Unsaved
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.plain
import app.blogsh.android.model.said
import app.blogsh.android.ui.Asks
import app.blogsh.android.ui.BroughtBack
import app.blogsh.android.ui.Busy
import app.blogsh.android.ui.Choice
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.EditorState
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.PaperEditor
import app.blogsh.android.ui.PaperScreen
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.PrimaryButton
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.broughtBackWords
import app.blogsh.android.ui.gap
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * [l]: the post's words in another language the site publishes. What
 * `translate` opens in the editor, opened here: the translation's header
 * (its title, its address) and its text, with the original beside it to
 * translate from. The save goes back as a file saying which post, which
 * version and which language; an empty title and body take the language
 * off the post, as the editor's hint says.
 *
 * What is written and not saved is kept on the device (`Unsaved`), for
 * this post and this language, and is back the next time it is opened.
 */
@Composable
fun TranslateScreen(slug: String, lang: String) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf<TranslationEntry?>(null) }
    val state = remember { EditorState() }
    var showingOriginal by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<ActionAnswer?>(null) }
    // The last save took the language off rather than wrote it.
    var tookOff by remember { mutableStateOf(false) }
    // Counted when a save has answered: the page goes to the answer.
    var answered by remember { mutableStateOf(0) }
    var confirmingRemoval by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    // The words brought back when the screen opened, for the line that says so.
    var broughtBack by remember { mutableStateOf<Unsaved?>(null) }
    // The blog the screen was opened for: what is kept is kept under it.
    val blog = remember { Blogs.currentId }
    val what = remember(lang) { Unsaved.What.Language(lang) }

    val languageName = remember(lang) { Locale.forLanguageTag(lang).getDisplayLanguage(Locale.getDefault()).ifEmpty { lang } }
    val text = state.text

    suspend fun load() {
        try {
            val answer = Engine.call<TranslationAnswer>("translate", slug, "--lang", lang)
            entry = answer.post
            // The blog's words, or the ones written here and never saved.
            val kept = blog?.let { Unsaved.kept(it, slug, what, BlogShelf.notes) }
            if (kept != null && kept.text != answer.post.text) {
                broughtBack = kept
                state.set(kept.text)
            } else {
                broughtBack = null
                state.set(answer.post.text)
            }
            problem = null
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        }
    }

    // At every letter; words that are the blog's again are nothing to keep.
    fun keep(written: String) {
        val post = entry ?: return
        if (blog == null) return
        if (written == post.text) Unsaved.forget(blog, slug, what, BlogShelf.notes)
        else Unsaved(written, post.base, System.currentTimeMillis(), post.title).keep(blog, slug, what, BlogShelf.notes)
        Desk.changed()
    }

    fun takeTheBlogs() {
        broughtBack = null
        state.set(entry?.text ?: "")
        blog?.let { Unsaved.forget(it, slug, what, BlogShelf.notes) }
    }

    // The header gets the three lines of the delivery: which post, which
    // version, which language.
    fun fileText(body: String): String {
        val post = entry ?: return body
        val lines = "edits: ${post.slug}\nbase: ${post.base}\nlang: $lang\n"
        if (body.startsWith("---\n")) return "---\n" + lines + body.drop(4)
        return "---\n" + lines + "---\n\n" + body
    }

    suspend fun save(body: String, takingOff: Boolean = false) {
        saving = true
        try {
            problem = null
            val file = DeliveryFile("$slug-$lang.md", fileText(body).toByteArray(Charsets.UTF_8))
            saved = delivered(Engine.deliver(listOf(file)))
            tookOff = takingOff
            // Saved, or taken off: nothing is left to bring back.
            blog?.let { Unsaved.forget(it, slug, what, BlogShelf.notes) }
            broughtBack = null
            Desk.changed()
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

    fun leave() = nav.pop()

    Box(Modifier.fillMaxSize()) {
        PaperScreen(title = entry?.title, answered = answered) {
            val post = entry
            if (post != null) {
                broughtBack?.let { back ->
                    BroughtBack(broughtBackWords(back, post.base, null), stringResource(R.string.take_the_text_as_the_blog_has)) { takeTheBlogs() }
                }
                SectionLabel(stringResource(R.string.the_original))
                Plate {
                    row {
                        Command(
                            stringResource(if (showingOriginal) R.string.hide_the_original else R.string.show_the_original),
                            if (showingOriginal) Symbols.chevronUp else Symbols.chevronDown,
                        ) { showingOriginal = !showingOriginal }
                    }
                    if (showingOriginal) {
                        row { SelectionContainer { Text(post.original, color = Theme.ink, style = mono(14f, bold = false)) } }
                    }
                }
                Hint(stringResource(R.string.only_the_words_are_translated_the_date))

                SectionLabel(stringResource(R.string.s_in, languageName))
                Plate {
                    row { PaperEditor(state, minHeight = 280.dp) }
                }
                Plate(Modifier.gap(10)) {
                    row { Command(stringResource(R.string.preview), Symbols.eye) { previewing = true } }
                }

                PrimaryButton(
                    if (saving) stringResource(R.string.saving) else stringResource(R.string.save_the_text, languageName),
                    modifier = Modifier.gap(22),
                    enabled = !(saving || text == post.text),
                    busy = saving,
                ) { scope.launch { save(state.text) } }
                problem?.let { ProblemLine(it) }
                if (post.written) {
                    Plate(Modifier.gap(14)) {
                        row {
                            Command(stringResource(R.string.take_this_language_off_the_post), Symbols.minusCircle, danger = true, enabled = !saving) {
                                confirmingRemoval = true
                            }
                        }
                    }
                }

                saved?.let { saved ->
                    SectionLabel(stringResource(if (tookOff) R.string.taken_off else R.string.saved))
                    Plate {
                        // What was done, not only to what: written, or taken off.
                        row {
                            Text(
                                stringResource(if (tookOff) R.string.the_text_is_taken_off else R.string.the_text, saved.slug, languageName),
                                color = Theme.ink, style = ui(15f),
                            )
                        }
                        saved.warnings?.plain?.forEach { warning ->
                            row { Text(warning, color = Theme.muted, style = ui(13f)) }
                        }
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
        // The translation's own title, or the post's while it has none;
        // the pictures are the post's, from beside its page on the blog.
        val parts = remember { Preview.parts(state.text) }
        val shown = remember { Preview.shown(entry?.media ?: emptyList(), entry?.preview ?: "/") }
        PreviewSheet(parts.first.ifEmpty { entry?.title ?: "" }, parts.second, shown, lang = lang) { previewing = false }
    }
    if (confirmingRemoval) {
        Asks(
            stringResource(R.string.take_the_text_off_the_post_then, languageName, slug),
            choices = listOf(Choice(stringResource(R.string.take_it_off), danger = true) { scope.launch { save("---\ntitle:\n---\n\n", takingOff = true) } }),
            onDismiss = { confirmingRemoval = false },
        )
    }
}
