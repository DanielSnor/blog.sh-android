package app.blogsh.android

import app.blogsh.android.model.Blog
import app.blogsh.android.model.BlogList
import app.blogsh.android.model.Pairing
import app.blogsh.android.model.PairingCode
import app.blogsh.android.model.PairingError
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

/**
 * The code `./blog.sh pair` shows, read by the app. A mistake here is a
 * key handed to the wrong machine, or a good code turned away.
 */
class PairingTest {
    /** 32 bytes, 0...31, in the alphabet a link can carry and without padding. */
    private val key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"

    private fun link(change: (MutableMap<String, String?>) -> Unit = {}): String {
        val parts = mutableMapOf<String, String?>(
            "v" to "1", "h" to "blog.example.org", "p" to "2222", "u" to "me", "k" to key,
            "f" to "abc-DEF_123AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA.zzzZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ", "n" to "M%C5%AFj%20blog",
        )
        change(parts)
        return "blogsh://pair?" + listOf("v", "h", "p", "u", "k", "f", "n").mapNotNull { name -> parts[name]?.let { "$name=$it" } }.joinToString("&")
    }

    private fun problem(text: String): PairingCode.Problem? =
        try {
            PairingCode(text)
            null
        } catch (e: PairingCode.Unreadable) {
            e.problem
        }

