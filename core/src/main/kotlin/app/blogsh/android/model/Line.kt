package app.blogsh.android.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Where a connection goes and what it is opened with: one blog's server
 * and that blog's key. Two calls share a connection only when all of it
 * is the same.
 */
data class Door(val host: String, val port: Int, val user: String, val keyAccount: String)

/**
 * The connection the app keeps to the open blog's server. A server
 * counts the connections an address opens, and turns the ones over its
 * count away -- so somebody going from screen to screen, each asking a
 * thing or two, was soon asking a server that no longer answered. One
 * connection is opened with the first call and kept for the ones after
 * it: every command is a channel of its own on it, several at once where
 * a build runs while a list is read. It is closed when nothing has used
 * it for a while, when another blog is opened, and when the app leaves
 * the screen.
 *
 * What is kept here is only which connection, for whom and how busy; how
 * one is opened and closed is handed in, so the keeping can be tested
 * without a server.
 *
 * `keep`: how long a connection nobody uses is kept, in milliseconds.
 * `rest`: after how long unused it is no longer taken on trust.
 */
class Line<Wire>(
    private val keep: Long,
    private val rest: Long = 20_000,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val open: suspend (Door) -> Wire,
    private val close: suspend (Wire) -> Unit,
) {
    /** One call's use of the connection, given back when the call is over. */
    class Hold<Wire> internal constructor(
        val wire: Wire,
        internal val id: Int,
        /**
         * The connection had been lying unused for a while when this call
         * took it, and no other call was on it: if it does not answer,
         * it is dead rather than slow, and nobody else is harmed by
         * closing it.
         */
        val rested: Boolean,
    )

    private class Kept<Wire>(val id: Int, val door: Door, val wire: Wire)
    private class Opening<Wire>(val id: Int, val door: Door, val task: Deferred<Wire>)

    // What is kept is looked at and changed under this, never across a wait.
    private val guard = Mutex()
    private var current: Kept<Wire>? = null

    /**
     * A connection on its way up: whoever asks for the same door
     * meanwhile waits for this one instead of opening a second.
     */
    private var opening: Opening<Wire>? = null

    /** Calls in flight, by connection. */
    private val busy = HashMap<Int, Int>()

    /** Connections no longer kept that a call is still on: closed when their last call gives them back. */
    private val leaving = HashMap<Int, Wire>()
    private val lastUsed = HashMap<Int, Long>()
    private var timer: Job? = null
    private var serial = 0

    /** How many connections were opened, ever: what a server counts. */
    @Volatile
    var opened = 0
        private set

    /**
     * The connection to this door: the kept one, the one just being
     * opened, or a new one -- after which whatever was kept to another
     * door is let go.
     */
    suspend fun take(door: Door): Hold<Wire> {
        var mine = false
        var toClose: Wire? = null
        var held: Hold<Wire>? = null
        var pending: Opening<Wire>? = null
        guard.withLock {
            val kept = current
            val coming = opening
            if (kept != null && kept.door == door) {
                held = hold(kept.id, kept.wire)
            } else if (coming != null && coming.door == door) {
                pending = coming
            } else {
                serial += 1
                // Said before anything is waited for: a call for the same door
                // that arrives meanwhile finds this one, and opens no second.
                val begun = Opening(serial, door, scope.async { open(door) })
                opening = begun
                pending = begun
                mine = true
                toClose = letGo()
            }
        }
        held?.let { return it }
        toClose?.let { close(it) }
        val coming = pending!!
        // Not called off half way: a connection that came up for a call
        // that left meanwhile would be kept by nobody and closed by nobody.
        return withContext(NonCancellable) {
            if (!mine) {
                val wire = coming.task.await()
                return@withContext guard.withLock { hold(coming.id, wire) }
            }
            val wire = try {
                coming.task.await()
            } catch (e: Throwable) {
                guard.withLock { if (opening?.id == coming.id) opening = null }
                throw e
            }
            guard.withLock {
                opened += 1
                if (opening?.id == coming.id) {
                    opening = null
                    current = Kept(coming.id, door, wire)
                } else {
                    // Let go of while it was coming up: used by the calls
                    // that waited for it, kept by nobody.
                    leaving[coming.id] = wire
                }
                hold(coming.id, wire)
            }
        }
    }

    /**
     * The call is over. `broken`: the connection failed under it, and is
     * not one to hand to the next call.
     */
    suspend fun give(hold: Hold<Wire>, broken: Boolean = false): Unit = withContext(NonCancellable) {
        val id = hold.id
        var toClose: Wire? = null
        guard.withLock {
            val kept = current
            if (broken && kept != null && kept.id == id) {
                current = null
                leaving[id] = kept.wire
            }
            lastUsed[id] = now()
            val left = (busy[id] ?: 1) - 1
            if (left > 0) busy[id] = left else busy.remove(id)
            if (left <= 0) {
                val gone = leaving.remove(id)
                if (gone != null) {
                    lastUsed.remove(id)
                    toClose = gone
                } else if (current?.id == id) {
                    arm(id)
                }
            }
        }
        toClose?.let { close(it) }
    }

    /**
     * Lets go of whatever is kept: the app left the screen, or the key
     * or the server's address was changed under the connection.
     */
    suspend fun drop(): Unit = withContext(NonCancellable) {
        val toClose = guard.withLock {
            opening = null
            letGo()
        }
        toClose?.let { close(it) }
    }

    private fun hold(id: Int, wire: Wire): Hold<Wire> {
        val alone = busy[id] == null
        val since = lastUsed[id]?.let { now() - it }
        busy[id] = (busy[id] ?: 0) + 1
        if (current?.id == id) timer?.cancel()
        return Hold(wire, id, rested = alone && since != null && since > rest)
    }

    /** What is kept is kept no more; handed back to be closed where no call is on it. */
    private fun letGo(): Wire? {
        timer?.cancel()
        val kept = current ?: return null
        current = null
        if (busy[kept.id] == null) {
            lastUsed.remove(kept.id)
            return kept.wire
        }
        leaving[kept.id] = kept.wire
        return null
    }

    private fun arm(id: Int) {
        timer?.cancel()
        timer = scope.launch {
            delay(keep)
            expire(id)
        }
    }

    private suspend fun expire(id: Int) {
        val toClose = guard.withLock {
            val kept = current
            if (kept == null || kept.id != id || busy[id] != null) return@withLock null
            current = null
            lastUsed.remove(id)
            kept.wire
        }
        toClose?.let { withContext(NonCancellable) { close(it) } }
    }
}
