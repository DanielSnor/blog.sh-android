package app.blogsh.android.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/**
 * The code `./blog.sh pair` shows: where the blog is, which machine is
 * to answer there, and a key good once, for ten minutes, for one thing --
 * handing in the public half of a key this app made itself. It arrives
 * as a link, read by a camera or pasted as text:
 *
 *     blogsh://pair?v=1&h=<host>&p=<port>&u=<user>&k=<key>&f=<prints>&n=<site>
 *
 * Nothing of it is kept but where the blog is: the key in it opens
 * nothing once it has been used.
 */
class PairingCode(text: String) {
    val host: String
    val port: Int
    val user: String

    /** The 32 bytes the one-time key is made of. */
    val seed: ByteArray

    /**
     * The fingerprints of the server's own keys, as the code spells
     * them; none where the code names none.
     */
    val fingerprints: List<String>

    /** What the blog calls itself, to ask "connect to ...?" with. */
    val site: String?

    enum class Problem {
        /** Not a pairing code at all: another link, a sentence, nothing. */
        NotACode,

        /** A code of a kind this app does not know yet. */
        AnotherVersion,

        /** A pairing code with a part missing or unreadable. */
        Incomplete,
    }

    /** The text is not a code this app can use, and why. */
    class Unreadable(val problem: Problem) : Exception(problem.name)

    init {
        // Nothing the engine writes into a code is ever a space or a line
        // break: one found inside a pasted code was put there on the way
        // -- a mail that wraps, a note that breaks the line -- and is no
        // part of it.
        val link = text.filterNot { it.isWhitespace() }
        val scheme = link.indexOf("://")
        if (scheme <= 0 || !link.substring(0, scheme).equals("blogsh", ignoreCase = true)) throw Unreadable(Problem.NotACode)
        val rest = link.substring(scheme + 3)
        val place = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        if (!place.equals("pair", ignoreCase = true)) throw Unreadable(Problem.NotACode)
        val said = HashMap<String, String>()
        for (item in rest.substringAfter('?', "").substringBefore('#').split('&')) {
            if (item.isEmpty()) continue
            val name = unescaped(item.substringBefore('='))
            if (name !in said) said[name] = unescaped(item.substringAfter('=', ""))
        }
        if (said["v"] != "1") throw Unreadable(if (said["v"] == null) Problem.Incomplete else Problem.AnotherVersion)
        val host = said["h"]
        val user = said["u"]
        val port = said["p"]?.toIntOrNull()
        val seed = said["k"]?.let { bytes(it) }
        if (host.isNullOrEmpty() || user.isNullOrEmpty() || port == null || port !in 1..65_535 || seed == null || seed.size != 32) {
            throw Unreadable(Problem.Incomplete)
        }
        this.host = host
        this.port = port
        this.user = user
        this.seed = seed
        // A fingerprint is whole or it is not one: 43 characters, SHA-256
        // without its padding. A code cut off inside its last fingerprint
        // would otherwise be taken, and then turn its own server away as
        // another machine.
        val prints = (said["f"] ?: "").split('.').filter { it.isNotEmpty() }
        if (prints.any { !Regex("^[A-Za-z0-9_-]{43}$").matches(it) }) throw Unreadable(Problem.Incomplete)
        fingerprints = prints
        site = said["n"]?.takeIf { it.trim(' ', '\t').isNotEmpty() }
    }

    /**
     * The server that answered is one the code told the app to expect --
     * or the code named none, and the first one met is taken on trust,
     * as with a blog set up by hand.
     */
    fun expects(fingerprint: String): Boolean = fingerprints.isEmpty() || urlSafe(fingerprint) in fingerprints

    companion object {
        /**
         * A fingerprint as the code spells it: without its "SHA256:", without
         * padding, in the alphabet a link can carry.
         */
        fun urlSafe(fingerprint: String): String =
            fingerprint.removePrefix("SHA256:").replace('+', '-').replace('/', '_').trim('=')

        /**
         * The fingerprint of a key, as ssh prints it: SHA-256 over the key as
         * it goes over the wire.
         */
        fun fingerprint(ofKey: ByteArray): String =
            "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(ofKey))

        private fun bytes(urlSafe: String): ByteArray? {
            var standard = urlSafe.replace('-', '+').replace('_', '/')
            while (standard.length % 4 != 0) standard += "="
            return runCatching { Base64.getDecoder().decode(standard) }.getOrNull()
        }

        /**
         * A part of the link as it was meant. The engine writes the link
         * the way a form is sent: `%C5%AF` is one letter, a plus is a space,
         * and a plus that was meant is `%2B`.
         */
        private fun unescaped(part: String): String {
            if ('%' !in part && '+' !in part) return part
            val out = ByteArrayOutputStream()
            var i = 0
            while (i < part.length) {
                val c = part[i]
                val byte = if (c == '%' && i + 2 < part.length) part.substring(i + 1, i + 3).toIntOrNull(16) else null
                if (byte != null) {
                    out.write(byte)
                    i += 3
                } else {
                    out.write((if (c == '+') " " else c.toString()).toByteArray(Charsets.UTF_8))
                    i += 1
                }
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        }
    }
}

/**
 * What went wrong on the way in, as far as it can be told apart -- each
 * is a different thing to say to somebody holding a phone.
 */
sealed class PairingError : Exception() {
    /**
     * The code is spent: too old, or used already. The engine said so,
     * or the server would not even take its key.
     */
    data object Spent : PairingError()

