package app.blogsh.android

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import app.blogsh.android.model.Blog
import app.blogsh.android.model.BlogShelf
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.BuildStamp
import app.blogsh.android.model.Colouring
import app.blogsh.android.model.Desk
import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.Now
import app.blogsh.android.model.Reach
import app.blogsh.android.model.Reading
import app.blogsh.android.model.Shot
import app.blogsh.android.model.TextSize
import app.blogsh.android.model.Unsaved
import app.blogsh.android.model.Unsent
import app.blogsh.android.model.Waiting
import app.blogsh.android.model.WaitingRoom
import app.blogsh.android.ui.BlogshTheme
import app.blogsh.android.ui.HomeScreen
import app.blogsh.android.ui.HomeState
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.MenuEntry
import app.blogsh.android.ui.Nav
import app.blogsh.android.ui.NavHost
import app.blogsh.android.ui.screens.AddBlogSheet
import app.blogsh.android.ui.screens.ArchiveScreen
import app.blogsh.android.ui.screens.BlogSettingsSheet
import app.blogsh.android.ui.screens.BlogsSheet
import app.blogsh.android.ui.screens.ComposeScreen
import app.blogsh.android.ui.screens.Diagnosis
import app.blogsh.android.ui.screens.DiagnosisScreen
import app.blogsh.android.ui.screens.PostPickerScreen
import app.blogsh.android.ui.screens.PropsScreen
import app.blogsh.android.ui.screens.QueueScreen
import app.blogsh.android.ui.screens.SettingsSheet
import app.blogsh.android.ui.screens.SiteScreen
import app.blogsh.android.ui.screens.TextEditScreen
import app.blogsh.android.ui.screens.TrashScreen
import app.blogsh.android.ui.screens.WaitingSheet
import app.blogsh.android.ui.worn
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/**
 * One way the app can look on a device: how large the display is and how
 * it is held, by day or by night, in which language, and how large the
 * type. The screens are drawn in each (see `ShotsTest`).
 */
class Look(val name: String, private val display: String, val lang: String, val night: Boolean = false, val type: TextSize = TextSize.System) {
    /** As Robolectric is told of a device. */
    val qualifiers: String get() = "$lang-$display-${if (night) "night" else "notnight"}-${if (display.startsWith("w360")) "xhdpi" else "mdpi"}"

    override fun toString(): String = name

    companion object {
        private const val PHONE = "w360dp-h800dp-port"
        private const val TABLET_ON_ITS_SIDE = "w1280dp-h800dp-land"
        private const val TABLET_UPRIGHT = "w800dp-h1280dp-port"

        /**
         * Not every combination -- the pictures are kept in the repository
         * -- but each thing a screen has to stand once: every language,
         * the night, the largest type, the two ways a tablet is held.
         */
        val all = listOf(
            Look("phone-cs", PHONE, "cs"),
            Look("phone-cs-night", PHONE, "cs", night = true),
            Look("phone-en", PHONE, "en"),
            Look("phone-de-largest", PHONE, "de", type = TextSize.Four),
            Look("tablet-cs", TABLET_ON_ITS_SIDE, "cs"),
            Look("tablet-en-night", TABLET_ON_ITS_SIDE, "en", night = true),
            Look("tablet-upright-cs", TABLET_UPRIGHT, "cs"),
        )
    }
}

