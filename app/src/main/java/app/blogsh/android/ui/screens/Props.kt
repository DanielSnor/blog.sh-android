package app.blogsh.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.ActionAnswer
import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.PostAction
import app.blogsh.android.model.PostState
import app.blogsh.android.model.PropsAnswer
import app.blogsh.android.model.RebuildAnswer
import app.blogsh.android.model.Spoken
import app.blogsh.android.model.engineInstant
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.plain
import app.blogsh.android.model.said
import app.blogsh.android.ui.Asks
import app.blogsh.android.ui.AsksFor
import app.blogsh.android.ui.Busy
import app.blogsh.android.ui.Choice
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.CommandRow
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.PaperScaffold
import app.blogsh.android.ui.Partial
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.PostSlug
import app.blogsh.android.ui.ShareKey
import app.blogsh.android.ui.gap
import app.blogsh.android.ui.humanDate
import app.blogsh.android.model.Doing
import app.blogsh.android.model.Herald
import app.blogsh.android.model.PostLink
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.RowDate
import app.blogsh.android.ui.Said
import app.blogsh.android.ui.Says
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One post's properties screen, as `props <slug> --json` hands it out:
 * the rows the terminal's frame shows, under the labels it uses, and
 * below them the keys the screen offers this post -- as buttons, in the
 * order of the keys row. Each asks what the screen asks, then calls the
 * command the key calls, then reads the screen again.
 *
 * `gone` is said to the screen this one was opened from, once the post is
 * deleted: that screen is about the same post, and leaves with it.
 * `renamed` tells it what the post is now, when it was renamed here: the
 * screen under this one was opened with the name it had before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropsScreen(slug: String, gone: (() -> Unit)? = null, renamed: ((PropsAnswer) -> Unit)? = null) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val told by rememberUpdatedState(gone)
    val toldSlug by rememberUpdatedState(renamed)
    val state = remember {
        // Away from a post that is no longer there -- this screen, and the
        // one before it when that is the post's too: it is told, and leaves
        // by itself once it is in front again.
        PropsState(slug, scope, renamed = { toldSlug?.invoke(it) }) {
            told?.invoke()
            nav.pop()
        }
    }
    var refreshing by remember { mutableStateOf(false) }
    val props = state.props
    val problem = state.problem

    LaunchedEffect(Unit) { state.load() }

    // A build holds the lock most of these keys need: while one runs, they wait.
    val still = state.busy || Herald.shared.isBuilding
    PaperScaffold(
        onBack = { nav.pop() }, title = props?.title ?: state.slug, doing = state.doing,
        actions = { props?.let { PostLink.of(it) }?.let { ShareKey(it) } },
    ) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    try {
                        state.load()
                    } finally {
                        refreshing = false
                    }
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Theme.gutter).padding(top = 2.dp, bottom = 28.dp)
            ) {
                if (problem != null) ProblemLine(problem)
                if (props != null) {
                    // Where the post is, under its title in the bar: its address, or that it has none yet.
                    PostSlug(
                        if (props.state == PostState.Draft) stringResource(R.string.draft_not_on_the_site_preview_only)
                        else props.address.removePrefix("/")
                    )
                    Rows(props, Modifier.gap(12))
                    if (props.url.isNotEmpty()) {
                        val label = stringResource(if (props.state == PostState.Draft) R.string.show_the_preview_on_the_web else R.string.show_on_the_web)
                        Plate(Modifier.gap(10)) {
                            row {
                                // Everything on the screen waits while the engine is asked; so does the way out to the web.
                                Pressable({ runCatching { uri.openUri(props.url) } }, enabled = !still) {
                                    CommandRow(label, Symbols.safari, enabled = !still)
                                }
                            }
                        }
                    }
                    SectionLabel(stringResource(R.string.actions))
                    Plate {
                        for (action in keys(props)) {
                            if (action == PostAction.Delete) continue
                            row {
                                Command(actionLabel(action, props), actionSymbol(action), leads = action in opensAScreen, enabled = !still) {
                                    state.tapped(action)
                                }
                            }
                        }
                    }
                    // What cannot be taken back sits apart, and last -- as [x] is the
                    // last key of the row.
                    if (PostAction.Delete in props.actions) {
                        Plate(Modifier.gap(10)) {
                            row {
                                Command(actionLabel(PostAction.Delete, props), actionSymbol(PostAction.Delete), danger = true, enabled = !still) {
                                    state.tapped(PostAction.Delete)
                                }
                            }
                        }
                    }
                }
            }
            if (props == null && problem == null) Busy(modifier = Modifier.align(Alignment.Center))
        }
    }

    // What a key asks before it acts. On iOS the question rises over the key
    // itself; here it is asked over the screen, one question at a time.
    state.confirming?.let { action ->
        Asks(
            confirmTitle(action, state.slug), props?.let { confirmMessage(action, it) },
            choices = listOf(
                Choice(confirmButton(action), danger = action == PostAction.Delete || action == PostAction.Unpublish) {
                    scope.launch { state.perform(action) }
                }
            ),
            onDismiss = { state.confirming = null },
        )
    }
    if (state.scheduling) {
        ScheduleSheet(
            slug = state.slug, offered = props?.slot, current = if (props?.scheduled == true) props.date else null,
            scheduled = props?.scheduled == true, onDismiss = { state.scheduling = false },
        ) { state.scheduled() }
    }
    val savedWords = stringResource(R.string.saved)
    val restoredWords = stringResource(R.string.restored, state.slug)
    if (state.editingProperties && props != null) {
        PropertiesForm(props, onDismiss = { state.editingProperties = false }) { state.afterWrite(saying = savedWords) }
    }
    if (state.showingAddresses && props != null) {
        AddressesSheet(props, onDismiss = { state.showingAddresses = false }) { state.afterWrite() }
    }
    if (state.showingVersions) {
        VersionsSheet(state.slug, onDismiss = { state.showingVersions = false }) { state.afterWrite(saying = restoredWords) }
    }
    if (state.renaming) {
        AsksFor(
            title = stringResource(R.string.rename_slug),
            value = state.newSlug, onValue = { state.newSlug = it },
            prompt = stringResource(R.string.new_slug),
            confirm = stringResource(R.string.rename),
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            message = stringResource(
                if (props?.state == PostState.Draft) R.string.a_draft_has_no_public_address_yet
                else R.string.the_old_address_keeps_answering_it_redirects
            ),
            onConfirm = { scope.launch { state.rename() } },
            onDismiss = { state.renaming = false },
        )
    }
    Says(state.said) { state.said = null }
}

