package app.blogsh.android.model

import java.util.Locale

/**
 * What a delivery weighs and whether the blog will take it -- the line
 * the /write/ page keeps under its pictures, and the legend beside it.
 */
object Delivery {
    /** The words the line is made of, in the reader's language. */
    class Words(
        val pictureOne: String = "picture",
        val pictureFew: String = "pictures",
        val pictureMany: String = "pictures",
        val videoOne: String = "video",
        val videoFew: String = "videos",
        val videoMany: String = "videos",
        val textOnly: String = "text only",
    )

    /**
     * The encoded stream, the thing the receiver measures: every file's
     * base64 with its line breaks, its name, and the line that ends it.
     */
    fun wireBytes(shots: List<Shot>, textBytes: Int): Int =
        shots.sumOf { encodedSize(it.data.size) + 80 } + encodedSize(textBytes + 200) + 80

    fun over(shots: List<Shot>, textBytes: Int, maxMb: Int): Boolean =
        maxMb > 0 && wireBytes(shots, textBytes) > maxMb * 1_048_576

    fun size(bytes: Long, locale: Locale = Locale.getDefault()): String {
        val kb = maxOf(if (bytes > 0) 1L else 0L, Math.round(bytes / 1024.0))
        if (kb < 1024) return "$kb kB"
        return String.format(locale, "%.1f", bytes / 1_048_576.0) + " MB"
    }

    fun size(bytes: Int, locale: Locale = Locale.getDefault()): String = size(bytes.toLong(), locale)

    /**
     * "3 pictures, 1 video, 4.2 MB", with the plural the reader's language
     * wants: one, a few (two to four, which Czech counts differently), many.
     */
    fun describe(shots: List<Shot>, textBytes: Int, words: Words = Words()): String {
        val videos = shots.count { it.kind == Shot.Kind.Video }
        val pictures = shots.size - videos
        val parts = mutableListOf<String>()
        if (pictures > 0) parts.add("$pictures " + if (pictures == 1) words.pictureOne else if (pictures < 5) words.pictureFew else words.pictureMany)
        if (videos > 0) parts.add("$videos " + if (videos == 1) words.videoOne else if (videos < 5) words.videoFew else words.videoMany)
        if (parts.isEmpty()) parts.add(words.textOnly)
        return parts.joinToString(", ") + ", " + size(shots.sumOf { it.data.size.toLong() } + textBytes)
    }
}