/**
 * The screens, drawn on the desk and compared with how they were last
 * looked at. A mistake here is one nobody typed: a row that no longer
 * fits in German, a card unreadable by night, a sheet that covers a
 * tablet from edge to edge. What the engine would answer comes from
 * `fixtures/shots/` -- a test blog's own answers, taken as the app asks
 * for them -- and the clock stands still, so a picture changes only when
 * a screen does.
 *
 *     tools/gw :app:testDebugUnitTest                  compares
 *     tools/gw :app:testDebugUnitTest -Pshots=record   takes the pictures anew
 *
 * The pictures are in `app/src/test/shots/<look>/<screen>.png`; a screen
 * that has changed is drawn beside its picture in the build's
 * `shots-changed/<look>/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ShotsTest(private val look: Look) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Look> = Look.all

        /** Thursday 8 October 2026, noon in Prague. */
        private val noon: Instant = Instant.parse("2026-10-08T10:00:00Z")
        private const val BLOG = "shots"
        private val SERVER = Reach.server("blog.invalid", 2222)
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private val zone = TimeZone.getDefault()
    private val locale = Locale.getDefault()

    /** What the engine would answer: a command's words in, the answer the test blog gave out. */
    private fun answer(args: List<String>): ByteArray {
        val name = when {
            args == listOf("list", "--drafts") -> "list-drafts"
            args.firstOrNull() == "empty" -> "empty-" + args.getOrElse(1) { "" }
            else -> args.firstOrNull() ?: ""
        }
        return checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/shots/$name.json")) { "no answer kept for $args" }.use { it.readBytes() }
    }

    @Before
    fun dress() {
        RuntimeEnvironment.setQualifiers(look.qualifiers)
        Locale.setDefault(Locale.forLanguageTag(look.lang))
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Prague"))
        Now.clock = { noon }
        BuildStamp.own = BuildStamp("9e1de85", Instant.parse("2026-10-08T13:31:11Z").toEpochMilli())
        Engine.stand = ::answer
        // One blog, the same for every picture, with nothing kept for it.
        for (blog in Blogs.all) Blogs.remove(blog.id)
        Blogs.adopt(Blog(id = BLOG, host = "blog.invalid", port = 2222, user = "me", name = "blog.sh"))
        Unsent.forget(BLOG, BlogShelf.notes)
        Unsaved.forgetAll(BLOG, BlogShelf.notes)
        WaitingRoom.removeAll(BLOG)
        Reach.shared.heard(SERVER)
        Reading.shared.wear(Colouring.Blog)
        Reading.shared.choose(null)
        Reading.shared.set(look.type)
    }

    @After
    fun undress() {
        WaitingRoom.removeAll(BLOG)
        Reach.shared.heard(SERVER)
        Engine.stand = null
        Now.clock = { Instant.now() }
        TimeZone.setDefault(zone)
        Locale.setDefault(locale)
    }

    /**
     * Draws the app with `screen` open -- over the menu on a phone, beside
     * it on a tablet -- and `over` laid on top (a sheet), and compares
     * the whole display with its picture.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(name: String, tag: MenuEntry? = null, over: (@Composable () -> Unit)? = null, screen: (@Composable () -> Unit)? = null) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    BlogshTheme {
                        val nav = remember { Nav() }
                        val home = remember { HomeState() }
                        CompositionLocalProvider(LocalNav provides nav) {
                            NavHost(nav) { HomeScreen(home) }
                            if (screen != null) LaunchedEffect(Unit) { nav.open(tag, screen) }
                            over?.invoke()
                        }
                    }
                }
            }
            settle()
            captureScreenRoboImage(
                File(System.getProperty("blogsh.shots"), "${look.name}/$name.png").path,
                // A screen that has changed is drawn beside its picture, each look in a folder of its own.
                RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(outputDirectoryPath = File(System.getProperty("blogsh.shots.changed"), look.name).path)),
            )
        }
    }

    /** Until what was asked for has answered and been drawn: the answers come at once, the drawing a frame or two later. */
    private fun settle() {
        repeat(6) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(40)
        }
        compose.waitForIdle()
    }

    private fun begin() {
        Unsent("Pes v trávě", "pes, zahrada", "Plazí se.\n\n![Pes](photo-1.jpg)\n", noon.minusSeconds(3 * 3600).toEpochMilli()).keep(BLOG, BlogShelf.notes)
        Unsaved("Deset kilometrů kolem rybníka. A jedenáctý navíc.", "abc", noon.minusSeconds(1800).toEpochMilli(), "Doběhnuto")
            .keep(BLOG, "dobehnuto", Unsaved.What.Text, BlogShelf.notes)
    }

    /** The server is silent, and two posts were finished meanwhile: one with a picture, one the blog turned away. */
    private fun silence() {
        Reach.shared.nothing(SERVER)
        Engine.stand = { throw EngineError.Unreachable("blog.invalid", "") }
        WaitingRoom.put(
            Waiting(id = "one", title = "Ráno u rybníka", text = "Mlha.\n\n![Hladina](photo-1.jpg)\n", at = noon.minusSeconds(2 * 3600).toEpochMilli(), receipt = "0123456789abcdef"),
            listOf(Shot("photo-1.jpg", ByteArray(3), 4, 3)), BLOG,
        )
        WaitingRoom.put(Waiting(id = "two", text = "Jen pár slov bez titulku.", at = noon.minusSeconds(600).toEpochMilli(), receipt = "fedcba9876543210"), emptyList(), BLOG)
        WaitingRoom.note("The delivery is larger than this blog takes: 31 MB, at most 24 MB.", "two", BLOG)
    }

    @Test
    fun home() = shot("home")

    @Test
    fun homeWithASilentServer() {
        begin()
        silence()
        shot("home-offline")
    }

    @Test
    fun whatWaitsToBeSent() {
        silence()
        shot("waiting", over = { WaitingSheet(onDismiss = {}, write = {}) })
    }

    @Test
    fun newPostWithASilentServer() {
        silence()
        shot("new-post-offline", MenuEntry.Add) { ComposeScreen() }
    }

    /**
     * A post that waited, taken back into the form: the form has its
     * picture, the post is held with its files -- not listed, not gone --
     * and the writing remembers where it came from.
     */
    @Test
    fun newPostTakenBackFromThoseThatWait() {
        silence()
        Desk.hand(WaitingRoom.all(BLOG).first { it.id == "one" })
        shot("new-post-taken-back", MenuEntry.Add) { ComposeScreen() }
        assertEquals(true, WaitingRoom.one("one", BLOG)?.held)
        assertEquals(listOf("two"), WaitingRoom.all(BLOG).map { it.id })
        assertEquals("one", Unsent.kept(BLOG, BlogShelf.notes)?.from)
        assertEquals("0123456789abcdef", Unsent.kept(BLOG, BlogShelf.notes)?.receipt)
    }

    /**
     * The same form opened again after the app was stopped: the picture
     * is back from the post's files, and nothing is said to be missing.
     */
    @Test
    fun newPostBroughtBackWithItsPictures() {
        silence()
        WaitingRoom.hold("one", BLOG)
        Unsent("Ráno u rybníka", "", "Mlha.\n\n![Hladina](photo-1.jpg)\n", noon.minusSeconds(3600).toEpochMilli(), "0123456789abcdef", from = "one")
            .keep(BLOG, BlogShelf.notes)
        shot("new-post-back-with-pictures", MenuEntry.Add) { ComposeScreen() }
        assertEquals(true, WaitingRoom.one("one", BLOG)?.held)
        assertEquals("one", Unsent.kept(BLOG, BlogShelf.notes)?.from)
    }

    /** A held post no writing holds any more waits again once the first screen is asked for. */
    @Test
    fun aHeldPostNobodyHoldsIsBackOnTheFirstScreen() {
        silence()
        WaitingRoom.hold("one", BLOG)
        assertEquals(listOf("two"), WaitingRoom.all(BLOG).map { it.id })
        kotlinx.coroutines.runBlocking { HomeState().load() }
        assertEquals(listOf("one", "two"), WaitingRoom.all(BLOG).map { it.id })
    }

    @Test
    fun homeWithWritingBegun() {
        begin()
        shot("home-begun")
    }

    @Test
    fun archive() = shot("archive", MenuEntry.Browse) { ArchiveScreen() }

    @Test
    fun queue() = shot("queue", MenuEntry.Queue) { QueueScreen() }

    @Test
    fun posts() = shot("posts", MenuEntry.Post) { PostPickerScreen() }

    @Test
    fun properties() = shot("properties", MenuEntry.Post) { PropsScreen("dobehnuto") }

    @Test
    fun text() = shot("text", MenuEntry.Post) { TextEditScreen("dobehnuto") }

    @Test
    fun newPost() = shot("new-post", MenuEntry.Add) { ComposeScreen() }

    @Test
    fun newPostBroughtBack() {
        begin()
        shot("new-post-back", MenuEntry.Add) { ComposeScreen() }
    }

    @Test
    fun site() = shot("site", MenuEntry.Rebuild) { SiteScreen() }

    @Test
    fun installationCheck() = shot("doctor", MenuEntry.Rebuild) { DiagnosisScreen(Diagnosis.Installation) }

    @Test
    fun trash() = shot("trash", MenuEntry.Restore) { TrashScreen() }

    @Test
    fun settings() = shot("settings", over = { SettingsSheet(onDismiss = {}) })

    @Test
    fun settingsWithColoursOfOnesOwn() {
        Reading.shared.wearChosen(Reading.shared.worn)
        shot("settings-colours", over = { SettingsSheet(onDismiss = {}) })
    }

    @Test
    fun blogs() = shot("blogs", over = { BlogsSheet(onDismiss = {}) })

    /** The open blog, as one a code let in: where it is, how it was let in, and nothing of a line to compose. */
    private fun paired() {
        Blogs.update { it.copy(pairedAs = "motorola edge 70 fusion") }
    }

    @Test
    fun settingsOfABlogACodeLetIn() {
        paired()
        shot("blog-settings-paired", over = { BlogSettingsSheet(onBack = {}, onDone = {}) })
    }

    @Test
    fun aBlogLetInAgain() {
        paired()
        shot("pair-again", over = { AddBlogSheet(into = Blogs.current, onBack = {}, onDone = {}) })
    }

    @Test
    fun addBlogWithACode() = shot(
        "add-blog",
        over = {
            AddBlogSheet(
                initialCode = "blogsh://pair?v=1&h=192.168.1.20&p=2222&u=me&k=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8&n=M%C5%AFj+blog",
                onBack = {}, onDone = {},
            )
        },
    )
}
