package app.blogsh.android.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.Engine
import app.blogsh.android.model.ListAnswer
import app.blogsh.android.model.PostLink
import app.blogsh.android.model.PostRow
import app.blogsh.android.model.PropsAnswer
import app.blogsh.android.model.PostState
import app.blogsh.android.model.TagStore
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.said
import app.blogsh.android.ui.Busy
import app.blogsh.android.ui.Card
import app.blogsh.android.ui.EmptyNote
import app.blogsh.android.ui.FilterPill
import app.blogsh.android.ui.Hairline
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.LocalShown
import app.blogsh.android.ui.Mark
import app.blogsh.android.ui.Menu
import app.blogsh.android.ui.MenuKey
import app.blogsh.android.ui.OnShown
import app.blogsh.android.ui.PaperRow
import app.blogsh.android.ui.PaperScaffold
import app.blogsh.android.ui.PlainField
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.Room
import app.blogsh.android.ui.RowDate
import app.blogsh.android.ui.share
import app.blogsh.android.ui.voiced
import androidx.compose.ui.platform.LocalContext
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** The states [s] offers, in the engine's own words. */
enum class StateFilter(private val labelId: Int) {
    Published(R.string.browse_state_published),
    Unpublished(R.string.browse_state_unpublished),
    Draft(R.string.browse_state_draft),
    Scheduled(R.string.browse_state_scheduled),
    Pinned(R.string.browse_state_pinned);

    val label: String @Composable get() = stringResource(labelId)

    fun matches(post: PostRow): Boolean = when (this) {
        Published -> post.state == PostState.Published
        Unpublished -> post.state == PostState.Draft
        Draft -> post.state == PostState.Draft && !post.scheduled
        Scheduled -> post.scheduled
        Pinned -> post.pinned
    }
}

/** What the engine found for a query, in its own order. */
private data class Found(val query: String, val rows: List<PostRow>)

