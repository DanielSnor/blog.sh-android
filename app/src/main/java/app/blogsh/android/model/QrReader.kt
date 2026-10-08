package app.blogsh.android.model

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * A QR code read out of a picture: what the camera hands over is looked
 * through for one, and what it says comes back as text. Whether that
 * text is a code of the blog's is for whoever asked to say.
 */
object QrReader {
    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    /**
     * `luminance`: how light each point is, one byte a point, row after
     * row -- a row `stride` bytes long, of which the first `width` are
     * the picture. A code drawn light on dark -- a terminal by night --
     * is a code too.
     */
    fun read(luminance: ByteArray, stride: Int, width: Int, height: Int): String? {
        // A picture that is not all there is not looked through: the reader
        // would walk off its end.
        if (width <= 0 || height <= 0 || stride < width || luminance.size < stride.toLong() * (height - 1) + width) return null
        val source = try {
            PlanarYUVLuminanceSource(luminance, stride, height, 0, 0, width, height, false)
        } catch (e: IllegalArgumentException) {
            return null
        }
        for (picture in listOf(source, source.invert())) {
            try {
                return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(picture)), hints).text
            } catch (e: ReaderException) {
                // No code in this one.
            } catch (e: RuntimeException) {
                // Something in the picture the reader took for a code and could not follow.
            }
        }
        return null
    }
}
