package app.blogsh.android.model

/**
 * The marks the text takes, from the row of buttons above it -- the
 * /write/ page's own (write/app.js, applyMark), rule for rule, so a text
 * marked on the phone reads the way one marked on the page does. Each is
 * a function of the text and the selection alone. A paired mark wraps
 * what is selected and, applied again to the same selection, takes itself
 * off; with nothing selected it puts the pair in and leaves the caret
 * between. A line mark goes to the start of every line the selection
 * touches, and comes off the same way.
 *
 * Positions are UTF-16 offsets, as on the page: a selection is counted
 * the way the text field counts it.
 */
object Marks {
    enum class Kind { Bold, Italic, Strike, Code, Link, H2, Quote, Ul, Ol, Fence }

    /** The two placeholders a link is made with, in the reader's language. */
    data class Words(val text: String = "text", val url: String = "https://")

    /** A selection: where it begins and how long it is. */
    data class Range(val location: Int, val length: Int)

    data class Marked(val value: String, val selection: Range)

    private val paired = mapOf(Kind.Bold to "**", Kind.Italic to "*", Kind.Strike to "~~", Kind.Code to "`")
    private val lined = mapOf(Kind.H2 to "## ", Kind.Quote to "> ", Kind.Ul to "- ")

    private val wholeLink = Regex("""^\[[^\]]*\]\([^)]*\)$""")
    private val address = Regex("""^(https?://|mailto:)\S+$""", RegexOption.IGNORE_CASE)
    private val domain = Regex("""^(www\.)?[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}(/\S*)?$""", RegexOption.IGNORE_CASE)
    private val numbered = Regex("""^[0-9]+\. """)

    fun apply(value: String, selection: Range, kind: Kind, words: Words = Words()): Marked {
        val length = value.length
        var start = maxOf(0, minOf(selection.location, length))
        var end = maxOf(start, minOf(selection.location + selection.length, length))
        fun slice(a: Int, b: Int): String = if (b > a) value.substring(a, b) else ""
        fun unit(i: Int): String = if (i in 0 until length) value.substring(i, i + 1) else ""
        fun range(a: Int, b: Int) = Range(a, maxOf(0, b - a))
        var sel = slice(start, end)

        paired[kind]?.let { mark ->
            val n = mark.length
            // A mark of one character next to another of the same character
            // is part of a longer mark -- the * of **bold** -- and not this
            // one: italic on a bold word must not un-bold it.
            fun edge(a: Int, b: Int): Boolean = n == 1 && (unit(a - 1) == mark || unit(b) == mark)
            // Selected together with its marks, or between them: either way
            // the second tap takes them off.
            if (sel.length >= 2 * n && sel.startsWith(mark) && sel.endsWith(mark) && !edge(start, end)) {
                val inner = sel.substring(n, sel.length - n)
                return Marked(slice(0, start) + inner + slice(end, length), range(start, end - 2 * n))
            }
            if (start >= n && slice(start - n, start) == mark && slice(end, minOf(length, end + n)) == mark && !edge(start - n, end + n)) {
                return Marked(slice(0, start - n) + sel + slice(end + n, length), range(start - n, end - n))
            }
            return Marked(slice(0, start) + mark + sel + mark + slice(end, length), range(start + n, end + n))
        }

        if (kind == Kind.Link) {
            fun blank(s: String): Boolean = s.isEmpty() || s.all { it.isWhitespace() }
            // The whole word, when the selection stops inside one: a phone
            // selects a word up to its dot, so "sean.cz" arrives as "sean."
            // -- and half an address is no address.
            while (start > 0 && !blank(unit(start - 1))) start -= 1
            while (end < length && !blank(unit(end))) end += 1
            val whole = slice(start, end)
            // Inside a link that already is one, there is nothing to make.
            if (wholeLink.containsMatchIn(whole) || whole.contains("](")) {
                return Marked(value, range(start, end))
            }
            // Brackets and the punctuation a sentence puts around a word
            // stay outside the link.
            while (start < end && "([{\"'".contains(unit(start))) start += 1
            while (end > start && ")]}.,;:!?\"'".contains(unit(end - 1))) end -= 1
            sel = slice(start, end)
            val isUrl = address.containsMatchIn(sel)
            // A bare domain is an address too, and its own best label.
            val isDomain = !isUrl && domain.containsMatchIn(sel)
            val label = if (isUrl) words.text else if (sel.isEmpty()) words.text else sel
            val url = if (isUrl) sel else if (isDomain) "https://$sel" else words.url
            val out = "[$label]($url)"
            // What is left selected is what the author may still want to
            // type: the words for an address, the address for words.
            val onLabel = isUrl || isDomain
            val from = if (onLabel) start + 1 else start + 1 + label.length + 2
            val span = if (onLabel) label.length else url.length
            return Marked(slice(0, start) + out + slice(end, length), Range(from, span))
        }

        if (kind == Kind.Fence) {
            val open = (if (start > 0 && unit(start - 1) != "\n") "\n" else "") + "```\n"
            val close = "\n```" + (if (end < length && unit(end) != "\n") "\n" else "")
            return Marked(slice(0, start) + open + sel + close + slice(end, length), Range(start + open.length, sel.length))
        }

        // Line marks: the lines the selection touches, whole.
        val before = if (start > 0) value.lastIndexOf('\n', start - 1) else -1
        val lineStart = if (before < 0) 0 else before + 1
        val probe = if (end > start) end - 1 else end
        val after = value.indexOf('\n', probe)
        val lineEnd = maxOf(lineStart, if (after < 0) length else after)
        val old = slice(lineStart, lineEnd).split("\n")
        // Added, a mark goes on every line once -- a line that already had
        // it is not given a second one -- and every line loses it when they
        // all had it.
        val lines: List<String> = if (kind == Kind.Ol) {
            val has = old.all { numbered.containsMatchIn(it) }
            old.mapIndexed { index, line ->
                val bare = numbered.replaceFirst(line, "")
                if (has) bare else "${index + 1}. $bare"
            }
        } else {
            val prefix = lined[kind] ?: return Marked(value, range(start, end))
            val has = old.all { it.startsWith(prefix) }
            old.map { line ->
                val bare = if (line.startsWith(prefix)) line.substring(prefix.length) else line
                if (has) bare else prefix + bare
            }
        }
        val block = lines.joinToString("\n")
        val changed = slice(0, lineStart) + block + slice(lineEnd, length)
        if (end > start) return Marked(changed, Range(lineStart, block.length))
        // A bare caret stays on its line, moved by what the line gained or
        // lost before it.
        val delta = lines[0].length - old[0].length
        return Marked(changed, Range(maxOf(lineStart, start + delta), 0))
    }
}