/**
 * The archive as `browse` walks it: the posts newest first, the three
 * filters the screen has ([t] type, [s] state, [g] tag), the search ([/]),
 * [z] to clear them, Enter to open a post -- its crossroads -- and Space
 * for a look at the one under the cursor. The search is the engine's own
 * (`list --search`), over the whole text; until its answer is back, and on
 * an engine from before the flag, the rows are matched on what they carry
 * (title, slug, tags).
 *
 * What the screen opens with: a state already chosen (`initialState`), the
 * search already open (`searching`).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(languages: List<String> = emptyList(), baseUrl: String = "", initialState: StateFilter? = null, searching: Boolean = false) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val noAddress = stringResource(R.string.the_site_has_no_address_set_so)
    var posts by remember { mutableStateOf(emptyList<PostRow>()) }
    var problem by remember { mutableStateOf<String?>(null) }
    // The first reading begins with the screen: nothing is said to be empty before it has been asked.
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(initialState) }
    var tag by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Found?>(null) }
    var searchingFor by remember { mutableStateOf<String?>(null) }
    var previewing by remember { mutableStateOf<PostRow?>(null) }
    var searchOpen by remember { mutableStateOf(searching) }
    // The row a long press was on: its menu is the one open.
    var pressed by remember { mutableStateOf<String?>(null) }

    val types = remember(posts) { posts.map { it.type }.distinct().sorted() }
    val tags = remember(posts) { posts.flatMap { it.tags }.distinct().sortedBy { it.lowercase() } }

    // The query as it is asked: what was typed, without the space around it.
    val words = query.trim()

    fun matchesLocally(post: PostRow): Boolean {
        val tokens = words.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val haystack = (listOf(post.title ?: "", post.slug) + post.tags).joinToString(" ").lowercase()
        return tokens.all { token -> if (token.startsWith("-")) !haystack.contains(token.drop(1)) else haystack.contains(token) }
    }

    // The rows the filters and the search leave. The engine's answer when
    // it is the answer to this very query -- it read the whole text, and
    // its order is the screen's -- and the rows' own words until then.
    val shown = remember(posts, found, words, type, state, tag) {
        val answer = found
        val wantedType = type
        val wantedState = state
        val wantedTag = tag?.lowercase()
        val searched = if (answer != null && answer.query == words) answer.rows else posts.filter(::matchesLocally)
        searched.filter { post ->
            when {
                wantedType != null && post.type != wantedType -> false
                wantedState != null && !wantedState.matches(post) -> false
                wantedTag != null && post.tags.none { it.lowercase() == wantedTag } -> false
                else -> true
            }
        }
    }

    // How many, beside the screen's name: all of them, or how many of
    // them the filters and the search leave.
    val filtered = type != null || state != null || tag != null || words.isNotEmpty()
    val line = if (filtered) stringResource(R.string.of, shown.size, posts.size) else NumberFormat.getIntegerInstance().format(posts.size)
    val countLine = if (searchingFor == null) line else "$line …"

    suspend fun load() {
        loading = true
        try {
            val answer = Engine.call<ListAnswer>("list")
            posts = answer.posts
            problem = null
            // The archive is in hand: the tag suggestions need not ask for it again.
            TagStore.take(answer.posts)
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        } finally {
            loading = false
        }
    }

    // [/]: the question goes to the engine once the keys rest, not on
    // every one of them -- each asking is a connection.
    suspend fun search(asked: String) {
        if (asked.isEmpty()) {
            found = null
            searchingFor = null
            return
        }
        delay(450)
        searchingFor = asked
        try {
            val answer = try {
                Engine.call<ListAnswer>("list", "--search=$asked")
            } catch (e: Throwable) {
                if (e.isCalledOff) throw e
                null
            }
            // An engine from before the flag ignores it and answers with the
            // whole archive; the query said back is how the two are told apart.
            if (answer?.search != null) found = Found(asked, answer.posts)
        } finally {
            if (searchingFor == asked) searchingFor = null
        }
    }

    // A row knows its slug, not its address: the engine says the address
    // (a post can carry one of its own, a draft has its hidden page), and
    // the sheet opens with it.
    suspend fun shareRow(post: PostRow) {
        try {
            val link = PostLink.of(Engine.call<PropsAnswer>("props", post.slug))
            if (link != null) {
                problem = null
                share(context, link)
            } else {
                problem = noAddress
            }
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        }
    }

    // Every time the screen is come to: a post may be another on the way back from it.
    OnShown { load() }
    val inFront = LocalShown.current
    LaunchedEffect(words, inFront) { if (inFront) search(words) }

    PaperScaffold(onBack = { nav.pop() }, name = stringResource(R.string.tile_browse), count = countLine) {
        // The search and the filters stay put: under the bar, over the rows,
        // however far down a long archive has been read -- a filter is wanted
        // in the middle of a list more often than at its head.
        PaperRow(rule = false) {
            Box(Modifier.padding(top = 4.dp, bottom = 2.dp)) {
                SearchField(query, { query = it }, open = searchOpen, opened = { searchOpen = false })
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Theme.gutter, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Pressable({ state = null }) { FilterPill(stringResource(R.string.all), selected = state == null) }
            for (one in StateFilter.entries) {
                Pressable({ state = if (state == one) null else one }) { FilterPill(one.label, selected = state == one) }
            }
            PickPill(
                label = type?.let { stringResource(R.string.type_2, it) } ?: stringResource(R.string.type),
                any = stringResource(R.string.any_type), options = types, selected = type,
            ) { type = it }
            PickPill(
                label = tag?.let { stringResource(R.string.tag_2, it) } ?: stringResource(R.string.tag),
                any = stringResource(R.string.any_tag), options = tags, selected = tag,
            ) { tag = it }
        }
        Hairline()
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
                problem?.let { text ->
                    item { PaperRow { Text(text, color = Theme.muted, style = ui(14f), modifier = Modifier.padding(vertical = 11.dp)) } }
                }
                items(shown, key = { it.id }) { post ->
                    // A long press is what a swipe and a held finger are on iOS: a look at the post.
                    PaperRow(
                        Modifier.combinedClickable(
                            onClick = { nav.push { PostCrossroadsScreen(post, languages, gone = { scope.launch { load() } }) } },
                            onLongClick = { pressed = post.id },
                        )
                    ) {
                        PostRowView(post)
                        Menu(pressed == post.id, { pressed = null }) {
                            MenuKey(stringResource(R.string.preview), Symbols.docTextMagnifyingglass) {
                                pressed = null
                                previewing = post
                            }
                            MenuKey(stringResource(R.string.share_the_link), Symbols.squareAndArrowUp) {
                                pressed = null
                                scope.launch { shareRow(post) }
                            }
                        }
                    }
                }
            }
            if (loading && posts.isEmpty()) {
                Busy(modifier = Modifier.align(Alignment.Center))
            } else if (!loading && shown.isEmpty() && problem == null && searchingFor == null) {
                // An archive with nothing in it is the screen; a search or a filter
                // that left nothing is a remark under the pills, the keyboard maybe up.
                EmptyNote(
                    Symbols.tray, stringResource(if (posts.isEmpty()) R.string.no_posts else R.string.nothing_matches),
                    room = if (posts.isEmpty()) Room.Screen else Room.Part, modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }

    previewing?.let { post -> PostPreviewSheet(post, baseUrl, onDismiss = { previewing = null }) }
}

/**
 * The search, a line of its own at the head of the list: a glass, the
 * words, and a key that takes them away again. Opened already when the
 * screen was come to for the search -- the cursor in it, the keyboard up.
 */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, open: Boolean, opened: () -> Unit) {
    val focus = remember { FocusRequester() }
    Card(capsule = true) {
        Mark(Symbols.magnifyingglass, 17.dp)
        PlainField(query, onQuery, prompt = stringResource(R.string.search_the_archive), modifier = Modifier.weight(1f).focusRequester(focus))
        if (query.isNotEmpty()) {
            val clear = stringResource(R.string.android_b_clear)
            Pressable({ onQuery("") }, modifier = Modifier.semantics { contentDescription = clear }) { Mark(Symbols.xmarkCircle, 18.dp, Theme.muted) }
        }
    }
    // Once: the screen come back to keeps the search as it was left.
    LaunchedEffect(Unit) {
        if (open) {
            opened()
            focus.requestFocus()
        }
    }
}

