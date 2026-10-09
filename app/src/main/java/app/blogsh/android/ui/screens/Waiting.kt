package app.blogsh.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.BlogShelf
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Desk
import app.blogsh.android.model.Herald
import app.blogsh.android.model.Outbox
import app.blogsh.android.model.Reach
import app.blogsh.android.model.Unsent
import app.blogsh.android.model.Waiting
import app.blogsh.android.model.WaitingRoom
import app.blogsh.android.ui.Asks
import app.blogsh.android.ui.Choice
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.DialogKey
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.OutOfReach
import app.blogsh.android.ui.PaperSheet
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.RowDate
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.gap
import app.blogsh.android.ui.ui
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant

/**
 * The posts kept on the device until their blog can be reached: each
 * with what it is called, when it was written and what goes with it,
 * and the three things there are to do with one -- send it, take it
 * back into the form to write on, throw it away.
 *
 * `write`: back to the form, with a post handed to it.
 */
@Composable
fun WaitingSheet(onDismiss: () -> Unit, write: () -> Unit) {
    val context = LocalContext.current
    var discarding by remember { mutableStateOf<Waiting?>(null) }
    var said by remember { mutableStateOf<String?>(null) }
    val blog = Blogs.currentId
    val changes = Desk.changes
    val posts = remember(blog, changes) { blog?.let { WaitingRoom.all(it) } ?: emptyList() }
    val offline = Reach.shared.isOffline(Blogs.current)
    // The form holds a post of its own: there is no room in it for another.
    val formTaken = remember(blog, changes) { blog?.let { Unsent.kept(it, BlogShelf.notes) != null } ?: true }
    val outbox = Outbox.shared
    val stillWaits = stringResource(R.string.the_blog_s_server_did_not_answer)

    suspend fun send(post: Waiting) {
        val to = blog ?: return
        said = null
        when (val outcome = outbox.send(post, to)) {
            is Outbox.Outcome.Sent -> Herald.shared.say(context.getString(R.string.draft_written, outcome.slug))
            Outbox.Outcome.Waits -> said = if (Reach.shared.isOffline(Blogs.current)) stillWaits else null
            // Its reason is written on the post, and shown with it.
            is Outbox.Outcome.Refused -> {}
        }
    }

    PaperSheet(
        onDismiss, name = stringResource(R.string.waiting_to_be_sent),
        count = if (posts.isEmpty()) null else NumberFormat.getIntegerInstance().format(posts.size),
        actions = { DialogKey(stringResource(R.string.done), onClick = onDismiss) },
    ) {
        Hint(
            stringResource(
                if (posts.isEmpty()) R.string.nothing_waits_on_this_device
                else if (offline) R.string.the_blog_s_server_cannot_be_reached_2
                else R.string.each_goes_to_the_blog_as_a
            ),
            Modifier.gap(14),
        )
        said?.let { ProblemLine(it) }
        for (post in posts) {
            key(post.id) {
                Plate(Modifier.gap(14)) {
                    row {
                        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                post.headline.ifEmpty { stringResource(R.string.a_post_without_words) }, color = Theme.ink,
                                style = ui(16f, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            // When it was written, and how many pictures and videos go with it.
                            val written = RowDate.spoken(Instant.ofEpochMilli(post.at))
                            Text(
                                if (post.pieces.isEmpty()) written else stringResource(R.string.pictures_and_video_2, written, post.pieces.size),
                                color = Theme.muted, style = ui(13f),
                            )
                            post.problem?.let { problem ->
                                SelectionContainer { Text(problem, color = Theme.danger, style = ui(13f, FontWeight.Medium)) }
                            }
                        }
                    }
                    row {
                        OutOfReach(offline) {
                            Command(
                                stringResource(R.string.send_to_the_blog_as_a_draft), Symbols.paperplane,
                                busy = outbox.sending == post.id, enabled = outbox.sending == null || outbox.sending == post.id,
                            ) { Desk.outliving.launch { send(post) } }
                        }
                    }
                    row {
                        OutOfReach(formTaken) {
                            Command(stringResource(R.string.back_to_the_form_to_write_on), Symbols.squareAndPencil, enabled = outbox.sending == null) {
                                Desk.hand(post)
                                onDismiss()
                                write()
                            }
                        }
                    }
                    row {
                        Command(stringResource(R.string.throw_away), Symbols.trash, danger = true, enabled = outbox.sending != post.id) { discarding = post }
                    }
                }
            }
        }
        if (formTaken && posts.isNotEmpty()) Hint(stringResource(R.string.the_form_holds_a_post_that_is))
    }

    discarding?.let { post ->
        Asks(
            stringResource(R.string.throw_away_it_was_never_sent_nothing, post.headline),
            choices = listOf(
                Choice(stringResource(R.string.throw_away), danger = true) {
                    blog?.let { WaitingRoom.remove(post.id, it) }
                    Desk.changed()
                },
            ),
            onDismiss = { discarding = null },
        )
    }
}
