package app.blogsh.android.model

import java.util.Base64

/**
 * The post as the blog would show it, near enough: the markdown the
 * /write/ page knows -- paragraphs, headings, bold, italic,
 * strikethrough, code, links, quotes, lists, fenced code, and a picture
 * or a video on a line of its own -- rendered here the way that page
 * renders it, and dressed in the blog's own stylesheets. Near enough, not
 * exact: the engine renders the real thing, and the draft's preview after
 * sending is that. This is for seeing the shape of a post before it
 * leaves the device.
 */
object Preview {
    /** What stands where the text names a picture or a video. */
    sealed class Shown {
        abstract val source: String

        /** A picture, by the address the page can load it from. */
        data class Picture(override val source: String) : Shown()

        /** A video the page can play. */
        data class Video(override val source: String) : Shown()

        /** A video shown by one frame of it, when its bytes are too many to hand a page. */
        data class Frame(override val source: String) : Shown()
    }

    /** The two sentences the preview may have to say, in the reader's language. */
    class Words(
        val missing: (String) -> String = { "Picture $it -- no preview on this device" },
        val glued: (String) -> String = {
            "$it: a picture has to stand on a line of its own, with a blank line before it and after it -- the blog refuses the post otherwise"
        },
    )

    /** The stylesheets a post page wears, in order, unless the site adds its own. */
    val stylesheets = listOf("/assets/css/colors.css", "/assets/css/site.css")

    fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // inline

    private val codeSpan = Regex("`[^`]+`")
    private val strong = Regex("""\*\*(.+?)\*\*""")
    private val emphasis = Regex("""(^|[^*])\*([^*\n]+)\*(?!\*)""")
    private val deleted = Regex("~~(.+?)~~")
    private val link = Regex("""\[([^\]]+)\]\(((?:\([^()\s]*\)|[^)\s])+)\)""")
    private val linkable = Regex("^(https?://|mailto:|/)", RegexOption.IGNORE_CASE)

    /** Code spans first and kept apart, so a * inside one is not emphasis. */
    fun inline(text: String): String {
        fun words(piece: String): String {
            var html = escape(piece)
            html = strong.replace(html, "<strong>$1</strong>")
            html = emphasis.replace(html, "$1<em>$2</em>")
            html = deleted.replace(html, "<del>$1</del>")
            // A link only to somewhere a link may go; anything else stays words.
            val linked = StringBuilder()
            var from = 0
            for (match in link.findAll(html)) {
                linked.append(html, from, match.range.first)
                val label = match.groupValues[1]
                val url = match.groupValues[2]
                linked.append(if (linkable.containsMatchIn(url)) "<a href=\"$url\">$label</a>" else label)
                from = match.range.last + 1
            }
            return linked.append(html, from, html.length).toString()
        }
        val out = StringBuilder()
        var at = 0
        for (match in codeSpan.findAll(text)) {
            out.append(words(text.substring(at, match.range.first)))
            out.append("<code>").append(escape(match.value.drop(1).dropLast(1))).append("</code>")
            at = match.range.last + 1
        }
        return out.append(words(text.substring(at))).toString()
    }

    // blocks

    private val fence = Regex("^```")

    /**
     * The line that splits a post in two -- what a list of posts shows of
     * it, and the rest -- as the engine reads it: alone on its line.
     */
    private val teaserEnd = Regex("""^[ \t]*//--more--//[ \t]*$""")
    private val heading = Regex("""^(#{1,3})\s+(.+)$""")
    private val clip = Regex("""^!!\[([^\n]*)\]\(([^)\s]+)\)\s*$""")
    private val picture = Regex("""^!\[([^\n]*)\]\(([^)\s]+)\)\s*$""")
    private val quoted = Regex("""^>\s?""")
    private val listed = Regex("""^\s*([-*+]|[0-9]+\.)\s+""")
    private val numberedItem = Regex("""^\s*[0-9]+\.""")

    private fun has(re: Regex, line: String) = re.containsMatchIn(line)
    private fun groups(re: Regex, line: String): List<String>? = re.find(line)?.groupValues

