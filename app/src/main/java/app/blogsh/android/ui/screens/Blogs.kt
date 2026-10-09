package app.blogsh.android.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.Blog
import app.blogsh.android.model.Blogs
import app.blogsh.android.ui.Asks
import app.blogsh.android.ui.Choice
import app.blogsh.android.ui.Command
import app.blogsh.android.ui.DialogKey
import app.blogsh.android.ui.Mark
import app.blogsh.android.ui.PaperRow
import app.blogsh.android.ui.PaperSheet
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.SiteIcon
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import java.text.NumberFormat

/**
 * The blogs the app drives, and the way from one to another: a row opens
 * its blog, the key at its end opens that blog's settings, the last key
 * begins a new one. A blog is its own server, its own key and its own
 * colour; nothing of one is used for another. A long press on a row
 * offers to carry it a place up or down the list, or to remove its blog.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlogsSheet(onDismiss: () -> Unit) {
    var held by remember { mutableStateOf<Blog?>(null) }
    var removing by remember { mutableStateOf<Blog?>(null) }
    var settingUp by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val settings = stringResource(R.string.the_blog_s_settings)
    PaperSheet(
        onDismiss, scrolls = false, name = stringResource(R.string.blogs),
        count = if (Blogs.all.isEmpty()) null else NumberFormat.getIntegerInstance().format(Blogs.all.size),
        actions = { DialogKey(stringResource(R.string.done), onClick = onDismiss) },
    ) {
        LazyColumn(Modifier.weight(1f)) {
            items(Blogs.all, key = { it.id }) { blog ->
                PaperRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.weight(1f).combinedClickable(
                                onClick = {
                                    Blogs.select(blog.id)
                                    onDismiss()
                                },
                                onLongClick = { held = blog },
                            )
                        ) { BlogRow(blog, open = blog.id == Blogs.currentId) }
                        // The settings are the open blog's: the key opens the blog with them.
                        Pressable(
                            {
                                Blogs.select(blog.id)
                                settingUp = true
                            },
                            modifier = Modifier.semantics { contentDescription = settings },
                        ) {
                            // A key that looks like one: the mark alone was
                            // small to see and smaller to believe in.
                            val shape = RoundedCornerShape(Theme.corner)
                            Box(
                                Modifier.size(46.dp).clip(shape).background(Theme.card).border(1.dp, Theme.keyLine, shape),
                                contentAlignment = Alignment.Center,
                            ) { Mark(Symbols.sliderHorizontal3, 20.dp) }
                        }
                    }
                }
            }
            item {
                PaperRow(rule = false) {
                    Box(Modifier.padding(vertical = 12.dp)) {
                        // With a code from the server, or by hand: the next screen asks which.
                        Command(stringResource(R.string.add_a_blog), Symbols.plus) { adding = true }
                    }
                }
            }
        }
    }
    if (settingUp) BlogSettingsSheet(onBack = { settingUp = false }, onDone = { settingUp = false; onDismiss() })
    if (adding) AddBlogSheet(onBack = { adding = false }, onDone = { adding = false; onDismiss() })
    // The order is the reader's own: a row held offers the way up and
    // down, as a row of the queue does.
    held?.let { blog ->
        Asks(
            blog.label.ifEmpty { stringResource(R.string.a_new_blog) },
            choices = listOfNotNull(
                if (blog.id != Blogs.all.firstOrNull()?.id) Choice(stringResource(R.string.up)) { Blogs.shift(blog.id, -1) } else null,
                if (blog.id != Blogs.all.lastOrNull()?.id) Choice(stringResource(R.string.down)) { Blogs.shift(blog.id, 1) } else null,
                Choice(stringResource(R.string.remove), danger = true) { removing = blog },
            ),
            onDismiss = { held = null },
        )
    }
    removing?.let { blog ->
        Asks(
            stringResource(R.string.remove_from_the_app_its_key_is, blog.label),
            choices = listOf(Choice(stringResource(R.string.remove), danger = true) { Blogs.remove(blog.id) }),
            onDismiss = { removing = null },
        )
    }
}

/** A blog as a row says it: its mark, its name, where it is -- and which one is open. */
@Composable
fun BlogRow(blog: Blog, open: Boolean) {
    var mark by remember(blog.url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(blog.url) { mark = SiteIcon.kept(blog.url) }
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(9.dp)
        val image = mark
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(shape))
        } else {
            Box(Modifier.size(40.dp).border(1.dp, Theme.line, shape), contentAlignment = Alignment.Center) { Mark(Symbols.globe, 20.dp, Theme.muted) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (blog.label.isEmpty()) {
                Text(stringResource(R.string.a_new_blog), color = Theme.muted, style = ui(15f, FontWeight.Medium))
            } else {
                Text(blog.label, color = Theme.ink, style = ui(15f, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val place = blog.place
            if (place.isNotEmpty()) Text(place, color = Theme.muted, style = mono(12f, bold = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (open) {
            val label = stringResource(R.string.open)
            Mark(Symbols.checkmark, 18.dp, modifier = Modifier.semantics { contentDescription = label })
        }
    }
}
