package app.blogsh.android

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Reading
import app.blogsh.android.model.Tones
import app.blogsh.android.ui.BlogshTheme
import app.blogsh.android.ui.HomeScreen
import app.blogsh.android.ui.HomeState
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.Nav
import app.blogsh.android.ui.NavHost

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
        bars(Tones.ownLight, Tones.ownDark)
        setContent {
            // The bars at the screen's edges are the ground's own colour, by day and by
            // night -- where the system would otherwise lay a veil of its own over a bar
            // of three keys. The ground is the open blog's, or the app's own.
            val blog = Blogs.current
            val worn = Tones.worn(Reading.shared.ownColours, blog?.tonesLight, blog?.tonesDark)
            LaunchedEffect(worn) { bars(worn.first, worn.second) }
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

    private fun bars(day: Tones.Scheme, night: Tones.Scheme) {
        val light = (0xFF000000 or day.bg).toInt()
        val dark = (0xFF000000 or night.bg).toInt()
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(light, dark), navigationBarStyle = SystemBarStyle.auto(light, dark))
    }
}