// ---- The screen

/** The keys that open a screen of their own rather than ask and act. */
private val opensAScreen = setOf(PostAction.Schedule, PostAction.Properties, PostAction.Addresses, PostAction.Versions)

/**
 * The keys the screen offers this post, in the order the terminal's keys
 * row has them: [p] [s] [n] [u] [t] [c] [e] [r] [a] [v] [x].
 */
private val keyOrder = listOf(
    PostAction.Publish, PostAction.Schedule, PostAction.Unschedule, PostAction.Unpublish, PostAction.Announce, PostAction.Pin,
    PostAction.Properties, PostAction.Rename, PostAction.Addresses, PostAction.Versions, PostAction.Delete,
)

private fun keys(props: PropsAnswer): List<PostAction> = keyOrder.filter { it in props.actions }

/** The rows of the terminal's frame, under the labels it uses. */
@Composable
private fun Rows(props: PropsAnswer, modifier: Modifier = Modifier) {
    // A plate's rows are named before it is built: the words are read here.
    val titleLabel = stringResource(R.string.title)
    val preview = stringResource(R.string.preview_2)
    val scheduled = stringResource(R.string.scheduled_2)
    val state = stringResource(R.string.state)
    val published = props.date?.let { stringResource(R.string.published_2, humanDate(it)) }
    val type = stringResource(R.string.type)
    val tags = stringResource(R.string.tags)
    val series = stringResource(R.string.series)
    val pinned = stringResource(R.string.pinned_2)
    val pinnedWords = if (props.pinned) stringResource(R.string.yes_held_at_the_top_of_the) else null
    val unlisted = stringResource(R.string.unlisted)
    val unlistedWords = if (props.unlisted) stringResource(R.string.yes_reachable_by_its_address_in_no) else null
    val languages = stringResource(R.string.languages)
    val announced = stringResource(R.string.announced_2)
    val oldLinks = stringResource(R.string.old_links_2)
    val oldLinksWords = if (props.addresses.isEmpty()) null else stringResource(R.string.address_es_still_redirect_here, props.addresses.size)
    val seriesWords = seriesLabel(props)
    val announcedWords = announcedLabel(props)
    Plate(modifier) {
        // The whole title: the bar has one line for it.
        info(titleLabel, props.title)
        if (props.state == PostState.Draft) {
            info(preview, props.url, mono = true)
            if (props.scheduled && props.date != null) info(scheduled, humanDate(props.date))
        } else if (props.date != null) {
            info(state, published)
        }
        info(type, props.type)
        info(tags, props.tags.joinToString(", "))
        info(series, seriesWords)
        info(pinned, pinnedWords)
        info(unlisted, unlistedWords)
        info(languages, languagesLabel(props))
        info(announced, announcedWords)
        info(oldLinks, oldLinksWords)
    }
}

