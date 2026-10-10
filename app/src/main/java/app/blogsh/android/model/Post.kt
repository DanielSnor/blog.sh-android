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
    val mark: String get() = (if (kind == Kind.Video) "!!" else "!") + "[${Kept.oneLine(alt)}]($name)"

    /** The shot's mark wherever the text has it, whatever it says there -- to the end of its caption, where it has one. */
    /**
     * The description runs up to the mark's own "](", not to the first
     * bracket: a description may hold brackets of its own.
     */
    val markPattern: Regex get() = Regex("""!{1,2}\[(?:(?!\]\().)*\]""" + Kept.target(name))
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
/**
 * A delivery's own name, as the engine takes one: sixteen lowercase hex
 * digits. A post keeps the one it was given for as long as it is the same
 * post; the next post gets another -- the engine writes a delivery with a
 * receipt it knows over the draft it wrote for it.
 */
object Receipt {
    fun mint(): String = ByteArray(8).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun isOne(text: String): Boolean = Regex("^[0-9a-f]{16}$").matches(text)
}

object Markdown {
    /**
     * `receipt`: the delivery's own name, by which the engine knows a
     * delivery it has seen before -- one whose answer was lost on the way
     * back and which is sent again -- and answers with the post it
     * already wrote instead of writing a second.
     */
    fun frontMatter(title: String, tags: String, publish: Boolean = false, receipt: String? = null): String {
        val lines = mutableListOf<String>()
        val cleanTitle = title.trim()
            // Unwrapped only where the whole title is wrapped once: a title
            // that merely begins and ends with a bracket or a quote of its
            // own -- "[foto] Sobota [Brno]" -- goes as it was typed.
            .replace(Regex("""^(["'])((?:(?!\1).)*)\1$"""), "$2")
            .replace(Regex("""^\[([^\[\]]*)\]$"""), "$1")
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
        if (receipt != null && Receipt.isOne(receipt)) lines.add("receipt: $receipt")
        return if (lines.isEmpty()) "" else "---\n" + lines.joinToString("\n") + "\n---\n\n"
    }

