package app.blogsh.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.Herald
import app.blogsh.android.model.Reading
import java.util.UUID

/**
 * The screens one over another, the way a phone has them: a screen opens
 * the next, and back leaves it. A screen that is covered is kept as it
 * was -- what was typed into it, where it was scrolled -- and is simply
 * not drawn; it is there again when the one over it leaves.
 */
class Nav {
    /** `tag`: what the screen was opened as -- an entry of the menu, where it is one. */
    class Entry(val id: String, val tag: Any?, val content: @Composable () -> Unit)

    val stack = mutableStateListOf<Entry>()

    /** Beside an open screen, the menu was put out of the way: the screen has the whole width. */
    var menuHidden by mutableStateOf(false)

    val depth: Int get() = stack.size

    fun push(tag: Any? = null, content: @Composable () -> Unit) {
        stack.add(Entry(UUID.randomUUID().toString(), tag, content))
    }

    /**
     * A fresh stack with one screen on it: what the menu opens beside
     * itself, where there is room for both -- another entry takes the
     * place of the one that was open, it is not laid over it.
     */
    fun open(tag: Any? = null, content: @Composable () -> Unit) {
        stack.clear()
        push(tag, content)
    }

    /** What the screen at the foot of the stack was opened as. */
    val root: Any? get() = stack.firstOrNull()?.tag

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

/**
 * How the screens share the display: one over another on a phone; on a
 * wide screen held upright one large page, the menu being that page while
 * nothing is open; on a wide screen on its side the menu and, beside it,
 * the screen that is open.
 */
enum class Layout { Phone, Page, Columns }

val LocalLayout = compositionLocalOf { Layout.Phone }

/**
 * Brings the menu back beside the open screen, where it was put out of
 * the way and there is room for both; null everywhere else.
 */
val LocalShowMenu = compositionLocalOf<(() -> Unit)?> { null }

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
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(if (speaking) Modifier.consumeWindowInsets(lowerEdge) else Modifier)) {
            val layout = when {
                !Theme.wide -> Layout.Phone
                maxWidth < maxHeight -> Layout.Page
                else -> Layout.Columns
            }
            val columns = layout == Layout.Columns
            // Out of the way only beside something: with the last screen closed
            // the menu is back, or nothing would be left to choose from.
            val hidden = columns && nav.menuHidden && nav.stack.isNotEmpty()
            LaunchedEffect(nav.stack.isEmpty()) { if (nav.stack.isEmpty()) nav.menuHidden = false }
            // Type enlarged in the settings needs a wider column to stand in.
            val menu = with(LocalDensity.current) { (320.dp * (1f + (Reading.shared.textSize.zoom(wide = true) - 1f) * 0.6f)).roundToPx() }
            CompositionLocalProvider(LocalLayout provides layout) {
                Panes(
                    columns, if (hidden) 0 else menu,
                    menuPane = { Layer(shown = (columns && !hidden) || nav.stack.isEmpty(), content = home) },
                    rule = { Box(Modifier.fillMaxSize().background(Theme.line)) },
                    openPane = {
                        // Beside the menu, with nothing open: the place says what it is for.
                        if (columns && nav.stack.isEmpty()) {
                            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
                                EmptyNote(Symbols.terminal, stringResource(R.string.what_do_you_want_to_do), room = Room.Welcome)
                            }
                        }
                        CompositionLocalProvider(LocalShowMenu provides if (hidden) ({ nav.menuHidden = false }) else null) {
                            nav.stack.forEachIndexed { index, entry ->
                                key(entry.id) { Layer(shown = index == nav.stack.lastIndex, content = entry.content) }
                            }
                        }
                    },
                )
            }
        }
        if (speaking) HeraldStrip(Modifier.windowInsetsPadding(lowerEdge))
    }
}

/**
 * The menu and the open screen, wherever they stand: one over the other,
 * each the whole of the place -- or side by side, a hairline between
 * them. The same two in the same order either way, so a display turned
 * from one to the other keeps every screen as it was: what was typed,
 * where it was scrolled.
 */
@Composable
private fun Panes(
    columns: Boolean,
    menu: Int,
    menuPane: @Composable () -> Unit,
    rule: @Composable () -> Unit,
    openPane: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(
        content = {
            Box { menuPane() }
            Box { rule() }
            Box { openPane() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (!columns) {
            val whole = Constraints.fixed(width, height)
            val first = measurables[0].measure(whole)
            val second = measurables[2].measure(whole)
            layout(width, height) {
                first.place(0, 0)
                second.place(0, 0)
            }
        } else if (menu == 0) {
            // The menu is out of the way: the open screen has the whole place.
            val second = measurables[2].measure(Constraints.fixed(width, height))
            layout(width, height) { second.place(0, 0) }
        } else {
            val hair = 1.dp.roundToPx()
            val side = menu.coerceAtMost(width / 2)
            val first = measurables[0].measure(Constraints.fixed(side, height))
            val line = measurables[1].measure(Constraints.fixed(hair, height))
            val second = measurables[2].measure(Constraints.fixed((width - side - hair).coerceAtLeast(0), height))
            layout(width, height) {
                first.place(0, 0)
                line.place(side, 0)
                second.place(side + hair, 0)
            }
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
 * Every time the screen comes to the front -- the first time, and again
 * when the one over it leaves: what a screen below may have changed is
 * read again here.
 */
@Composable
fun OnShown(block: suspend () -> Unit) {
    val shown = LocalShown.current
    LaunchedEffect(shown) { if (shown) block() }
}