    @Test
    fun aCodeIsReadWhole() {
        val code = PairingCode(link())
        assertEquals("blog.example.org", code.host)
        assertEquals(2222, code.port)
        assertEquals("me", code.user)
        assertArrayEquals(ByteArray(32) { it.toByte() }, code.seed)
        assertEquals(listOf("abc-DEF_123AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "zzzZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"), code.fingerprints)
        assertEquals("Můj blog", code.site)
    }

    /** Pasted with a line break after it, scanned in another case of its scheme. */
    @Test
    fun whatSurroundsTheLinkDoesNotMatter() {
        assertEquals("blog.example.org", PairingCode("  \n" + link() + "\n").host)
        assertEquals(2222, PairingCode(link().replace("blogsh://pair", "BLOGSH://PAIR")).port)
    }

    @Test
    fun theFingerprintsAndTheNameMayBeMissing() {
        val code = PairingCode(link { it["f"] = null; it["n"] = null })
        assertTrue(code.fingerprints.isEmpty())
        assertNull(code.site)
        assertNull(PairingCode(link { it["n"] = "%20" }).site)
    }

    @Test
    fun anAddressOfAnyKindIsAnAddress() {
        assertEquals("192.168.1.20", PairingCode(link { it["h"] = "192.168.1.20" }).host)
        assertEquals("fe80::1", PairingCode(link { it["h"] = "fe80%3A%3A1" }).host)
    }

    @Test
    fun whatIsNotACodeIsNotOne() {
        for (text in listOf("", "ahoj", "https://example.org/pair?v=1", "blogsh://open?v=1&h=x", "blogsh:pair")) {
            assertEquals(text, PairingCode.Problem.NotACode, problem(text))
        }
    }

    /**
     * A kind of code the app has not heard of is said to be that -- not
     * read as if it were the kind it knows.
     */
    @Test
    fun aCodeOfAnotherVersionIsSaidToBeOne() {
        assertEquals(PairingCode.Problem.AnotherVersion, problem(link { it["v"] = "2" }))
        assertEquals(PairingCode.Problem.Incomplete, problem(link { it["v"] = null }))
    }

    @Test
    fun aCodeWithAPartMissingIsIncomplete() {
        for (part in listOf("h", "p", "u", "k")) {
            assertEquals(part, PairingCode.Problem.Incomplete, problem(link { it[part] = null }))
        }
        assertEquals(PairingCode.Problem.Incomplete, problem(link { it["p"] = "0" }))
        assertEquals(PairingCode.Problem.Incomplete, problem(link { it["p"] = "70000" }))
        assertEquals(PairingCode.Problem.Incomplete, problem(link { it["p"] = "ssh" }))
        // A key of another length is no key of this kind.
        assertEquals(PairingCode.Problem.Incomplete, problem(link { it["k"] = "AAECAwQ" }))
        assertEquals(PairingCode.Problem.Incomplete, problem(link { it["k"] = "!!!" }))
    }

    /**
     * The engine writes the link the way a form is sent (Ruby's
     * `URI.encode_www_form`): a space is a plus, a plus that was meant is
     * `%2B`. A blog called "Můj blog" is not "Můj+blog".
     */
    @Test
    fun aPartOfTheLinkIsReadAsTheEngineWroteIt() {
        assertEquals("Můj blog a+b", PairingCode(link { it["n"] = "M%C5%AFj+blog+a%2Bb" }).site)
        assertEquals("me+you", PairingCode(link { it["u"] = "me%2Byou" }).user)
        assertEquals("100%", PairingCode(link { it["n"] = "100%" }).site)
        assertEquals("Žluťoučký kůň", PairingCode(link { it["n"] = "%C5%BDlu%C5%A5ou%C4%8Dk%C3%BD%20k%C5%AF%C5%88" }).site)
        // The first of two is the one that counts.
        assertEquals("Můj blog", PairingCode(link() + "&n=second").site)
        assertEquals("me", PairingCode(link() + "&u=somebody-else").user)
    }

    /** As ssh prints a fingerprint, and as the code spells the same one. */
    @Test
    fun aFingerprintIsComparedInTheCodesSpelling() {
        assertEquals("ab-_cd", PairingCode.urlSafe("SHA256:ab+/cd=="))
        assertEquals("ab-_cd", PairingCode.urlSafe("ab-_cd"))
        val code = PairingCode(link { it["f"] = "ab-_cdBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB.otherOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO" })
        assertTrue(code.expects("SHA256:ab+/cdBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"))
        assertTrue(code.expects("SHA256:otherOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO"))
        assertFalse(code.expects("SHA256:somebody+else"))
    }

    /**
     * A code that names no server takes the first one on trust, as a
     * blog set up by hand does.
     */
    @Test
    fun aCodeWithoutFingerprintsExpectsAnyServer() {
        assertTrue(PairingCode(link { it["f"] = null }).expects("SHA256:whatever"))
    }

    /** The fingerprint of a key is the one ssh-keygen prints for it. */
    @Test
    fun theFingerprintOfAKeyIsSshsOwn() {
        // ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJZ2lFQ0Nlp7tkPAqXT0k5i1a1xPB2SQXQv7fmM2U1bV
        val blob = Base64.getDecoder().decode("AAAAC3NzaC1lZDI1NTE5AAAAIJZ2lFQ0Nlp7tkPAqXT0k5i1a1xPB2SQXQv7fmM2U1bV")
        val print = PairingCode.fingerprint(blob)
        assertTrue(print.startsWith("SHA256:"))
        assertEquals(7 + 43, print.length)
        assertFalse(print.contains("="))
    }

    // ---- The engine's answer

    @Test
    fun theEnginesYesNamesTheDevice() {
        assertEquals("Opravdový telefon", PairingError.device("""{"ok": true, "device": "Opravdový telefon"}""".toByteArray()))
    }

    private fun refusal(answer: String): PairingError? =
        try {
            PairingError.device(answer.toByteArray())
            null
        } catch (e: PairingError) {
            e
        }

    /**
     * Too old, used already, or a code the blog never heard of: all the
     * same to somebody holding a phone -- ask for a new one.
     */
    @Test
    fun aSpentCodeIsSaidToBeSpent() {
        for (word in listOf("expired", "used", "unknown_code")) {
            assertEquals(word, PairingError.Spent, refusal("""{"ok": false, "error": "$word", "message": "…"}"""))
        }
    }

    @Test
    fun anotherNoCarriesTheEnginesSentence() {
        assertEquals(
            PairingError.Refused("That is not one ed25519 public key.", "bad_key"),
            refusal("""{"ok": false, "error": "bad_key", "message": "That is not one ed25519 public key."}"""),
        )
        assertEquals(PairingError.Refused("bash: no such file"), refusal("bash: no such file\n"))
    }

    // ---- The blog it leaves behind

    /**
     * Where a blog is, under its name: with its port where the port is
     * not ssh's usual one.
     */
    @Test
    fun whereABlogIsSaysItsPortWhenItIsNotTheUsualOne() {
        var blog = Blog()
        assertEquals("", blog.place)
        blog = blog.copy(host = "blog.example")
        assertEquals("blog.example", blog.place)
        blog = blog.copy(user = "me")
        assertEquals("me@blog.example", blog.place)
        blog = blog.copy(port = 202)
        assertEquals("me@blog.example:202", blog.place)
        blog = blog.copy(host = "10.0.0.5", port = 2222)
        assertEquals("me@10.0.0.5:2222", blog.place)
        blog = blog.copy(host = "fe80::1")
        assertEquals("me@[fe80::1]:2222", blog.place)
        blog = blog.copy(port = 22)
        assertEquals("me@fe80::1", blog.place)
        // A port never set is the usual one.
        blog = blog.copy(host = "blog.example", port = 0)
        assertEquals("me@blog.example", blog.place)
    }

    /** A blog let in by a code comes whole, is the open one at once, and is there the next time the app starts. */
    @Test
    fun aBlogLetInByACodeIsTheOpenOneAtOnce() {
        val notes = MemoryNotes()
        val list = BlogList(notes) { }
        val first = list.add()
        val paired = Blog(host = "10.0.0.5", port = 2222, user = "me", name = "Můj blog")
        list.adopt(paired)
        assertEquals(paired.id, list.currentId)
        assertEquals(listOf(first.id, paired.id), list.all.map { it.id })
        val again = BlogList(notes) { }
        assertEquals(paired, again.current)
    }

    /**
     * A code cut off inside its fingerprint is not whole -- taken, it
     * would turn its own server away as another machine.
     */
    @Test
    fun aCodeCutInsideItsFingerprintIsNotWhole() {
        val whole = link { it["n"] = null }
        assertEquals(PairingCode.Problem.Incomplete, problem(whole.dropLast(20)))
        assertEquals(PairingCode.Problem.Incomplete, problem(link { it["f"] = "short" }))
        assertEquals(PairingCode.Problem.Incomplete, problem(link { it["f"] = "a".repeat(42) + "!" }))
        assertEquals(2, PairingCode(whole).fingerprints.size)
    }

    /**
     * A line break or a space inside a pasted code was put there on the
     * way, and is no part of it: the code reads as it was written.
     */
    @Test
    fun aLineBreakInsideAPastedCodeIsNoPartOfIt() {
        val good = PairingCode(link())
        val text = link()
        fun same(code: PairingCode?): Boolean = code != null && code.host == good.host && code.port == good.port && code.user == good.user &&
            code.seed.contentEquals(good.seed) && code.fingerprints == good.fingerprints && code.site == good.site
        for (at in 14 until text.length step 9) {
            val broken = text.substring(0, at) + "\n " + text.substring(at)
            assertTrue("a break at $at", same(runCatching { PairingCode(broken) }.getOrNull()))
        }
        assertTrue(same(runCatching { PairingCode("  \n" + text + "\n") }.getOrNull()))
    }

    // ---- A blog the app already has, let in again

    private fun inUse() = PairingError.Refused("The key stands on another line.", "key_in_use")

    /**
     * A blog the app has hands in the key it has: the server knows a
     * device by its key and writes the new line in place of the old.
     */
    @Test
    fun aBlogHandsInTheKeyItHas() {
        var made = 0
        val handed = mutableListOf<String>()
        val (account, answer) = Pairing.withItsKey(own = "blog-old", fresh = { made += 1; "blog-new" }) { name ->
            handed.add(name)
            "taken"
        }
        assertEquals("blog-old", account)
        assertEquals("taken", answer)
        assertEquals(listOf("blog-old"), handed)
        assertEquals(0, made)
    }

    /**
     * Where that key stands on a line that is not the blog's own -- one
     * written by hand -- the blog is given a key of its own, and the same
     * code takes it: a refusal uses nothing up.
     */
    @Test
    fun aKeyOnALineOfSomebodyElsesIsReplacedByANewOne() {
        val handed = mutableListOf<String>()
        val (account, _) = Pairing.withItsKey(own = "blog-old", fresh = { "blog-new" }) { name ->
            handed.add(name)
            if (name == "blog-old") throw inUse()
            "taken"
        }
        assertEquals("blog-new", account)
        assertEquals(listOf("blog-old", "blog-new"), handed)
    }

    /** Any other no is the answer: no second key is made to try again with. */
    @Test
    fun anotherRefusalIsNotTriedAgain() {
        var made = 0
        val said = runCatching {
            Pairing.withItsKey<String>(own = "blog-old", fresh = { made += 1; "blog-new" }) { throw PairingError.Refused("No.", "bad_key") }
        }.exceptionOrNull()
        assertEquals(PairingError.Refused("No.", "bad_key"), said)
        assertEquals(0, made)
        assertEquals(PairingError.Spent, runCatching { Pairing.withItsKey<String>("blog-old", { made += 1; "x" }) { throw PairingError.Spent } }.exceptionOrNull())
        assertEquals(0, made)
        // A new key the server will not take either is said, not tried a third time.
        var tries = 0
        assertEquals(inUse(), runCatching { Pairing.withItsKey<String>("blog-old", { "blog-new" }) { tries += 1; throw inUse() } }.exceptionOrNull())
        assertEquals(2, tries)
    }

    /** A new blog has no key to hand in again: one is made for it. */
    @Test
    fun aNewBlogIsGivenAKey() {
        var made = 0
        val (account, _) = Pairing.withItsKey(own = null, fresh = { made += 1; "blog-new" }) { "taken" }
        assertEquals("blog-new", account)
        assertEquals(1, made)
    }

    /**
     * The blog keeps the name the server wrote the device down under; a
     * server that said none leaves the device its own.
     */
    @Test
    fun aDeviceTheServerDidNotNameKeepsItsOwnName() {
        assertEquals("SeanoPad", Pairing.known("SeanoPad", "Pixel"))
        assertEquals("Pixel", Pairing.known("", "Pixel"))
        assertEquals("Pixel", Pairing.known("  \n", " Pixel "))
        assertEquals("", Pairing.known("", ""))
    }

    /**
     * The key is on a line of the server's that is not a device's: said
     * as that, apart from every other no -- the code is still good, and
     * a new key can go in with it.
     */
    @Test
    fun aKeyThatIsInUseIsSaidApartFromOtherRefusals() {
        assertEquals(
            PairingError.Refused("This key already stands in authorized_keys.", "key_in_use"),
            refusal("""{"ok":false,"error":"key_in_use","message":"This key already stands in authorized_keys."}"""),
        )
        assertEquals(PairingError.Spent, refusal("""{"ok":false,"error":"used"}"""))
    }
}
