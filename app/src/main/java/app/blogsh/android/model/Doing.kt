package app.blogsh.android.model

import app.blogsh.android.R

/** The word for what an engine command is doing, by the command. */
object Doing {
    fun word(args: List<String>): Int = when (args.firstOrNull()) {
        "publish" -> R.string.publishing
        "unpublish" -> R.string.unpublishing
        "delete" -> R.string.deleting
        "restore" -> R.string.restoring
        "toot", "bluesky" -> R.string.announcing
        "empty" -> R.string.clearing_out_2
        "rebuild" -> R.string.rebuilding
        "check", "doctor" -> R.string.checking
        else -> R.string.saving
    }
}
