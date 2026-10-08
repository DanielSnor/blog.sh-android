package app.blogsh.android.ui

import android.graphics.BitmapFactory
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blogsh.android.BlogshApp
import app.blogsh.android.R
import app.blogsh.android.model.Begun
import app.blogsh.android.model.BlogShelf
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Desk
import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.Facts
import app.blogsh.android.model.HeldAnswer
import app.blogsh.android.model.ListAnswer
import app.blogsh.android.model.QueueAnswer
import app.blogsh.android.model.QueueRow
import app.blogsh.android.model.StatsAnswer
import app.blogsh.android.model.TagStore
import app.blogsh.android.model.VersionAnswer
import app.blogsh.android.model.engineInstant
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.said
import app.blogsh.android.ui.screens.AddBlogSheet
import app.blogsh.android.ui.screens.ArchiveScreen
import app.blogsh.android.ui.screens.BlogsSheet
import app.blogsh.android.ui.screens.ComposeScreen
import app.blogsh.android.ui.screens.PostPickerScreen
import app.blogsh.android.ui.screens.QueueScreen
import app.blogsh.android.ui.screens.SettingsSheet
import app.blogsh.android.ui.screens.SiteScreen
import app.blogsh.android.ui.screens.StateFilter
import app.blogsh.android.ui.screens.TextEditScreen
import app.blogsh.android.ui.screens.TranslateScreen
import app.blogsh.android.ui.screens.TrashScreen
import java.net.URI
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * The wizard's main menu: what `./blog.sh` with no command shows. The
 * order and the number of entries are the engine's (its locales, the
 * wizard_menu_ keys); the app adds nothing to it. Settings are not a menu
 * entry -- the terminal has none -- so they sit in the bar.
 */
enum class MenuEntry(val nameId: Int, val shortId: Int, val symbol: Int) {
    Add(R.string.new_post, R.string.tile_add, Symbols.plus),
    Post(R.string.a_post, R.string.tile_post, Symbols.docText),
    Queue(R.string.the_scheduled_post_queue, R.string.tile_queue, Symbols.listNumber),
    Browse(R.string.the_archive, R.string.tile_browse, Symbols.archivebox),
    Restore(R.string.trash, R.string.tile_restore, Symbols.trash),
    Rebuild(R.string.the_site, R.string.tile_rebuild, Symbols.globe),
}

/**
 * What waits, read once for the first screen: the queue, and how many
 * drafts are in progress.
 */
data class Glance(val queue: List<QueueRow>, val drafts: Int)

/** What the first screen knows, kept while other screens are over it. */
class HomeState {
    var identity by mutableStateOf<VersionAnswer?>(null)
    var problem by mutableStateOf<String?>(null)
    var glance by mutableStateOf<Glance?>(null)
    var refreshing by mutableStateOf(false)

    // One counting at a time: it is the slowest thing the first screen asks.
    private var counting = false

    /** The languages the site publishes beyond its own. */
    val otherLanguages: List<String>
        get() = identity?.let { answer -> answer.site.locales.filter { it != answer.site.lang } } ?: emptyList()

    /** Another blog: nothing of the last one stays on the screen. */
    fun forget() {
        identity = null
        problem = null
        glance = null
        TagStore.reset()
    }

    /**
     * Everything the first screen says, over one connection: the identity
     * block (`version --json`), the queue and the drafts. Without a server
     * set up the header says so and the settings are one tap away.
     */
    suspend fun load() {
        val asked = Blogs.currentId
        try {
            val answers = Engine.batch(listOf(listOf("version"), listOf("queue"), listOf("list", "--drafts")))
            // Another blog was opened while this one was answering.
            if (asked != Blogs.currentId) return
            val answer = Engine.decode<VersionAnswer>(answers[0])
            identity = answer
            problem = null
            Blogs.update { blog ->
                blog.copy(
                    name = answer.site.name, claim = answer.site.claim, url = answer.site.url, maxMb = answer.maxMb,
                    accentLight = answer.site.accent?.light ?: blog.accentLight, accentDark = answer.site.accent?.dark ?: blog.accentDark,
                    tonesLight = answer.site.palette?.light ?: blog.tonesLight, tonesDark = answer.site.palette?.dark ?: blog.tonesDark,
                )
            }
            glance = glance(answers[1], answers[2])
            // The numbers under the search come after the screen itself:
            // counting the archive takes the engine seconds.
            loadFacts(whole = true)
        } catch (e: EngineError.NotConfigured) {
            identity = null
            glance = null
            problem = BlogshApp.context.getString(R.string.nothing_to_connect_to_yet_the_name)
        } catch (e: Throwable) {
            // Called off, or another blog by now: the screen keeps what it shows.
            if (e.isCalledOff) throw e
            if (asked != Blogs.currentId) return
            identity = null
            glance = null
            problem = e.said
        }
    }

