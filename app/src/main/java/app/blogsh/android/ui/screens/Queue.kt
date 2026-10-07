package app.blogsh.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.ActionAnswer
import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.QueueAnswer
import app.blogsh.android.model.QueueRow
import app.blogsh.android.model.Doing
import app.blogsh.android.model.Herald
import app.blogsh.android.model.PostRow
import app.blogsh.android.model.engineInstant
import androidx.compose.foundation.clickable
import app.blogsh.android.model.engineInstant
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.plain
import app.blogsh.android.model.said
import app.blogsh.android.ui.Asks
import app.blogsh.android.ui.AsksFor
import app.blogsh.android.ui.Busy
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.Card
import app.blogsh.android.ui.Choice
import app.blogsh.android.ui.EmptyNote
import app.blogsh.android.ui.KeyChip
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.Mark
import app.blogsh.android.ui.Menu
import app.blogsh.android.ui.MenuKey
import app.blogsh.android.ui.PaperRow
import app.blogsh.android.ui.PaperScaffold
import app.blogsh.android.ui.Partial
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.RowDate
import app.blogsh.android.ui.Said
import app.blogsh.android.ui.Says
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A post about to leave the queue, and which way. */
private class Leaving(val row: QueueRow, val publish: Boolean)

/** A line of a row's menu: said once, for the menu and for one who cannot see it. */
private class RowKey(val label: String, val symbol: Int, val danger: Boolean = false, val enabled: Boolean = true, val run: () -> Unit)

