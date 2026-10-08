package app.blogsh.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime

// The engine's `--json` answers, as Kotlin sees them. Each type mirrors
// one promise the engine makes in scripts/manage_post.rb: every key is
// always present, so nothing here is nullable unless the engine itself
// sends null for it (a draft's date, a post with no title or series).
//
// A refusal is an object too -- `{"ok": false, "error": ..., "message":
// ...}` with a zero exit -- so every answer is decoded as either its own
// shape or a Refusal, never as "the command failed".

/** How the engine's objects are read: a key the app does not know is not an error. */
val EngineJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * What the engine says when it will not do something: a code a program
 * can switch on, and a sentence a person can read.
 */
@Serializable
data class Refusal(val ok: Boolean, val error: String, val message: String)

/**
 * `version --json`: the identity block the terminal shows above every
 * screen -- which engine, which site, where it is.
 */
@Serializable
data class VersionAnswer(
    val ok: Boolean,
    val engine: String,
    /** The receiver's ceiling on a delivery, measured on the encoded stream. */
    @SerialName("max_mb") val maxMb: Int,
    val site: Site,
) {
    @Serializable
    data class Site(
        val name: String,
        @SerialName("claim") private val rawClaim: String,
        val url: String,
        val lang: String,
        val locales: List<String>,
        /** The palette's accent, per scheme; null from an engine before it said so. */
        val accent: Accent? = null,
        /** The rest of the palette, per scheme; null from an engine before it said so. */
        val palette: Palette? = null,
    ) {
        /**
         * The claim is markdown in the site's configuration, and a blog may
         * break it over two lines the markdown way -- a backslash, or two
         * spaces, at the end of the first. Here it is the lines themselves.
         */
        val claim: String
            get() = rawClaim.split("\n")
                .map { line ->
                    var l = line.trim()
                    if (l.endsWith("\\")) l = l.dropLast(1).trim()
                    l
                }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
    }

    @Serializable
    data class Accent(val light: String, val dark: String)

    @Serializable
    data class Palette(val light: Tones, val dark: Tones)
}

@Serializable
enum class PostState {
    @SerialName("draft") Draft,
    @SerialName("published") Published,
}

/** The engine writes `2026-05-01T10:00:00+02:00`: no fractional seconds, an offset rather than Z. */
fun engineInstant(text: String?): Instant? =
    if (text == null) null else runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()

/** One row of `list --json`. */
@Serializable
data class PostRow(
    val slug: String,
    val year: String,
    val date: String? = null,
    val title: String? = null,
    val type: String,
    val tags: List<String>,
    val state: PostState,
    val scheduled: Boolean,
    val series: String? = null,
    val pinned: Boolean,
    /**
     * The line of the text a search matched on; null without a search, on
     * a hit in the title or a tag, and from an engine before `--search`.
     */
    val match: String? = null,
) {
    /**
     * Slug and year together: the same slug can live in two years, and
     * the engine refuses to guess between them, so neither does this.
     */
    val id: String get() = "$year/$slug"

    /**
     * The day the row shows, or nothing for a plain draft -- whose
     * timestamp is bookkeeping, not a fact about the post, which is why
     * the terminal shows dashes for it.
     */
    val day: Instant?
        get() = if (state == PostState.Draft && !scheduled) null else engineInstant(date)
}

/**
 * The row a post has in a list, from what its properties say now: a
 * screen opened from a row keeps up with the post through this, when
 * its title or its slug has changed since the list was read.
 */
fun PostRow(props: PropsAnswer): PostRow = PostRow(
    slug = props.slug, year = props.year, date = props.date, title = props.title.ifEmpty { null },
    type = props.type, tags = props.tags, state = props.state, scheduled = props.scheduled,
    series = props.series, pinned = props.pinned,
)

/**
 * This row as the post is now. A post without a title keeps none: its
 * properties call it by its opening words, a list by its slug, and a
 * screen opened from the list would otherwise change the post's name
 * under the reader a moment after opening.
 */
fun PostRow.seenAs(props: PropsAnswer): PostRow = PostRow(props).let { now -> if (title == null) now.copy(title = null) else now }