    /**
     * The two cards again, on the way back from a screen that may have
     * changed them -- one connection, and a failure leaves them as they were.
     */
    suspend fun loadGlance() {
        val asked = Blogs.currentId
        if (identity == null) return
        val answers = try {
            Engine.batch(listOf(listOf("queue"), listOf("list", "--drafts")))
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            return
        }
        if (asked != Blogs.currentId) return
        glance = glance(answers[0], answers[1]) ?: glance
        loadFacts(whole = false)
    }

    /**
     * The blog in numbers: the archive counted (`stats`), and what the
     * trash and the versions hold -- `empty` asked without `--yes` says
     * how much and touches nothing. Kept with the blog, so the next launch
     * shows them at once. On the way back from a screen only the two that
     * a screen can have changed are asked again; the archive is counted
     * when the first screen is loaded whole.
     */
    private suspend fun loadFacts(whole: Boolean) {
        val asked = Blogs.currentId
        if (identity == null || counting) return
        counting = true
        try {
            var facts = Blogs.current?.facts ?: Facts()
            val all = whole || Blogs.current?.facts == null
            val commands = (if (all) listOf(listOf("stats")) else emptyList()) + listOf(listOf("empty", "trash"), listOf("empty", "versions"))
            val answers = try {
                Engine.batch(commands)
            } catch (e: Throwable) {
                if (e.isCalledOff) throw e
                return
            }
            if (answers.size != commands.size || asked != Blogs.currentId) return
            if (all) {
                val stats = runCatching { Engine.decode<StatsAnswer>(answers[0]) }.getOrNull() ?: return
                facts = facts.copy(
                    posts = stats.posts.total, since = (stats.span.first ?: "").take(4), words = stats.words.total,
                    readingHours = stats.words.readingHours, tags = stats.tags.unique, media = stats.media.files, mediaBytes = stats.media.bytes,
                )
            }
            runCatching { Engine.decode<HeldAnswer>(answers[answers.size - 2]) }.getOrNull()?.let { facts = facts.copy(trash = it.count, trashBytes = it.bytes) }
            runCatching { Engine.decode<HeldAnswer>(answers[answers.size - 1]) }.getOrNull()?.let { facts = facts.copy(versions = it.count, versionsBytes = it.bytes) }
            Blogs.update { it.copy(facts = facts) }
        } finally {
            counting = false
        }
    }

    /** An engine too old to answer one of the two has no cards, not an error. */
    private fun glance(queue: ByteArray, drafts: ByteArray): Glance? {
        val waiting = runCatching { Engine.decode<QueueAnswer>(queue) }.getOrNull() ?: return null
        val unpublished = runCatching { Engine.decode<ListAnswer>(drafts) }.getOrNull() ?: return null
        return Glance(waiting.queue, unpublished.posts.count { !it.scheduled })
    }
}

/**
 * A link that opened the app -- a pairing code read elsewhere, by a
 * camera app or out of a note -- kept until its screen has taken it.
 */
object Incoming {
    var code by mutableStateOf<String?>(null)
}