    /**
     * The text as the body of a post page. `shots` is what each name the
     * text may use stands for; a name it does not know is said so, in the
     * box where the picture would stand.
     */
    fun render(markdown: String, shots: Map<String, Shown> = emptyMap(), words: Words = Words()): String {
        val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val html = mutableListOf<String>()
        var para = mutableListOf<String>()
        var i = 0
        fun blank(index: Int) = lines[index].trim(' ', '\t').isEmpty()
        fun flush() {
            if (para.isNotEmpty()) html.add("<p>" + inline(para.joinToString("\n")) + "</p>")
            para = mutableListOf()
        }
        fun box(sentence: String) = "<figure><div class=\"no-preview\">" + escape(sentence) + "</div></figure>"
        while (i < lines.size) {
            val line = lines[i]
            if (has(fence, line)) {
                flush()
                val code = mutableListOf<String>()
                i += 1
                while (i < lines.size && !has(fence, lines[i])) {
                    code.add(lines[i])
                    i += 1
                }
                i += 1
                html.add("<pre class=\"code-block\"><code>" + escape(code.joinToString("\n")) + "</code></pre>")
                continue
            }
            // The one place this leaves the page's own rendering: there the
            // line is printed as words. Here it is what it means on the
            // blog -- nothing to read, a place where the post is cut -- so
            // it is drawn as a hairline.
            if (has(teaserEnd, line)) {
                flush()
                html.add("<hr class=\"teaser-end\">")
                i += 1
                continue
            }
            groups(heading, line)?.let { m ->
                flush()
                val level = m[1].length + 1   // a post's own title is the h1
                html.add("<h$level>" + inline(m[2]) + "</h$level>")
                i += 1
            }?.let { continue }
            // The engine takes a picture only as a paragraph of its own: a
            // blank line before it and after it.
            val glued = (i > 0 && !blank(i - 1)) || (i + 1 < lines.size && !blank(i + 1))
            val video = groups(clip, line)
            if (video != null) {
                flush()
                val shown = shots[video[2]]
                val caption = if (video[1].isEmpty()) "" else "<figcaption>" + escape(video[1]) + "</figcaption>"
                if (glued) {
                    html.add(box(words.glued(video[2])))
                } else if (shown is Shown.Frame) {
                    html.add("<figure><img src=\"" + escape(shown.source) + "\" alt=\"" + escape(video[1]) + "\">" + caption + "</figure>")
                } else if (shown != null) {
                    // By the mark, as the page does it: two marks are a video, whatever the name ends in.
                    html.add("<figure><video controls playsinline src=\"" + escape(shown.source) + "\"></video>" + caption + "</figure>")
                } else {
                    html.add(box(words.missing(video[2])))
                }
                i += 1
                continue
            }
            val first = groups(picture, line)
            if (first != null) {
                flush()
                if (glued) {
                    html.add(box(words.glued(first[2])))
                    i += 1
                    continue
                }
                fun figure(m: List<String>): String {
                    val shown = shots[m[2]] ?: return box(words.missing(m[2]))
                    return "<figure><img src=\"" + escape(shown.source) + "\" alt=\"" + escape(m[1]) + "\"></figure>"
                }
                // The second place this leaves the page's rendering, and
                // follows the blog's: pictures in a row, with nothing but
                // blank lines between them, are a gallery there -- two side
                // by side, an odd last one across both -- in the markup the
                // engine writes (build/blocks.rb, render_photo_grid).
                val row = mutableListOf(figure(first))
                var next = i + 1
                while (true) {
                    var k = next
                    while (k < lines.size && blank(k)) k += 1
                    if (k >= lines.size) break
                    val more = groups(picture, lines[k]) ?: break
                    if (!(k + 1 >= lines.size || blank(k + 1))) break
                    row.add(figure(more))
                    next = k + 1
                }
                if (row.size > 1) {
                    if (row.size % 2 == 1) row[row.size - 1] = row[row.size - 1].replaceFirst("<figure>", "<figure class=\"span-2\">")
                    html.add("<div class=\"photo-grid\">" + row.joinToString("") + "</div>")
                } else {
                    html.add(row[0])
                }
                i = next
                continue
            }
            if (has(quoted, line)) {
                flush()
                val quote = mutableListOf<String>()
                while (i < lines.size && has(quoted, lines[i])) {
                    quote.add(quoted.replace(lines[i], ""))
                    i += 1
                }
                html.add("<blockquote><p>" + inline(quote.joinToString("\n")) + "</p></blockquote>")
                continue
            }
            if (has(listed, line)) {
                flush()
                val ordered = has(numberedItem, line)
                val items = mutableListOf<String>()
                while (i < lines.size && has(listed, lines[i])) {
                    items.add("<li>" + inline(listed.replace(lines[i], "")) + "</li>")
                    i += 1
                }
                html.add((if (ordered) "<ol>" else "<ul>") + items.joinToString("") + (if (ordered) "</ol>" else "</ul>"))
                continue
            }
            if (blank(i)) {
                flush()
                i += 1
                continue
            }
            para.add(line)
            i += 1
        }
        flush()
        return html.joinToString("\n")
    }

