package app.blogsh.android.model

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.blogsh.android.BlogshApp
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where the open blog is: the machine, the account, the port, and which
 * key reaches it. The path to the engine is not what the app connects
 * by, on purpose -- the key's forced command on the server fixes it
 * (scripts/remote.sh), so the app cannot be pointed anywhere else.
 */
data class ServerSettings(
    val host: String, val port: Int, val user: String, val keyAccount: String,
    /** The blog was let in by a code: what is said of a key the server does not know depends on it. */
    val paired: Boolean = false,
) {
    companion object {
        /**
         * `only`: the blog the call is meant for. Another one open by now
         * is nothing to connect to -- what was written for one blog is
         * never sent to the next.
         */
        fun load(from: Notes = BlogShelf.notes, only: String? = null): ServerSettings? {
            val blog = BlogShelf.current(from) ?: return null
            if (only != null && blog.id != only) return null
            val (host, port) = blog.reached
            val user = blog.user.trim()
            if (host.isEmpty() || user.isEmpty()) return null
            return ServerSettings(host, port, user, blog.keyAccount, paired = blog.pairedAs != null)
        }
    }
}

/**
 * A blog's SSH key: an ed25519 seed made on this device and never leaving
 * it. The seed lies in the app's own files, encrypted with a key that
 * lives in the Android Keystore -- the counterpart of the keychain item on
 * iOS -- and the app's files are kept out of every backup. The public
 * half is what goes into the server's authorized_keys, in front of the
 * forced command. One to a blog, each under its own name.
 */
object KeyStore {
    class NoKey : Exception()

    private fun file(account: String) = File(File(BlogshApp.context.filesDir, "keys").apply { mkdirs() }, "$account.bin")
    private fun alias(account: String) = "blogsh-wrap-$account"

    fun hasKey(account: String): Boolean = file(account).exists()

    /** The 32 bytes the key is made from. */
    fun seed(account: String): ByteArray {
        val file = file(account)
        if (!file.exists()) throw NoKey()
        val all = file.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(account), GCMParameterSpec(128, all, 0, 12))
        return cipher.doFinal(all, 12, all.size - 12)
    }

    /** A new key, replacing the old one: the server has to be told again. */
    fun makeKey(account: String): ByteArray {
        deleteKey(account)
        val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey(account))
        file(account).writeBytes(cipher.iv + cipher.doFinal(seed))
        return seed
    }

    fun deleteKey(account: String) {
        file(account).delete()
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.runCatching { deleteEntry(alias(account)) }
    }

    fun publicBytes(seed: ByteArray): ByteArray = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded

    /** The line for authorized_keys, as ssh-keygen would print it. */
    fun publicKeyLine(account: String, comment: String = "blogsh-app"): String = publicKeyLine(publicBytes(seed(account)), comment)

    fun publicKeyLine(public: ByteArray, comment: String = "blogsh-app"): String {
        val wire = ByteArrayOutputStream()
        fun sshString(bytes: ByteArray) {
            wire.write(ByteBuffer.allocate(4).putInt(bytes.size).array())
            wire.write(bytes)
        }
        sshString("ssh-ed25519".toByteArray())
        sshString(public)
        return "ssh-ed25519 " + Base64.getEncoder().encodeToString(wire.toByteArray()) + " " + comment
    }

    private fun wrappingKey(account: String): SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias(account), null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias(account), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}

/**
 * The line for the account's `~/.ssh/authorized_keys`: the key, and in
 * front of it the forced command that is all the key may run -- one
 * blog's `scripts/remote.sh`.
 */
object KeyLine {
    private const val SCRIPT = "/scripts/remote.sh"

    /**
     * Null until the blog's directory is known: a made-up path in the line
     * is a line somebody copies. A directory given with the script's own
     * name on its end, or with a slash, is the same directory. A path the
     * shell would take apart is quoted. With a command the blog is reached
     * through, the word SSH was asked for does not reach the script by
     * itself, so it is handed over as its argument.
     */
    fun compose(publicKey: String, path: String, through: String): String? {
        var directory = path.trim(' ', '\t')
        if (directory.endsWith(SCRIPT)) directory = directory.dropLast(SCRIPT.length)
        while (directory.length > 1 && directory.endsWith("/")) directory = directory.dropLast(1)
        if (directory.isEmpty()) return null
        val script = directory + SCRIPT
        var safe = true
        var i = 0
        while (i < script.length) {
            val point = script.codePointAt(i)
            if (!(Character.isLetterOrDigit(point) || Character.getType(point) == Character.NON_SPACING_MARK.toInt() || "/._-+@:".indexOf(point.toChar()) >= 0 && point < 128)) safe = false
            i += Character.charCount(point)
        }
        val quoted = if (safe) script else "'" + script.replace("'", "'\\''") + "'"
        val wrapper = through.trim(' ', '\t')
        val command = if (wrapper.isEmpty()) quoted else "$wrapper $quoted \"\$SSH_ORIGINAL_COMMAND\""
        return "restrict,command=\"" + command.replace("\"", "\\\"") + "\" " + publicKey
    }
}