/**
 * A post of the queue as the row a list would have for it: a draft
 * with a plan. What the queue does not say -- its type, its tags --
 * the screen it opens asks for itself.
 */
fun PostRow(queued: QueueRow): PostRow = PostRow(
    slug = queued.slug, year = queued.year, date = queued.date, title = queued.title.ifEmpty { null },
    type = "text", tags = emptyList(), state = PostState.Draft, scheduled = true, series = null, pinned = false,
)

/**
 * A post's address to hand to somebody: where it is, and what it is
 * called there. A published post's own address; for a draft the hidden
 * page the build keeps for it -- the engine says which, the app only
 * passes it on.
 */
data class PostLink(val url: String, val title: String) {
    companion object {
        /** Nothing for a site that has no address set: there is no link to give. */
        fun of(props: PropsAnswer): PostLink? = of(props.url, props.title.ifEmpty { props.slug })

        fun of(address: String, title: String): PostLink? {
            val uri = runCatching { java.net.URI(address) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "https" && scheme != "http") return null
            if (uri.host.isNullOrEmpty()) return null
            return PostLink(address, title)
        }
    }
}

/** `list --json`. */
@Serializable
data class ListAnswer(
    val ok: Boolean,
    val posts: List<PostRow>,
    val count: Int,
    val drafts: Int,
    /**
     * The query said back; null when there was none, and from an engine
     * that does not know `--search` and answered with everything.
     */
    val search: String? = null,
)

/** `props <slug> --json`. */
@Serializable
data class PropsAnswer(
    val ok: Boolean,
    val slug: String,
    val year: String,
    val path: String,
    val title: String,
    val state: PostState,
    val scheduled: Boolean,
    val date: String? = null,
    val url: String,
    val address: String,
    val type: String,
    /** The type the post says for itself; null when the content decides. */
    @SerialName("type_set") val typeSet: String? = null,
    /** The two three-state flags, in the words --set takes: yes, no, default. */
    val hero: String,
    val toc: String,
    val tags: List<String>,
    val series: String? = null,
    @SerialName("series_part") val seriesPart: String? = null,
    val pinned: Boolean,
    val unlisted: Boolean,
    val languages: Languages,
    val announced: String? = null,
    val announces: Announces,
    /** Which network [t] announces on: "mastodon", "bluesky", or none. */
    val network: String? = null,
    /** The time the schedule dialog would offer a plain draft, or none. */
    val slot: String? = null,
    val addresses: List<OldAddress>,
    val actions: List<PostAction>,
    /** Present on the answer to a write (`--set`, `--rename`...), absent on a read. */
    val deploy: String? = null,
    val warnings: List<String>? = null,
) {
    @Serializable
    data class Languages(val own: String, val others: Map<String, String>)

    @Serializable
    data class OldAddress(val kind: String, val value: String)

    /**
     * Which of the six cases the announcement is in -- the ladder the
     * properties screen climbs, as a word.
     */
    @Serializable
    enum class Announces {
        @SerialName("announced") Announced,
        @SerialName("on_publish") OnPublish,
        @SerialName("nowhere") Nowhere,
        @SerialName("never_unlisted") NeverUnlisted,
        @SerialName("no_secret") NoSecret,
        @SerialName("not_announced") NotAnnounced,
    }
}

/**
 * The keys the properties screen would offer a post, by name. The
 * engine decides which apply; the app only draws the ones it is handed.
 */
@Serializable
enum class PostAction {
    @SerialName("publish") Publish,
    @SerialName("schedule") Schedule,
    @SerialName("unschedule") Unschedule,
    @SerialName("unpublish") Unpublish,
    @SerialName("announce") Announce,
    @SerialName("pin") Pin,
    @SerialName("properties") Properties,
    @SerialName("rename") Rename,
    @SerialName("addresses") Addresses,
    @SerialName("versions") Versions,
    @SerialName("delete") Delete,
}

/**
 * What an action says about the post it acted on: the keys `add --json`
 * and `publish --json` answer with, and the ones `delete` adds. All but
 * the slug are nullable here because the shapes differ by action.
 */
