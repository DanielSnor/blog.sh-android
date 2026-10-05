package app.blogsh.android.model

import java.text.Normalizer
import java.util.UUID

/**
 * A picture or a video chosen for a post: its bytes as they will travel
 * (a picture as JPEG with the long edge capped, the way /write/ shrinks a
 * phone photograph; a video as H.264 in an MP4), the name the markdown
 * refers to it by, and its description.
 */
class Shot(
    val name: String,
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val alt: String = "",
    val kind: Kind = Kind.Picture,
    /** A frame of a video, for its card. */
    val poster: ByteArray? = null,
    /** False for a video that could not be converted and goes as it came. */
    val converted: Boolean = true,
    /**
     * The shot small, for its card: drawn again with every letter typed
     * beside it, which the whole picture is too heavy for.
     */
    val thumb: ByteArray? = null,
    val id: String = UUID.randomUUID().toString(),
) {
    enum class Kind { Picture, Video }

    fun withAlt(alt: String) = Shot(name, data, width, height, alt, kind, poster, converted, thumb, id)

    /**
     * The shot as a line of markdown, a paragraph of its own. One mark or
     * two: a picture is ![…](name), a video !![…](name).
     */
    val mark: String get() = (if (kind == Kind.Video) "!!" else "!") + "[${alt.trim()}]($name)"

    /** The shot's mark wherever the text has it, whatever it says there. */
    val markPattern: Regex get() = Regex("""!{1,2}\[[^\]]*\]\(""" + Regex.escape(name) + """\)""")
}

/** A word folded the way a file name is: accents off, lower case. */
internal fun folded(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "").lowercase()

/**
 * What /write/ does to a name, so a post from the app is the same post a
 * post from the page would be. What it does to the picture itself is in
 * Media.kt, with the rest of what needs the device.
 */
object Pictures {
    /** Long edge; a phone photo is far larger than any blog needs. */
    const val MAX_EDGE = 2560
    const val QUALITY = 88

    /**
     * A file name the receiver takes and a reader recognises: the
     * original's stem, folded to a-z0-9 and dashes, `.jpg` on the end.
     */
    fun safeName(original: String?, index: Int, stem: String = "photo", ext: String = "jpg"): String {
        var base = (original ?: "").replace(Regex("""\.[^.]*$"""), "")
        base = folded(base)
        base = base.replace(Regex("[^a-z0-9]+"), "-")
        base = base.trim('-')
        base = base.take(100)
        return (if (base.isEmpty()) "$stem-$index" else base) + "." + ext
    }

    /** Two photographs can fold to one name; the second gets a number. */
    fun freeName(name: String, taken: List<String>): String {
        if (!taken.contains(name)) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot >= 0) name.substring(0, dot) else name
        val ext = if (dot >= 0) name.substring(dot) else ""
        var n = 2
        while (taken.contains("$stem-$n$ext")) n += 1
        return "$stem-$n$ext"
    }
}

/**
 * The post as a markdown file, the way /write/ writes it: a header of
 * what the form has fields for, then the text.
 */
object Markdown {
    fun frontMatter(title: String, tags: String, publish: Boolean = false): String {
        val lines = mutableListOf<String>()
        val cleanTitle = title.trim()
            .replace(Regex("""^(["'])(.*)\1$"""), "$2")
            .replace(Regex("""^\[(.*)\]$"""), "$1")
            .trim(' ', '\t')
        if (cleanTitle.isNotEmpty()) lines.add("title: $cleanTitle")
        val cleanTags = tags.trim(' ', '\t')
            .replace(Regex("""^\[|\]$"""), "")
            .split(",")
            .map {
                it.trim(' ', '\t')
                    .replace(Regex("^#"), "")
                    .replace(Regex("""^\[|\]$"""), "")
                    .replace(Regex("""^["']|["']$"""), "")
                    .trim(' ', '\t')
            }
            .filter { it.isNotEmpty() }
        if (cleanTags.isNotEmpty()) lines.add("tags: " + cleanTags.joinToString(", "))
        if (publish) lines.add("publish: yes")
        return if (lines.isEmpty()) "" else "---\n" + lines.joinToString("\n") + "\n---\n\n"
    }

    fun file(title: String, tags: String, body: String, publish: Boolean = false): String {
        val text = body.trim()
        val header = frontMatter(title, tags, publish)
        // A body that itself opens with --- would be read as a header.
        val guarded = if (header.isEmpty() && text.startsWith("---")) "---\n---\n\n" else header
        return guarded + text + "\n"
    }

    /** The file's name: the first words of the title or the text, folded. */
    fun fileName(title: String, body: String): String {
        val source = if (title.trim(' ', '\t').isEmpty()) body else title
        var base = source.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.take(6).joinToString(" ")
        base = folded(base)
        base = base.replace(Regex("[^a-z0-9]+"), "-")
        base = base.trim('-')
        return (if (base.isEmpty()) "post" else base).take(40) + ".md"
    }
}

/**
 * What an edit sent from the app may do to a post's pictures. The engine
 * asks before a save that would lose something, and a file has nobody to
 * answer: it refuses one that leaves the post with fewer pictures, or
 * fewer videos, than it had. One taken out and another put in is as many
 * as before, and that it takes.
 */
object Kept {
    private val videoEndings = setOf("mov", "mp4", "m4v", "webm", "mkv", "avi")

    fun isVideo(name: String): Boolean = videoEndings.contains(name.substringAfterLast('.', "").lowercase())

    fun named(name: String, text: String): Boolean = text.contains("($name)")

    /**
     * What travels with the text: the shots it names. One picked and
     * never put into the text would arrive, stand in no post and lie in
     * the blog's incoming/ for good.
     */
    fun sent(shots: List<Shot>, text: String): List<Shot> = shots.filter { named(it.name, text) }

    /** The post's own media the text has stopped naming. */
    fun dropped(media: List<String>, text: String): List<String> = media.filter { !named(it, text) }

    /**
     * True when the text names fewer pictures, or fewer videos, than the
     * post has -- counting what it has and still names, and what is new
     * and named. That save the engine refuses.
     */
    fun fewer(media: List<String>, shots: List<Shot>, text: String): Boolean {
        fun count(video: Boolean): Pair<Int, Int> {
            val had = media.filter { isVideo(it) == video }
            val kept = had.count { named(it, text) }
            val new = shots.count { (it.kind == Shot.Kind.Video) == video && named(it.name, text) }
            return had.size to (kept + new)
        }
        val pictures = count(video = false)
        val videos = count(video = true)
        return pictures.second < pictures.first || videos.second < videos.first
    }
}

/**
 * How much a delivery weighs on the wire: base64 is a third larger, and
 * the receiver measures the encoded stream.
 */
fun encodedSize(bytes: Int): Int = (bytes + 2) / 3 * 4 + bytes / 57 + 1