    // the page

    /**
     * A whole page wearing the stylesheets a post page wears -- the same
     * paths, resolved against the site, so the preview changes when the
     * skin does -- and built the way the engine's own post page is
     * (templates/post.html.erb): a card, in it the header, in the header
     * the body with the title over the content. The nesting is what the
     * stylesheet is written for: the header's negative margins are the
     * card's padding taken back, and without the card around it the title
     * stood outside the window; the text's own headings are sized as
     * `.content` sizes them, not as a card's.
     */
    fun document(title: String, body: String, lang: String, stylesheets: List<String> = Preview.stylesheets): String {
        val links = stylesheets.joinToString("") { "<link rel=\"stylesheet\" href=\"" + escape(it) + "\">" }
        val name = title.trim()
        val heading = if (name.isEmpty()) "" else "<h1>" + escape(name) + "</h1>"
        return "<!doctype html><html lang=\"" + escape(lang) + "\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><base href=\"/\">" +
            // The gallery as the engine's own stylesheet lays it out, said
            // before that stylesheet so the blog's -- or a skin's -- wins:
            // it holds where the blog cannot be reached.
            "<style>.photo-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:4px;margin:1rem 0}" +
            ".photo-grid figure{margin:0;display:flex;flex-direction:column}" +
            ".photo-grid img{width:100%;flex:1 1 auto;min-height:0;object-fit:cover;display:block}" +
            ".photo-grid .span-2{grid-column:1/-1}</style>" + links +
            "<style>body{margin:0;padding:1rem;background:var(--card-bg,transparent)}" +
            "figure{margin:1rem 0}figure img,figure video{max-width:100%;height:auto}" +
            ".no-preview{padding:1rem;border:1px dashed currentColor;opacity:.6;font-size:.9em}" +
            "hr.teaser-end{border:0;border-top:1px solid currentColor;opacity:.3;margin:1.5rem 0}</style></head>" +
            "<body><main><div class=\"card\"><article><div class=\"post-header\"><div class=\"post-body\">" + heading +
            "<div class=\"content\">" + body + "</div></div></div></article></div></main></body></html>"
    }

