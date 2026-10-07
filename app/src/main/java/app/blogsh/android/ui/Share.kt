package app.blogsh.android.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.blogsh.android.R
import app.blogsh.android.model.PostLink

/**
 * A post's address handed to somebody: the system's own sheet, with the
 * address and the title to go with it.
 */
fun share(context: Context, link: PostLink) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link.url)
        putExtra(Intent.EXTRA_SUBJECT, link.title)
        // What the sheet shows of what it is about to hand on.
        putExtra(Intent.EXTRA_TITLE, link.title)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/**
 * The key that hands a post's address to somebody. It stands in the bar
 * of every screen that is about one post, once the engine has said where
 * the post is.
 */
@Composable
fun ShareKey(link: PostLink) {
    val context = LocalContext.current
    BarKey(Symbols.squareAndArrowUp, stringResource(R.string.share_the_link)) { share(context, link) }
}
