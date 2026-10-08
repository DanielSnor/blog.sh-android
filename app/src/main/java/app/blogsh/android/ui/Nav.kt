package app.blogsh.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.blogsh.android.model.Herald
import java.util.UUID

/**
 * The screens one over another, the way a phone has them: a screen opens
 * the next, and back leaves it. A screen that is covered is kept as it
 * was -- what was typed into it, where it was scrolled -- and is simply
 * not drawn; it is there again when the one over it leaves.
 */
class Nav {
    class Entry(val id: String, val content: @Composable () -> Unit)

    val stack = mutableStateListOf<Entry>()

    val depth: Int get() = stack.size

    fun push(content: @Composable () -> Unit) {
        stack.add(Entry(UUID.randomUUID().toString(), content))
    }

    /** Leaves the screen in front. */
    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    /** Leaves so many screens at once: a post deleted from its properties takes the screen before them with it. */
    fun pop(count: Int) {
        repeat(count) { pop() }
    }

    /** Back to the first screen. */
    fun home() = stack.clear()
}

val LocalNav = staticCompositionLocalOf<Nav> { error("no Nav") }

/** Whether the screen asking is the one in front. A covered screen is still there, and should not act as if it were seen. */
val LocalShown = compositionLocalOf { true }

@Composable
fun NavHost(nav: Nav, home: @Composable () -> Unit) {
    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.pop() }
    // What was just done, and the site being brought up to date: said under
    // whichever screen is open, which then ends where the line begins.
    val herald = Herald.shared
    val speaking = herald.build != Herald.Build.None || herald.note != null
    val lowerEdge = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
    Column(Modifier.fillMaxSize().background(Theme.paper)) {
        Box(Modifier.weight(1f).fillMaxWidth().then(if (speaking) Modifier.consumeWindowInsets(lowerEdge) else Modifier)) {
            Layer(shown = nav.stack.isEmpty(), content = home)
            nav.stack.forEachIndexed { index, entry ->
                key(entry.id) { Layer(shown = index == nav.stack.lastIndex, content = entry.content) }
            }
        }
        if (speaking) HeraldStrip(Modifier.windowInsetsPadding(lowerEdge))
    }
}

@Composable
private fun Layer(shown: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalShown provides shown) {
        Box(Modifier.fillMaxSize().then(if (shown) Modifier else Modifier.covered())) { content() }
    }
}

/** Measured, so it keeps its shape, and not placed: not drawn, not touched, not read out. */
private fun Modifier.covered(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {}
    }.clearAndSetSemantics {}

/**
 * Every time the screen comes to the front -- the first time, and again
 * when the one over it leaves: what a screen below may have changed is
 * read again here.
 */
@Composable
fun OnShown(block: suspend () -> Unit) {
    val shown = LocalShown.current
    LaunchedEffect(shown) { if (shown) block() }
}