@Serializable
data class ActionAnswer(
    val ok: Boolean? = null,
    val slug: String,
    val state: PostState? = null,
    val url: String? = null,
    val deploy: String? = null,
    val warnings: List<String>? = null,
    val compacted: Int? = null,
    val trash: String? = null,
)

/** `stats --json`: the archive counted. Only what the first screen says. */
@Serializable
data class StatsAnswer(val posts: Posts, val span: Span, val words: Words, val tags: Tags, val media: Media) {
    @Serializable data class Posts(val total: Int)
    @Serializable data class Span(val first: String? = null)
    @Serializable data class Words(val total: Int, @SerialName("reading_hours") val readingHours: Double)
    @Serializable data class Tags(val unique: Int)
    @Serializable data class Media(val files: Int, val bytes: Long)
}

/**
 * `empty trash --json` and `empty versions --json`, asked without `--yes`:
 * how much there is, and nothing is touched.
 */
@Serializable
data class HeldAnswer(
    val count: Int,
    val bytes: Long,
    /** With `--yes`: whether anything was removed. */
    val emptied: Boolean? = null,
)

/** `props <slug> --versions --json`. */
@Serializable
data class VersionsAnswer(val ok: Boolean, val slug: String, val versions: List<Version>) {
    @Serializable
    data class Version(val name: String, val date: String? = null, val label: String) {
        val id: String get() = name
    }
}

/** `rebuild --json`. */
@Serializable
data class RebuildAnswer(val ok: Boolean, val deploy: String, val warnings: List<String>)

/**
 * `edit <slug> --json`: one post with what it takes to edit its text
 * elsewhere -- the same entry `drafts --json` hands out for a draft.
 */
@Serializable
data class EditAnswer(val ok: Boolean, val post: EditEntry)

@Serializable
data class EditEntry(
    val slug: String,
    val title: String,
    val date: String,
    val scheduled: Boolean,
    /**
     * False when the text holds something markdown cannot carry; the
     * engine names the reason, and the save would lose it.
     */
    val editable: Boolean,
    val problem: String? = null,
    /** The text as the editor opens it, pictures by bare name. */
    val text: String? = null,
    /** The pictures the post has, by name. */
    val media: List<String>,
    val preview: String,
    /** The digest the save hands back as `base:`. */
    val base: String,
)

/**
 * `translate <slug> --lang <code> --json`: one language of a post, with
 * the original beside it, to be written elsewhere.
 */
@Serializable
data class TranslationAnswer(val ok: Boolean, val post: TranslationEntry)

@Serializable
data class TranslationEntry(
    val slug: String,
    val lang: String,
    val title: String,
    /** Whether the language has words yet. */
    val written: Boolean,
    /**
     * The translation as the editor opens it: a header of its title and
     * its address, then its words -- empty when there are none yet.
     */
    val text: String,
    /** The post's own text, pictures by bare name, to translate from. */
    val original: String,
    val media: List<String>,
    val preview: String,
    val base: String,
)

/** `restore --json`: what the trash holds. */
@Serializable
data class TrashAnswer(val ok: Boolean, val trash: List<TrashRow>)

@Serializable
data class TrashRow(
    val slug: String,
    val year: String? = null,
    val date: String? = null,
    val title: String? = null,
    val type: String? = null,
    val tags: List<String>,
    val state: String? = null,
    @SerialName("media_only") val mediaOnly: Boolean,
) {
    val id: String get() = "${year ?: "-"}/$slug"
}

/** `queue --json`. */
@Serializable
data class QueueAnswer(val ok: Boolean, val queue: List<QueueRow>)

@Serializable
data class QueueRow(
    val position: Int,
    val date: String,
    val slug: String,
    val year: String,
    val title: String,
    val overdue: Boolean,
) {
    val id: String get() = "$year/$slug"
}

/**
 * The engine says what it did in the terminal's words, and at the desk
 * those name a file where it lies -- "Restored: /srv/blog/content.nosync/
 * posts/2026/venku.json". On a phone that is a line of somebody's server
 * with the one useful word at its far end; here the word stays and the
 * way to it goes.
 */