// ---- The keys

/**
 * What the screen holds and what its keys do. The words it says after an
 * action are made where no screen is at hand -- in the middle of a call --
 * so they come through `Spoken`, as the engine's own errors do.
 */
private class PropsState(
    slug: String, private val scope: CoroutineScope, private val renamed: (PropsAnswer) -> Unit = {}, private val leave: () -> Unit,
) {
    /** A rename changes it, and every key after that speaks of the new one. */
    var slug by mutableStateOf(slug)
    var props by mutableStateOf<PropsAnswer?>(null)
    var problem by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)

    /** What the screen is doing while it cannot be touched. */
    var doing by mutableStateOf<String?>(null)

    // What is being asked, one at a time, the way one keypress asks.
    var confirming by mutableStateOf<PostAction?>(null)
    var scheduling by mutableStateOf(false)
    var editingProperties by mutableStateOf(false)
    var renaming by mutableStateOf(false)
    var newSlug by mutableStateOf("")
    var showingAddresses by mutableStateOf(false)
    var showingVersions by mutableStateOf(false)

    /** What the screen says after an action -- one thing, its question in it. */
    var said by mutableStateOf<Said?>(null)

    private fun say(id: Int, vararg args: Any): String = Spoken.say(id, *args)

    /**
     * A question in what the screen says. Its answer is carried out by
     * the screen, not by the dialog it was asked in: that one is closed
     * before the answer runs, and whatever it had started would end with it.
     */
    private fun ask(button: String, cancel: String? = null, run: suspend () -> Unit): Said.Ask =
        Said.Ask(button, cancel) { scope.launch { run() } }

    fun tapped(action: PostAction) {
        when (action) {
            PostAction.Publish, PostAction.Unschedule, PostAction.Unpublish, PostAction.Announce, PostAction.Delete -> confirming = action
            PostAction.Schedule -> scheduling = true
            PostAction.Pin -> scope.launch { pin() }
            PostAction.Properties -> editingProperties = true
            PostAction.Rename -> {
                newSlug = slug
                renaming = true
            }
            PostAction.Addresses -> showingAddresses = true
            PostAction.Versions -> showingVersions = true
        }
    }

    suspend fun perform(action: PostAction) {
        when (action) {
            PostAction.Publish -> publish(anyway = false)
            PostAction.Unschedule -> run(listOf("schedule", slug, "--cancel"))?.let { answer ->
                load()
                tell(answer.warnings, or = say(R.string.the_schedule_is_cancelled_the_post_is))
            }
            PostAction.Unpublish -> run(listOf("unpublish", slug, "--yes"))?.let { answer ->
                // Taking a post off the site builds the site.
                Herald.shared.settled()
                load()
                tell(answer.warnings, or = say(R.string.unpublished_the_post_is_a_draft_again))
            }
            PostAction.Announce -> announce(force = false)
            PostAction.Delete -> if (run(listOf("delete", slug, "--yes")) != null) {
                // The post is out of this screen's reach now. Said once, and
                // the screen is left: its keys would act on a post that is
                // not there. The site is brought up to date by itself.
                Herald.shared.owe()
                said = Said(title = say(R.string.deleted), text = say(R.string.the_post_is_in_the_trash_and), after = { leave() })
            }
            else -> {}
        }
    }

    /**
     * [p]. On a site of more than one language the engine refuses a post
     * without words in one of them; the refusal is asked as a question,
     * and the answer is the flag the terminal would have been given.
     */
    private suspend fun publish(anyway: Boolean) {
        val args = mutableListOf("publish", slug, "--yes")
        if (anyway) args.add("--allow-partial")
        val ask = ask(say(R.string.publish_anyway)) { publish(anyway = true) }
        run(args, anyway = if (anyway) null else ask)?.let { answer ->
            // Publishing builds the whole site: nothing is owed after it.
            Herald.shared.settled()
            load()
            tell(answer.warnings, or = say(R.string.published, answer.url ?: slug))
        }
    }

    /**
     * The engine's own lines about what it did, when it had any -- they
     * are worth an answer. Otherwise the plain fact that it was done,
     * said in passing.
     */
    private fun tell(lines: List<String>?, or: String? = null) {
        val plain = lines?.plain
        if (!plain.isNullOrEmpty()) said = Said(text = plain.joinToString("\n")) else if (or != null) Herald.shared.say(or)
    }

    /** The schedule dialog closed on a plan: when the post goes out now. */
    suspend fun scheduled() {
        load()
        val now = props ?: return
        val date = engineInstant(now.date) ?: return
        if (now.scheduled) Herald.shared.say(say(R.string.scheduled_goes_out, RowDate.spoken(date)))
    }

    private suspend fun announce(force: Boolean) {
        val args = mutableListOf(if (props?.network == "bluesky") "bluesky" else "toot", slug)
        if (force) args.add("--force")
        busy = true
        doing = say(Doing.word(args))
        try {
            val answer = Engine.call<ActionAnswer>(args)
            load()
            said = Said(text = say(R.string.announced, answer.url ?: ""))
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            val refusal = (e as? EngineError.Refused)?.refusal
            said = if (refusal?.error == "outside_window") {
                Said(
                    title = say(R.string.announce), text = refusal.message,
                    ask = ask(say(R.string.announce_anyway)) { announce(force = true) },
                )
            } else {
                Said(text = e.said)
            }
        } finally {
            busy = false
            doing = null
        }
    }

    private suspend fun pin() {
        val props = props ?: return
        write(listOf("props", slug, "--set", "pinned=${if (props.pinned) "no" else "yes"}"), owed = true)
    }

    suspend fun rename() {
        val wanted = newSlug.trim(' ', '\t')
        if (wanted.isEmpty() || wanted == slug) return
        val answer = writeProps(listOf("props", slug, "--rename", wanted, "--yes")) ?: return
        val wasDraft = props?.state == PostState.Draft
        slug = answer.slug
        props = answer
        renamed(answer)
        // A draft's own preview follows it by itself; a published post's pages are owed a build.
        if (!wasDraft) Herald.shared.owe()
        tell(answer.warnings)
    }

    /**
     * Runs an action and hands back its answer, or says why it was
     * refused. `anyway`: what to offer when the refusal is the one a
     * second word overrides -- a post not written in every language.
     */
    private suspend fun run(args: List<String>, anyway: Said.Ask? = null): ActionAnswer? {
        busy = true
        doing = say(Doing.word(args))
        try {
            return Engine.call<ActionAnswer>(args)
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            val refused = (e as? EngineError.Refused)?.refusal?.error
            said = if (refused == Partial.CODE && anyway != null) {
                // Partial's own words, said where no screen is at hand to read them from.
                Said(text = say(R.string.this_site_publishes_in_more_than_one), ask = anyway)
            } else {
                Said(text = e.said)
            }
            return null
        } finally {
            busy = false
            doing = null
        }
    }

    /** A write that answers with the screen itself. */
    private suspend fun writeProps(args: List<String>): PropsAnswer? {
        busy = true
        doing = say(Doing.word(args))
        try {
            return Engine.call<PropsAnswer>(args)
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            said = Said(text = e.said)
            return null
        } finally {
            busy = false
            doing = null
        }
    }

    private suspend fun write(args: List<String>, owed: Boolean) {
        val answer = writeProps(args) ?: return
        props = answer
        if (owed) Herald.shared.owe()
        tell(answer.warnings)
    }

    /** A sheet wrote something the site does not show yet. */
    suspend fun afterWrite(saying: String? = null) {
        load()
        Herald.shared.owe()
        if (saying != null) Herald.shared.say(saying)
    }

    suspend fun load() {
        try {
            props = Engine.call<PropsAnswer>("props", slug)
            problem = null
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        }
    }
}

