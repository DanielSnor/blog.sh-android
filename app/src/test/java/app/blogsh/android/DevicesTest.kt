package app.blogsh.android

import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.PairedDevices
import app.blogsh.android.model.Refusal
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The devices a code let in, as the blog lists them through its door,
 * and what the app makes of who is asking. A mistake here is a device
 * offered a way out the blog would say no to, or one that cannot find
 * itself among the others.
 */
class DevicesTest {
    @Before
    fun words() = English.speak()

    private val phone = "SHA256:la6QSKoLVR1OyiPKsCYMqieZSBecBuurCBCGPv6Fals"
    private val tablet = "SHA256:ysUXkFzgUqoqTPIe2meJVL9qcNx4GcU/usa/kLQPlPY"

    private fun read(json: String): PairedDevices = Engine.decode(json.toByteArray())

    private fun refusal(code: String, message: String? = null) = EngineError.Refused(Refusal(false, code, message))

    @Test
    fun theListIsRead() {
        val answer = read("""{"ok":true,"devices":[{"name":"Danielův iPhone","fingerprint":"$phone","self":true},{"name":"Tablet","fingerprint":"$tablet","self":false}],"self":"$phone"}""")
        assertEquals(listOf("Danielův iPhone", "Tablet"), answer.devices.map { it.name })
        assertEquals(listOf(true, false), answer.devices.map { it.own })
        assertEquals(phone, answer.own)
        assertTrue(answer.mayLeave)
        assertNull(answer.revoked)
    }

    /**
     * After the device was taken off, the blog names it and lists who
     * is left; nobody is asking any more.
     */
    @Test
    fun theTakingOffIsRead() {
        val answer = read("""{"ok":true,"revoked":{"name":"Danielův iPhone","fingerprint":"$phone"},"devices":[{"name":"Tablet","fingerprint":"$tablet","self":false}],"self":null}""")
        assertEquals("Danielův iPhone", answer.revoked?.name)
        assertEquals(1, answer.devices.size)
        assertFalse(answer.mayLeave)
    }

    /** A blog reached by a line written by hand: no devices, nobody known. */
    @Test
    fun aBlogWithNoneIsRead() {
        val answer = read("""{"ok":true,"devices":[],"self":null}""")
        assertTrue(answer.devices.isEmpty())
        assertFalse(answer.mayLeave)
        assertFalse(answer.holdsThis(phone))
    }

    /**
     * An older pairing's line does not say whose it is: the blog cannot
     * tell who asks, the app still knows its own key in the list -- and
     * does not offer what the blog would say no to.
     */
    @Test
    fun thisDeviceIsKnownByItsKeyWhereTheBlogCannotTell() {
        val answer = read("""{"ok":true,"devices":[{"name":"Phone","fingerprint":"$phone","self":false},{"name":"Tablet","fingerprint":"$tablet","self":false}],"self":null}""")
        assertTrue(answer.isThis(answer.devices[0], phone))
        assertFalse(answer.isThis(answer.devices[1], phone))
        assertTrue(answer.holdsThis(phone))
        assertFalse(answer.holdsThis(null))
        assertFalse(answer.mayLeave)
    }

    /**
     * A line whose key was damaged by hand carries no fingerprint, and
     * is nobody's by that.
     */
    @Test
    fun aLineWithNoKeyIsNobodys() {
        val broken = PairedDevices.Device("?", "")
        val answer = PairedDevices(devices = listOf(broken))
        assertFalse(answer.isThis(broken, ""))
        assertFalse(answer.isThis(broken, null))
        // What the engine may leave out is read as nothing, not as a screen that cannot be drawn.
        assertEquals(PairedDevices.Device(), read("""{"ok":true,"devices":[{}],"self":null}""").devices.single())
    }

    @Test
    fun anOlderBlogIsToldApart() {
        assertTrue(PairedDevices.notKnownThere(refusal("unknown_command", "pair is not a command here")))
        assertFalse(PairedDevices.notKnownThere(refusal("device_unknown")))
        assertFalse(PairedDevices.notKnownThere(EngineError.KeyNotKnown(paired = true)))
    }

    /**
     * The answer to the taking off was lost, the app asked again, and
     * the server no longer knew the key: that is the taking off.
     */
    @Test
    fun aKeyNoLongerKnownIsTheDeviceGone() {
        assertTrue(PairedDevices.isGone(EngineError.KeyNotKnown(paired = true)))
        assertFalse(PairedDevices.isGone(refusal("busy")))
        assertFalse(PairedDevices.isGone(EngineError.Unreachable("blog.example", "timeout")))
    }

    /**
     * The key file held by another run is said in the blog's words, not
     * in the app's sentence about a build and a save.
     */
    @Test
    fun aHeldKeyFileIsSaidInTheBlogsWords() {
        val said = "Soubor s klíči zrovna mění někdo jiný, takže se nic neodebralo. Za chvíli to zkus znovu."
        assertEquals(said, PairedDevices.words(refusal("busy", said)))
        assertEquals(Refusal.held, PairedDevices.words(refusal("busy")))
        assertEquals("could not be written", PairedDevices.words(refusal("write_failed", "could not be written")))
    }

    /**
     * Taking off waits its turn at the key file; looking does not. Both
     * are asked in the language the app speaks.
     */
    @Test
    fun theDoorIsAskedAsItExpects() {
        assertTrue(Engine.waitsItsTurn(listOf("pair", "--revoke")))
        assertFalse(Engine.waitsItsTurn(listOf("pair", "--list")))
        assertEquals(JsonPrimitive("cs"), Engine.request(listOf("pair", "--list"), lang = "cs")["lang"])
        assertEquals(JsonPrimitive("de"), Engine.request(listOf("pair", "--revoke"), lang = "de")["lang"])
        assertNull(Engine.request(listOf("props", "a"), lang = "cs")["lang"])
        assertFalse(Engine.changesTheBlog(listOf("pair", "--revoke")))
    }
}
