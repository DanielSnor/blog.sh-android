package app.blogsh.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import app.blogsh.android.model.Blogs
import app.blogsh.android.ui.BlogshTheme
import app.blogsh.android.ui.HomeScreen
import app.blogsh.android.ui.HomeState
import app.blogsh.android.ui.LocalNav
import app.blogsh.android.ui.Nav
import app.blogsh.android.ui.NavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The bars at the screen's edges are paper by day and black by night, like the ground
        // between them -- where the system would otherwise lay a veil of its own over a bar of three keys.
        val day = android.graphics.Color.rgb(0xEA, 0xE9, 0xE3)
        val night = android.graphics.Color.BLACK
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(day, night),
            navigationBarStyle = SystemBarStyle.auto(day, night),
        )
        setContent {
            // The blog's own accent, as /write/ wears it: every control of the
            // app, the sheets included. Until a blog has said its own, the look's.
            val blog = Blogs.current
            BlogshTheme(blog?.accentLight, blog?.accentDark) {
                val nav = remember { Nav() }
                val home = remember { HomeState() }
                CompositionLocalProvider(LocalNav provides nav) {
                    NavHost(nav) { HomeScreen(home) }
                }
            }
        }
    }
}
