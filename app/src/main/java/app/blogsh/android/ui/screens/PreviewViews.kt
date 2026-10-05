package app.blogsh.android.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.TextureView
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import app.blogsh.android.R
import app.blogsh.android.model.Blogs
import app.blogsh.android.model.Delivery
import app.blogsh.android.model.Preview
import app.blogsh.android.model.Shot
import app.blogsh.android.ui.DialogKey
import app.blogsh.android.ui.EngineLabel
import app.blogsh.android.ui.Hairline
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.Mark
import app.blogsh.android.ui.PaperSheet
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.ScreenHeader
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max

/**
 * The post as the blog would show it, before it is sent: the text
 * rendered the way the /write/ page renders it, in the blog's own
 * stylesheets. Near enough, not exact -- and it says so.
 *
 * `lang` is the language the text is in, when it is not the reader's own:
 * a translation's.
 */
@Composable
fun PreviewSheet(title: String, markdown: String, shown: Map<String, Preview.Shown>, lang: String? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val site = Blogs.current?.url ?: ""
    val language = lang ?: Locale.getDefault().language.ifEmpty { "en" }
    // Made once for what the sheet was opened with: a picture rides in the
    // page whole, and is not to be written into it again with every frame.
    val page = remember(title, markdown, shown, language) {
        // The two sentences in the reader's language, in the page's own words.
        val spoken = Preview.Words(
            missing = { context.getString(R.string.picture_no_preview_on_this_device, it) },
            glued = { context.getString(R.string.a_picture_has_to_stand_on_a, it) },
        )
        Preview.document(title, Preview.render(markdown, shown, spoken), language)
    }
    PaperSheet(onDismiss, actions = { DialogKey(stringResource(R.string.done), onClick = onDismiss) }, scrolls = false) {
        ScreenHeader(stringResource(R.string.preview), modifier = Modifier.padding(horizontal = Theme.gutter).padding(top = 2.dp))
        Hairline(Modifier.padding(top = 10.dp))
        WebPage(page, site.ifEmpty { null }, Modifier.weight(1f).fillMaxWidth())
        Hairline()
        Hint(
            stringResource(R.string.near_enough_not_exact_the_blog_itself),
            Modifier.padding(horizontal = Theme.gutter).padding(bottom = 10.dp),
        )
    }
}

/**
 * A page of markup, shown; a link in it leads nowhere -- this is a look
 * at a post, not a browser. The page is the first thing loaded and the
 * only one: every way on from it is turned down. Nothing in it is a
 * script, and none would run.
 */
@Composable
private fun WebPage(html: String, base: String?, modifier: Modifier = Modifier) {
    // The app's own window is a light one whatever the hour; the page asks
    // the view it is shown in whether it is night, so by night the view is
    // made in a dark one and the blog's stylesheet answers with its night.
    val night = isSystemInDarkTheme()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val dressed = if (night) ContextThemeWrapper(context, android.R.style.Theme_Material_NoActionBar) else context
            WebView(dressed).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

/**
 * Bytes as a picture no larger than it will be drawn: a photograph whole
 * is some twenty megabytes of memory, and a card shows it the size of a
 * thumb. Null when the bytes are not a picture.
 */
internal fun pictureOf(bytes: ByteArray, edge: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= edge) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

/**
 * The shots of a form, one to a page and large: what each of them is, and
 * under it the line that describes it -- written while looking at it.
 * `current` is the shot it opens on; a description changed here comes
 * back through `onShot` as the shot with its new words.
 */
@Composable
fun ShotsViewer(shots: List<Shot>, current: String, onShot: (Shot) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(initialPage = shots.indexOfFirst { it.id == current }.coerceAtLeast(0)) { shots.size }
        Column(Modifier.fillMaxSize().background(Theme.paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Theme.gutter, vertical = 14.dp)) {
                if (pager.currentPage < shots.size) {
                    EngineLabel(stringResource(R.string.of, pager.currentPage + 1, shots.size), Modifier.alignByBaseline(), size = 13f)
                }
                Spacer(Modifier.weight(1f))
                Pressable(onDismiss, modifier = Modifier.alignByBaseline()) {
                    Text(stringResource(R.string.done), color = Theme.accent, style = ui(16f, FontWeight.SemiBold))
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { shots[it].id }) { page ->
                val shot = shots[page]
                ShotPage(shot, front = pager.currentPage == page) { onShot(shot.withAlt(it)) }
            }
        }
    }
}

