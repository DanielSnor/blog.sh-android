package app.blogsh.android.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Whether a newer version of the app has been released: asked of the
 * repository's releases on GitHub, when somebody taps the mark at the foot
 * of the settings -- never by itself. Nothing is downloaded but a small
 * answer about the newest release, and nothing is installed: the release's
 * page is opened, and the APK is taken from there by whoever asked.
 *
 * A release is named by its tag, `v0.8.0`; one before it `v0.8.0-beta.1`
 * or `v0.8.0-rc.1`. GitHub's newest release is never a pre-release, so
 * what is offered here is the newest finished one.
 */
object Updates {
    /** The newest release that is not a draft and not a pre-release. */
    const val LATEST = "https://api.github.com/repos/DanielSnor/blog.sh-android/releases/latest"

    /** What there is to say after asking. */
    sealed class Said {
        /** A newer version is out: its name as the release calls it, and its page. */
        data class Newer(val version: String, val page: String) : Said()

        /** What runs here is the newest there is -- or newer, a build not released yet. */
        data object Newest : Said()

        /** Nothing has been released yet. */
        data object NoneYet : Said()

        /** GitHub could not be asked, or its answer not read. */
        data object Unknown : Said()
    }

    /** Asks GitHub, off the screen's thread. */
    suspend fun ask(current: String, from: String = LATEST): Said = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(from).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "blogsh-android/$current")
            try {
                when (connection.responseCode) {
                    200 -> said(connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8), current)
                    // A repository with no release answers that there is none to be had.
                    404 -> Said.NoneYet
                    else -> Said.Unknown
                }
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(Said.Unknown)
    }

    /** GitHub's answer about its newest release, read against the version that runs here. */
    fun said(answer: String, current: String): Said {
        val release = runCatching { EngineJson.parseToJsonElement(answer) as JsonObject }.getOrNull() ?: return Said.Unknown
        fun text(key: String) = (release[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
        val tag = text("tag_name")?.takeIf { it.isNotEmpty() } ?: return Said.Unknown
        val page = text("html_url")?.takeIf { it.startsWith("https://github.com/") } ?: return Said.Unknown
        return if (isNewer(tag, current)) Said.Newer(tag.trimStart('v', 'V'), page) else Said.Newest
    }

    /** A version as it is ranked: its numbers, then how finished it is, then which of those. */
    private class Ranked(val numbers: List<Int>, val stage: Int, val step: Int)

    private const val ALPHA = 0
    private const val BETA = 1
    private const val RC = 2
    private const val FINISHED = 3

    /**
     * True where `candidate` is a later version than `current`: by its
     * numbers, part by part (0.10 comes after 0.9), and at the same
     * numbers a finished one after its release candidates, those after
     * the betas and the betas after the alphas. A `v` before the numbers
     * says nothing.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = ranked(candidate)
        val b = ranked(current)
        for (i in 0 until maxOf(a.numbers.size, b.numbers.size)) {
            val x = a.numbers.getOrElse(i) { 0 }
            val y = b.numbers.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        if (a.stage != b.stage) return a.stage > b.stage
        return a.step > b.step
    }

    private fun ranked(version: String): Ranked {
        val plain = version.trim().dropWhile { !it.isDigit() }
        val core = plain.takeWhile { it.isDigit() || it == '.' }
        val numbers = core.split('.').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }.ifEmpty { listOf(0) }
        val suffix = plain.drop(core.length).dropWhile { !it.isLetterOrDigit() }.lowercase(Locale.ROOT)
        if (suffix.isEmpty()) return Ranked(numbers, FINISHED, 0)
        val word = suffix.takeWhile { it.isLetter() }
        val step = suffix.drop(word.length).filter { it.isDigit() }.toIntOrNull() ?: 0
        val stage = when (word) {
            "rc" -> RC
            "beta", "b" -> BETA
            else -> ALPHA
        }
        return Ranked(numbers, stage, step)
    }
}
