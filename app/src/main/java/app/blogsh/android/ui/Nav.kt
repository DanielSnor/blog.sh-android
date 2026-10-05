package app.blogsh.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    Box(Modifier.fillMaxSize()) {
        Layer(shown = nav.stack.isEmpty(), content = home)
        nav.stack.forEachIndexed { index, entry ->
            key(entry.id) { Layer(shown = index == nav.stack.lastIndex, content = entry.content) }
        }
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
 * What a screen does with the back key itself -- asking before unsent
 * words are lost. Only the screen in front is asked: one that is covered
 * would otherwise answer for the one over it.
 */
@Composable
fun ScreenBack(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandler(enabled = enabled && LocalShown.current, onBack = onBack)
}

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
