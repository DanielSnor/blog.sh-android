package app.blogsh.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.blogsh.android.BlogshApp
import app.blogsh.android.BuildConfig
import app.blogsh.android.model.Media
import app.blogsh.android.model.Shot
import app.blogsh.android.model.TextFile
import app.blogsh.android.model.read
import java.io.File
import java.util.Date
import java.util.Locale

// What the screens ask of the system they are drawn on, as Android
// answers it. The screens are the same on a desktop: the desktop app
// compiles them too, and answers these same names in its own file
// (blog.sh-desktop, OnDesktop.kt). Each one here hands the question to
// what the screens asked before, unchanged.

/** The words under this id, in the app's language. */
@Composable
fun stringResource(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
fun stringResource(id: Int, vararg args: Any): String = androidx.compose.ui.res.stringResource(id, *args)

/** One of Symbols, or another picture among the app's resources. */
@Composable
fun painterResource(id: Int): Painter = androidx.compose.ui.res.painterResource(id)

/** A face of type among the app's resources: one weight of it, or one place on a variable face's axis. */
@OptIn(ExperimentalTextApi::class)
fun appFont(id: Int, weight: FontWeight, variation: FontVariation.Settings? = null): Font =
    if (variation == null) Font(id, weight) else Font(id, weight, variationSettings = variation)

/**
 * The system the screens are drawn on, where a screen has to ask it
 * something of its own: a word outside composition, the clipboard, a
 * picture or a file picked.
 */
class Host(val context: Context) {
    fun getString(id: Int): String = context.getString(id)

    fun getString(id: Int, vararg args: Any): String = context.getString(id, *args)
}

@Composable
fun rememberHost(): Host {
    val context = LocalContext.current
    return remember(context) { Host(context) }
}

/** The way back: the system's key, gesture or button. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

/** A sheet: a window of its own over the screen, as wide and as tall as it needs. */
@Composable
fun SheetDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) =
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false), content = content)

/**
 * A sheet is a window of its own, and the system draws its bars' marks
 * light unless told otherwise: on paper they have to be dark, by night light.
 */
@Composable
fun SheetBars() {
    val view = LocalView.current
    val dark = wearsNight()
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/** A display with room for longer steps of type: a tablet, read from further away than a phone. */
@Composable
fun wideDisplay(): Boolean = LocalConfiguration.current.smallestScreenWidthDp >= 600

/** The size of type the system's own settings ask for. */
@Composable
fun systemFontScale(): Float = LocalConfiguration.current.fontScale

/** The app's version, as it is named by it: 0.9.0. */
val appVersion: String get() = BuildConfig.VERSION_NAME

/** A size in bytes the way the system says one: 2.4 MB. */
fun fileSize(host: Host, bytes: Long): String = Formatter.formatShortFileSize(host.context, bytes)

/** A number of hours in words: 12 hours. */
fun hoursInWords(hours: Long): String =
    MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE).format(Measure(hours, MeasureUnit.HOUR))

/** The pattern the language writes these parts of a date in, in its own order. */
fun bestDatePattern(locale: Locale, skeleton: String): String = DateFormat.getBestDateTimePattern(locale, skeleton)

/** A day the way the system's settings write one. */
fun mediumDate(host: Host, moment: Date): String = DateFormat.getMediumDateFormat(host.context).format(moment)

/** An hour the way the system's settings write one. */
fun timeOfDay(host: Host, moment: Date): String = DateFormat.getTimeFormat(host.context).format(moment)

/** Whether the system's settings count the hours to 24. */
fun is24Hour(host: Host): Boolean = DateFormat.is24HourFormat(host.context)

/** This device as a blog's list of devices will name it. */
fun deviceName(host: Host): String =
    Settings.Global.getString(host.context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: ""

/** The text on the clipboard; null where there is none. */
fun pasteText(host: Host): String? {
    val clipboard = host.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(host.context)?.toString()
}

/** Text put on the clipboard, under a label the system may show. */
fun copyText(host: Host, label: String, text: String) {
    val clipboard = host.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Where what the system may clear is kept. */
val cacheDir: File get() = BlogshApp.context.cacheDir

/** A picture's bytes made into one to draw; null where they are no picture. */
fun decodeImage(data: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()

/** A picture, a video or a file, as the system handed it over. */
typealias Picked = Uri

/** The photo library: pictures and videos, any number. */
@Composable
fun rememberMediaPicker(onPicked: (List<Picked>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { onPicked(it) }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
}

/** The device's folders and whatever is plugged into it: pictures and videos, any number. */
@Composable
fun rememberFilesPicker(onPicked: (List<Picked>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { onPicked(it) }
    return { launcher.launch(Media.FROM_FILES) }
}

/** One text file, for the New post form. */
@Composable
fun rememberTextFilePicker(onPicked: (Picked) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file -> if (file != null) onPicked(file) }
    return { launcher.launch(TextFile.KINDS) }
}

/** The camera asked for; the answer said back. */
@Composable
fun rememberCameraAsking(onAnswer: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAnswer(it) }
    return { launcher.launch(android.Manifest.permission.CAMERA) }
}

/** What was picked, read into the form's card. */
suspend fun shotOf(host: Host, item: Picked, index: Int, taken: List<String>): Shot = Media.shot(host.context, item, index, taken)

/** A text file picked, read and taken apart. */
suspend fun readTextFile(host: Host, item: Picked): TextFile.Read = TextFile.read(host.context, item)
