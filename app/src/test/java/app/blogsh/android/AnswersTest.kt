package app.blogsh.android

import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.HeldAnswer
import app.blogsh.android.model.Refusal
import app.blogsh.android.model.ServerPaths
import app.blogsh.android.model.StatsAnswer
import app.blogsh.android.model.VersionAnswer
import app.blogsh.android.model.plain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * What the engine answers, read: the shapes are its contract
 * (`--json`), and the app must take them as they come.
 */
class AnswersTest {
    @Before
    fun words() = English.speak()

    private fun version(claim: String, accent: Boolean = true): ByteArray {
        val colours = if (accent) ""","accent":{"light":"#1da1f2","dark":"#4ab3f4"}""" else ""
        return """{"ok":true,"engine":"1.10.pre","max_mb":24,"site":{"name":"./blog.sh","claim":"$claim","url":"https://blogsh.app","lang":"en","locales":["en","cs"]$colours}}"""
            .toByteArray()
    }

    @Test
    fun theIdentityIsRead() {
        val answer = Engine.decode<VersionAnswer>(version("just a blog"))
        assertEquals("1.10.pre", answer.engine)
        assertEquals(24, answer.maxMb)
        assertEquals("./blog.sh", answer.site.name)
        assertEquals("just a blog", answer.site.claim)
        assertEquals(listOf("en", "cs"), answer.site.locales)
        assertEquals("#1da1f2", answer.site.accent?.light)
        assertEquals("#4ab3f4", answer.site.accent?.dark)
    }

    /**
     * A claim is markdown in the site's configuration; broken the markdown
     * way, with a backslash at the end of a line, it is its two lines here.
     */
    @Test
    fun aClaimBrokenWithABackslashIsItsLines() {
        val answer = Engine.decode<VersionAnswer>(version("""just ./blog.sh\\\nno database"""))
        assertEquals("just ./blog.sh\nno database", answer.site.claim)
    }

    @Test
    fun aClaimBrokenWithTwoSpacesIsItsLines() {
        assertEquals("one\ntwo", Engine.decode<VersionAnswer>(version("""one  \ntwo""")).site.claim)
    }

    @Test
    fun emptyLinesOfAClaimAreDropped() {
        assertEquals("one\ntwo", Engine.decode<VersionAnswer>(version("""one\n\n  \ntwo\n""")).site.claim)
    }

    /** An engine from before it said its accent: the app falls back to its own. */
    @Test
    fun anEngineWithoutAnAccentIsStillRead() {
        assertNull(Engine.decode<VersionAnswer>(version("x", accent = false)).site.accent)
    }

    /**
     * `stats --json` has no `ok` of its own and far more than the first
     * screen says; what it says is read, the rest let be.
     */
    @Test
    fun theArchiveCountedIsRead() {
        val json = """
        {"posts":{"total":6653,"published":6649,"drafts":2,"scheduled":2,"pages":0},
         "span":{"first":"2003-10-31","last":"2026-10-08","days":8378,"busiest_year":{"year":"2024","posts":642}},
         "years":{"2003":23},"types":{"text":4815},
         "words":{"total":415207,"mean":62.4,"median":22,"longest":{"slug":"x","words":1925},"reading_hours":34.6},
         "tags":{"unique":864,"per_post":2.6,"top":[["twitter",2160]]},
         "media":{"files":5545,"bytes":2045037447,"referenced":5546,"posts_with_media":2562},
         "sources":{"twitter":2133}}
        """
        val stats = Engine.decode<StatsAnswer>(json.toByteArray())
        assertEquals(6653, stats.posts.total)
        assertEquals("2003-10-31", stats.span.first)
        assertEquals(415207, stats.words.total)
        assertEquals(34.6, stats.words.readingHours, 0.0)
        assertEquals(864, stats.tags.unique)
        assertEquals(5545, stats.media.files)
        assertEquals(2_045_037_447L, stats.media.bytes)
    }

    @Test
    fun anEmptyArchiveHasNoFirstDay() {
        val json = """{"posts":{"total":0},"span":{"first":null},"words":{"total":0,"reading_hours":0.0},"tags":{"unique":0},"media":{"files":0,"bytes":0}}"""
        val stats = Engine.decode<StatsAnswer>(json.toByteArray())
        assertNull(stats.span.first)
        assertEquals(0, stats.posts.total)
    }

    /** `empty trash --json` without `--yes`: how much there is. */
    @Test
    fun whatTheTrashHoldsIsRead() {
        val held = Engine.decode<HeldAnswer>("""{"ok":true,"what":"trash","count":10,"bytes":1231404,"size":"1.2MB","emptied":false}""".toByteArray())
        assertEquals(10, held.count)
        assertEquals(1_231_404L, held.bytes)
    }

