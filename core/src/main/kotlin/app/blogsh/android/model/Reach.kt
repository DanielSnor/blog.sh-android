package app.blogsh.android.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Whether a blog's server answered the last time it was asked. A blog
 * whose server did not is offline: nothing that needs the server is
 * offered there, and what can be done without it -- writing -- still
 * is. The server is what is silent, not the blog: two blogs behind one
 * address share its fate.
 *
 * Nothing here asks the server anything. The engine's calls say what
 * became of them: one that could not get through makes the server
 * silent, any that was answered makes it heard from again.
 */
class Reach {
    var silent by mutableStateOf(emptySet<String>())
        private set

    @Synchronized
    fun nothing(from: String) {
        if (from !in silent) silent = silent + from
    }

    @Synchronized
    fun heard(from: String) {
        if (from in silent) silent = silent - from
    }

    /** The blog's server did not answer the last time it was asked. */
    fun isOffline(blog: Blog?): Boolean = blog != null && server(blog.host, blog.port) in silent

    companion object {
        /** The one the app has. */
        val shared = Reach()

        /**
         * How long a silent server is left alone before it is asked
         * again by itself, attempt after attempt: five seconds, then
         * twice as long each time, and a minute at the most. A network
         * that has just come back often does not carry a connection yet;
         * asked once and no more, a server that would have answered a
         * moment later stayed silent until somebody thought of it.
         */
        fun pause(attempt: Int): Long = if (attempt >= 4) 60_000L else 5_000L shl attempt.coerceAtLeast(0)

        /** A server, as the app tells one from another. */
        fun server(host: String, port: Int): String = host.trim(' ', '\t').lowercase() + ":" + (if (port == 0) 22 else port)
    }
}

/**
 * The device's own network, watched for one thing: the moment it has
 * one that can carry a connection. That is when a server that was
 * silent is worth asking again -- which nobody should have to think of.
 */
object NetworkWatch {
    @Volatile
    private var up = true
    private val arrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * The device has a network that can carry a connection. Until the
     * system has said anything, it is taken to have one -- and so is a
     * device that will not say.
     */
    val hasNetwork: Boolean get() = up

    /** Said each time the device finds a network. */
    val comes: SharedFlow<Unit> get() = arrivals

    /** The system says the device has a network again: what the app watches it by calls this. */
    fun found() {
        up = true
        arrivals.tryEmit(Unit)
    }

    /** The system says the network is gone. */
    fun lost() {
        up = false
    }
}