@Composable
private fun confirmTitle(action: PostAction, slug: String): String = when (action) {
    PostAction.Publish -> stringResource(R.string.publish_2, slug)
    PostAction.Unschedule -> stringResource(R.string.cancel_the_schedule_of, slug)
    PostAction.Unpublish -> stringResource(R.string.really_move_back_to_draft, slug)
    PostAction.Announce -> stringResource(R.string.announce_2, slug)
    PostAction.Delete -> stringResource(R.string.really_delete, slug)
    else -> ""
}

@Composable
private fun confirmMessage(action: PostAction, props: PropsAnswer): String? = when (action) {
    PostAction.Publish ->
        if (props.network != null && !props.unlisted) stringResource(R.string.with_a_network_configured_it_announces_too) else null
    PostAction.Unpublish -> stringResource(R.string.its_announcement_is_deleted_too_the_post)
    PostAction.Delete ->
        if (props.announced != null) stringResource(R.string.the_post_goes_to_the_trash_and_2)
        else stringResource(R.string.the_post_goes_to_the_trash_and)
    PostAction.Unschedule -> stringResource(R.string.the_post_stays_a_draft_with_the)
    else -> null
}

@Composable
private fun confirmButton(action: PostAction): String = when (action) {
    PostAction.Publish -> stringResource(R.string.publish)
    PostAction.Unschedule -> stringResource(R.string.cancel_the_schedule)
    PostAction.Unpublish -> stringResource(R.string.unpublish)
    PostAction.Announce -> stringResource(R.string.announce)
    PostAction.Delete -> stringResource(R.string.delete)
    else -> stringResource(R.string.ok)
}