/**
 * The scheduled-post queue as `queue --json` hands it out, with the keys
 * of the terminal's screen on each row: [u] and [d] trade times with the
 * neighbour, [m] carries the post to a position, [p] publishes it now,
 * [s] asks for another time, [n] returns it to the drafts -- the last two
 * under the names the properties screen has for them, reschedule and
 * cancel the schedule: one thing, one name. A row itself opens its post,
 * as a row of the archive does. When a post
 * leaves the queue the screen asks whether the rest should step forward
 * into the gap; the app asks the same, before the call, because the
 * engine answers both in one. The preview is rebuilt once, when you are
 * done -- the screen does it on the way out; the app does it by itself
 * once the queue has been left alone a moment (`Herald`), and says what
 * each key did: when the post goes out now.
 *
 * The keys are behind a long press on the row. The same press is what
 * lifts the row: a finger that moves carries it, one that lets go where
 * it pressed gets the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(languages: List<String> = emptyList()) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var rows by remember { mutableStateOf(emptyList<QueueRow>()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    var leaving by remember { mutableStateOf<Leaving?>(null) }
    var rescheduling by remember { mutableStateOf<QueueRow?>(null) }
    var carrying by remember { mutableStateOf<QueueRow?>(null) }
    var carryTo by remember { mutableStateOf(1) }
    var carryText by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var said by remember { mutableStateOf<Said?>(null) }
    // What the screen is doing while it cannot be touched.
    var doing by remember { mutableStateOf<String?>(null) }

    /** The row whose menu is open. */
    var menuFor by remember { mutableStateOf<String?>(null) }
    /** Whether the row in the hand has changed places since it was lifted. */
    var moved by remember { mutableStateOf(false) }

    val partialWords = Partial.words

    suspend fun load() {
        loading = true
        try {
            val answer = Engine.call<QueueAnswer>("queue")
            rows = answer.queue
            problem = null
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        } finally {
            loading = false
        }
    }

    // ---- The keys

    // What the previews are behind in, and that it does not matter for
    // what goes out when: the herald says it while the site is brought up
    // to date.
    val previewsBehind = stringResource(R.string.draft_previews_still_show_the_old_times)
    val movedWord = stringResource(R.string.moved)
    val carriedWord = stringResource(R.string.carried)
    val rescheduledWord = stringResource(R.string.rescheduled)

    // A key moved a post: where it is now and when it goes out, read off
    // the queue the engine answered with -- and the previews are owed a build.
    fun changed(row: QueueRow, what: String) {
        Herald.shared.owe(previewsBehind)
        val now = rows.firstOrNull { it.id == row.id } ?: return
        val at = engineInstant(now.date)?.let { RowDate.spoken(it) } ?: now.date
        Herald.shared.say(context.getString(R.string.goes_out_of_in_the_queue, what, now.title.ifEmpty { now.slug }, at, now.position, rows.size))
    }

    suspend fun move(row: QueueRow, direction: String) {
        busy = true
        try {
            val answer = Engine.call<QueueAnswer>("queue", direction, "${row.year}/${row.slug}")
            rows = answer.queue
            changed(row, movedWord)
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            notice = e.said
        } finally {
            busy = false
        }
    }

    suspend fun carry(row: QueueRow, position: Int) {
        busy = true
        try {
            try {
                val answer = Engine.call<QueueAnswer>("queue", "--move", "${row.year}/${row.slug}", "--to", "$position")
                rows = answer.queue
                changed(row, carriedWord)
            } catch (e: Throwable) {
                if (e.isCalledOff) throw e
                notice = e.said
                // The row was shown where it was dropped; the queue is as the engine has it.
                load()
            }
        } finally {
            busy = false
        }
    }

    suspend fun leave(going: Leaving, compact: Boolean, anyway: Boolean = false) {
        busy = true
        doing = context.getString(Doing.word(listOf(if (going.publish) "publish" else "schedule")))
        try {
            val args = (if (going.publish) listOf("publish", going.row.slug, "--yes") else listOf("schedule", going.row.slug, "--cancel")).toMutableList()
            if (compact) args.add("--compact")
            if (anyway) args.add("--allow-partial")
            try {
                val answer = Engine.call<ActionAnswer>(args)
                // The engine says what it did in its own words (the warnings carry
                // the screen's lines); the app adds only the address a publish gave.
                val done = if (going.publish) context.getString(R.string.published, answer.url ?: going.row.slug)
                else context.getString(R.string.the_schedule_is_cancelled_the_post_is)
                val warnings = answer.warnings?.plain
                if (!warnings.isNullOrEmpty()) notice = (listOf(done) + warnings).joinToString("\n") else Herald.shared.say(done)
                // Publishing builds the whole site; a plan cancelled leaves the previews behind.
                if (going.publish) Herald.shared.settled() else Herald.shared.owe(previewsBehind)
                load()
            } catch (e: Throwable) {
                if (e.isCalledOff) throw e
                if ((e as? EngineError.Refused)?.refusal?.error == Partial.CODE && going.publish && !anyway) {
                    // Not written in every language the site publishes: asked, as the properties screen asks it.
                    // The answer is acted on by the screen itself, which is still here when the question is put away.
                    said = Said(text = partialWords, ask = Said.Ask(button = context.getString(R.string.publish_anyway)) {
                        scope.launch { leave(going, compact = compact, anyway = true) }.join()
                    })
                } else {
                    notice = e.said
                }
            }
        } finally {
            busy = false
            doing = null
        }
    }

    // The schedule dialog closed on another time for a post.
    suspend fun rescheduled(row: QueueRow) {
        // Read by the screen, not by the sheet that says so: the sheet may be gone before the answer.
        scope.launch {
            load()
            changed(row, rescheduledWord)
        }.join()
    }

    LaunchedEffect(Unit) { load() }

    // [m] with a finger: the row is carried to where it is dropped. It
    // is shown there at once; the engine's answer is what stays.
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val list = rows.toMutableList()
        val a = list.indexOfFirst { it.id == from.key }
        val b = list.indexOfFirst { it.id == to.key }
        if (a >= 0 && b >= 0) {
            list.add(b, list.removeAt(a))
            rows = list
            moved = true
        }
    }

    // A build holds the lock a change of the queue needs: while one runs, the rows wait.
    val still = busy || Herald.shared.isBuilding
    PaperScaffold(
        onBack = { nav.pop() }, name = stringResource(R.string.tile_queue),
        count = if (rows.isEmpty()) null else NumberFormat.getIntegerInstance().format(rows.size), doing = doing,
    ) {
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
            LazyColumn(Modifier.fillMaxSize().alpha(if (still) 0.5f else 1f), state = listState, userScrollEnabled = !still) {
                problem?.let { words ->
                    item(key = "problem") {
                        PaperRow { Text(words, color = Theme.muted, style = ui(14f), modifier = Modifier.padding(vertical = 12.dp)) }
                    }
                }
                items(rows, key = { it.id }) { row ->
                    // A post waiting for the cron has no slot to trade.
                    ReorderableItem(reorder, key = row.id, enabled = !row.overdue && !still) { dragging ->
                        val keys = listOf(
                            RowKey(stringResource(R.string.up_a_slot_earlier), Symbols.arrowUp, enabled = !(row.position == 1 || row.overdue)) {
                                scope.launch { move(row, "--up") }
                            },
                            RowKey(stringResource(R.string.down_a_slot_later), Symbols.arrowDown, enabled = !(row.position == rows.size || row.overdue)) {
                                scope.launch { move(row, "--down") }
                            },
                            RowKey(stringResource(R.string.carry_to_a_position), Symbols.arrowUpArrowDown, enabled = !(row.overdue || rows.size < 2)) {
                                carryTo = row.position
                                carryText = "${row.position}"
                                carrying = row
                            },
                            RowKey(stringResource(R.string.publish_now_2), Symbols.paperplane) { leaving = Leaving(row, publish = true) },
                            // Under the names the properties screen has for them: one thing, one name.
                            RowKey(stringResource(R.string.reschedule), Symbols.calendarBadgeClock) { rescheduling = row },
                            RowKey(stringResource(R.string.cancel_the_schedule), Symbols.calendarBadgeMinus, danger = true) { leaving = Leaving(row, publish = false) },
                        )
                        val lifted: (Offset) -> Unit = {
                            moved = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        // The row is looked up when it is dropped, by its name: the hand may
                        // have been given this before the queue was last read.
                        val id = row.id
                        val dropped: () -> Unit = {
                            val index = rows.indexOfFirst { it.id == id }
                            val held = rows.getOrNull(index)
                            if (held != null && index + 1 != held.position) {
                                scope.launch { carry(held, index + 1) }
                            } else if (held != null && !moved) {
                                // Let go where it was pressed: it was the menu that was asked for.
                                menuFor = id
                            }
                        }
                        val press = when {
                            still -> Modifier
                            // It cannot be lifted, and it still has its keys.
                            row.overdue -> Modifier.pointerInput(row.id) {
                                detectTapGestures(onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuFor = row.id
                                })
                            }
                            else -> Modifier.longPressDraggableHandle(onDragStarted = lifted, onDragStopped = dropped)
                        }
                        Box {
                            PaperRow(
                                // In the hand it is a sheet of its own over the others.
                                (if (dragging) Modifier.background(Theme.paper).background(Theme.accent.copy(alpha = 0.12f)) else Modifier)
                                    // A row opens its post, as in the archive: its text, its
                                    // properties, and so its slug -- which the queue has no key for.
                                    .clickable(enabled = !still) {
                                        val post = PostRow(row)
                                        nav.push { PostCrossroadsScreen(post, languages, gone = { scope.launch { load() } }) }
                                    }
                                    .then(press)
                                    .semantics {
                                        customActions = keys.filter { it.enabled }.map { key -> CustomAccessibilityAction(key.label) { key.run(); true } }
                                    }
                            ) { QueueRowView(row) }
                            Menu(menuFor == row.id, { menuFor = null }) {
                                keys.forEachIndexed { index, key ->
                                    if (index == 3) HorizontalDivider(color = Theme.line)
                                    MenuKey(key.label, key.symbol, danger = key.danger, enabled = key.enabled) {
                                        menuFor = null
                                        key.run()
                                    }
                                }
                            }
                        }
                    }
                }
                // Nothing on a row says it can be held: on iOS a row is also
                // swept aside for its keys, here the long press is the one way.
                if (rows.isNotEmpty()) {
                    item(key = "hold") {
                        PaperRow(rule = false) { Hint(stringResource(R.string.android_queue_hold), Modifier.padding(top = 6.dp, bottom = 24.dp)) }
                    }
                }
            }
            if (loading && rows.isEmpty() && !refreshing) {
                Busy(modifier = Modifier.align(Alignment.Center))
            } else if (!loading && rows.isEmpty() && problem == null) {
                EmptyNote(
                    Symbols.calendar, stringResource(R.string.the_queue_is_empty), stringResource(R.string.a_draft_is_scheduled_from_its_properties),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }

    // Asked about the row it names.
    leaving?.let { asked ->
        val behind = rows.size - asked.row.position
        val alone = Choice(stringResource(if (asked.publish) R.string.publish_now_2 else R.string.cancel_the_schedule), danger = !asked.publish) {
            scope.launch { leave(asked, compact = false) }
        }
        val shifting = if (behind > 0 && !asked.row.overdue) {
            Choice(stringResource(if (asked.publish) R.string.publish_now_and_shift_the_rest_a else R.string.cancel_the_schedule_and_shift_the_rest, behind)) {
                scope.launch { leave(asked, compact = true) }
            }
        } else null
        Asks(
            stringResource(if (asked.publish) R.string.publish_now else R.string.cancel_the_schedule_of, asked.row.slug),
            stringResource(if (asked.publish) R.string.the_same_as_publishing_a_draft_by else R.string.the_post_keeps_its_text_and_loses),
            listOfNotNull(alone, shifting),
            onDismiss = { leaving = null },
        )
    }
    rescheduling?.let { row ->
        ScheduleSheet(row.slug, null, row.date, true, onDismiss = { rescheduling = null }, done = { rescheduled(row) })
    }
    carrying?.let { row ->
        AsksFor(
            stringResource(R.string.carry_to_which_position),
            carryText,
            { typed ->
                // A number, and the last one that was one is what is carried to.
                carryText = typed.filter(Char::isDigit)
                carryText.toIntOrNull()?.let { carryTo = it }
            },
            prompt = stringResource(R.string.position_1_to, rows.size),
            confirm = stringResource(R.string.carry),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
            message = stringResource(R.string.the_posts_in_between_step_back_one),
            onConfirm = { scope.launch { carry(row, carryTo) } },
            onDismiss = { carrying = null },
        )
    }
    Says(said) { said = null }
    Says(notice?.let { Said(text = it) }) { notice = null }
}

/**
 * A post in the queue: its place as a key, its title, and when it goes
 * out -- the hour at the edge, the whole date with its zone under the
 * title, because the queue is the blog's clock and a phone abroad would
 * otherwise show an hour nobody scheduled.
 */
@Composable
fun QueueRowView(row: QueueRow) {
    val due = engineInstant(row.date)
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        KeyChip("${row.position}")
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title.ifEmpty { row.slug }, color = Theme.ink, style = ui(15f, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (due != null) whole(due) else row.date, color = Theme.muted, style = ui(13f))
            if (row.overdue) Text(stringResource(R.string.waiting_for_the_cron), color = Theme.muted, style = ui(13f))
        }
        Spacer(Modifier.width(8.dp))
        if (due != null) Text(RowDate.soon(due), color = Theme.accent, style = mono(11f), modifier = Modifier.padding(top = 3.dp))
    }
}

/** A day, its hour and the zone the hour is told in, the way the reader's own language writes them. */
private fun whole(date: Instant): String {
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMdjmz")
    return DateTimeFormatter.ofPattern(pattern, locale).format(date.atZone(ZoneId.systemDefault()))
}
