package app.blogsh.android.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.blogsh.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What the app says without being asked and without asking back: that
 * something was done, and that the site is being brought up to date. One
 * place for both, shown under whatever screen is open, so the same thing
 * is said the same way wherever it happens -- and nothing of it is a key.
 *
 * The site is built by the app itself. A change the site does not show
 * yet -- a pin, a rename, a post deleted or restored, the queue put in
 * another order -- is owed a build; the build waits a moment for the
 * next such change, as the terminal's queue waits for the way out, and
 * then runs. Nobody is asked whether it should.
 */
class Herald(
    /** How long a build waits for the next change, and for whatever else holds the engine when it finds it busy. */
    private val pause: Long = 6_000,
    private val retry: Long = 10_000,
    /** Which blog is open, and the build itself: the engine's `rebuild`. */
    private val open: () -> String? = { Blogs.currentId },
    private val rebuild: suspend () -> Unit = { Engine.call<RebuildAnswer>("rebuild") },
    private val scope: CoroutineScope = MainScope(),
    /** The words for a change nobody put into words. */
    private val plainly: () -> String = { Spoken.say(R.string.the_site_does_not_show_this_change) },
) {
    sealed class Build {
        data object None : Build()

        /** A change waits for its build; another may follow. */
        data object Owed : Build()
        data object Building : Build()
        data class Failed(val why: String) : Build()
    }

    var build: Build by mutableStateOf(Build.None)
        private set

    /** What the site does not show yet, in the words of the screen that changed it. */
    var owedFor by mutableStateOf("")
        private set

    /** What was just done, said for a few seconds. */
    var note by mutableStateOf<String?>(null)
        private set

    private var waiting: Job? = null
    private var fading: Job? = null

    /** The blog the build is owed to: a build is that blog's and no other's. */
    private var blog: String? = null
    private var again = false
    private var tries = 0

    /** A build holds the lock a change of the queue or another build needs. */
    val isBuilding: Boolean get() = build == Build.Building

    /** Something was done: said, and gone again by itself. */
    fun say(words: String) {
        if (words.isEmpty()) return
        note = words
        fading?.cancel()
        fading = scope.launch {
            delay(6_000)
            note = null
        }
    }

    /**
     * A change the site does not show yet. `why`: what a reader of the
     * site would notice, where the screen has more to say than that.
     */
    fun owe(why: String? = null) {
        owedFor = why ?: plainly()
        blog = open()
        tries = 0
        if (build == Build.Building) {
            // Changed while a build runs: that build may have missed it.
            again = true
        } else {
            build = Build.Owed
            arm()
        }
    }

    /**
     * The site was just built by something else -- a publish builds it
     * whole -- so what was owed is paid.
     */
    fun settled() {
        if (build != Build.Owed) return
        waiting?.cancel()
        build = Build.None
    }

    /** A failure was read; the line goes. */
    fun acknowledge() {
        if (build is Build.Failed) build = Build.None
    }

    private fun arm(after: Long = pause) {
        waiting?.cancel()
        waiting = scope.launch {
            delay(after)
            run()
        }
    }

    private suspend fun run() {
        if (build != Build.Owed) return
        // Another blog is open by now: its engine is not the one to ask.
        if (blog != open()) {
            build = Build.None
            return
        }
        build = Build.Building
        again = false
        try {
            rebuild()
            if (again) {
                build = Build.Owed
                arm()
            } else {
                build = Build.None
            }
        } catch (e: Throwable) {
            if (e is EngineError.Refused && e.refusal.error == "busy" && tries < 6) {
                // Something else is building or publishing: the same wish, a little later.
                tries += 1
                build = Build.Owed
                arm(retry)
            } else {
                build = if (e.isCalledOff) Build.None else Build.Failed(e.said)
            }
        }
    }

    companion object {
        /** The one the app has. */
        val shared: Herald by lazy { Herald() }
    }
}

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
        else -> R.string.saving
    }
}
