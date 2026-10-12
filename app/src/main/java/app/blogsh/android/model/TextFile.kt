package app.blogsh.android.model

import android.content.Context
import android.net.Uri
import app.blogsh.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * A finished text taken into the New post form from a file: one written
 * elsewhere -- dictated, kept in a cloud folder, come back to more than
 * once -- which was carried over through the clipboard until now. The
 * file is read and left as it is; nothing goes back into it.
 *
 * All of it is the post's text: a title is not guessed from its first
 * line. Only where the file opens with a header between two `---` are
 * its `title` and `tags` read out, for the form's own fields, and the
 * rest of the header is left behind -- the form writes the header the
 * blog gets, and the blog reads one.
 */
object TextFile {
    /** What was read: the text, and what a header said of it. */
    data class Read(val title: String? = null, val tags: String? = null, val body: String) {
        /**
         * What a field of the form holds once the file was taken: a
         * field written in is left as it is, an empty one takes what
         * the header said.
         */
        fun filling(field: String, said: String?): String = if (field.isBlank() && said != null) said else field
    }

    enum class Failure(private val sentence: Int) {
        Unreadable(R.string.the_file_could_not_be_read),

        /** Not plain text: a document of a word processor, or anything else. */
        NotText(R.string.this_file_is_not_plain_text_a),
        Empty(R.string.there_is_no_text_in_the_file),
        TooLarge(R.string.the_file_is_too_large_to_be);

        /** The sentence for it, in the reader's language. */
        val words: String get() = Spoken.say(sentence)
    }

    /** The file was not taken, and why. */
    class NotTaken(val why: Failure) : Exception(why.name)

    /**
     * What the picker is asked for: plain text and Markdown -- and a
     * file its keeper calls nothing in particular, which is what a
     * Markdown file is to many of them. A word processor's document has
     * a kind of its own, and stays grey there; whether a file of no
     * kind is text is seen once it is read.
     */
    val KINDS = arrayOf("text/plain", "text/markdown", "text/x-markdown", "application/octet-stream")

    /** More than this is no post's text; a file that large was picked by mistake. */
    const val CEILING = 1_000_000

    /** The file the picker lent, read whole -- and no further than a text can be long -- and taken apart. */
    suspend fun read(context: Context, uri: Uri): Read = withContext(Dispatchers.IO) {
        val data = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val held = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (held.size() <= CEILING) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    held.write(buffer, 0, count)
                }
                held.toByteArray()
            }
        } catch (e: Exception) {
            // Kept where a device's log can be read: the keeper's own reason.
            android.util.Log.w("blogsh", "text file not read", e)
            null
        } ?: throw NotTaken(Failure.Unreadable)
        read(data)
    }

    fun read(data: ByteArray): Read {
        if (data.size > CEILING) throw NotTaken(Failure.TooLarge)
        val text = text(data) ?: throw NotTaken(Failure.NotText)
        val read = read(text)
        if (read.body.isEmpty() && read.title == null && read.tags == null) throw NotTaken(Failure.Empty)
        return read
    }

    /**
     * The bytes as text, whatever wrote them: UTF-8 with its mark or
     * without, UTF-16 with its mark or without, and -- for a file from
     * an older Windows -- the code pages Central and Western Europe
     * wrote in. Null for what is not text at all.
     */
    fun text(data: ByteArray): String? {
        fun starts(vararg bytes: Int) = data.size >= bytes.size && bytes.indices.all { data[it] == bytes[it].toByte() }
        var text = when {
            starts(0xEF, 0xBB, 0xBF) -> decoded(data, Charsets.UTF_8, from = 3)
            starts(0xFF, 0xFE) || starts(0xFE, 0xFF) -> decoded(data, Charsets.UTF_16)
            else -> unmarkedUtf16(data) ?: decoded(data, Charsets.UTF_8)
                // Not UTF-8: a code page. Central Europe's first -- it is what this
                // app's blogs are written in -- and each only where every byte is a letter of it.
                ?: listOf("windows-1250", "windows-1252", "ISO-8859-2").firstNotNullOfOrNull { name ->
                    runCatching { Charset.forName(name) }.getOrNull()?.let { decoded(data, it) }
                }
        } ?: return null
        if (text.startsWith("﻿")) text = text.drop(1)
        // What a word processor saves under a text file's name, and
        // anything with a byte no text has.
        if (text.startsWith("{\\rtf") || text.contains('\u0000')) return null
        return text.replace("\r\n", "\n").replace("\r", "\n")
    }

    /** The bytes in this encoding, or null where a byte among them is none of its letters. */
    private fun decoded(data: ByteArray, charset: Charset, from: Int = 0): String? = runCatching {
        charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(data, from, data.size - from)).toString()
    }.getOrNull()

    /**
     * UTF-16 written without its mark: every other byte of a text in
     * Latin letters is a zero there, and which half says which end comes
     * first. Read as UTF-8, such a file would pass for text full of
     * bytes no text has.
     */
    private fun unmarkedUtf16(data: ByteArray): String? {
        if (data.size < 4 || data.size % 2 != 0) return null
        var even = 0
        var odd = 0
        val looked = minOf(data.size, 4096)
        for (at in 0 until looked) {
            if (data[at] != 0.toByte()) continue
            if (at % 2 == 0) even += 1 else odd += 1
        }
        val half = looked / 2
        if (odd * 10 > half * 3 && even * 10 < half) return decoded(data, Charsets.UTF_16LE)
        if (even * 10 > half * 3 && odd * 10 < half) return decoded(data, Charsets.UTF_16BE)
        return null
    }

    /**
     * The text taken apart: a header, where the text opens with one and
     * closes it, gives its title and its tags and is gone from the text.
     * A `---` that nothing closes is a line of the text.
     */
    fun read(text: String): Read {
        val lines = text.split("\n")
        fun isFence(line: String) = line.trim(' ', '\t') == "---"
        val end = if (isFence(lines.first())) lines.drop(1).indexOfFirst(::isFence) + 1 else 0
        if (end <= 0) return Read(body = text.trim())
        val header = lines.subList(1, end)
        return Read(title(header), tags(header), lines.drop(end + 1).joinToString("\n").trim())
    }

    /** What stands after `key:` on its own line of the header, and where. */
    private fun value(key: String, header: List<String>): Pair<Int, String>? {
        val at = header.indexOfFirst { it.startsWith("$key:") }
        return if (at < 0) null else at to header[at].drop(key.length + 1).trim(' ', '\t')
    }

    private fun unquoted(value: String): String {
        if (value.length < 2 || value.first() != value.last() || value.first() !in "\"'") return value
        return value.substring(1, value.length - 1).trim(' ', '\t')
    }

    private fun title(header: List<String>): String? = value("title", header)?.let { unquoted(it.second) }?.ifEmpty { null }

    /**
     * The tags as the form's field holds them, comma separated --
     * whether the header lists them on the line, in brackets, or one to
     * a line under it.
     */
    private fun tags(header: List<String>): String? {
        val (at, said) = value("tags", header) ?: return null
        val raw = if (said.isEmpty()) {
            header.drop(at + 1).takeWhile { it.trim(' ', '\t').startsWith("-") }.map { it.trim(' ', '\t').drop(1) }
        } else {
            (if (said.startsWith("[") && said.endsWith("]")) said.substring(1, said.length - 1) else said).split(",")
        }
        val tags = raw.map { unquoted(it.trim(' ', '\t')) }.map { it.removePrefix("#") }.filter { it.isNotEmpty() }
        return if (tags.isEmpty()) null else tags.joinToString(", ")
    }
}
