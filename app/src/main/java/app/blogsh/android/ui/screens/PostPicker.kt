package app.blogsh.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blogsh.android.R
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.EditAnswer
import app.blogsh.android.model.EditEntry
import app.blogsh.android.model.Engine
import app.blogsh.android.model.Lede
import app.blogsh.android.model.ListAnswer
import app.blogsh.android.model.PostRow
import app.blogsh.android.model.PostState
import app.blogsh.android.model.PropsAnswer
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.said
import app.blogsh.android.ui.Busy
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.EmptyNote
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.OnShown
import app.blogsh.android.ui.PaperRow
import app.blogsh.android.ui.PaperScaffold
import app.blogsh.android.ui.PaperScreen
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.PostHeading
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.RowDate
import app.blogsh.android.ui.ScreenHeader
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** How many posts the pick offers: the terminal's RECENT_LIST_COUNT. */
private const val RECENT_COUNT = 50

/**
 * "A post": first the pick, the last fifty posts the terminal offers
 * (pick_slug_interactively, RECENT_LIST_COUNT), then the crossroads the
 * wizard puts after it -- the text, or the properties and the actions.
 *
 * `languages` are the ones the site publishes beyond its own, for the crossroads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostPickerScreen(languages: List<String> = emptyList()) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf(emptyList<PostRow>()) }
    // How many the blog has in all: the list is only its newest.
    var total by remember { mutableStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }
    // The first reading begins with the screen: nothing is said to be empty before it has been asked.
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        try {
            val answer = Engine.call<ListAnswer>("list")
            posts = answer.posts.take(RECENT_COUNT)
            total = answer.posts.size
            problem = null
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        } finally {
            loading = false
        }
    }

    // Every time the screen is come to: a post may be another on the way back from it.
    OnShown { load() }

    PaperScaffold(onBack = { nav.pop() }) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    try {
                        load()
                    } finally {
                        refreshing = false
                    }
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    PaperRow { ScreenHeader(stringResource(R.string.tile_post), modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)) }
                }
                problem?.let { text ->
                    item { PaperRow { Text(text, color = Theme.muted, style = ui(14f), modifier = Modifier.padding(vertical = 11.dp)) } }
                }
                items(posts, key = { it.id }) { post ->
                    PaperRow(Modifier.clickable { nav.push { PostCrossroadsScreen(post, languages, gone = { scope.launch { load() } }) } }) {
                        PostRowView(post)
                    }
                }
                // Why the list ends here, and where the rest is: said once
                // there is a rest. The whole of it is the way to the archive,
                // with its search already open.
                if (total > posts.size) {
                    item {
                        PaperRow(
                            Modifier.clickable { nav.push { ArchiveScreen(languages, Blogs.current?.url ?: "", searching = true) } },
                            rule = false,
                        ) {
                            val words = buildAnnotatedString {
                                withStyle(SpanStyle(color = Theme.muted)) {
                                    append(stringResource(R.string.the_last_posts_of_as_blog_sh, "${posts.size}", NumberFormat.getIntegerInstance().format(total)))
                                }
                                append(" ")
                                withStyle(SpanStyle(color = Theme.accent)) { append(stringResource(R.string.search_the_archive_2)) }
                            }
                            Text(words, style = ui(13f), modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
                        }
                    }
                }
            }
            if (loading && posts.isEmpty()) {
                Busy(modifier = Modifier.align(Alignment.Center))
            } else if (!loading && posts.isEmpty() && problem == null) {
                EmptyNote(Symbols.tray, stringResource(R.string.no_posts_to_choose_from), modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/**
 * The wizard's crossroads for one post: Enter opens the text, [v] the
 * properties. The text is the editor's, and comes with it; the
 * properties are here.
 *
 * `gone` is said to the list the post was picked from, when the post is deleted.
 */