    /**
     * A moment as the engine is told it: to the second, with the device's
     * own offset -- the time a schedule was picked at, as the picker
     * showed it.
     */
    fun stamp(moment: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
        java.time.OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(Math.floorDiv(moment, 1000L)), zone)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    fun file(title: String, tags: String, body: String, publish: Boolean = false, receipt: String? = null): String {
        val text = body.trim()
        val header = frontMatter(title, tags, publish, receipt)
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

    /**
     * A picture's caption, as the engine takes one: after the name, in
     * quotes -- straight ones, or the pair a Czech, a German or an
     * English keyboard types. `![description](photo.jpg "caption")`.
     */
    const val CAPTION = """(?:\s+(?:"(?:\\.|[^"\\])*"|\u201E[^\u201C]*\u201C|\u201C[^\u201D]*\u201D))?"""

    /** What a file's name in a mark may not hold: the bracket that ends the mark, a space, a quote that opens a caption. */
    const val NAME = """[^)\s"\u201E\u201C\u201D]+"""

    /**
     * What stands in a mark's round brackets for this file: its name,
     * and a caption or none.
     */
    fun target(name: String): String = """\(""" + Regex.escape(name) + CAPTION + """\)"""

    /**
     * The text names the file: with a caption after the name or without.
     * A picture read as "not named" is one the app would not send, would
     * call deleted on the next save, and would ask about for nothing.
     */
    fun named(name: String, text: String): Boolean = Regex(target(name)).containsMatchIn(text)

    /**
     * A shot's mark put into the text where the caret is -- at its end
     * when the text was never touched. A blank line on each side, counted:
     * the blog renders a picture only as a paragraph of its own and
     * refuses one on the line straight after a sentence, so a mark put in
     * the middle of a line breaks the line there; and counted so that a
     * blank line already standing is not doubled. The rule of /write/
     * (spacedMark). The caret comes back after the mark and its gap, where
     * one goes on writing.
     */
    class Placed(val text: String, val caret: Int)

    fun placed(mark: String, text: String, at: Int?): Placed {
        val position = (at ?: text.length).coerceIn(0, text.length)
        val before = text.substring(0, position)
        val after = text.substring(position)
        val gaps = listOf("\n\n", "\n", "")
        val trailing = before.takeLastWhile { it == '\n' }.length
        val leading = after.takeWhile { it == '\n' }.length
        val gapBefore = if (before.isEmpty()) "" else gaps[minOf(2, trailing)]
        val gapAfter = if (after.isEmpty()) "\n" else gaps[minOf(2, leading)]
        val put = gapBefore + mark + gapAfter
        return Placed(before + put + after, position + put.length)
    }

    /**
     * A description is one line in the markdown, whatever its field held:
     * a line break inside ![…](name) is a picture the engine refuses, with
     * a reason that names the wrong thing.
     */
    fun oneLine(words: String): String = words.split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ")

    // A picture's description is ONE thing with two places to write it:
    // the card, and the mark the text has for the picture. Whichever is
    // written in, the other follows, at every letter -- so it does not
    // matter where one happens to be when a word comes to mind. Two
    // functions, one for each way; each leaves alone what already says
    // the same, which is what keeps the two from chasing each other and
    // keeps a space typed at the end of a word where it was typed.

    /**
     * What the text says of a picture: the words of its first mark, as
     * they stand there -- nothing at all where the text has no mark for
     * it, the empty string where the mark says nothing.
     *
     * Read the way the engine reads a picture: a mark is a line of its
     * own, and its description runs to the last "](" before the name --
     * so a square bracket in it is part of it, as it is for the engine.
     * A mark that shares its line with prose, which the engine refuses
     * but the text may hold while it is being written, is read too, up
     * to its first closing bracket.
     */
    fun described(name: String, text: String): String? {
        val target = target(name)
        // A line that holds another mark before this one is not this mark's line:
        // what was read as its description has the other's end in it.
        val line = Regex("""^[ \t]*!{1,2}\[(.*)\]""" + target + """[ \t]*$""", RegexOption.MULTILINE).find(text)
            ?.takeIf { !it.groupValues[1].contains("](") }
        val inline = Regex("""!\[([^\]\n]*)\]""" + target).find(text)
        // Whichever stands first in the text is the picture's first mark.
        if (line != null && inline != null) return (if (line.range.first <= inline.range.first) line else inline).groupValues[1]
        return (line ?: inline)?.groupValues?.get(1)
    }

    /**
     * The text was written in: every card takes what the text now says of
     * its picture. A card whose picture the text does not name keeps its
     * own words -- they go in with the mark when it is put there.
     */
    fun heard(shots: List<Shot>, text: String): List<Shot> = shots.map { shot ->
        val words = described(shot.name, text)
        if (words == null || oneLine(words) == oneLine(shot.alt)) shot else shot.withAlt(words)
    }

    /**
     * A card was written on: the mark the text has for its picture says
     * the same -- that mark, and any other of the picture that said what
     * it said. A text with no mark for the picture is let be. (A video's
     * two marks end in the same one, so the one rule serves both.)
     */
    fun typed(text: String, name: String, after: String): String {
        val words = described(name, text) ?: return text
        if (oneLine(words) == oneLine(after)) return text
        // The description alone is rewritten: the name and whatever caption
        // stands after it are put back as they were.
        val said = Regex(Regex.escape("![$words]($name") + "(" + CAPTION + """\))""")
        return said.replace(text) { "![${oneLine(after)}]($name" + it.groupValues[1] }
    }

    /**
     * The same for every card at once: the shots as they are now, their
     * descriptions as they were. With a shot added or taken away between
     * the two there is nothing to compare, and the text is let be.
     */
    fun retitled(text: String, shots: List<Shot>, before: List<String>): String {
        if (shots.size != before.size) return text
        var now = text
        for ((shot, was) in shots.zip(before)) if (shot.alt != was) now = typed(now, shot.name, shot.alt)
        return now
    }

    /**
     * What travels with the text: the shots it names. One picked and
     * never put into the text would arrive, stand in no post and lie in
     * the blog's incoming/ for good.
     */
    fun sent(shots: List<Shot>, text: String): List<Shot> = shots.filter { named(it.name, text) }

    /** The post's own media the text has stopped naming. */
    fun dropped(media: List<String>, text: String): List<String> = media.filter { !named(it, text) }

    /**
     * A save of this text would delete a picture of the post's from the
     * blog: somebody is asked first, every time. Pictures are not kept
     * among a post's versions; one deleted is gone.
     */
    fun asksBeforeSaving(media: List<String>, text: String): Boolean = dropped(media, text).isNotEmpty()

    /**
     * True when the text names fewer pictures, or fewer videos, than the
     * post has -- counting what it has and still names, and what is new
     * and named. An engine before 1.10 refuses that save; a newer one
     * takes it, and deletes what is no longer named.
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
