package app.blogsh.android.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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
    private var watching = false
    private val arrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * The device has a network that can carry a connection. Until the
     * system has said anything, it is taken to have one.
     */
    val hasNetwork: Boolean get() = up

    /** Said each time the device finds a network. */
    val comes: SharedFlow<Unit> get() = arrivals

    /**
     * Watched from the start, so that it is known by the time a picture
     * is chosen. A device that will not say is taken to have a network.
     */
    @Synchronized
    fun begin(context: Context) {
        if (watching) return
        watching = true
        runCatching {
            val system = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            system.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    up = true
                    arrivals.tryEmit(Unit)
                }

                override fun onLost(network: Network) {
                    up = false
                }
            })
        }
    }
}
