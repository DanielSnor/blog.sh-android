package app.blogsh.android.model

import app.blogsh.android.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.Ed25519KeyFactory
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.util.Base64
import java.util.concurrent.TimeUnit

/** What the engine said no to, or what went wrong on the way to it. */
sealed class EngineError : Exception() {
    object NotConfigured : EngineError()
    object NoKey : EngineError()

    /**
     * The server let the connection in and turned the key away: its
     * account has no line for it.
     */
    object KeyNotKnown : EngineError()
    class HostKeyChanged(val fingerprint: String) : EngineError()
    class Refused(val refusal: Refusal) : EngineError()
    class Unreadable(val text: String) : EngineError()

    /** Where on the way to the engine it broke, for the message that says so. */
    class Stage(val stage: String, val error: Throwable) : EngineError()

    override val message: String
        get() {
            return when (this) {
                NotConfigured -> Spoken.say(R.string.the_server_is_not_set_up_yet)
                NoKey -> Spoken.say(R.string.the_app_has_no_key_yet)
                KeyNotKnown -> Spoken.say(R.string.the_server_does_not_know_this_blog)
                is HostKeyChanged -> Spoken.say(R.string.the_server_s_key_changed_if_the, fingerprint)
                // The engine’s own sentence here speaks of --yes and of a screen the
                // terminal has; on a phone neither is anything one can do.
                is Refused -> if (refusal.error == "ambiguous_slug") Spoken.say(R.string.two_posts_in_different_years_share_this) else refusal.message
                is Unreadable -> Spoken.say(R.string.the_engine_did_not_answer_as_data, text)
                is Stage -> "$stage: ${error.message ?: error.javaClass.simpleName}"
            }
        }
}

/** One file of a delivery: a bare name and its bytes. */
class DeliveryFile(val name: String, val data: ByteArray)

/**
 * The server's key is remembered the first time it is seen and has to be
 * the same every time after -- what ssh itself does with known_hosts.
 */
class TrustOnFirstUse(private val host: String, private val port: Int, private val notes: Notes = BlogShelf.notes) : HostKeyVerifier {
    var changed: String? = null
        private set

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
        val name = notesKey(host, this.port)
        val known = notes.read(name)
        if (known == null) {
            notes.write(name, fingerprint)
            return true
        }
        if (known == fingerprint) return true
        changed = fingerprint
        return false
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()

    companion object {
        fun notesKey(host: String, port: Int) = "hostkey.$host:$port"
        fun forget(host: String, port: Int, notes: Notes = BlogShelf.notes) = notes.write(notesKey(host, port), null)
        fun known(host: String, port: Int, notes: Notes = BlogShelf.notes): String? = notes.read(notesKey(host, port))
    }
}

/**
 * One engine command, run on the server through the app's key and the
 * forced command there (scripts/remote.sh, `run`): the argv goes over as
 * one line of JSON, the answer comes back as one object. Nothing here is
 * a shell; the server checks every word before the engine sees it.
 *
 * Every command is a channel of its own on one connection, which is kept
 * between them (`Line`) rather than opened for each.
 */
object Engine {
    /**
     * Android carries a cut-down BouncyCastle under the name "BC"; the SSH
     * library asks for "BC" by name and needs the whole one (Ed25519,
     * X25519). The provider the app brings takes the name over, once.
     */
    fun installCrypto() {
        if (Security.getProvider("BC")?.javaClass == BouncyCastleProvider::class.java) return
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
    }

    /**
     * Runs `./blog.sh <args>` and decodes its answer, or throws the
     * refusal the engine gave -- which is an answer too, just a no.
     */
    suspend inline fun <reified T> call(args: List<String>): T = decode(run(args))

    suspend inline fun <reified T> call(vararg args: String): T = call(args.toList())