@Composable
private fun ShotPage(shot: Shot, front: Boolean, onAlt: (String) -> Unit) {
    val context = LocalContext.current
    // Decoded once: the whole picture is too heavy to read again with every letter.
    var image by remember(shot.id) { mutableStateOf<ImageBitmap?>(null) }
    var file by remember(shot.id) { mutableStateOf<File?>(null) }
    val place = remember(shot.id) { File(context.cacheDir, "look-" + shot.id + "-" + shot.name) }

    // A picture is decoded; a video is put where a player can read it.
    LaunchedEffect(shot.id) {
        if (shot.kind == Shot.Kind.Video) {
            val written = try {
                withContext(Dispatchers.IO) { runCatching { place.writeBytes(shot.data) }.isSuccess }
            } catch (e: CancellationException) {
                place.delete()
                throw e
            }
            if (written) file = place else image = shot.poster?.let { pictureOf(it, 1024) }
        } else {
            val metrics = context.resources.displayMetrics
            val edge = max(metrics.widthPixels, metrics.heightPixels)
            image = withContext(Dispatchers.Default) { pictureOf(shot.data, edge) }
        }
    }
    DisposableEffect(shot.id) { onDispose { place.delete() } }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Theme.gutter), contentAlignment = Alignment.Center) {
            val clip = file
            val picture = image
            if (clip != null) {
                Clip(clip, front)
            } else if (picture != null) {
                Image(picture, contentDescription = shot.alt.ifEmpty { null }, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        Plate(Modifier.padding(horizontal = Theme.gutter).padding(bottom = 16.dp)) {
            row {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        shot.name, color = Theme.muted, style = mono(12f, bold = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).alignByBaseline(),
                    )
                    Text(Delivery.size(shot.data.size), color = Theme.muted, style = mono(11f, bold = false), modifier = Modifier.alignByBaseline())
                }
            }
            row {
                // One line to four: a description is a sentence, sometimes two.
                val style = ui(16f)
                BasicTextField(
                    value = shot.alt, onValueChange = onAlt, modifier = Modifier.fillMaxWidth(), maxLines = 4,
                    textStyle = style.copy(color = Theme.ink), cursorBrush = SolidColor(Theme.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { inner ->
                        Box {
                            if (shot.alt.isEmpty()) Text(stringResource(R.string.no_description_yet), color = Theme.muted, style = style, maxLines = 1)
                            inner()
                        }
                    },
                )
            }
        }
    }
}

/**
 * A video, played where it stands: a touch starts it and stops it, the
 * line under it says where in it one is and takes one elsewhere. It stops
 * by itself when its page is turned.
 */
@Composable
private fun Clip(file: File, front: Boolean) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    var playing by remember(file) { mutableStateOf(false) }
    var shape by remember(file) { mutableStateOf(16f / 9f) }
    var length by remember(file) { mutableStateOf(0L) }
    var at by remember(file) { mutableStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) shape = videoSize.width.toFloat() / videoSize.height
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) length = player.duration.coerceAtLeast(0)
                // At its end it is at its beginning again, as a player leaves it.
                if (playbackState == Player.STATE_ENDED) {
                    player.pause()
                    player.seekTo(0)
                    at = 0
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(front) { if (!front) player.pause() }
    LaunchedEffect(playing) {
        while (playing) {
            at = player.currentPosition
            delay(200)
        }
    }

    val word = stringResource(if (playing) R.string.android_d_pause else R.string.android_d_play)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Pressable({ if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.semantics { contentDescription = word }) {
                Box(contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { TextureView(it).also { view -> player.setVideoTextureView(view) } },
                        modifier = Modifier.aspectRatio(shape),
                    )
                    if (!playing) {
                        Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(14.dp)) {
                            Mark(Symbols.playFill, 28.dp, Color.White)
                        }
                    }
                }
            }
        }
        if (length > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(clock(at), color = Theme.muted, style = mono(11f, bold = false))
                Slider(
                    value = (at.toFloat() / length).coerceIn(0f, 1f),
                    onValueChange = {
                        at = (it * length).toLong()
                        player.seekTo(at)
                    },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Theme.accent, activeTrackColor = Theme.accent, inactiveTrackColor = Theme.line),
                )
                Text(clock(length), color = Theme.muted, style = mono(11f, bold = false))
            }
        }
    }
}

/** A length of time as a player says it: minutes and seconds. */
private fun clock(ms: Long): String {
    val seconds = (ms + 500) / 1000
    return "${seconds / 60}:" + "${seconds % 60}".padStart(2, '0')
}
