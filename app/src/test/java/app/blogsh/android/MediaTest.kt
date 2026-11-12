package app.blogsh.android

import app.blogsh.android.model.Media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the phone reads a picked item by: the model's tests are in core/. */
class MediaTest {
    /**
     * What comes from the device's files is whatever its keeper says it
     * is -- and a volume plugged in, or a cloud, may say nothing, or only
     * "a file". Then the name decides; a video taken for a picture would
     * be handed to the picture decoder and come back as unreadable.
     */
    @Test
    fun aFileIsAVideoByWhatItsKeeperSaysOrByItsName() {
        assertTrue(Media.isVideo("video/mp4", "IMG_0001"))
        assertTrue(Media.isVideo("VIDEO/quicktime", null))
        assertFalse(Media.isVideo("image/jpeg", "clip.mp4"))
        assertTrue(Media.isVideo(null, "Holiday.MOV"))
        assertTrue(Media.isVideo("application/octet-stream", "clip.mp4"))
        assertFalse(Media.isVideo("application/octet-stream", "photo.jpg"))
        assertFalse(Media.isVideo(null, null))
        assertFalse(Media.isVideo("", ""))
    }

    /** The library saying it needs the network is that, whatever the device thinks of its own. */
    @Test
    fun theLibrarysOwnWordForTheNetworkIsBelieved() {
        assertEquals(Media.Unread.NeedsNetwork, Media.unread(java.net.UnknownHostException("photos.example"), online = true))
        val wrapped = java.io.FileNotFoundException("no such file").apply { initCause(java.net.SocketTimeoutException("timeout")) }
        assertEquals(Media.Unread.NeedsNetwork, Media.unread(wrapped, online = true))
        assertEquals(Media.Unread.NeedsNetwork, Media.unread(java.net.ConnectException("refused"), online = true))
    }

    /**
     * Any failure on a device with no network is most likely the same
     * thing; with a network, it is a picture that could not be read and
     * no more.
     */
    @Test
    fun withoutANetworkAFailureIsTakenForIt() {
        val other = java.io.FileNotFoundException("no such file")
        assertEquals(Media.Unread.NeedsNetwork, Media.unread(other, online = false))
        assertEquals(Media.Unread.NeedsNetwork, Media.unread(null, online = false))
        assertEquals(Media.Unread.Unreadable, Media.unread(other, online = true))
        assertEquals(Media.Unread.Unreadable, Media.unread(null, online = true))
    }
}
