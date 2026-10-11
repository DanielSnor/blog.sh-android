package app.blogsh.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `pair --list` and `pair --revoke`, asked through the door: the devices
 * a code let in to the blog, in the order the terminal numbers them, and
 * which of them is asking. Who asks, the blog reads from the line that
 * let the connection in -- nothing the app says is listened to -- so a
 * device sees the others and takes off itself, and no other.
 */
@Serializable
data class PairedDevices(
    val ok: Boolean = true,
    val devices: List<Device> = emptyList(),
    /**
     * The key of the device asking. Null where the blog cannot tell: the
     * line that let it in was written by hand, or by a pairing from
     * before a line said whose it is.
     */
    @SerialName("self") val own: String? = null,
    /** After `--revoke`: the device that was taken off. */
    val revoked: Device? = null,
) {
    @Serializable
    data class Device(
        val name: String = "",
        /** As `ssh-keygen -l` writes it. Empty for a line whose key somebody damaged by hand. */
        val fingerprint: String = "",
        /** The blog says this is the device asking. */
        @SerialName("self") val own: Boolean = false,
    )

    /** The blog knows who is asking, and so can take that device off. */
    val mayLeave: Boolean get() = own != null

    /**
     * The row is this device: the blog says so -- or, where it cannot
     * tell who asks, the row carries the key the app holds.
     */
    fun isThis(device: Device, key: String?): Boolean = device.own || (device.fingerprint.isNotEmpty() && device.fingerprint == key)

    /** This device is among them, by either sign. */
    fun holdsThis(key: String?): Boolean = devices.any { isThis(it, key) }

    companion object {
        /** The blog is an older one, which lets no `pair` through its door. */
        fun notKnownThere(error: Throwable): Boolean = error.refusedAs == "unknown_command"

        /**
         * What went wrong after asking to be taken off is the taking off
         * itself: the answer was lost on its way, the app asked again, and
         * the server no longer knew the key.
         */
        fun isGone(after: Throwable): Boolean = after is EngineError.KeyNotKnown

        /**
         * What to say of a no. The key file held by another run is said in
         * the blog's own words: the app's sentence for a held blog speaks
         * of a build and a save, and this is neither.
         */
        fun words(error: Throwable): String {
            val refusal = (error as? EngineError.Refused)?.refusal
            if (refusal?.error == "busy" && !refusal.message.isNullOrBlank()) return refusal.message
            return error.said
        }
    }
}
