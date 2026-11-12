package app.blogsh.android.model

/**
 * Quotes in code are the straight ones. A typographic quote is what
 * prose wants, and what the blog reads in a caption or a link's title --
 * and in code `echo “hi”` is no longer a command. A keyboard may write
 * one, a text pasted or taken from a file may bring one. So the quotes
 * of code are made straight -- once the writing has stopped: when the
 * editor is left, and again when the post goes.
 *
 * Where code is, is the blog's to say, and this reads it as the blog's
 * own parser does: a block between two lines of three or more backticks
 * -- however far indented, the closing one at least as long as the
 * opening one and alone on its line, a block nobody closed running to
 * the end; and within a paragraph, what stands between a run of
 * backticks and the next run of as many, across a line break but not
 * across an empty line. Tildes fence nothing there, and neither does an
 * indent of four spaces.
 *
 * Not while somebody types: the text under a keyboard is the keyboard's
 * too. One mark stands for one, so the text stays as long as it was, and
 * a caret stays where it is.
 */
object CodeQuotes {
    private const val TICK = '`'

    /** One line of the text, as the blocks see it. */
    private sealed class Line {
        /** Three backticks or more, and whether anything follows them on the line. */
        class Fence(val length: Int, val bare: Boolean) : Line()
        data object Blank : Line()
        data object Words : Line()
    }

    private fun isSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r'

    /** The line that holds this place, its end past the line break. */
    private fun lineAt(text: String, at: Int): IntRange {
        val start = text.lastIndexOf('\n', at - 1) + 1
        val broke = text.indexOf('\n', at)
        return start until (if (broke < 0) text.length else broke + 1)
    }

    private fun kind(text: String, line: IntRange): Line {
        var at = line.first
        var end = line.last + 1
        while (at < end && isSpace(text[at])) at += 1
        while (end > at && isSpace(text[end - 1])) end -= 1
        if (at >= end) return Line.Blank
        var run = at
        while (run < end && text[run] == TICK) run += 1
        if (run - at < 3) return Line.Words
        return Line.Fence(run - at, bare = run == end)
    }

    /**
     * The text taken apart: its blocks' contents, and its paragraphs --
     * the stretches a run of backticks can be answered in.
     */
    private fun parts(text: String): Pair<List<IntRange>, List<IntRange>> {
        val blocks = mutableListOf<IntRange>()
        val paragraphs = mutableListOf<IntRange>()
        var open: Int? = null
        var paragraph: IntRange? = null
        fun close() {
            paragraph?.let { paragraphs.add(it) }
            paragraph = null
        }
        var at = 0
        while (at < text.length) {
            val line = lineAt(text, at)
            at = line.last + 1
            val what = kind(text, line)
            val length = open
            if (length != null) {
                if (what is Line.Fence && what.bare && what.length >= length) open = null else blocks.add(line)
                continue
            }
            when (what) {
                is Line.Fence -> {
                    close()
                    open = what.length
                }
                Line.Blank -> close()
                Line.Words -> paragraph = paragraph?.let { it.first..line.last } ?: line
            }
        }
        close()
        return blocks to paragraphs
    }

    /** Where the text is code. */
    fun code(text: String): List<IntRange> {
        val (blocks, paragraphs) = parts(text)
        return (blocks + paragraphs.flatMap { spans(text, it) }).sortedBy { it.first }
    }

    /**
     * The code within one paragraph: what stands between a run of
     * backticks and the first as many after it. A run nothing answers
     * is itself.
     */
    private fun spans(text: String, paragraph: IntRange): List<IntRange> {
        val found = mutableListOf<IntRange>()
        val end = paragraph.last + 1
        var at = paragraph.first
        while (at < end) {
            if (text[at] != TICK) {
                at += 1
                continue
            }
            var open = at
            while (open < end && text[open] == TICK) open += 1
            val count = open - at
            // What closes it: the first run of at least as many.
            var seek = open
            var closed: Int? = null
            while (seek < end) {
                if (text[seek] != TICK) {
                    seek += 1
                    continue
                }
                var run = seek
                while (run < end && text[run] == TICK) run += 1
                if (run - seek >= count) {
                    closed = seek
                    break
                }
                seek = run
            }
            if (closed == null) {
                at = open
                continue
            }
            found.add(open until closed)
            at = closed + count
        }
        return found
    }

    private fun straight(mark: Char): Char? = when (mark) {
        '“', '”', '„', '‟' -> '"'
        '‘', '’', '‚', '‛' -> '\''
        else -> null
    }

    /** The quotes made straight wherever `within` says, among the places that are code. */
    private fun put(text: String, within: (span: IntRange, line: IntRange) -> Boolean): String {
        var units: CharArray? = null
        for (span in code(text)) {
            var at = span.first
            while (at <= span.last) {
                val line = lineAt(text, at)
                val last = minOf(span.last, line.last)
                if (within(span, line)) {
                    for (one in at..last) {
                        val plain = straight(text[one]) ?: continue
                        val all = units ?: text.toCharArray().also { units = it }
                        all[one] = plain
                    }
                }
                at = line.last + 1
            }
        }
        return units?.let { String(it) } ?: text
    }

    /**
     * The text with the quotes of its code made straight, on the lines
     * `touched` reaches into and on no others: what was not just made
     * code is left as its author has it.
     */
    fun straightened(text: String, touched: IntRange): String {
        if (touched.first > text.length) return text
        val first = lineAt(text, touched.first.coerceAtMost(text.length)).first
        val last = lineAt(text, maxOf(touched.first, touched.last).coerceAtMost(text.length)).last
        return put(text) { _, line -> line.first <= last && line.last >= first }
    }

    /**
     * The text as it is left once the writing has stopped -- the editor
     * was left, or the post is on its way: the quotes of its code made
     * straight. Over a post that came from the blog, only on the lines
     * the blog's text does not have; what nobody wrote here is left as
     * its author has it, or a post merely opened would read as changed.
     */
    fun settled(text: String, original: String? = null): String {
        val had = original?.split("\n")?.toSet()
        return put(text) { _, line -> had == null || text.substring(line.first, line.last + 1).trimEnd('\n', '\r') !in had }
    }
}