    /** Another machine answered than the one the code describes. */
    data object WrongServer : PairingError()

    /**
     * Nobody answered there. What the network said of it is kept for
     * whoever reads a log, not shown: it is the library's own English.
     */
    data class Unreachable(val said: String) : PairingError()

    /** The engine said no for a reason of its own; its sentence, and the engine's word for the reason. */
    data class Refused(val words: String, val error: String? = null) : PairingError()

    /** The app could not make or read its own key. */
    data object NoKey : PairingError()

    companion object {
        @Serializable
        private class Answer(val ok: Boolean, val device: String? = null, val error: String? = null, val message: String? = null)

        /**
         * The engine's answer to `enroll`, read: the device's name as the
         * blog wrote it down, or what it said no with.
         */
        fun device(from: ByteArray): String {
            val said = runCatching { EngineJson.decodeFromString(Answer.serializer(), String(from, Charsets.UTF_8)) }.getOrNull()
                ?: throw Refused(String(from.copyOf(minOf(from.size, 300)), Charsets.UTF_8).trim())
            if (said.ok) return said.device ?: ""
            when (said.error) {
                "expired", "used", "unknown_code" -> throw Spent
                else -> throw Refused(said.message ?: said.error ?: "", said.error)
            }
        }
    }
}

/**
 * The exchange itself: connect with the code's key, make sure the right
 * machine answered, hand in the app's own public key. One connection of
 * its own, closed when it is over -- nothing of it is kept.
 */
object Pairing {
    /** What a pairing came to: the device's name as the blog wrote it down, and the fingerprint of the server that answered. */
    class Handed(val device: String, val server: String)

    /**
     * Which key a blog hands in, and what came of it. A blog the app
     * already has hands in the key it has: the server knows a device by
     * its key, and writes the new line in place of the old -- a list of
     * devices there stays a list of devices, not of attempts. Where that
     * key stands on a line that is not the blog's own -- one somebody
     * wrote by hand -- the server says so, and the blog is given a key
     * of its own; a refusal uses nothing of the code up. A new blog has
     * no key to hand in again.
     *
     * `own`: the name the blog's key is kept under, where it has one.
     * `fresh`: makes a key and says the name it is kept under.
     */
    fun <T> withItsKey(own: String?, fresh: () -> String, hand: (account: String) -> T): Pair<String, T> {
        if (own == null) return fresh().let { it to hand(it) }
        return try {
            own to hand(own)
        } catch (e: PairingError.Refused) {
            if (e.error != "key_in_use") throw e
            fresh().let { it to hand(it) }
        }
    }

    /**
     * The name a blog keeps for this device: the one the server wrote it
     * down under, and where the server said none, the device's own --
     * the one it was handed.
     */
    fun known(said: String, here: String): String = said.trim().ifEmpty { here.trim() }

    /** Blocks; called off the main thread. */
    fun handIn(publicKey: String, name: String, code: PairingCode): Handed {
        val trust = Trust(code)
        val ssh = SSHClient(DefaultConfig())
        // Somebody is standing there holding a phone: an address nobody
        // answers on is given up after ten seconds, not thirty.
        ssh.connectTimeout = 10_000
        ssh.timeout = 20_000
        ssh.addHostKeyVerifier(trust)
        try {
            try {
                ssh.connect(code.host, code.port)
                ssh.authPublickey(code.user, Engine.keyProvider(code.seed))
            } catch (e: UserAuthException) {
                // The server no longer takes the code's key: its line is gone.
                throw PairingError.Spent
            } catch (e: Exception) {
                // The check of the server's key fails the connection from inside.
                if (trust.refused) throw PairingError.WrongServer
                throw PairingError.Unreachable(e.toString())
            }
            val request = buildJsonObject {
                put("key", JsonPrimitive(publicKey))
                put("name", JsonPrimitive(name))
            }.toString() + "\n"
            val answer = try {
                ssh.startSession().use { session ->
                    val command = session.exec("enroll")
                    command.outputStream.use {
                        it.write(request.toByteArray(Charsets.UTF_8))
                        it.flush()
                    }
                    command.inputStream.readBytes()
                }
            } catch (e: Exception) {
                throw PairingError.Unreachable(e.toString())
            }
            return Handed(PairingError.device(answer), trust.seen)
        } finally {
            runCatching { ssh.disconnect() }
        }
    }

    /**
     * The server's key, checked against the code before anything is said
     * to the server, and remembered for the blog's later connections.
     */
    private class Trust(private val code: PairingCode) : HostKeyVerifier {
        @Volatile var seen = ""
            private set

        @Volatile var refused = false
            private set

        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val fingerprint = PairingCode.fingerprint(Buffer.PlainBuffer().putPublicKey(key).compactData)
            if (code.expects(fingerprint)) {
                seen = fingerprint
                return true
            }
            refused = true
            return false
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
    }
}
