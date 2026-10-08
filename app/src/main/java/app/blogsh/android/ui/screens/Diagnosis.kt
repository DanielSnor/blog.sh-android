package app.blogsh.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.DiagnosisAnswer
import app.blogsh.android.model.Doing
import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.said
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.Mark
import app.blogsh.android.ui.PaperScreen
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.gap
import app.blogsh.android.ui.ui
import kotlinx.coroutines.launch

/** Which of the two looks: at the archive, or at the installation. */
enum class Diagnosis(val args: List<String>, val nameId: Int) {
    Archive(listOf("check"), R.string.archive_check),
    Installation(listOf("doctor"), R.string.installation_check),
}

/**
 * A look at the blog that changes nothing: `./blog.sh check` goes
 * through the archive -- the posts, their pictures, links and addresses
 * -- and `./blog.sh doctor` through the installation: its configuration,
 * its announcing, its schedule, its deploy. What either finds is listed
 * here, the problems first, each with the blog's own advice under it. A
 * finding about a post is a way to that post; one about the trash, to
 * the trash. The repairs both commands have at the desk are not here:
 * the blog does not let a program with a key run them.
 */
@Composable
fun DiagnosisScreen(what: Diagnosis) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf<DiagnosisAnswer?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    // The blog's engine is older than this check's way to the app.
    var notOffered by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }

    suspend fun run() {
        running = true
        try {
            answer = Engine.call<DiagnosisAnswer>(what.args)
            problem = null
            notOffered = false
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            if ((e as? EngineError.Refused)?.refusal?.error == "unknown_command") notOffered = true else problem = e.said
        } finally {
            running = false
        }
    }

    LaunchedEffect(Unit) { if (answer == null) run() }

    PaperScreen(name = stringResource(what.nameId), doing = if (running) stringResource(Doing.word(what.args)) else null) {
        val found = answer
        val wrong = problem
        if (found != null) {
            Plate {
                row {
                    Text(
                        if (found.errors == 0 && found.warnings == 0) stringResource(R.string.no_problems)
                        else stringResource(R.string.problems_worth_a_look, found.errors, found.warnings),
                        color = if (found.errors > 0) Theme.danger else Theme.ink, style = ui(15f, FontWeight.Medium),
                    )
                }
            }
            Plate(Modifier.gap(14)) {
                for (finding in found.ordered) {
                    row {
                        val slug = finding.slug
                        if (!slug.isNullOrEmpty()) {
                            Pressable({ nav.push { PropsScreen(slug) } }) { FindingLine(finding, leads = true) }
                        } else if (finding.kind == "trash" && finding.level != DiagnosisAnswer.Finding.Level.Fine) {
                            Pressable({ nav.push { TrashScreen() } }) { FindingLine(finding, leads = true) }
                        } else {
                            FindingLine(finding, leads = false)
                        }
                    }
                }
            }
            Hint(stringResource(R.string.the_sentences_are_the_blog_s_own))
            Plate(Modifier.gap(14)) {
                row { Command(stringResource(R.string.check_again), Symbols.arrowClockwise, enabled = !running) { scope.launch { run() } } }
            }
        } else if (notOffered) {
            Hint(stringResource(R.string.this_blog_s_engine_does_not_offer))
        } else if (wrong != null) {
            ProblemLine(wrong)
        }
    }
}

/** One finding: how grave it is, the blog's sentence, and under it the blog's advice. */
@Composable
private fun FindingLine(finding: DiagnosisAnswer.Finding, leads: Boolean) {
    val fine = finding.level == DiagnosisAnswer.Finding.Level.Fine
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(20.dp).padding(top = 2.dp), contentAlignment = Alignment.TopStart) {
            when (finding.level) {
                DiagnosisAnswer.Finding.Level.Error -> {
                    val said = stringResource(R.string.problem)
                    Mark(Symbols.xmarkOctagon, 18.dp, Theme.danger, Modifier.semantics { contentDescription = said })
                }
                DiagnosisAnswer.Finding.Level.Warning -> {
                    val said = stringResource(R.string.worth_a_look)
                    Mark(Symbols.exclamationmarkTriangle, 18.dp, Theme.accent, Modifier.semantics { contentDescription = said })
                }
                DiagnosisAnswer.Finding.Level.Fine -> {
                    val said = stringResource(R.string.fine)
                    Mark(Symbols.checkmark, 18.dp, Theme.muted, Modifier.semantics { contentDescription = said })
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(finding.text, color = if (fine) Theme.muted else Theme.ink, style = ui(15f))
            finding.fix?.let { Text(it, color = Theme.muted, style = ui(13f)) }
        }
        if (leads) Mark(Symbols.chevronRight, 14.dp, Theme.muted, Modifier.align(Alignment.CenterVertically))
    }
}