/**
 * The first screen, at one glance: which blog and which engine, what
 * waits -- the next post in the queue, the drafts in progress -- and the
 * six entries of the wizard's menu as tiles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: HomeState) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var showingSettings by remember { mutableStateOf(false) }
    var showingBlogs by remember { mutableStateOf(false) }
    val links = LocalUriHandler.current
    val blog = Blogs.current
    val identity = state.identity
    val name = identity?.site?.name ?: blog?.label ?: ""
    val claim = identity?.site?.claim ?: blog?.claim ?: ""
    val url = identity?.site?.url ?: blog?.url ?: ""

    val layout = LocalLayout.current
    // Beside the menu a screen takes the place of the one that was open;
    // on a phone, and on a page, it is laid over the menu.
    fun show(tag: Any?, screen: @Composable () -> Unit) {
        if (layout == Layout.Columns) nav.open(tag, screen) else nav.push(tag, screen)
    }

    fun open(entry: MenuEntry, filter: StateFilter? = null, searching: Boolean = false) {
        show(entry) {
            // Read where the screen is drawn, not where it was asked for: the blog may say
            // its languages and its address a moment after the tile was tapped.
            val languages = state.otherLanguages
            val base = state.identity?.site?.url ?: ""
            when (entry) {
                MenuEntry.Add -> ComposeScreen()
                MenuEntry.Post -> PostPickerScreen(languages)
                MenuEntry.Queue -> QueueScreen(languages)
                MenuEntry.Browse -> ArchiveScreen(languages, base, filter, searching)
                MenuEntry.Restore -> TrashScreen()
                MenuEntry.Rebuild -> SiteScreen()
            }
        }
    }

    // What was begun on this device for the open blog and not finished: a
    // look into the device's own keeping, taken again when the desk says
    // that changed -- and only while this screen is the one that is seen.
    val begun = if (LocalShown.current) {
        remember(Blogs.currentId, Desk.changes) { Blogs.currentId?.let { Begun.all(it, BlogShelf.notes) } ?: emptyList() }
    } else {
        emptyList()
    }

    // Straight to where it waits: the new post's form, or the editor of
    // the post -- or of its language -- that was being changed.
    fun resume(one: Begun) {
        when (val what = one.what) {
            Begun.What.New -> open(MenuEntry.Add)
            is Begun.What.Text -> show(null) { TextEditScreen(what.slug) }
            is Begun.What.Language -> show(null) { TranslateScreen(what.slug, what.lang) }
        }
    }

    // Another blog: its own name and colour are there before its server answers.
    LaunchedEffect(Blogs.currentId) {
        state.forget()
        state.load()
    }
    // What changed on the way back is on the first screen again.
    var seen by remember { mutableStateOf(false) }
    OnShown {
        if (seen) state.loadGlance()
        seen = true
    }
    // Beside an open screen the menu is never covered, so never comes
    // back to the front: there it is the screen beside it closing that
    // says something may have changed.
    val depth = nav.depth
    var deepest by remember { mutableStateOf(0) }
    LaunchedEffect(depth) {
        if (layout == Layout.Columns && depth < deepest) state.loadGlance()
        deepest = depth
    }

    PaperScaffold(
        actions = { BarKey(Symbols.gearshape, stringResource(R.string.settings)) { showingSettings = true } },
        // Beside an open screen the menu can step out of its way.
        hideMenu = if (layout == Layout.Columns && nav.depth > 0) ({ nav.menuHidden = true }) else null,
    ) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = {
                scope.launch {
                    state.refreshing = true
                    state.load()
                    state.refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            // A wide screen held upright is one large page: the menu on it
            // is two thirds of its width, in its middle, and everything on
            // it larger by the same measure.
            val roomy = layout == Layout.Page
            val density = LocalDensity.current
            val measure = if (roomy) Density(density.density * 1.35f, density.fontScale) else density
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            CompositionLocalProvider(LocalDensity provides measure) {
            Column(
                if (roomy) Modifier.fillMaxWidth(2f / 3f).padding(top = 30.dp, bottom = 24.dp)
                else Modifier.fillMaxWidth().padding(horizontal = Theme.gutter).padding(top = 4.dp, bottom = 24.dp)
            ) {
                // The blog's own mark beside its name, as /write/ wears it: the name
                // and the claim share one left edge, the mark stands before both.
                // The whole of it is a key: the other blogs are behind it.
                val blogs = stringResource(R.string.blogs)
                Pressable({ showingBlogs = true }, modifier = Modifier.semantics { contentDescription = "$name, $blogs" }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiteMark(url)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(
                                (name.ifEmpty { "blog.sh" }).lowercase(Locale.getDefault()), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = display(34f).copy(color = Theme.ink),
                                autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 34.sp, stepSize = 1.sp),
                                modifier = Modifier.semantics { heading() },
                            )
                            if (claim.isNotEmpty()) ClaimText(claim)
                        }
                        Mark(Symbols.chevronUpChevronDown, 18.dp)
                    }
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp, 1.dp).background(Theme.accent))
                    EngineLabel("./blog.sh ${identity?.engine ?: ""}")
                }

                if (identity == null) {
                    val problem = state.problem
                    if (problem != null) Text(problem, color = Theme.muted, style = ui(14f), modifier = Modifier.padding(top = 14.dp))
                    else Busy(modifier = Modifier.padding(top = 14.dp))
                }

                // What waits, in the order it wants a hand: the drafts on
                // the blog, then the writing kept on this device from the
                // last time -- said here, or nobody knows of it before
                // opening the form it waits in -- and last the queue, which
                // goes out by itself.
                val glance = state.glance
                if (glance != null || begun.isNotEmpty()) {
                    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (glance != null) Pressable({ open(MenuEntry.Browse, StateFilter.Draft) }) { DraftsCard(glance) }
                        for (one in begun) Pressable({ resume(one) }) { BegunCard(one) }
                        if (glance != null) Pressable({ open(MenuEntry.Queue) }) { QueueCard(glance) }
                    }
                }

                Column(Modifier.padding(top = if (glance == null && begun.isEmpty()) 22.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in MenuEntry.entries.chunked(3)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (entry in row) {
                                val label = stringResource(entry.nameId)
                                Pressable({ open(entry) }, modifier = Modifier.weight(1f).semantics { contentDescription = label }) {
                                    // Beside its screen, the entry that is open says so.
                                    Tile(entry, highlighted = layout == Layout.Columns && nav.root == entry)
                                }
                            }
                        }
                    }
                }

                Pressable({ open(MenuEntry.Browse, searching = true) }, modifier = Modifier.padding(top = 14.dp)) {
                    Card(capsule = true) {
                        // A glass, not the terminal's own key for it: a slash says
                        // "search" only to somebody who knows the terminal.
                        Mark(Symbols.magnifyingglass, 17.dp)
                        EngineLabel(stringResource(R.string.search_the_archive), color = wordUnderPointer(Theme.muted))
                    }
                }

                // The blog itself, as a reader has it.
                val site = remember(url) {
                    runCatching { URI(url) }.getOrNull()?.takeIf { it.scheme?.lowercase() in listOf("http", "https") && !it.host.isNullOrEmpty() }
                }
                if (site != null) {
                    Pressable({ runCatching { links.openUri(url) } }, modifier = Modifier.padding(top = 8.dp)) {
                        Card(capsule = true) {
                            Mark(Symbols.safari, 17.dp)
                            EngineLabel(stringResource(R.string.open_the_blog_in_the_browser), color = wordUnderPointer(Theme.muted))
                        }
                    }
                }

                blog?.facts?.let { facts ->
                    Box(Modifier.fillMaxWidth().padding(top = 26.dp), contentAlignment = Alignment.Center) {
                        FactLines(facts) { open(MenuEntry.Restore) }
                    }
                }
            }
            }
            }
        }
    }

    // A code read elsewhere opens the app with it: the way in for a blog,
    // with the code already there, over whatever was open.
    val incoming = Incoming.code
    LaunchedEffect(incoming) {
        if (incoming != null) {
            showingSettings = false
            showingBlogs = false
        }
    }
    if (incoming != null) {
        key(incoming) {
            AddBlogSheet(
                initialCode = incoming,
                onBack = { Incoming.code = null },
                onDone = {
                    Incoming.code = null
                    scope.launch { state.load() }
                },
            )
        }
    }

    if (showingSettings) SettingsSheet(onDismiss = { showingSettings = false })
    if (showingBlogs) {
        // A blog's settings are behind its row there: what was changed in
        // them is asked of the server again when the list closes.
        BlogsSheet(onDismiss = {
            showingBlogs = false
            scope.launch { state.load() }
        })
    }
}

/** The next post to go out, and how many wait in all. */
@Composable
private fun QueueCard(glance: Glance) {
    Card {
        Mark(Symbols.clock, 21.dp)
        val next = glance.queue.firstOrNull()
        if (next != null) {
            val whenText = engineInstant(next.date)?.let { RowDate.soon(it) } ?: ""
            Text(
                "${next.title.ifEmpty { next.slug }} · $whenText", color = Theme.ink, style = ui(15f, FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            CountBadge(glance.queue.size)
        } else {
            Text(stringResource(R.string.nothing_scheduled), color = Theme.muted, style = ui(15f))
        }
    }
}

@Composable
private fun DraftsCard(glance: Glance) {
    Card {
        Mark(Symbols.pencil, 21.dp)
        if (glance.drafts > 0) {
            Text(stringResource(R.string.drafts_in_progress), color = wordUnderPointer(Theme.ink), style = ui(15f, FontWeight.Medium), modifier = Modifier.weight(1f))
            CountBadge(glance.drafts)
        } else {
            Text(stringResource(R.string.no_drafts_in_progress), color = Theme.muted, style = ui(15f))
        }
    }
}

/**
 * One thing begun: what it is and when it was last written in, and
 * under that what it is called.
 */
@Composable
private fun BegunCard(one: Begun) {
    val kind = when (val what = one.what) {
        Begun.What.New -> stringResource(R.string.unsent_new_post)
        is Begun.What.Text -> stringResource(R.string.unsaved_changes)
        is Begun.What.Language ->
            stringResource(R.string.unsaved_translation, Locale.forLanguageTag(what.lang).getDisplayLanguage(Locale.getDefault()).ifEmpty { what.lang })
    }
    Card(warning = true) {
        Mark(Symbols.squareAndPencil, 21.dp, Theme.danger)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "$kind · ${RowDate.spoken(Instant.ofEpochMilli(one.at))}", color = Theme.danger, style = ui(12f, FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (one.title.isNotEmpty()) {
                Text(one.title, color = Theme.ink, style = ui(15f, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Mark(Symbols.chevronRight, 14.dp, Theme.muted)
    }
}

/** One entry of the menu: its mark and its word. */
@Composable
private fun Tile(entry: MenuEntry, highlighted: Boolean = false) {
    val shape = RoundedCornerShape(Theme.corner)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (highlighted) Theme.accent.copy(alpha = 0.12f) else Theme.card)
            .border(1.dp, if (highlighted) Theme.accent else Theme.keyLine, shape)
            .padding(top = 16.dp, bottom = 12.dp)
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A tile is an action: its mark is in the accent, its word in
        // ink -- as the cards over the tiles have it.
        Mark(entry.symbol, 26.dp)
        Text(stringResource(entry.shortId), color = wordUnderPointer(Theme.ink), style = ui(13f, FontWeight.SemiBold), maxLines = 1)
    }
}

/**
 * The blog in numbers, in the engine's voice: what the archive holds,
 * and what waits in the trash and among the versions -- those two are
 * keys, to the screen that empties them.
 */
@Composable
private fun FactLines(facts: Facts, toTrash: () -> Unit) {
    val context = LocalContext.current
    fun number(n: Int) = NumberFormat.getIntegerInstance().format(n)
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    val hours = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
        .format(Measure(facts.readingHours.roundToLong(), MeasureUnit.HOUR))
    class Line(val label: String, val value: String, val detail: String?, val action: (() -> Unit)? = null)
    // A line whose number is nought is not said at all, as on the blog's
    // own pages: there is nothing in it to read, and behind the last two
    // nothing to empty.
    val all = listOf(
        facts.posts to Line(stringResource(R.string.facts_posts), number(facts.posts), if (facts.since.isEmpty()) null else stringResource(R.string.facts_since, facts.since)),
        facts.words to Line(stringResource(R.string.facts_words), number(facts.words), if (facts.readingHours >= 1) stringResource(R.string.facts_reading, hours) else null),
        facts.tags to Line(stringResource(R.string.facts_tags), number(facts.tags), null),
        facts.media to Line(stringResource(R.string.facts_media), number(facts.media), size(facts.mediaBytes)),
        facts.trash to Line(stringResource(R.string.facts_trash), number(facts.trash), size(facts.trashBytes), toTrash),
        facts.versions to Line(stringResource(R.string.facts_versions), number(facts.versions), size(facts.versionsBytes), toTrash),
    )
    val lines = all.filter { it.first > 0 }.map { it.second }
    if (lines.isEmpty()) return
    // The block stands in the middle as one: its widest line centred,
    // the others keeping their places under it.
    // A line is as tall as its type; where large type leaves it too short
    // for the number and what is said to it, the second stands under the first.
    val crowded = Theme.crowded
    val tall = 18.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    fun height(line: Line) = if (crowded && line.detail != null) tall * 2 else tall
    Row(Modifier.width(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            for (line in lines) Box(Modifier.height(height(line)), contentAlignment = Alignment.TopStart) { EngineLabel(line.label, maxLines = 1) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            for (line in lines) {
                val words: @Composable () -> Unit = {
                    if (crowded) {
                        Column(Modifier.height(height(line))) {
                            Text(line.value, color = if (line.action != null) Theme.accent else Theme.ink, style = mono(12f), maxLines = 1)
                            if (line.detail != null) Text(line.detail, color = Theme.muted, style = mono(12f, bold = false), maxLines = 1)
                        }
                    } else {
                        Row(Modifier.height(tall), verticalAlignment = Alignment.Top) {
                            Text(line.value, color = if (line.action != null) Theme.accent else Theme.ink, style = mono(12f), maxLines = 1)
                            if (line.detail != null) Text(" · " + line.detail, color = Theme.muted, style = mono(12f, bold = false), maxLines = 1)
                        }
                    }
                }
                if (line.action != null) Pressable(line.action) { words() } else words()
            }
        }
    }
}

/**
 * The claim under the blog's name, in the largest size that lets every
 * line of it stand whole: a short claim speaks up, a long one lowers its
 * voice. A claim the blog broke into lines keeps them; one too long even
 * for the smallest size wraps at it.
 */
@Composable
fun ClaimText(claim: String) {
    val lines = claim.split("\n").filter { it.isNotEmpty() }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val room = with(LocalDensity.current) { maxWidth.toPx() }
        val fits = (20 downTo 13).firstOrNull { size ->
            lines.all { measurer.measure(it, ui(size.toFloat()), maxLines = 1).size.width <= room }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            for (line in lines) {
                Text(line, color = Theme.muted, style = ui((fits ?: 13).toFloat()), maxLines = if (fits == null) Int.MAX_VALUE else 1)
            }
        }
    }
}

/**
 * The blog's favicon, the one the build puts at /assets/images/favicon.png
 * and /write/ shows in its header. Shown at once from the copy kept on the
 * device, then fetched again; a blog without one has no mark, and the name
 * stands at the edge by itself.
 */
@Composable
fun SiteMark(url: String) {
    var mark by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        mark = SiteIcon.kept(url)
        SiteIcon.fetch(url)?.let { mark = it }
    }
    mark?.let {
        Image(
            it, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
        )
    }
}

object SiteIcon {
    fun address(site: String): URL? {
        if (site.isEmpty()) return null
        val base = runCatching { URL(site) }.getOrNull() ?: return null
        if (base.protocol != "https" && base.protocol != "http") return null
        return runCatching { URL(site.trimEnd('/') + "/assets/images/favicon.png") }.getOrNull()
    }

    /** One file per blog, by its host, among what the system may clear. */
    private fun file(site: String): File? {
        val host = runCatching { URL(site).host }.getOrNull()
        if (host.isNullOrEmpty()) return null
        return File(BlogshApp.context.cacheDir, "site-mark-" + host.replace(Regex("[^A-Za-z0-9.-]"), "_") + ".png")
    }

    suspend fun kept(site: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = file(site) ?: return@withContext null
        if (!file.exists()) return@withContext null
        runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
    }

    suspend fun fetch(site: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val address = address(site) ?: return@withContext null
        runCatching {
            val connection = address.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            try {
                if (connection.responseCode != 200) return@runCatching null
                val data = connection.inputStream.use { it.readBytes() }
                val image = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return@runCatching null
                file(site)?.writeBytes(data)
                image.asImageBitmap()
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