object ServerPaths {
    /** The directories only the engine's own files live under. */
    private val homes = listOf("/content.nosync/", "/media.nosync/", "/public.nosync/", "/trash/", "/incoming/")

    // An absolute path of two parts or more: not one begun inside a word or
    // an address (https://…), and ended by a space or the punctuation of a sentence.
    private val path = Regex("""(?<![\p{L}\p{N}_:/.])/(?:[^\s/:,;)]+/)+[^\s:,;)]*""")

    fun plain(line: String): String {
        val out = StringBuilder()
        var from = 0
        for (match in path.findAll(line)) {
            var token = match.value
            out.append(line, from, match.range.first)
            from = match.range.last + 1
            if (homes.none { ("$token/").contains(it) }) {
                out.append(token)
                continue
            }
            // A full stop after the path is the sentence's, not the file's.
            var tail = ""
            if (token.endsWith(".")) {
                token = token.dropLast(1)
                tail = "."
            }
            var name = token.split("/").lastOrNull { it.isNotEmpty() } ?: token
            if (name.endsWith(".json")) name = name.dropLast(5)
            out.append(name).append(tail)
        }
        return out.append(line, from, line.length).toString()
    }
}

/** The engine's lines without the server's paths in them. */
val List<String>.plain: List<String> get() = map(ServerPaths::plain)

/**
 * `check --json` and `doctor --json`: what a look at the archive, or at
 * the installation, found. Both say how many problems and how many
 * things worth a look, and then each finding: how grave, which check it
 * came from, a sentence, and -- where there is one -- what to do about
 * it. The sentences are the blog's own, in the blog's language.
 */
@Serializable
data class DiagnosisAnswer(val errors: Int, val warnings: Int, val findings: List<Finding>) {
    @Serializable(with = FindingReader::class)
    data class Finding(
        val level: Level,
        /** The check it came from: `trash`, `post_entities`, `all_clear`. */
        val kind: String,
        val text: String,
        val fix: String?,
        /** The post it is about, where it is about one. */
        val slug: String?,
    ) {
        enum class Level { Error, Warning, Fine }
    }

    /**
     * The problems first, then what wants a look, then what is fine --
     * each in the order the blog gave them.
     */
    val ordered: List<Finding>
        get() = findings.withIndex().sortedWith(compareBy({ it.value.level.ordinal }, { it.index })).map { it.value }
}

/** A finding as the engine writes one: read key by key, since what it is about differs with its kind. */
internal object FindingReader : kotlinx.serialization.KSerializer<DiagnosisAnswer.Finding> {
    override val descriptor = kotlinx.serialization.descriptors.buildClassSerialDescriptor("Finding")

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): DiagnosisAnswer.Finding {
        val said = (decoder as kotlinx.serialization.json.JsonDecoder).decodeJsonElement() as? kotlinx.serialization.json.JsonObject
            ?: throw kotlinx.serialization.SerializationException("a finding is an object")
        fun word(of: kotlinx.serialization.json.JsonObject, key: String): String? =
            (of[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        val level = when (word(said, "level") ?: throw kotlinx.serialization.SerializationException("a finding has a level")) {
            "error" -> DiagnosisAnswer.Finding.Level.Error
            "ok" -> DiagnosisAnswer.Finding.Level.Fine
            // A level this app has not heard of is not passed over as fine.
            else -> DiagnosisAnswer.Finding.Level.Warning
        }
        return DiagnosisAnswer.Finding(
            level = level,
            kind = word(said, "kind") ?: "",
            text = word(said, "text") ?: throw kotlinx.serialization.SerializationException("a finding has a sentence"),
            fix = word(said, "fix")?.takeIf { it.isNotEmpty() },
            // What a finding is about differs with its kind; only the post is asked for.
            slug = (said["data"] as? kotlinx.serialization.json.JsonObject)?.let { word(it, "slug") },
        )
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: DiagnosisAnswer.Finding) =
        throw kotlinx.serialization.SerializationException("a finding is only read")
}
