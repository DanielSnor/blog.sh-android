package app.blogsh.android

import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.HeldAnswer
import app.blogsh.android.model.PostLink
import app.blogsh.android.model.QueueRow
import app.blogsh.android.model.Tones
import app.blogsh.android.model.PostRow
import app.blogsh.android.model.PostState
import app.blogsh.android.model.PropsAnswer
import app.blogsh.android.model.Refusal
import app.blogsh.android.model.ServerPaths
import app.blogsh.android.model.StatsAnswer
import app.blogsh.android.model.VersionAnswer
import app.blogsh.android.model.plain
import app.blogsh.android.model.seenAs
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

    private fun version(claim: String, accent: Boolean = true, palette: Boolean = false): ByteArray {
        var colours = if (accent) ""","accent":{"light":"#1da1f2","dark":"#4ab3f4"}""" else ""
        if (palette) {
            colours += ""","palette":{"light":{"bg":"#fff7eb","text":"#1e1d1c","meta_text":"#6b6862","border":"#d7d0c6"},"dark":{"bg":"#000000","text":"#e6dccb","meta_text":"#a1988a","border":"#3c3935"}}"""
        }
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

    /** The rest of the palette, as the engine names its colours. */
    @Test
    fun thePaletteIsRead() {
        val answer = Engine.decode<VersionAnswer>(version("x", palette = true))
        assertEquals(Tones("#fff7eb", "#1e1d1c", "#6b6862", "#d7d0c6"), answer.site.palette?.light)
        assertEquals(Tones("#000000", "#e6dccb", "#a1988a", "#3c3935"), answer.site.palette?.dark)
    }

    /** An engine from before it said its palette: the accent is still its own. */
    @Test
    fun anEngineWithoutAPaletteIsStillRead() {
        val answer = Engine.decode<VersionAnswer>(version("x"))
        assertNull(answer.site.palette)
        assertEquals("#1da1f2", answer.site.accent?.light)
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

    // a post that changed while it was looked at

    /**
     * A post renamed in its properties, as the engine answers the rename:
     * the screen it was opened from takes its new name from this, and
     * asks for the text and the properties under it.
     */
    @Test
    fun aPostsRowIsReadFromItsProperties() {
        val props = Engine.decode<PropsAnswer>(Fixture.text("props-renamed").toByteArray())
        val row = PostRow(props)
        assertEquals("novy-nazev", row.slug)
        assertEquals("2026", row.year)
        assertEquals("2026/novy-nazev", row.id)
        assertEquals("K přejmenování", row.title)
        assertEquals(PostState.Published, row.state)
        assertFalse(row.scheduled)
        assertEquals("2026-05-01T10:00:00+02:00", row.date)
        assertTrue(row.day != null)
        assertEquals("text", row.type)
        assertEquals(emptyList<String>(), row.tags)
        assertFalse(row.pinned)
        assertNull(row.match)
    }

    /**
     * A post without a title is called by its slug in a list and by its
     * opening words in its properties: the row keeps none, so the screen
     * opened from the list does not rename the post a moment later.
     */
    @Test
    fun aRowWithoutATitleKeepsNone() {
        val props = Engine.decode<PropsAnswer>(Fixture.text("props-renamed").toByteArray())
        val untitled = PostRow("stary", "2026", null, null, "text", emptyList(), PostState.Draft, false, null, false)
        val now = untitled.seenAs(props)
        assertNull(now.title)
        assertEquals("novy-nazev", now.slug)
        assertEquals(PostState.Published, now.state)
        val titled = untitled.copy(title = "Starý")
        assertEquals("K přejmenování", titled.seenAs(props).title)
    }

    /** What is handed to somebody: the address the engine says, and the post's title to go with it. */
    @Test
    fun aPostsLinkIsItsAddressAndItsTitle() {
        val props = Engine.decode<PropsAnswer>(Fixture.text("props-renamed").toByteArray())
        val link = PostLink.of(props)
        assertEquals("https://example.com/posts/2026/novy-nazev/", link?.url)
        assertEquals("K přejmenování", link?.title)
    }

    /** A site with no address set, or one that is not a web address: no link to give. */
    @Test
    fun withoutAWebAddressThereIsNoLink() {
        assertNull(PostLink.of("", "x"))
        assertNull(PostLink.of("/posts/2026/x/", "x"))
        assertNull(PostLink.of("file:///etc/passwd", "x"))
        assertNull(PostLink.of("javascript:alert(1)", "x"))
        assertEquals("http://localhost:8000/draft/abc/x/", PostLink.of("http://localhost:8000/draft/abc/x/", "x")?.url)
        assertEquals("X", PostLink.of("https://sean.cz/posts/2026/x/", "X")?.title)
        // A blog at home, on a port of its own: the address goes on whole.
        val home = "http://10.0.0.5:8080/posts/2026/zkouska-portu/"
        assertEquals(home, PostLink.of(home, "x")?.url)
    }

    /**
     * A row of the queue opens its post: the row it hands over is a
     * draft with a plan, under the same slug and year.
     */
    @Test
    fun aQueuedPostIsARowOfItsOwn() {
        val json = """{"position":2,"date":"2026-10-10T09:00:00+02:00","slug":"plan-d","year":"2026","title":"Plan D","overdue":false}"""
        val queued = Engine.decode<QueueRow>(json.toByteArray())
        val row = PostRow(queued)
        assertEquals(queued.id, row.id)
        assertEquals("plan-d", row.slug)
        assertEquals("Plan D", row.title)
        assertEquals(PostState.Draft, row.state)
        assertTrue(row.scheduled)
        assertTrue(row.day != null)
        // A post without a title is called by its slug, as everywhere.
        val bare = Engine.decode<QueueRow>(json.replace("Plan D", "").toByteArray())
        assertNull(PostRow(bare).title)
    }
}

/**
 * What `check` and `doctor` answer, read as they are sent. A mistake
 * here is a problem the blog reported and the app passed over.
 */
class DiagnosisTest {
    private fun read(name: String): app.blogsh.android.model.DiagnosisAnswer =
        Engine.decode(Fixture.text(name).toByteArray(Charsets.UTF_8))

    private val error = app.blogsh.android.model.DiagnosisAnswer.Finding.Level.Error
    private val warning = app.blogsh.android.model.DiagnosisAnswer.Finding.Level.Warning
    private val fine = app.blogsh.android.model.DiagnosisAnswer.Finding.Level.Fine

    /** As blogsh.app answered `check` on 8. 10. 2026. */
    @Test
    fun aFindingAboutAPostNamesThePost() {
        val answer = read("check-warning")
        assertEquals(0, answer.errors)
        assertEquals(1, answer.warnings)
        val finding = answer.findings.first()
        assertEquals(warning, finding.level)
        assertEquals("post_entities", finding.kind)
        assertEquals("everything-else-in-1-6", finding.slug)
        assertTrue(finding.text.startsWith("everything-else-in-1-6:"))
        assertFalse(finding.fix.isNullOrEmpty())
    }

    /** As sean.cz answered: one finding, and it is the all-clear. */
    @Test
    fun anArchiveInOrderSaysSoInOneFinding() {
        val answer = read("check-clear")
        assertTrue(answer.errors == 0 && answer.warnings == 0)
        assertEquals(listOf(fine), answer.findings.map { it.level })
        assertEquals("all_clear", answer.findings.first().kind)
        assertNull(answer.findings.first().slug)
        assertNull(answer.findings.first().fix)
    }

    /**
     * As the engine's `doctor --json` answers: a kind on every finding,
     * a fix that is null where there is no advice, nothing about a post.
     */
    @Test
    fun theInstallationsFindingsAreReadWithTheirKinds() {
        val answer = read("doctor")
        assertEquals(0, answer.errors)
        assertEquals(answer.findings.count { it.level == warning }, answer.warnings)
        assertTrue(answer.findings.all { it.kind.isNotEmpty() })
        assertTrue(answer.findings.all { it.slug == null })
        assertTrue(answer.findings.any { it.level == fine && it.fix == null })
        assertTrue(answer.findings.any { it.kind == "scheduler" })
    }

    @Test
    fun theProblemsComeFirstThenWhatWantsALookThenWhatIsFine() {
        val mixed = """
        {"errors": 1, "warnings": 2, "findings": [
          {"level": "ok", "kind": "a", "text": "fine one", "fix": null},
          {"level": "warn", "kind": "b", "text": "first warning"},
          {"level": "error", "kind": "c", "text": "the error", "fix": "do this"},
          {"level": "warn", "kind": "d", "text": "second warning", "data": {"slugs": ["x", "y"]}},
          {"level": "notice", "text": "a level nobody knows"}
        ]}
        """.toByteArray(Charsets.UTF_8)
        val answer = Engine.decode<app.blogsh.android.model.DiagnosisAnswer>(mixed)
        assertEquals(listOf("the error", "first warning", "second warning", "a level nobody knows", "fine one"), answer.ordered.map { it.text })
        assertEquals(error, answer.ordered.first().level)
        // A level the app has not heard of is not passed over as fine.
        assertEquals(warning, answer.findings.last().level)
        assertEquals("", answer.findings.last().kind)
        // What a finding is about differs with its kind: no post, no failure.
        assertNull(answer.findings[3].slug)
        // No advice, or advice that says nothing, is no advice.
        assertNull(answer.findings[0].fix)
        assertEquals("do this", answer.findings[2].fix)
    }

    /**
     * The blog said no -- an engine without the check: that is a
     * refusal, not a diagnosis with nothing in it.
     */
    @Test
    fun aRefusalIsNotADiagnosis() {
        val refusal = """{"ok":false,"error":"unknown_command","message":"\"doctor\" is not a command a program may run."}""".toByteArray(Charsets.UTF_8)
        try {
            Engine.decode<app.blogsh.android.model.DiagnosisAnswer>(refusal)
            fail("a refusal was read as a diagnosis")
        } catch (e: EngineError.Refused) {
            assertEquals("unknown_command", e.refusal.error)
        }
    }
}
