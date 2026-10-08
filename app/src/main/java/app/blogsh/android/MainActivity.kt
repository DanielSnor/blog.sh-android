package app.blogsh.android

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.blogsh.android.model.Colours
import app.blogsh.android.model.Engine
import app.blogsh.android.model.Reading
import app.blogsh.android.ui.BlogshTheme
import app.blogsh.android.ui.HomeScreen
import app.blogsh.android.ui.HomeState
import app.blogsh.android.ui.Incoming
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.Nav
import app.blogsh.android.ui.NavHost
import app.blogsh.android.ui.worn

class MainActivity : ComponentActivity() {
    /** The language chosen in the settings, taken up when the app starts: the screens are drawn in it. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        BlogshApp.chosenLocale(base)?.let { applyOverrideConfiguration(Configuration().apply { setLocale(it) }) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The system puts its own language back with every change -- a turn, the night coming on.
        BlogshApp.speak(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BlogshApp.speak(this)
        take(intent)
        bars(Colours.own)
        setContent {
            // The bars at the screen's edges are the ground's own colour, by day and by
            // night -- where the system would otherwise lay a veil of its own over a bar
            // of three keys. The ground is the one that is worn: the open blog's, the
            // app's own, or the one chosen here.
            val worn = Reading.shared.worn
            LaunchedEffect(worn.light.bg, worn.dark.bg) { bars(worn) }
            // The blog's own colours and its accent, as its pages wear them: every
            // control of the app, the sheets included. Until a blog has said its own,
            // the app's.
            BlogshTheme {
                val nav = remember { Nav() }
                val home = remember { HomeState() }
                CompositionLocalProvider(LocalNav provides nav) {
                    NavHost(nav) { HomeScreen(home) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    /** A pairing code that came in as a link, opened from another app. */
    private fun take(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        if (link.startsWith("blogsh:", ignoreCase = true)) Incoming.code = link
    }

    /**
     * Off the screen the app is asked nothing, and the system may stop it
     * at any moment: the connection it keeps to the server is closed now,
     * in order, rather than left to die there unannounced.
     */
    override fun onStop() {
        super.onStop()
        Engine.hangUp()
    }

    private fun bars(worn: Colours) {
        val light = (0xFF000000 or worn.light.bg).toInt()
        val dark = (0xFF000000 or worn.dark.bg).toInt()
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(light, dark), navigationBarStyle = SystemBarStyle.auto(light, dark))
    }
}