    /**
     * What the names of a form's own shots stand for: a picture rides in
     * the page as a data address, the way the /write/ page hands it over;
     * a video is shown by a frame of it -- its bytes are too many.
     */
    fun shown(shots: List<Shot>): Map<String, Shown> {
        val map = LinkedHashMap<String, Shown>()
        for (shot in shots) {
            if (shot.kind == Shot.Kind.Video) {
                (shot.poster ?: shot.thumb)?.let { map[shot.name] = Shown.Frame("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(it)) }
            } else {
                map[shot.name] = Shown.Picture("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(shot.data))
            }
        }
        return map
    }

    /**
     * What the names of a post's own media stand for: the files the build
     * keeps beside the post's page, at the address the engine gave.
     */
    fun shown(media: List<String>, beside: String): Map<String, Shown> {
        val map = LinkedHashMap<String, Shown>()
        for (name in media) {
            val source = beside + pathEncoded(name)
            map[name] = if (Kept.isVideo(name)) Shown.Video(source) else Shown.Picture(source)
        }
        return map
    }

    /** A name as a path may carry it: what is not a letter, a digit or one of the path's own signs, in per cents. */
    internal fun pathEncoded(name: String): String {
        val allowed = "!$&'()*+,-./:=@_~"
        val out = StringBuilder()
        for (byte in name.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xff
            if (c < 128 && (Character.isLetterOrDigit(c) || allowed.indexOf(c.toChar()) >= 0)) out.append(c.toChar())
            else out.append('%').append("%02X".format(c))
        }
        return out.toString()
    }

    /**
     * A text that opens with a header, as the editor opens a post: the
     * title the header gives, and the text under it.
     */
    fun parts(text: String): Pair<String, String> {
        if (!text.startsWith("---\n")) return "" to text
        val end = text.indexOf("\n---\n", 3)
        if (end < 0) return "" to text
        // An empty header closes on the very line that opened it.
        val header = if (end >= 4) text.substring(4, end) else ""
        var title = ""
        for (line in header.split("\n")) {
            if (line.startsWith("title:")) title = line.substring("title:".length).trim(' ', '\t')
        }
        return title to text.substring(end + 5)
    }
}

/**
 * How a post begins, in plain words: what stands under its title where a
 * post is picked, to tell it from the one beside it. The part before the
 * line that cuts the post in two, where it has one; its first paragraph
 * where it has none. The marks are taken off -- this is read, not
 * rendered -- and a post that is only a picture is known by the
 * picture's description.
 */
data class Lede(
    /** The words, or nothing when the post has none of its own. */
    val words: String,
    /** The first picture's or video's description, for a post without words. */
    val picture: String?,
) {
    companion object {
        const val LIMIT = 600

        private val cut = Regex("""^[ \t]*//--more--//[ \t]*$""")
        private val media = Regex("""^!{1,2}\[([^\n]*)\]\(([^)\s]+)\)\s*$""")
        private val fence = Regex("^```")
        private val labelled = Regex("""!{0,2}\[([^\]]*)\]\((?:\([^()\s]*\)|[^)\s])+\)""")
        private val lead = Regex("""^\s*(?:#{1,6}\s+|>\s?)""")
        private val emphasis = Regex("""(^|[^*])\*([^*\n]+)\*(?!\*)""")

        /**
         * A line of the text as words: a link is its label, a heading or a
         * quote its text, and what was bold or struck is just said.
         */
        fun plain(line: String): String {
            var out = lead.replace(line, "")
            out = labelled.replace(out, "$1")
            out = out.replace("**", "").replace("~~", "")
            out = emphasis.replace(out, "$1$2")
            return out.replace("`", "")
        }

        /** Of a text as the editor opens it, header and all. */
        fun of(text: String): Lede {
            val body = Preview.parts(text).second.replace("\r\n", "\n")
            var lines = body.split("\n")
            val teaser = lines.indexOfFirst { cut.containsMatchIn(it) }
            if (teaser >= 0) lines = lines.subList(0, teaser)
            val paragraphs = mutableListOf<String>()
            var current = mutableListOf<String>()
            var picture: String? = null
            var fenced = false
            fun close() {
                if (current.isNotEmpty()) paragraphs.add(current.joinToString(" "))
                current = mutableListOf()
            }
            for (line in lines) {
                if (fence.containsMatchIn(line)) {
                    close()
                    fenced = !fenced
                    continue
                }
                if (fenced) continue
                val match = media.find(line)
                if (match != null) {
                    close()
                    val alt = match.groupValues[1].trim(' ', '\t')
                    if (picture == null && alt.isNotEmpty()) picture = alt
                    continue
                }
                val words = plain(line).trim(' ', '\t')
                if (words.isEmpty()) close() else current.add(words)
            }
            close()
            // Up to the cut the author made, all of it; without one, the first paragraph.
            var words = if (teaser < 0) paragraphs.firstOrNull() ?: "" else paragraphs.joinToString("\n\n")
            // A cut at the very top leaves nothing above it: then the post begins after it.
            if (words.isEmpty() && teaser >= 0) {
                val rest = of(body.split("\n").filter { !cut.containsMatchIn(it) }.joinToString("\n"))
                return Lede(rest.words, picture ?: rest.picture)
            }
            if (words.codePointCount(0, words.length) > LIMIT) {
                val head = words.substring(0, words.offsetByCodePoints(0, LIMIT))
                val end = head.indexOfLast { it == ' ' || it == '\n' }.let { if (it < 0) head.length else it }
                words = head.substring(0, end).trim() + "…"
            }
            return Lede(words, picture)
        }
    }
}
