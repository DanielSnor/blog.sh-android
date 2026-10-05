package app.blogsh.android.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4TimestampData
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.DefaultMuxer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What the picker hands over, made into what travels. A photograph is
 * shrunk and written as JPEG whatever it was -- HEIC included, which is
 * what a phone may write and what the /write/ page has to send whole for
 * the blog to convert. A video is made into H.264 in an MP4 inside
 * 1280 x 720: the size the page's own recipe sends, and a format every
 * browser plays. Neither carries where it was taken. What cannot be
 * converted goes as it is, as on the page.
 */
object Media {
    /** One picked item as a shot, or null when nothing could be read of it. */
    suspend fun shot(context: Context, uri: Uri, index: Int, taken: List<String>): Shot? = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri) ?: ""
        val original = displayName(context, uri)
        if (type.startsWith("video/")) return@withContext video(context, uri, original, index, taken)
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@withContext null
        val shrunk = shrink(bytes) ?: return@withContext null
        val name = Pictures.freeName(Pictures.safeName(original, index), taken)
        Shot(name, shrunk.first, shrunk.second, shrunk.third, thumb = thumbnail(shrunk.first))
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    /**
     * The bytes shrunk and re-encoded as JPEG; a picture already small
     * enough is re-encoded all the same, which also drops its GPS tags.
     * The decoder turns the picture the way it was held.
     */
    fun shrink(original: ByteArray, edge: Int = Pictures.MAX_EDGE, quality: Int = Pictures.QUALITY): Triple<ByteArray, Int, Int>? = runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(original))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val long = max(info.size.width, info.size.height)
            if (long > edge) {
                val scale = edge.toDouble() / long
                decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
            }
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val result = Triple(out.toByteArray(), bitmap.width, bitmap.height)
        bitmap.recycle()
        result
    }.getOrNull()

    /** The picture small, as JPEG: what a card shows. */
    fun thumbnail(original: ByteArray, edge: Int = 480): ByteArray? = shrink(original, edge, 75)?.first

    private suspend fun video(context: Context, uri: Uri, original: String?, index: Int, taken: List<String>): Shot? {
        val ending = (original ?: "").substringAfterLast('.', "").lowercase()
        val source = File(context.cacheDir, UUID.randomUUID().toString() + "." + ending.ifEmpty { "mp4" })
        try {
            val copied = runCatching { context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use { input.copyTo(it) } } }.getOrNull()
            if (copied == null) return null
            val poster = poster(source)
            val made = h264(context, source)
            if (made != null) {
                try {
                    val name = Pictures.freeName(Pictures.safeName(original, index, stem = "video", ext = "mp4"), taken)
                    return Shot(name, made.readBytes(), 0, 0, kind = Shot.Kind.Video, poster = poster)
                } finally {
                    made.delete()
                }
            }
            // Not converted: whole, under the ending it came with.
            val ext = if (ending in listOf("mp4", "mov", "m4v")) ending else "mov"
            val name = Pictures.freeName(Pictures.safeName(original, index, stem = "video", ext = ext), taken)
            return Shot(name, source.readBytes(), 0, 0, kind = Shot.Kind.Video, poster = poster, converted = false)
        } finally {
            source.delete()
        }
    }

    /** A frame of the video, small, for its card. */
    private fun poster(source: File): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.path)
            val frame = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 320) ?: return null
            ByteArrayOutputStream().also { frame.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** The short side the picture is scaled to, or null when it already fits in 1280 x 720. */
    fun shortSide(width: Int, height: Int): Int? {
        val short = min(width, height)
        val long = max(width, height)
        if (short <= 0) return null
        val scale = min(720.0 / short, 1280.0 / long)
        if (scale >= 1.0) return null
        val side = (short * scale).roundToInt()
        return side - side % 2
    }

    private class Source(val width: Int, val height: Int, val frames: Int)

    private fun source(file: File): Source? = runCatching {
        var frames = 0
        var width = 0
        var height = 0
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") != true) continue
                width = format.getInteger(MediaFormat.KEY_WIDTH)
                height = format.getInteger(MediaFormat.KEY_HEIGHT)
                extractor.selectTrack(i)
                while (extractor.sampleTime >= 0) {
                    frames += 1
                    if (!extractor.advance()) break
                }
                break
            }
        } finally {
            extractor.release()
        }
        Source(width, height, frames)
    }.getOrNull()

    /**
     * The video as H.264 in an MP4, 720p at most, or null when it cannot be
     * made. A device's own decoder may hand frames over out of order, and
     * what is then lost shows as a video shorter in frames than it was: so
     * the frames are counted, and where some are missing the export is done
     * once more with the plain software decoders.
     */
    @OptIn(UnstableApi::class)
    private suspend fun h264(context: Context, source: File): File? {
        val facts = source(source) ?: return null
        val first = export(context, source, facts, softwareDecoders = false)
        if (first != null && first.second.videoFrameCount >= facts.frames * 0.97) return first.first
        val second = export(context, source, facts, softwareDecoders = true)
        if (second != null && (first == null || second.second.videoFrameCount > first.second.videoFrameCount)) {
            first?.first?.delete()
            return second.first
        }
        second?.first?.delete()
        return first?.first
    }

    @OptIn(UnstableApi::class)
    private suspend fun export(context: Context, source: File, facts: Source, softwareDecoders: Boolean): Pair<File, ExportResult>? {
        val out = File(context.cacheDir, UUID.randomUUID().toString() + ".mp4")
        val side = shortSide(facts.width, facts.height)
        val effects: List<Effect> = side?.let { listOf(Presentation.createForShortSide(it)) } ?: emptyList()
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source))).setEffects(Effects(emptyList(), effects)).build()
        val composition = Composition.Builder(EditedMediaItemSequence.withAudioAndVideoFrom(listOf(item)))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
        // What the iOS app's export gives a 720p picture, and less for a smaller one.
        val scale = if (side == null) 1.0 else side.toDouble() / min(facts.width, facts.height)
        val pixels = facts.width * scale * facts.height * scale
        val bitrate = (2_400_000 * pixels / (1280.0 * 720.0)).toInt().coerceIn(400_000, 2_400_000)
        val result = suspendCancellableCoroutine<ExportResult?> { continuation ->
            // The transformer lives on a thread with a looper.
            Handler(Looper.getMainLooper()).post {
                val selector = MediaCodecSelector { mime, secure, tunneling ->
                    val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
                    val plain = all.filter { it.softwareOnly }
                    if (softwareDecoders && plain.isNotEmpty()) plain else all
                }
                val decoders = DefaultDecoderFactory.Builder(context).setMediaCodecSelector(selector).build()
                val encoders = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                    .build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setMuxerFactory(PlacelessMuxer.Factory(DefaultMuxer.Factory()))
                    .setAssetLoaderFactory(DefaultAssetLoaderFactory(context, decoders, Clock.DEFAULT, null))
                    .setEncoderFactory(encoders)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(exportResult)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                    .build()
                continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { runCatching { transformer.cancel() } } }
                try {
                    transformer.start(composition, out.path)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
        if (result == null || !out.exists() || out.length() == 0L) {
            out.delete()
            return null
        }
        return out to result
    }
}

/**
 * A muxer that does not write down where the video was taken. Media3 hands
 * the source's own metadata on to the output -- the place among it, as
 * `©xyz` and as the QuickTime key -- so what a photograph loses when it is
 * re-encoded a video would keep. Only the dates pass.
 */
@OptIn(UnstableApi::class)
class PlacelessMuxer(private val inner: Muxer) : Muxer {
    class Factory(private val inner: Muxer.Factory) : Muxer.Factory {
        override fun create(path: String): Muxer = PlacelessMuxer(inner.create(path))
        override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> = inner.getSupportedSampleMimeTypes(trackType)
        override fun supportsWritingNegativeTimestampsInEditList(): Boolean = inner.supportsWritingNegativeTimestampsInEditList()
    }

    override fun addTrack(format: Format): Int = inner.addTrack(format)
    override fun writeSampleData(trackId: Int, byteBuffer: ByteBuffer, bufferInfo: BufferInfo) = inner.writeSampleData(trackId, byteBuffer, bufferInfo)
    override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
        if (metadataEntry is Mp4TimestampData) inner.addMetadataEntry(metadataEntry)
    }

    override fun close() = inner.close()
}