// ---- Words

@Composable
private fun seriesLabel(props: PropsAnswer): String? {
    val series = props.series ?: return null
    val part = props.seriesPart ?: return series
    return stringResource(R.string.part, series, part)
}

private fun languagesLabel(props: PropsAnswer): String? {
    if (props.languages.others.isEmpty()) return null
    val marks = mapOf("written" to "✅", "started" to "◐", "none" to "·")
    val others = props.languages.others.entries.sortedBy { it.key }.map { "${marks[it.value] ?: "·"} ${it.key}" }
    return (listOf("✅ ${props.languages.own}") + others).joinToString("  ")
}

@Composable
private fun announcedLabel(props: PropsAnswer): String {
    props.announced?.let { return it }
    return when (props.announces) {
        PropsAnswer.Announces.Announced -> props.announced ?: ""
        PropsAnswer.Announces.OnPublish -> stringResource(R.string.not_yet_goes_out_when_the_post)
        PropsAnswer.Announces.Nowhere -> stringResource(R.string.never_this_site_announces_nowhere)
        PropsAnswer.Announces.NeverUnlisted -> stringResource(R.string.never_an_unlisted_post_is_not_announced)
        PropsAnswer.Announces.NoSecret -> stringResource(R.string.never_as_things_stand_the_network_s)
        PropsAnswer.Announces.NotAnnounced -> stringResource(R.string.not_announced)
    }
}

@Composable
private fun actionLabel(action: PostAction, props: PropsAnswer): String = when (action) {
    PostAction.Publish -> stringResource(if (props.scheduled) R.string.publish_now_3 else R.string.publish_3)
    PostAction.Schedule -> stringResource(if (props.scheduled) R.string.reschedule_2 else R.string.schedule_2)
    PostAction.Unschedule -> stringResource(R.string.cancel_the_schedule_2)
    PostAction.Unpublish -> stringResource(R.string.unpublish_2)
    PostAction.Announce -> stringResource(R.string.announce_3, if (props.network == "bluesky") "Bluesky" else "Mastodon")
    PostAction.Pin -> stringResource(if (props.pinned) R.string.unpin else R.string.pin)
    PostAction.Properties -> stringResource(R.string.properties_2)
    PostAction.Rename -> stringResource(R.string.rename_slug_2)
    PostAction.Addresses -> stringResource(R.string.old_links_2)
    PostAction.Versions -> stringResource(R.string.earlier_versions_2)
    PostAction.Delete -> stringResource(R.string.delete_2)
}

/**
 * megaphone: the one mark of this screen that Symbols does not hold yet -- the Material Symbol `campaign`,
 * kept under the name the import of the marks would give it.
 */
private val megaphone = R.drawable.ic_campaign

private fun actionSymbol(action: PostAction): Int = when (action) {
    PostAction.Publish -> Symbols.paperplane
    PostAction.Schedule -> Symbols.calendarBadgeClock
    PostAction.Unschedule -> Symbols.calendarBadgeMinus
    PostAction.Unpublish -> Symbols.arrowUturnBackward
    PostAction.Announce -> megaphone
    PostAction.Pin -> Symbols.pin
    PostAction.Properties -> Symbols.sliderHorizontal3
    PostAction.Rename -> Symbols.pencilLine
    PostAction.Addresses -> Symbols.link
    PostAction.Versions -> Symbols.clockArrowCirclepath
    PostAction.Delete -> Symbols.trash
}
