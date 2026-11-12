package app.blogsh.android

import app.blogsh.android.model.Door
import app.blogsh.android.model.Line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The connection kept to the server. A mistake here is a server that
 * stops answering because it was asked through too many connections --
 * or a connection left open to a blog nobody is looking at.
 */
class LineTest {
    /** A server that counts: what was opened, what is open now. */
    private class Server {
        val opened = AtomicInteger(0)
        val closed = CopyOnWriteArrayList<Int>()
        @Volatile var slow = false
        @Volatile var refuses = false

        class Refused : Exception()

        suspend fun open(): Int {
            if (slow) delay(80)
            if (refuses) throw Refused()
            return opened.incrementAndGet()
        }

        fun close(wire: Int) {
            closed.add(wire)
        }
    }

    private val home = Door("example.org", 22, "me", "a")
    private val other = Door("example.org", 22, "me", "b")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun stop() = scope.cancel()

    private fun line(server: Server, keep: Long = 30_000, rest: Long = 20_000): Line<Int> =
        Line(keep = keep, rest = rest, scope = scope, open = { server.open() }, close = { server.close(it) })

    private suspend fun until(what: () -> Boolean): Boolean {
        repeat(400) {
            if (what()) return true
            delay(25)
        }
        return what()
    }

    @Test
    fun oneConnectionServesTheCallsThatFollow() = runBlocking {
        val server = Server()
        val line = line(server)
        repeat(12) {
            val hold = line.take(home)
            assertEquals(1, hold.wire)
            line.give(hold)
        }
        assertEquals(1, server.opened.get())
        assertEquals(1, line.opened)
        assertTrue(server.closed.isEmpty())
    }

    /**
     * A screen that asks three things at once, before anything is open:
     * one connection, not three.
     */
    @Test
    fun callsThatComeTogetherShareTheOneBeingOpened() = runBlocking {
        val server = Server()
        val line = line(server)
        server.slow = true
        val holds = listOf(async { line.take(home) }, async { line.take(home) }, async { line.take(home) }).awaitAll()
        assertEquals(listOf(1, 1, 1), holds.map { it.wire })
        assertEquals(1, server.opened.get())
        for (hold in holds) line.give(hold)
        assertTrue(server.closed.isEmpty())
    }

    @Test
    fun itIsClosedOnceNobodyHasUsedItForAWhile() = runBlocking {
        val server = Server()
        val line = line(server, keep = 100)
        val hold = line.take(home)
        line.give(hold)
        assertTrue(until { server.closed == listOf(1) })
        // And the next call opens another.
        val next = line.take(home)
        assertEquals(2, next.wire)
        line.give(next)
    }

    /**
     * The while is counted from the last call's end, and not at all
     * under a call that is still running -- a build takes a minute.
     */
    @Test
    fun aCallInFlightKeepsItOpen() = runBlocking {
        val server = Server()
        val line = line(server, keep = 100)
        val long = line.take(home)
        val short = line.take(home)
        line.give(short)
        delay(300)
        assertTrue(server.closed.isEmpty())
        line.give(long)
        assertTrue(until { server.closed == listOf(1) })
    }

    @Test
    fun anotherBlogIsAnotherConnectionAndTheFirstIsLetGo() = runBlocking {
        val server = Server()
        val line = line(server)
        val first = line.take(home)
        line.give(first)
        val second = line.take(other)
        assertEquals(2, second.wire)
        assertEquals(listOf(1), server.closed.toList())
        line.give(second)
    }

    /**
     * Let go of under a call: that call finishes on it, and only then
     * is it closed.
     */
    @Test
    fun aConnectionLetGoOfUnderACallIsClosedWhenTheCallEnds() = runBlocking {
        val server = Server()
        val line = line(server)
        val running = line.take(home)
        val second = line.take(other)
        assertTrue(server.closed.isEmpty())
        line.give(running)
        assertEquals(listOf(1), server.closed.toList())
        line.give(second)
        line.drop()
        assertEquals(listOf(1, 2), server.closed.toList())
    }

    @Test
    fun droppedItIsClosedAndTheNextCallOpensAnother() = runBlocking {
        val server = Server()
        val line = line(server)
        val hold = line.take(home)
        line.give(hold)
        line.drop()
        assertEquals(listOf(1), server.closed.toList())
        line.drop()
        assertEquals(listOf(1), server.closed.toList())
        val next = line.take(home)
        assertEquals(2, next.wire)
        line.give(next)
    }

    /** One that failed under a call is not handed to the next. */
    @Test
    fun aBrokenOneIsNotKept() = runBlocking {
        val server = Server()
        val line = line(server)
        val hold = line.take(home)
        line.give(hold, broken = true)
        assertEquals(listOf(1), server.closed.toList())
        val next = line.take(home)
        assertEquals(2, next.wire)
        line.give(next)
        assertEquals(2, server.opened.get())
    }

    @Test
    fun aServerThatTurnsTheConnectionAwayIsAskedAgainNextTime() = runBlocking {
        val server = Server()
        val line = line(server)
        server.refuses = true
        try {
            line.take(home)
            fail("a refused connection was handed out")
        } catch (e: Server.Refused) {
            // As it should be.
        }
        server.refuses = false
        val hold = line.take(home)
        assertEquals(1, hold.wire)
        line.give(hold)
    }

    /**
     * Fresh from a call it is taken on trust; after lying unused, and
     * with nobody else on it, it is one to be wary of.
     */
    @Test
    fun aConnectionThatHasRestedSaysSo() = runBlocking {
        val server = Server()
        val line = line(server, rest = 100)
        val first = line.take(home)
        assertFalse(first.rested)
        line.give(first)
        val soon = line.take(home)
        assertFalse(soon.rested)
        line.give(soon)
        delay(250)
        val late = line.take(home)
        assertTrue(late.rested)
        // Somebody else is on it by now: not one to close under them.
        val beside = line.take(home)
        assertFalse(beside.rested)
        line.give(late)
        line.give(beside)
    }
}
