package app.blogsh.android.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import app.blogsh.android.BlogshApp
import app.blogsh.android.BuildConfig
import app.blogsh.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * What the model asks of the device (`Device`), as a phone answers it:
 * the app's files, its preferences, the Android Keystore, its words in
 * the app's resources. The model itself lives in `core/`, the same for
 * the desktop app; what only Android has is here.
 */
object Phone {
    /** Said once, as the app starts, before anything asks the model. */
    fun install(context: Context) {
        Device.files = context.filesDir
        Device.notes = PreferenceNotes(context)
        Device.vault = KeystoreVault(context.filesDir)
        Device.language = { BlogshApp.spoken }
        Device.thumbnail = { Media.thumbnail(it) }
        Device.stamp = {
            runCatching { BuildStamp.of(context.assets.open("BuildStamp.txt").bufferedReader().use { it.readText() }) }
                .getOrDefault(BuildStamp(null, null))
                .copy(version = BuildConfig.VERSION_NAME.ifEmpty { null })
        }
        Device.log = { what, error -> Log.w("blogsh", what, error) }
        Spoken.source = { word, args -> BlogshApp.context.getString(word.id, *args) }
    }
}

/** The app's own preferences. */
class PreferenceNotes(context: Context, name: String = "blogsh") : Notes {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun read(key: String): String? = runCatching { prefs.getString(key, null) }.getOrNull()
    override fun keys(): Set<String> = prefs.all.keys
    override fun write(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}

/**
 * The seeds of the blogs' keys in the app's own files, each encrypted
 * with a key that lives in the Android Keystore -- the counterpart of the
 * keychain item on iOS -- and the app's files are kept out of every
 * backup.
 */
class KeystoreVault(private val files: File) : KeyVault {
    private fun file(account: String) = File(File(files, "keys").apply { mkdirs() }, "$account.bin")
    private fun alias(account: String) = "blogsh-wrap-$account"

    override fun has(account: String): Boolean = file(account).exists()

    override fun read(account: String): ByteArray? {
        val file = file(account)
        if (!file.exists()) return null
        val all = file.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(account), GCMParameterSpec(128, all, 0, 12))
        return cipher.doFinal(all, 12, all.size - 12)
    }

    override fun write(account: String, seed: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey(account))
        file(account).writeBytes(cipher.iv + cipher.doFinal(seed))
    }

    override fun delete(account: String) {
        file(account).delete()
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.runCatching { deleteEntry(alias(account)) }
    }

    private fun wrappingKey(account: String): SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias(account), null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias(account), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}

private var watching = false

/**
 * The device's network watched from the start, so that it is known by
 * the time a picture is chosen. A device that will not say is taken to
 * have a network.
 */
@Synchronized
fun NetworkWatch.begin(context: Context) {
    if (watching) return
    watching = true
    runCatching {
        val system = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        system.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = found()

            override fun onLost(network: Network) = lost()
        })
    }
}

/** The file the picker lent, read whole -- and no further than a text can be long -- and taken apart. */
suspend fun TextFile.read(context: Context, uri: Uri): TextFile.Read = withContext(Dispatchers.IO) {
    val data = try {
        context.contentResolver.openInputStream(uri)?.use { bytes(it) }
    } catch (e: Exception) {
        // Kept where a device's log can be read: the keeper's own reason.
        Log.w("blogsh", "text file not read", e)
        null
    } ?: throw TextFile.NotTaken(TextFile.Failure.Unreadable)
    read(data)
}

/** A word of the screens', said where the model's are: by its id among the app's resources. */
fun Spoken.say(id: Int, vararg args: Any): String = BlogshApp.context.getString(id, *args)

/** The word for what an engine command is doing, by the command. */
object Doing {
    fun word(args: List<String>): Int = when (args.firstOrNull()) {
        "publish" -> R.string.publishing
        "unpublish" -> R.string.unpublishing
        "delete" -> R.string.deleting
        "restore" -> R.string.restoring
        "toot", "bluesky" -> R.string.announcing
        "empty" -> R.string.clearing_out_2
        "rebuild" -> R.string.rebuilding
        "check", "doctor" -> R.string.checking
        else -> R.string.saving
    }
}

/** Where the model's own words are among the app's resources: every one of them, or the build says which is missing. */
val Word.id: Int
    get() = when (this) {
        Word.something_else_holds_the_blog_just_now -> R.string.something_else_holds_the_blog_just_now
        Word.the_app_has_no_key_yet -> R.string.the_app_has_no_key_yet
        Word.the_blog_said_no_2 -> R.string.the_blog_said_no_2
        Word.the_engine_did_not_answer_as_data -> R.string.the_engine_did_not_answer_as_data
        Word.the_file_could_not_be_read -> R.string.the_file_could_not_be_read
        Word.the_file_is_too_large_to_be -> R.string.the_file_is_too_large_to_be
        Word.the_server_closed_the_connection_without_an -> R.string.the_server_closed_the_connection_without_an
        Word.the_server_did_not_answer_is_this -> R.string.the_server_did_not_answer_is_this
        Word.the_server_does_not_know_this_blog -> R.string.the_server_does_not_know_this_blog
        Word.the_server_does_not_know_this_blog_2 -> R.string.the_server_does_not_know_this_blog_2
        Word.the_server_is_not_set_up_yet -> R.string.the_server_is_not_set_up_yet
        Word.the_server_s_key_changed_if_the -> R.string.the_server_s_key_changed_if_the
        Word.the_site_does_not_show_this_change -> R.string.the_site_does_not_show_this_change
        Word.there_is_no_text_in_the_file -> R.string.there_is_no_text_in_the_file
        Word.this_file_is_not_plain_text_a -> R.string.this_file_is_not_plain_text_a
        Word.this_is_the_practice_blog_it_lives -> R.string.this_is_the_practice_blog_it_lives
        Word.two_posts_in_different_years_share_this -> R.string.two_posts_in_different_years_share_this
    }