/**
 * A filter chosen from a list -- the type, the tag: the pill says which
 * one is on and opens the others, the first line being none at all.
 */
@Composable
private fun PickPill(label: String, any: String, options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Pressable({ open = true }) { FilterPill(label, selected = selected != null) }
        Menu(open, { open = false }) {
            MenuKey(any, if (selected == null) Symbols.checkmark else null) {
                open = false
                onSelect(null)
            }
            for (option in options) {
                MenuKey(option, if (option == selected) Symbols.checkmark else null) {
                    open = false
                    onSelect(option)
                }
            }
        }
    }
}

/**
 * A post as a row says it: its title, its tags under it, and at the
 * edge its date -- in the accent when it is a date still to come.
 */
@Composable
fun PostRowView(post: PostRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // An untitled post goes by its slug, dimmed, as the terminal dims it.
            Text(
                post.title ?: post.slug, color = if (post.title == null) Theme.muted else Theme.ink,
                style = ui(15f, if (post.title == null) FontWeight.Medium else FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (post.tags.isEmpty()) post.type else post.tags.joinToString(", "), color = Theme.muted, style = ui(13f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // Why the search found it: the line of its text that matched.
            val match = post.match
            if (!match.isNullOrEmpty()) {
                Text(match, color = Theme.muted, style = ui(12f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.End) {
            val day = post.day
            if (day != null) {
                // A date is in the accent, as on the blog's own pages; one
                // still to come is told by its weight, and by saying a day
                // and an hour.
                Text(
                    if (post.scheduled) RowDate.soon(day) else RowDate.short(day), color = Theme.accent,
                    style = mono(11f, bold = post.scheduled), maxLines = 1,
                )
            } else {
                StateBadge(post)
            }
            if (post.pinned) {
                val pinned = stringResource(R.string.pinned)
                Mark(Symbols.pin, 14.dp, Theme.accent, Modifier.semantics { contentDescription = pinned })
            }
        }
    }
}

/**
 * A draft, a scheduled draft, or nothing at all for a published post:
 * the same three states `list` marks on the terminal.
 */
@Composable
fun StateBadge(post: PostRow) {
    if (post.scheduled) {
        StateMark(stringResource(R.string.scheduled), filled = true)
    } else if (post.state == PostState.Draft) {
        StateMark(stringResource(R.string.draft), filled = false)
    }
}

@Composable
private fun StateMark(text: String, filled: Boolean) {
    Text(
        voiced(text), color = if (filled) Color.White else Theme.muted, style = mono(11f, bold = filled), maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (filled) Modifier.background(Theme.accent) else Modifier.border(1.dp, Theme.line, CircleShape))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
