package app.blogsh.android.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.ActionAnswer
import app.blogsh.android.model.Engine
import app.blogsh.android.model.HeldAnswer
import app.blogsh.android.model.PostState
import app.blogsh.android.model.RebuildAnswer
import app.blogsh.android.model.TrashAnswer
import app.blogsh.android.model.TrashRow
import app.blogsh.android.model.engineInstant
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.plain
import app.blogsh.android.model.said
import app.blogsh.android.ui.Asks
import app.blogsh.android.ui.Busy
import app.blogsh.android.ui.Choice
import app.blogsh.android.ui.CommandRow
import app.blogsh.android.ui.EmptyNote
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.Menu
import app.blogsh.android.ui.MenuKey
import app.blogsh.android.ui.PaperRow
import app.blogsh.android.ui.PaperScaffold
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.RowDate
import app.blogsh.android.ui.Said
import app.blogsh.android.ui.Says
import app.blogsh.android.ui.ScreenHeader
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** What is cleared out, by the word `empty` takes for it. */
private enum class Clearing(val word: String) { Trash("trash"), Versions("versions") }

/**
 * What the trash holds, as `restore --json` lists it -- the rows the
 * terminal offers when `restore` is run with no slug -- and what a row
 * does: it restores its post. A draft comes back with its preview
 * rebuilt, as the terminal rebuilds it; a published post is asked about,
 * as the terminal asks. Under the rows, the clearing out the terminal
 * has two commands for: `empty trash` and `empty versions`. Both are for
 * good, both say how much before they ask, and each is asked by its
 * own key.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf(emptyList<TrashRow>()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf<TrashRow?>(null) }
    // How much the trash and the older versions hold, as `empty` counts them.
    var held by remember { mutableStateOf<HeldAnswer?>(null) }
    var older by remember { mutableStateOf<HeldAnswer?>(null) }
    var emptying by remember { mutableStateOf<Clearing?>(null) }
    // What the screen says after a restore -- one thing, its question in it.
    var said by remember { mutableStateOf<Said?>(null) }
    /** The row whose menu is open. */
    var menuFor by remember { mutableStateOf<String?>(null) }

    fun size(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    suspend fun load() {
        loading = true
        try {
            // One connection: the rows, and how much there is to clear out --
            // `empty` without `--yes` counts and touches nothing.
            val answers = Engine.batch(listOf(listOf("restore"), listOf("empty", "trash"), listOf("empty", "versions")))
            val answer = Engine.decode<TrashAnswer>(answers[0])
            rows = answer.trash
            held = if (answers.size > 1) runCatching { Engine.decode<HeldAnswer>(answers[1]) }.getOrNull() else null
            older = if (answers.size > 2) runCatching { Engine.decode<HeldAnswer>(answers[2]) }.getOrNull() else null
            problem = null
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        } finally {
            loading = false
        }
    }

    /**
     * `empty trash --yes` / `empty versions --yes`: gone for good. The
     * answer says how much went; the screen reads itself again.
     */
    suspend fun empty(what: Clearing) {
        busy = true
        try {
            val answer = Engine.call<HeldAnswer>("empty", what.word, "--yes")
            load()
            said = when (what) {
                Clearing.Trash -> Said(text = context.getString(R.string.trash_emptied_item_s_freed, answer.count, size(answer.bytes)))
                Clearing.Versions -> Said(text = context.getString(R.string.older_versions_removed_freed_every_post_kept, answer.count, size(answer.bytes)))
            }
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            said = Said(text = e.said)
        } finally {
            busy = false
        }
    }

    suspend fun rebuild() {
        busy = true
        try {
            val answer = Engine.call<RebuildAnswer>("rebuild")
            said = Said(text = context.getString(if (answer.deploy == "done") R.string.rebuilt_and_deployed else R.string.rebuilt_the_deploy_is_owed_to_the))
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            said = Said(text = e.said)
        } finally {
            busy = false
        }
    }

    // The row as a value: the question has put its own state away by the
    // time this runs.
    suspend fun restore(row: TrashRow) {
        busy = true
        try {
            // A draft's preview is rebuilt without asking, the way the terminal
            // does it; a published post's page is asked about.
            val args = listOf("restore", row.slug) + (if (row.state == "draft") listOf("--rebuild") else emptyList())
            val answer = Engine.call<ActionAnswer>(args)
            // The engine's own lines say it; the address only when it said nothing.
            val lines = (answer.warnings ?: emptyList()).plain
            val words = if (lines.isEmpty()) context.getString(R.string.restored, answer.url ?: row.slug) else lines.joinToString("\n")
            load()
            // A published post is back in the archive and not yet on the site:
            // that it is back, and the question about the site, as one.
            said = if (answer.state == PostState.Published) {
                Said(
                    title = context.getString(R.string.rebuild_and_deploy_the_site_now), text = words,
                    // Rebuilt by the screen itself, which is still here when the question is put away.
                    ask = Said.Ask(button = context.getString(R.string.rebuild), cancel = context.getString(R.string.not_now)) {
                        scope.launch { rebuild() }.join()
                    },
                )
            } else {
                Said(text = words)
            }
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            said = Said(text = e.said)
        } finally {
            busy = false
        }
    }

    LaunchedEffect(Unit) { load() }

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
            LazyColumn(Modifier.fillMaxSize().alpha(if (busy) 0.5f else 1f), userScrollEnabled = !busy) {
                item(key = "header") {
                    PaperRow {
                        ScreenHeader(
                            stringResource(R.string.tile_restore), if (rows.isEmpty()) null else NumberFormat.getIntegerInstance().format(rows.size),
                            Modifier.padding(top = 2.dp, bottom = 6.dp),
                        )
                    }
                }
                problem?.let { words ->
                    item(key = "problem") {
                        PaperRow { Text(words, color = Theme.muted, style = ui(14f), modifier = Modifier.padding(vertical = 12.dp)) }
                    }
                }
                items(rows, key = { it.id }) { row ->
                    Box {
                        // A press restores; a long one opens the row's menu, which says the same.
                        PaperRow(Modifier.combinedClickable(enabled = !busy, onClick = { restoring = row }, onLongClick = { menuFor = row.id })) {
                            TrashRowView(row)
                        }
                        Menu(menuFor == row.id, { menuFor = null }) {
                            MenuKey(stringResource(R.string.restore), Symbols.arrowUturnBackward) {
                                menuFor = null
                                restoring = row
                            }
                        }
                    }
                }
                val trash = held?.takeIf { it.count > 0 }
                val versions = older?.takeIf { it.count > 0 }
                if (trash != null || versions != null) {
                    item(key = "clearing") {
                        PaperRow(rule = false) {
                            Column(Modifier.padding(bottom = 18.dp)) {
                                SectionLabel(stringResource(R.string.clearing_out))
                                // What cannot be taken back, set apart and last.
                                Plate {
                                    if (trash != null) row {
                                        Pressable({ emptying = Clearing.Trash }, enabled = !busy) {
                                            CommandRow(stringResource(R.string.empty_the_trash_item_s, trash.count, size(trash.bytes)), Symbols.trashSlash, danger = true)
                                        }
                                    }
                                    if (versions != null) row {
                                        Pressable({ emptying = Clearing.Versions }, enabled = !busy) {
                                            CommandRow(stringResource(R.string.remove_older_versions_file_s, versions.count, size(versions.bytes)), Symbols.clockBadgeXmark, danger = true)
                                        }
                                    }
                                }
                                Hint(stringResource(R.string.what_is_cleared_out_is_gone_for))
                            }
                        }
                    }
                }
            }
            if (loading && rows.isEmpty() && !refreshing) {
                Busy(modifier = Modifier.align(Alignment.Center))
            } else if (!loading && rows.isEmpty() && problem == null) {
                EmptyNote(Symbols.trash, stringResource(R.string.trash_is_empty), modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    // Asked about the row it names, not about the screen.
    restoring?.let { row ->
        Asks(
            stringResource(R.string.restore_2, row.slug),
            stringResource(if (row.mediaOnly) R.string.only_media_are_in_the_trash_for else R.string.the_post_its_media_and_its_history),
            listOf(Choice(stringResource(R.string.restore)) { scope.launch { restore(row) } }),
            onDismiss = { restoring = null },
        )
    }
    when (emptying) {
        Clearing.Trash -> held?.let { now ->
            Asks(
                stringResource(R.string.delete_item_s_from_the_trash_for, now.count, size(now.bytes)),
                choices = listOf(Choice(stringResource(R.string.empty_the_trash), danger = true) { scope.launch { empty(Clearing.Trash) } }),
                onDismiss = { emptying = null },
            )
        }
        Clearing.Versions -> older?.let { now ->
            Asks(
                stringResource(R.string.remove_older_version_s_freeing_every_post, now.count, size(now.bytes)),
                choices = listOf(Choice(stringResource(R.string.remove_older_versions), danger = true) { scope.launch { empty(Clearing.Versions) } }),
                onDismiss = { emptying = null },
            )
        }
        null -> {}
    }
    Says(said) { said = null }
}

@Composable
fun TrashRowView(row: TrashRow) {
    val title = row.title
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title ?: row.slug, color = if (title == null) Theme.muted else Theme.ink,
                style = ui(15f, if (title == null) FontWeight.Medium else FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (row.mediaOnly) stringResource(R.string.media_only) else listOf(row.type ?: "", row.slug).filter { it.isNotEmpty() }.joinToString(" · "),
                color = Theme.muted, style = ui(13f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        engineInstant(row.date)?.let { day ->
            Text(RowDate.short(day), color = Theme.muted, style = mono(11f, bold = false), modifier = Modifier.padding(top = 3.dp))
        }
    }
}