    /**
     * A refusal is an answer too: it is thrown as what the engine said,
     * not as an answer that failed to parse.
     */
    @Test
    fun aRefusalIsThrownAsTheEnginesOwnWords() {
        val json = """{"ok":false,"error":"unknown_command","message":"Only run, receive and deliver are allowed on this key."}"""
        try {
            Engine.decode<VersionAnswer>(json.toByteArray())
            fail("a refusal was read as an answer")
        } catch (e: EngineError.Refused) {
            assertEquals("unknown_command", e.refusal.error)
            assertTrue(e.refusal.message.startsWith("Only run"))
        }
    }

    // the server's paths

    /**
     * What the engine says after a restore names a file on the server;
     * the name is what a phone can use.
     */
    @Test
    fun aFileOnTheServerIsCalledByItsName() {
        assertEquals("Obnoveno: venku", ServerPaths.plain("Obnoveno: /app/data/sean.cz/content.nosync/posts/2026/venku.json"))
        assertEquals("Smazáno (v koši): smazat", ServerPaths.plain("Smazáno (v koši): /srv/blog/trash/2026/smazat"))
        assertEquals("Kept 01.jpg, dropped the rest.", ServerPaths.plain("Kept /srv/blog/media.nosync/2026/venku/01.jpg, dropped the rest."))
        assertEquals("Moved to smazat.", ServerPaths.plain("Moved to /srv/blog/trash/2026/smazat."))
    }

    /** An address, a preview path, a command: none of them is a file of the engine's. */
    @Test
    fun whatIsNotTheEnginesFileIsLeftAsItIs() {
        for (line in listOf(
            "Publikováno: https://sean.cz/posts/2026/venku/",
            "Náhled: /draft/9d7e7bbcce6f229e/venku/",
            "obnovíš přes ./blog.sh restore venku",
            "Fronta posunuta: každý následující příspěvek převzal dřívější slot.",
            "see https://example.com/trash/2026/x for more", "",
        )) {
            assertEquals(line, ServerPaths.plain(line))
        }
        assertEquals(listOf("a x", "plain"), listOf("a /srv/b/trash/x", "plain").plain)
    }

    /** `empty trash --yes --json`: how much went. */
    @Test
    fun whatWasClearedOutIsRead() {
        val held = Engine.decode<HeldAnswer>("""{"ok":true,"what":"trash","count":2,"bytes":536,"size":"536 B","emptied":true}""".toByteArray())
        assertTrue(held.count == 2 && held.bytes == 536L && held.emptied == true)
    }

    /**
     * The engine's sentence about a slug two posts share speaks of --yes
     * and of a screen the terminal has; the app says what a phone can do.
     */
    @Test
    fun aSlugTwoPostsShareIsSaidInTheAppsWords() {
        val said = EngineError.Refused(Refusal(false, "ambiguous_slug", "Slug x je ve víc než jednom roce a --yes se nemá koho zeptat.")).message
        assertFalse(said.contains("--yes"))
        assertTrue(said.contains("./blog.sh props"))
        assertEquals("Příspěvek nenalezen.", EngineError.Refused(Refusal(false, "not_found", "Příspěvek nenalezen.")).message)
    }

    @Test
    fun noAnswerAtAllIsSaidSo() {
        try {
            Engine.decode<VersionAnswer>(ByteArray(0))
            fail("nothing was read as an answer")
        } catch (e: EngineError.Unreadable) {
            // as it should be
        }
    }

    @Test
    fun whatIsNotJsonIsShownNotSwallowed() {
        try {
            Engine.decode<VersionAnswer>("ruby: command not found".toByteArray())
            fail("a shell's complaint was read as an answer")
        } catch (e: EngineError.Unreadable) {
            assertTrue(e.text.contains("command not found"))
        }
    }

    /**
     * The server turning the key away is said in words a person can act
     * on, not in the SSH library's own.
     */
    @Test
    fun aKeyTheServerDoesNotKnowIsSaidInWords() {
        val said = EngineError.KeyNotKnown.message
        assertTrue(said.isNotEmpty())
        assertFalse(said.contains("UserAuthException"))
        assertTrue(said.contains("authorized_keys"))
    }

    /** The answers of a delivery come one after another: a line a picture, the engine's own object last. */
    @Test
    fun theAnswersOfADeliveryAreToldApart() {
        val out = "{\"ok\":true,\"file\":\"a.jpg\"}\n{\"ok\":true,\"file\":\"b.jpg\"}\n{\n  \"ok\": true,\n  \"slug\": \"x\"\n}\n"
        val found = Engine.objects(out.toByteArray())
        assertEquals(3, found.size)
        assertTrue(String(found[2]).contains("\"slug\""))
    }
}
