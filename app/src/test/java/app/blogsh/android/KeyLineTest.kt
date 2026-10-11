package app.blogsh.android

import app.blogsh.android.model.KeyLine
import app.blogsh.android.model.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line for `authorized_keys`. A mistake here is a server that does
 * not let the app in -- or lets the key run more than it should.
 */
class KeyLineTest {
    private val key = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIexample blogsh-app"
    private val word = "\"\$SSH_ORIGINAL_COMMAND\""

    @Test
    fun noLineUntilTheDirectoryIsKnown() {
        assertNull(KeyLine.compose(key, "", ""))
        assertNull(KeyLine.compose(key, "   ", "sudo docker exec -i blog"))
    }

    @Test
    fun aPlainPathStaysPlain() {
        assertEquals("""restrict,command="/home/dan/blog/scripts/remote.sh" $key""", KeyLine.compose(key, "/home/dan/blog", ""))
    }

    @Test
    fun aTrailingSlashOrTheScriptsOwnNameIsTheSameDirectory() {
        val plain = KeyLine.compose(key, "/home/dan/blog", "")
        assertEquals(plain, KeyLine.compose(key, "/home/dan/blog/", ""))
        assertEquals(plain, KeyLine.compose(key, "/home/dan/blog///", ""))
        assertEquals(plain, KeyLine.compose(key, "/home/dan/blog/scripts/remote.sh", ""))
        assertEquals(plain, KeyLine.compose(key, "  /home/dan/blog  ", ""))
    }

    /**
     * The forced command runs through the account's shell: a path with a
     * space in it is one word only inside quotes.
     */
    @Test
    fun aPathWithASpaceIsQuoted() {
        assertEquals("""restrict,command="'/Users/dan/My Blog/scripts/remote.sh'" $key""", KeyLine.compose(key, "/Users/dan/My Blog", ""))
    }

    @Test
    fun anApostropheInThePathIsClosedAndReopened() {
        assertEquals(
            """restrict,command="'/Users/dan/Dan'\''s Blog/scripts/remote.sh'" $key""",
            KeyLine.compose(key, "/Users/dan/Dan's Blog", ""),
        )
    }

    /**
     * Through the command that enters a container, the word SSH was asked
     * for is handed to the script as its argument -- the shape the
     * engine's docs/operations.md gives.
     */
    @Test
    fun throughAContainerTheWordIsHandedOver() {
        assertEquals(
            "restrict,command=\"sudo docker exec -i blog /app/blog/scripts/remote.sh \\\"\$SSH_ORIGINAL_COMMAND\\\"\" $key",
            KeyLine.compose(key, "/app/blog", "sudo docker exec -i blog"),
        )
    }

    /**
     * What the blog is reached through may carry quotes of its own; inside
     * the line's double quotes each has to be escaped, or sshd ends the
     * command there.
     */
    @Test
    fun doubleQuotesInTheWrapperAreEscaped() {
        val line = KeyLine.compose(key, "/app/blog", "sudo docker exec -i \$(sudo docker ps --filter \"name=^blog\$\" -q)")
        assertEquals(
            "restrict,command=\"sudo docker exec -i \$(sudo docker ps --filter \\\"name=^blog\$\\\" -q) /app/blog/scripts/remote.sh \\\"\$SSH_ORIGINAL_COMMAND\\\"\" $key",
            line,
        )
    }

    @Test
    fun theLineAlwaysRestrictsTheKeyAndEndsWithIt() {
        val line = KeyLine.compose(key, "/srv/blog", "env PATH=/opt/ruby/bin:/usr/bin")!!
        assertTrue(line.startsWith("restrict,command=\""))
        assertTrue(line.endsWith(" $key"))
    }

    /** The public half as ssh-keygen prints it: the type, the key in the wire's own frame, a comment. */
    @Test
    fun thePublicKeyIsWrittenAsSshKeygenWritesIt() {
        val line = KeyStore.publicKeyLine(ByteArray(32) { it.toByte() }, "blogsh-app")
        assertEquals("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhscHR4f blogsh-app", line)
    }

    /**
     * The key's fingerprint is the one ./blog.sh pair --list prints: the
     * SHA-256 of the key as it goes over the wire, in base64 with no
     * padding, after "SHA256:". A blog's own, and another's is another.
     */
    @Test
    fun aBlogsKeyIsKnownByTheFingerprintTheServerLists() {
        val seeds = mapOf("blog-a" to ByteArray(32) { it.toByte() }, "blog-b" to ByteArray(32) { (it + 1).toByte() })
        KeyStore.stand = { seeds[it] }
        try {
            val print = KeyStore.fingerprint("blog-a")
            val blob = java.util.Base64.getDecoder().decode(KeyStore.publicKeyLine("blog-a").split(" ")[1])
            val digest = java.util.Base64.getEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(blob))
            assertEquals("SHA256:$digest", print)
            assertEquals(7 + 43, print.length)
            // What ssh-keygen -lf says of the line the test above this one composes.
            assertEquals("SHA256:ZkAslGjFiUHdGf/WUL8rQvkib4PTvQatUV0OUQSncCA", KeyStore.fingerprintOf(KeyStore.publicKeyLine(ByteArray(32) { it.toByte() })))
            assertEquals(print, KeyStore.fingerprintOf(KeyStore.publicKeyLine("blog-a", "another comment")))
            assertTrue(KeyStore.fingerprint("blog-b") != print)
            for (none in listOf({ KeyStore.fingerprint("blog-none") }, { KeyStore.fingerprintOf("ssh-ed25519") }, { KeyStore.fingerprintOf("ssh-ed25519 %%% x") })) {
                try {
                    none()
                    org.junit.Assert.fail("a fingerprint of no key")
                } catch (e: KeyStore.NoKey) {
                    // As it should be.
                }
            }
        } finally {
            KeyStore.stand = null
        }
    }
}
