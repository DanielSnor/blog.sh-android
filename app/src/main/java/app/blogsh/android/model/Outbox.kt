package app.blogsh.android.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The posts that wait on the device, on their way out: one at a time,
 * in the order they were written, each to the blog it was written for
 * and to no other. A post goes as a draft -- nothing is published by
 * sending it -- and one that arrived waits no longer.
 *
 * A post the blog would not take is kept, with the blog's reason, and is
 * not sent again unasked: what was refused once is refused again until
 * somebody has changed something. A silent server refuses nothing; what
 * could not reach it simply still waits.
 */
class Outbox(
    private val home: () -> File = { WaitingRoom.home },
    private val open: () -> String? = { Blogs.currentId },
    private val deliver: suspend (List<DeliveryFile>, String) -> ActionAnswer = { files, blog -> Engine.made(Engine.deliver(files, to = blog)) },
) {
    sealed class Outcome {
        /** Written on the blog as a draft, under this slug. */
        data class Sent(val slug: String) : Outcome()

        /** Still waits: the server could not be reached, or another post is on its way. */
        data object Waits : Outcome()

        /** The blog said no, or the delivery broke on the way; its words. */
        data class Refused(val words: String) : Outcome()
    }

    /** The post on its way. */
    var sending by mutableStateOf<String?>(null)
        private set

    /** The blogs whose waiting posts are being gone through. */
    private val running = mutableSetOf<String>()

    /** One post, to the blog it waits for -- which has to be the open one. */
    suspend fun send(post: Waiting, blog: String): Outcome {
        if (sending != null || open() != blog) return Outcome.Waits
        sending = post.id
        try {
            val files = withContext(Dispatchers.IO) { WaitingRoom.delivery(post, blog, home()) }
            val made = deliver(files, blog)
            WaitingRoom.remove(post.id, blog, home())
            return Outcome.Sent(made.slug)
        } catch (e: Throwable) {
            if (e.isCalledOff) return Outcome.Waits
            if (e is EngineError.Unreachable) return Outcome.Waits
            // The blog is taking another delivery, or building: nothing of
            // this one was kept, and it is not a no to the post -- it waits,
            // and goes with the next asking.
            if (e is EngineError.Refused && e.refusal.error == "busy") return Outcome.Waits
            val words = e.said
            WaitingRoom.note(words, post.id, blog, home())
            return Outcome.Refused(words)
        } finally {
            sending = null
            Desk.changed()
        }
    }

    /**
     * Everything that waits for the blog and was not turned away before,
     * until the server falls silent. How many arrived.
     *
     * A run for one blog does not stand in the way of another's: with a
     * post on its way -- another blog's, or one sent by hand -- this run
     * takes its turn after it. And the room is looked into again before
     * every post: one that was thrown away, or taken back into the form,
     * while another was going is not sent from memory.
     */
    suspend fun sendAll(blog: String): Int {
        if (!running.add(blog)) return 0
        try {
            while (sending != null) delay(100)
            var sent = 0
            val tried = mutableSetOf<String>()
            while (true) {
                val post = WaitingRoom.all(blog, home()).firstOrNull { it.problem == null && it.id !in tried } ?: return sent
                tried.add(post.id)
                when (send(post, blog)) {
                    is Outcome.Sent -> sent += 1
                    Outcome.Waits -> return sent
                    is Outcome.Refused -> continue
                }
            }
        } finally {
            running.remove(blog)
        }
    }

    companion object {
        /** The one the app has. */
        val shared: Outbox by lazy { Outbox() }
    }
}
