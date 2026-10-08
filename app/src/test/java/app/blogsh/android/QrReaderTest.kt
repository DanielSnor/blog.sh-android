package app.blogsh.android

import app.blogsh.android.model.PairingCode
import app.blogsh.android.model.QrReader
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A code read out of the camera's picture. A mistake here is a code on
 * the server's screen that the app looks at and does not see.
 */
class QrReaderTest {
    private val link = "blogsh://pair?v=1&h=blog.example.org&p=2222&u=me&k=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8" +
        "&f=nThbg6kXUpJWGl7E1IGOCspRomTxdCARLviKw6E5SY8.47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU&n=M%C5%AFj%20blog"

    /**
     * The picture a camera would hand over: the code drawn `scale` points
     * to a module on a ground, each row `stride` bytes long.
     */
    private fun picture(text: String, scale: Int = 6, stride: Int? = null, dark: Int = 20, light: Int = 235): Triple<ByteArray, Int, Pair<Int, Int>> {
        val modules = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4))
        val width = modules.width * scale
        val height = modules.height * scale
        val row = stride ?: width
        val bytes = ByteArray(row * (height - 1) + width)
        for (y in 0 until height) for (x in 0 until width) {
            bytes[y * row + x] = (if (modules.get(x / scale, y / scale)) dark else light).toByte()
        }
        return Triple(bytes, row, width to height)
    }

    @Test
    fun aCodeInThePictureIsReadAsItsText() {
        val (bytes, stride, size) = picture(link)
        val text = QrReader.read(bytes, stride, size.first, size.second)
        assertEquals(link, text)
        assertEquals("Můj blog", PairingCode(text!!).site)
    }

    /** A camera's rows are often longer than the picture is wide: what is past its edge is not the picture. */
    @Test
    fun rowsLongerThanThePictureAreReadByItsWidth() {
        val (bytes, stride, size) = picture(link, stride = 6 * 200)
        assertEquals(link, QrReader.read(bytes, stride, size.first, size.second))
    }

    /** A terminal by night draws the code light on dark. */
    @Test
    fun aCodeDrawnLightOnDarkIsACodeToo() {
        val (bytes, stride, size) = picture(link, dark = 235, light = 20)
        assertEquals(link, QrReader.read(bytes, stride, size.first, size.second))
    }

    @Test
    fun aPictureWithoutACodeSaysNothing() {
        assertNull(QrReader.read(ByteArray(320 * 240) { 128.toByte() }, 320, 320, 240))
        assertNull(QrReader.read(ByteArray(320 * 240) { ((it * 31) % 251).toByte() }, 320, 320, 240))
        // A buffer shorter than it says it is: nothing, not a crash.
        assertNull(QrReader.read(ByteArray(10), 320, 320, 240))
    }
}
