package app.blogsh.android.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Engine
import app.blogsh.android.model.Herald
import app.blogsh.android.model.RebuildAnswer
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.plain
import app.blogsh.android.model.said
import app.blogsh.android.ui.CommandRow
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.MenuEntry
import app.blogsh.android.ui.PaperScreen
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.PrimaryButton
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.SectionLabel
import app.blogsh.android.ui.SwitchRow
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.gap
import app.blogsh.android.ui.ui
import kotlinx.coroutines.launch

/**
 * "The site": the wizard's last entry, `./blog.sh rebuild` -- the whole
 * site built and deployed, not tied to a post. With the two switches the
 * command has: every page again, and the whole site uploaded past the
 * deploy's guards. Under it the two looks that only read: `check` and
 * `doctor` (see `DiagnosisScreen`).
 */
@Composable
fun SiteScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var full by remember { mutableStateOf(false) }
    var force by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RebuildAnswer?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    // The blog this screen was opened for: its site is the one that is built.
    val home = remember { Blogs.currentId }

    suspend fun rebuild() {
        running = true
        try {
            val args = mutableListOf("rebuild")
            if (full) args.add("--full")
            if (force) args.add("--force")
            result = Engine.call<RebuildAnswer>(args)
            problem = null
            // Built here, by hand: nothing is owed, and a build that
            // failed before this one has been tried again.
            Herald.shared.settled(home)
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        } finally {
            running = false
        }
    }

    PaperScreen(name = stringResource(MenuEntry.Rebuild.shortId), symbol = MenuEntry.Rebuild.symbol) {
        // Each switch with what it is for under it: the engine's own
        // names for them (--full, --force) say what they do to the
        // engine, not when somebody would want them.
        Plate {
            row { SwitchRow(stringResource(R.string.build_every_page_again), full, { full = it }) }
        }
        Hint(stringResource(R.string.usually_only_the_pages_that_changed_are))
        Plate(Modifier.gap(14)) {
            row { SwitchRow(stringResource(R.string.upload_the_whole_site_unchecked), force, { force = it }) }
        }
        Hint(stringResource(R.string.a_deploy_uploads_what_changed_and_stops))
        PrimaryButton(
            stringResource(if (running) R.string.rebuilding else R.string.rebuild_and_deploy),
            modifier = Modifier.gap(22), busy = running,
        ) { scope.launch { rebuild() } }
        problem?.let { ProblemLine(it) }
        result?.let { answer ->
            SectionLabel(stringResource(R.string.result))
            Plate {
                row {
                    Text(
                        stringResource(if (answer.deploy == "done") R.string.rebuilt_and_deployed else R.string.rebuilt_the_deploy_is_owed_to_the),
                        color = Theme.ink, style = ui(15f),
                    )
                }
                for (line in answer.warnings.plain) row { Text(line, color = Theme.muted, style = ui(13f)) }
            }
        }

        // A look that changes nothing, beside the two things that do.
        SectionLabel(stringResource(R.string.diagnostics))
        Plate {
            row {
                Pressable({ nav.push { DiagnosisScreen(Diagnosis.Archive) } }) {
                    CommandRow(stringResource(R.string.check_the_archive), Symbols.checklist, leads = true)
                }
            }
            row {
                Pressable({ nav.push { DiagnosisScreen(Diagnosis.Installation) } }) {
                    CommandRow(stringResource(R.string.check_the_installation), Symbols.stethoscope, leads = true)
                }
            }
        }
        Hint(stringResource(R.string.both_only_read_the_first_goes_through))
    }
}
