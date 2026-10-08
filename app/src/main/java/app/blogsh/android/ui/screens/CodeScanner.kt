package app.blogsh.android.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.blogsh.android.R
import app.blogsh.android.model.QrReader
import app.blogsh.android.ui.DialogKey
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.Typed
import app.blogsh.android.ui.ui
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The camera, reading the code `./blog.sh pair` drew on a screen. What
 * it reads is handed over as text -- whether that text is a code of the
 * blog's is for whoever asked to say.
 */
object CodeScanner {
    /** There is a camera here to read a code with. */
    fun isOffered(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    /** The app may use the camera already; when not, it has to ask. */
    fun isAllowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

/**
 * The scanner as a screen of its own: the camera's picture, a line
 * saying what to point it at, and a way out. The first code read is the
 * one, and closes the screen.
 */
@Composable
fun CodeScannerScreen(onRead: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val read by rememberUpdatedState(onRead)
    val dismiss by rememberUpdatedState(onDismiss)
    val looking = remember { Executors.newSingleThreadExecutor() }
    val done = remember { AtomicBoolean(false) }
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(owner) {
        val main = ContextCompat.getMainExecutor(context)
        val coming = ProcessCameraProvider.getInstance(context)
        var cameras: ProcessCameraProvider? = null
        coming.addListener({
            val provider = runCatching { coming.get() }.getOrNull() ?: return@addListener
            cameras = provider
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(looking) { image ->
                image.use {
                    if (done.get()) return@use
                    val plane = it.planes[0]
                    val light = ByteArray(plane.buffer.remaining()).also { bytes -> plane.buffer.get(bytes) }
                    val text = QrReader.read(light, plane.rowStride, it.width, it.height)
                    // The first code is the one: the camera would otherwise
                    // hand the same one over at every frame.
                    if (!text.isNullOrEmpty() && done.compareAndSet(false, true)) {
                        main.execute {
                            read(text)
                            dismiss()
                        }
                    }
                }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure {
                // No camera on the back: whichever there is.
                runCatching { provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis) }
            }
        }, main)
        onDispose {
            cameras?.unbindAll()
            looking.shutdown()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Typed {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView({ view }, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Box(Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))) {
                        DialogKey(stringResource(R.string.cancel), onClick = onDismiss)
                    }
                    Text(
                        stringResource(R.string.point_the_camera_at_the_code_on),
                        color = Color.White, style = ui(14f, FontWeight.Medium), textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = Theme.gutter).padding(bottom = 28.dp)
                            .clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
