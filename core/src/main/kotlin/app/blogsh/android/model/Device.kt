package app.blogsh.android.model

import java.io.File
import java.util.Locale

/**
 * What the model asks of the machine it runs on: where the app's own
 * files lie, which language it speaks, where a few words and the keys
 * are kept, how a picture is made small, which build this is. The model
 * is the same on a phone and on a desktop; each app says these once,
 * when it starts, before anything asks.
 */
object Device {
    /** The directory the app's own files go in: the posts kept on it, the pictures that wait. */
    lateinit var files: File

    /** Where the blogs and the rest of the settings are kept between two runs. */
    lateinit var notes: Notes

    /** Where the blogs' keys are kept. */
    lateinit var vault: KeyVault

    /** The language the app's words are in: the one chosen, or the system's. */
    var language: () -> Locale = { Locale.getDefault() }

    /** The picture small, as JPEG -- what a card shows. Null where it cannot be read. */
    var thumbnail: (ByteArray) -> ByteArray? = { null }

    /** Which build this is, as the build wrote it into the app. */
    var stamp: () -> BuildStamp = { BuildStamp(null, null) }

    /** What went wrong where nobody is shown it, kept where the device's log can be read. */
    var log: (String, Throwable) -> Unit = { what, error -> System.err.println("blogsh: $what: $error") }
}

/**
 * Where the seed of each blog's key is kept, under the name of its
 * account: on a phone in the app's files, encrypted with a key of the
 * system's keystore; on a desktop in whatever the system keeps secrets
 * in. Nothing of it leaves the device.
 */
interface KeyVault {
    fun has(account: String): Boolean

    /** The 32 bytes of the seed; null where the account has no key. */
    fun read(account: String): ByteArray?

    fun write(account: String, seed: ByteArray)

    fun delete(account: String)
}