    /** One answer of a batch, read as `call` reads its own. */
    inline fun <reified T> decode(data: ByteArray): T {
        val element = element(data)
        return try {
            EngineJson.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), element)
        } catch (e: Exception) {
            throw EngineError.Unreadable(String(data, Charsets.UTF_8).take(300).ifEmpty { e.toString() })
        }
    }

    /** The answer as an object, or the refusal it is. */
    fun element(data: ByteArray): JsonElement {
        if (data.isEmpty()) {
            throw EngineError.Unreadable(Spoken.say(R.string.the_server_closed_the_connection_without_an))
        }
        val text = String(data, Charsets.UTF_8)
        val element = try {
            EngineJson.parseToJsonElement(text)
        } catch (e: Exception) {
            throw EngineError.Unreadable(text.take(300).ifEmpty { e.toString() })
        }
        if (element is JsonObject && (element["ok"] as? JsonPrimitive)?.content == "false") {
            runCatching { EngineJson.decodeFromJsonElement(Refusal.serializer(), element) }.getOrNull()?.let {
                throw EngineError.Refused(it)
            }
        }
        return element
    }

    suspend fun run(args: List<String>): ByteArray = batch(listOf(args))[0]

    /**
     * Several commands over ONE connection, one after another, an answer
     * for each. A screen that needs three things asks for them here: a
     * connection is the expensive part -- a handshake each -- and a
     * server that counts connections (a firewall's rate limit does) turns
     * the fourth one away. A command that fails ends the batch; what was
     * answered before it is lost with it.
     */
    /**
     * In a test, what stands in for the server: a command's words in, the
     * engine's answer out. Nothing sets it in the app.
     */
    @Volatile
    internal var stand: ((List<String>) -> ByteArray)? = null

    suspend fun batch(commands: List<List<String>>): List<ByteArray> = stand?.let { answer -> commands.map(answer) } ?: onTheLine { ssh, wary ->
        val answers = mutableListOf<ByteArray>()
        for (args in commands) {
            // Only the first can find the connection dead without having
            // said anything; after it, the connection has just been heard from.
            answers.add(exec(args, ssh, wary = wary && answers.isEmpty(), said = answers.isNotEmpty()))
        }
        answers
    }

    /**
     * A delivery: the files, pictures first and the markdown last, in the
     * receiver's frame (a name, its base64, a line with a dot), ended by a
     * line saying `end` -- scripts/remote.sh's `deliver`. One answer per
     * file comes back; the last is the engine's own for the markdown.
     */
    suspend fun deliver(files: List<DeliveryFile>): List<ByteArray> = onTheLine { ssh, wary -> send(files, ssh, wary) }

    // ---- The connection

    private val wires = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Kept for five minutes after its last call: somebody reading through
     * the app asks the server every few seconds, and a server that counts
     * connections stops answering the tenth. See `Line`.
     */
    val line = Line<SSHClient>(
        keep = 300_000, scope = wires,
        open = { door -> connect(door) },
        close = { ssh -> runCatching { ssh.disconnect() } },
    )

    /**
     * Lets go of the kept connection: the app left the screen, or what a
     * connection is opened with was changed.
     */
    fun hangUp() {
        wires.launch { line.drop() }
    }

    /**
     * The connection failed before the call had said anything on it, so
     * nothing was done on the server and the call can be made again.
     */
    private class NothingSaid(val reason: Throwable) : Exception()

    /**
     * Runs `work` on the kept connection, opening one where none is kept.
     * A kept connection can be dead without anybody knowing -- the network
     * changed under it, a router forgot it: if it fails before the call
     * said anything, the call is made once more on a new one. A call that
     * had begun to speak is never repeated; whether a publish arrived is
     * not something to guess at.
     */
    private suspend fun <T> onTheLine(work: (SSHClient, Boolean) -> T): T = withContext(Dispatchers.IO) { ride(work) }

    private suspend fun <T> ride(work: (SSHClient, Boolean) -> T): T {
        val settings = ServerSettings.load() ?: throw EngineError.NotConfigured
        val door = Door(settings.host, settings.port, settings.user, settings.keyAccount)
        var again = true
        while (true) {
            val hold = line.take(door)
            try {
                val result = work(hold.wire, hold.rested)
                line.give(hold)
                return result
            } catch (nothing: NothingSaid) {
                line.give(hold, broken = true)
                if (!again) throw EngineError.Stage("exec (0 bytes so far)", nothing.reason)
                again = false
            } catch (e: Throwable) {
                // Whatever broke, this is not a connection to hand on --
                // except where the engine itself answered and the close
                // after it complained, which never comes here.
                line.give(hold, broken = true)
                throw e
            }
        }
    }

    private fun connect(door: Door): SSHClient {
        val seed = try {
            KeyStore.seed(door.keyAccount)
        } catch (e: Exception) {
            throw EngineError.NoKey
        }
        val trust = TrustOnFirstUse(door.host, door.port)
        val ssh = SSHClient(DefaultConfig())
        ssh.connectTimeout = 15_000
        ssh.timeout = 0
        ssh.addHostKeyVerifier(trust)
        try {
            ssh.connect(door.host, door.port)
            ssh.authPublickey(door.user, provider(seed))
        } catch (e: Exception) {
            runCatching { ssh.disconnect() }
            if (e is UserAuthException) throw EngineError.KeyNotKnown
            if (e is CancellationException) throw e
            trust.changed?.let { throw EngineError.HostKeyChanged(it) }
            throw EngineError.Stage("connect", e)
        }
        return ssh
    }

    /**
     * Watches a call's first step on a connection that has lain unused:
     * one that has not let the call in after ten seconds is closed, which
     * ends the wait -- and the call is made again on a new one.
     */
    private class Watch(ssh: SSHClient, wary: Boolean, scope: CoroutineScope) {
        @Volatile
        private var done = false
        private val waiting: Job? = if (!wary) null else scope.launch {
            delay(10_000)
            if (!done) runCatching { ssh.disconnect() }
        }

        /** The call was let in: the connection lives. */
        fun letIn() {
            done = true
            waiting?.cancel()
        }
    }

    /**
     * `said`: an earlier command of the same batch has already run on
     * this connection, so a failure here is not one to start over from.
     */
    private fun exec(args: List<String>, ssh: SSHClient, wary: Boolean, said: Boolean): ByteArray {
        val request = buildJsonObject { put("args", JsonArray(args.map { JsonPrimitive(it) })) }.toString() + "\n"
        var answer = ByteArray(0)
        var stage = "exec"
        val watch = Watch(ssh, wary, wires)
        try {
            ssh.startSession().use { session ->
                val command = session.exec("run")
                watch.letIn()
                stage = "write"
                command.outputStream.write(request.toByteArray(Charsets.UTF_8))
                command.outputStream.flush()
                stage = "read"
                answer = command.inputStream.readBytes()
                stage = "close"
                command.join(30, TimeUnit.SECONDS)
            }
        } catch (e: Exception) {
            watch.letIn()
            // The answer may be whole even when the close after it complains.
            if (stage == "close" && answer.isNotEmpty()) return answer
            if (stage == "exec" && !said) throw NothingSaid(e)
            throw EngineError.Stage("$stage (${answer.size} bytes so far)", e)
        }
        return answer
    }

    private fun send(files: List<DeliveryFile>, ssh: SSHClient, wary: Boolean): List<ByteArray> {
        var output = ByteArray(0)
        var stage = "exec"
        val watch = Watch(ssh, wary, wires)
        try {
            ssh.startSession().use { session ->
                val command = session.exec("deliver")
                watch.letIn()
                stage = "write"
                val out = command.outputStream
                // 76 columns and a newline, the way base64 is written on
                // a wire; the receiver strips the breaks before decoding.
                val encoder = Base64.getMimeEncoder(76, "\n".toByteArray())
                for (file in files) {
                    out.write((file.name + "\n").toByteArray(Charsets.UTF_8))
                    out.write(encoder.encode(file.data))
                    out.write("\n.\n".toByteArray())
                }
                out.write("end\n".toByteArray())
                out.flush()
                stage = "read"
                output = command.inputStream.readBytes()
                stage = "close"
                command.join(30, TimeUnit.SECONDS)
            }
        } catch (e: Exception) {
            watch.letIn()
            if (stage == "close" && output.isNotEmpty()) return objects(output)
            if (stage == "exec") throw NothingSaid(e)
            throw EngineError.Stage("$stage (${output.size} bytes so far)", e)
        }
        return objects(output)
    }

    /**
     * The answers as they came: one-line receipts for the pictures, the
     * engine's pretty-printed object for the markdown. Split where a line
     * opens a new object, each piece checked to be one.
     */
    fun objects(output: ByteArray): List<ByteArray> {
        fun whole(text: String): Boolean = runCatching { EngineJson.parseToJsonElement(text) is JsonObject }.getOrDefault(false)
        val found = mutableListOf<ByteArray>()
        val current = StringBuilder()
        for (line in String(output, Charsets.UTF_8).split("\n")) {
            if (line.startsWith("{") && current.isNotEmpty() && whole(current.toString())) {
                found.add(current.toString().toByteArray(Charsets.UTF_8))
                current.clear()
            }
            current.append(line).append("\n")
        }
        if (whole(current.toString())) found.add(current.toString().toByteArray(Charsets.UTF_8))
        return found
    }

    private fun provider(seed: ByteArray): KeyProvider = keyProvider(seed)

    /** An ed25519 key as the SSH library asks for one, from the 32 bytes it is made of. */
    internal fun keyProvider(seed: ByteArray): KeyProvider = object : KeyProvider {
        override fun getPrivate(): PrivateKey = Ed25519KeyFactory.getPrivateKey(seed)
        override fun getPublic(): PublicKey = Ed25519KeyFactory.getPublicKey(KeyStore.publicBytes(seed))
        override fun getType(): KeyType = KeyType.ED25519
    }
}

/**
 * A call the screen itself called off -- it went away, or asked again.
 * Nobody is left to tell, so a screen keeps what it was saying.
 */
val Throwable.isCalledOff: Boolean get() = this is CancellationException

/** What to tell a person about a failure. */
val Throwable.said: String get() = message ?: javaClass.simpleName
