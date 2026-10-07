package app.blogsh.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.EditAnswer
import app.blogsh.android.model.EditEntry
import app.blogsh.android.model.Engine
import app.blogsh.android.model.PostLink
import app.blogsh.android.model.PostRow
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.said
import app.blogsh.android.ui.BarKey
import app.blogsh.android.ui.Busy
import app.blogsh.android.ui.Hairline
import app.blogsh.android.ui.PaperSheet
import app.blogsh.android.ui.PostFacts
import app.blogsh.android.ui.ShareKey
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.RowDate
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui

/**
 * Space on the archive screen: a look at the post under the cursor without
 * opening it -- its title, the row's own line, its tags, and the text as
 * the editor would show it. `edit <slug> --json` hands the text out for
 * any post, a draft as well as a published one; nothing is written. The
 * page itself is one tap further: a published post's address, or the
 * hidden page the build keeps for a draft.
 */
@Composable
fun PostPreviewSheet(post: PostRow, baseUrl: String = "", onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    var entry by remember { mutableStateOf<EditEntry?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val answer = Engine.call<EditAnswer>("edit", post.slug)
            entry = answer.post
            problem = null
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        }
    }

    val loaded = entry
    val web = loaded?.let { webUrl(it, baseUrl) }
    // The sheet's own key closes it, where iOS writes Done.
    PaperSheet(onDismiss, title = post.title ?: post.slug, actions = {
        if (web != null) {
            BarKey(Symbols.safari, stringResource(R.string.show_on_the_web)) { uri.openUri(web) }
            PostLink.of(web, post.title ?: post.slug)?.let { ShareKey(it) }
        }
    }) {
        PostFacts(post)
        Hairline(Modifier.padding(vertical = 14.dp))
        val failed = problem
        if (loaded != null) {
            val text = remember(loaded) { words(loaded) }
            if (text.isEmpty()) {
                Text(stringResource(R.string.the_post_has_no_text), color = Theme.muted, style = ui(15f))
            } else {
                SelectionContainer { Text(text, color = Theme.ink, style = mono(15f, bold = false)) }
            }
        } else if (failed != null) {
            ProblemLine(failed)
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Busy() }
        }
    }
}

/**
 * The text without the header the editor puts above it: the title and
 * the tags are already on the screen.
 */
private fun words(entry: EditEntry): String {
    var text = entry.text ?: return ""
    if (text.startsWith("---\n")) {
        val close = text.indexOf("\n---\n", 3)
        if (close >= 0) text = text.substring(close + 5)
    }
    return text.trim()
}

/**
 * Where the page is: the site's address and the post's own path, which
 * for a draft is its hidden preview.
 */
private fun webUrl(entry: EditEntry, baseUrl: String): String? {
    if (baseUrl.isEmpty() || !entry.preview.startsWith("/")) return null
    return baseUrl.removeSuffix("/") + entry.preview
}