@Composable
fun PostCrossroadsScreen(post: PostRow, languages: List<String> = emptyList(), gone: (() -> Unit)? = null) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var looking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    // The post's text as the editor would open it: where the lede is read
    // from, and handed on to the editor so it need not ask again.
    var entry by remember { mutableStateOf<EditEntry?>(null) }
    var reading by remember { mutableStateOf(false) }
    // The post was deleted from its properties: this screen is about a
    // post that is not there, and leaves once it is in front again.
    var deleted by remember { mutableStateOf(false) }
    val noAddress = stringResource(R.string.the_site_has_no_address_set_so)

    // `edit <slug> --json`: the text handed out, nothing written. A
    // failure costs the lede and nothing else -- the keys below ask for
    // themselves.
    suspend fun read() {
        if (deleted) {
            // Back in front, on the way out. iOS waits here for the screen
            // above to finish leaving; these screens change at once, and so
            // does this one.
            gone?.invoke()
            nav.pop()
            return
        }
        reading = true
        try {
            val answer = try {
                Engine.call<EditAnswer>("edit", post.slug)
            } catch (e: Throwable) {
                if (e.isCalledOff) throw e
                null
            }
            if (answer != null) entry = answer.post
        } finally {
            reading = false
        }
    }

    // The address is the engine's to say: a post can carry one of its own.
    suspend fun show() {
        looking = true
        try {
            val props = Engine.call<PropsAnswer>("props", post.slug)
            if (props.url.isEmpty()) {
                problem = noAddress
                return
            }
            problem = null
            uri.openUri(props.url)
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        } finally {
            looking = false
        }
    }

    // Every time the screen is come to, not once: on the way back from
    // the editor the text may be another, and so is the version a save
    // has to name.
    OnShown { read() }

    PaperScreen {
        PostHeading(post.title ?: post.slug, post.slug)
        // Which post this is, before anything is done to it: when it is
        // from, what state it is in, and how it begins.
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            post.day?.let { day ->
                Text(
                    if (post.scheduled) RowDate.soon(day) else RowDate.short(day), color = if (post.scheduled) Theme.accent else Theme.muted,
                    style = mono(12f, bold = post.scheduled), maxLines = 1,
                )
            }
            StateBadge(post)
            Text(
                if (post.tags.isEmpty()) post.type else post.tags.joinToString(", "), color = Theme.muted, style = ui(13f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val text = entry?.text
        if (text != null || reading) {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                val style = ui(15f).copy(lineHeight = 21.sp)
                if (text != null) {
                    val lede = remember(text) { Lede.of(text) }
                    val picture = lede.picture
                    if (lede.words.isNotEmpty()) {
                        Text(lede.words, color = Theme.ink, style = style, maxLines = 9, overflow = TextOverflow.Ellipsis)
                    } else if (picture != null) {
                        Text(stringResource(R.string.picture, picture), color = Theme.muted, style = style)
                    } else {
                        Text(stringResource(R.string.the_post_has_no_text), color = Theme.muted, style = style)
                    }
                } else {
                    Busy()
                }
            }
        }
        // The prompt's words: "Edit what? [Enter] the text  [v] properties and
        // actions". The text is the editor's and comes with it.
        SectionLabel(stringResource(R.string.edit_what))
        val theText = stringResource(R.string.the_text_2)
        val properties = stringResource(R.string.properties_and_actions)
        // [l]: only on a site that publishes more than one language,
        // the way the prompt only mentions it there.
        val tongues = languages.map { lang ->
            lang to stringResource(R.string.language, Locale.forLanguageTag(lang).getDisplayLanguage(Locale.getDefault()).ifEmpty { lang })
        }
        Plate {
            row {
                Command(theText, Symbols.textAlignleft, leads = true) {
                    val loaded = entry
                    nav.push { TextEditScreen(post.slug, loaded) }
                }
            }
            for ((lang, label) in tongues) {
                row { Command(label, Symbols.characterBubble, leads = true) { nav.push { TranslateScreen(post.slug, lang) } } }
            }
            row {
                // A post deleted from its properties takes this screen with
                // it, and the list reads itself again.
                Command(properties, Symbols.sliderHorizontal3, leads = true) { nav.push { PropsScreen(post.slug, gone = { deleted = true }) } }
            }
        }
        // Not a key of the prompt: at the desk the browser is one window
        // away, on a phone the page is only this far. A published post
        // opens at its address, a draft at the hidden page the build keeps.
        val web = stringResource(if (post.state == PostState.Published) R.string.show_on_the_web else R.string.show_the_preview_on_the_web)
        Plate(Modifier.padding(top = 10.dp)) {
            row { Command(web, Symbols.safari, busy = looking) { scope.launch { show() } } }
        }
        problem?.let { ProblemLine(it) }
    }
}
