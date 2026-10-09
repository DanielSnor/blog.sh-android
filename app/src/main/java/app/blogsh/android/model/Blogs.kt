package app.blogsh.android.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.blogsh.android.BlogshApp
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One blog the app drives: where it is, what it is reached through, the
 * key it is reached with, and what it last said about itself -- so its
 * screen opens as that blog, in its colour, before the server has
 * answered. A key belongs to one blog: on the server a key runs one
 * forced command, and that command is one blog's `scripts/remote.sh`.
 *
 * Every field has a default: a blog written down by an earlier build,
 * before a field existed, is still that blog.
 */
@Serializable
data class Blog(
    val id: String = UUID.randomUUID().toString(),
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    /** The blog's directory on the server, and the command it is entered through. */
    val path: String = "",
    val through: String = "",
    /** The name its key is kept under. */
    val keyAccount: String = "blog-" + id.lowercase(),

    // What it said last: `version --json`.
    val name: String = "",
    val claim: String = "",
    val url: String = "",
    val accentLight: String = "",
    val accentDark: String = "",
    /** The rest of its palette, per scheme; null until it has said it. */
    val tonesLight: Tones? = null,
    val tonesDark: Tones? = null,
    val maxMb: Int = 24,
    /** What it counted last: `stats`, the trash, the versions. */
    val facts: Facts? = null,
) {
    /** What to call it in a list: its own name, or where it is, before it has said one. */
    val label: String
        get() {
            if (name.isNotEmpty()) return name
            // Until the engine has said its name, the directory tells two
            // blogs on one server apart; the host alone would not.
            val folder = path.split("/").lastOrNull { it.isNotEmpty() } ?: ""
            return folder.ifEmpty { host.trim() }
        }

    /**
     * The server as a connection takes it: the host without the space
     * typed after it, no port as ssh's own. Whatever is remembered of a
     * server -- its key, its silence -- is remembered under these.
     */
    val reached: Pair<String, Int> get() = host.trim() to (if (port == 0) 22 else port)

    /**
     * Where it is reached, as ssh would be told: the user, the server,
     * and the port where it is not ssh's usual one -- a blog at home, or
     * behind a host that moved its ssh, is on a port of its own, and two
     * blogs can differ in nothing else.
     */
    val place: String
        get() {
            val server = host.trim(' ', '\t')
            if (server.isEmpty()) return ""
            val who = user.trim(' ', '\t').let { if (it.isEmpty()) "" else "$it@" }
            if (port == 22 || port == 0) return who + server
            // An address with colons of its own is set apart from the port's.
            return who + (if (':' in server) "[$server]" else server) + ":$port"
        }
}

/**
 * The blog in numbers, as the first screen says them under the search:
 * the archive counted (`stats`), and what the trash and the versions hold.
 */
@Serializable
data class Facts(
    val posts: Int = 0,
    /** The year of the first post. */
    val since: String = "",
    val words: Int = 0,
    val readingHours: Double = 0.0,
    val tags: Int = 0,
    val media: Int = 0,
    val mediaBytes: Long = 0,
    val trash: Int = 0,
    val trashBytes: Long = 0,
    val versions: Int = 0,
    val versionsBytes: Long = 0,
)

/** Where a few words are kept between two runs of the app. */
interface Notes {
    fun read(key: String): String?
    fun write(key: String, value: String?)

    /** Every key something is kept under. */
    fun keys(): Set<String>
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
 * Where the blogs are written down. Plain preferences, read and written
 * from wherever the app happens to be running -- the engine's calls read
 * the current blog off the main thread.
 */
object BlogShelf {
    const val LIST_KEY = "blogs"
    const val CURRENT_KEY = "blogs.current"

    val notes: Notes by lazy { PreferenceNotes(BlogshApp.context) }

    fun read(from: Notes = notes): Pair<List<Blog>, String?> {
        val blogs = from.read(LIST_KEY)?.let { runCatching { EngineJson.decodeFromString<List<Blog>>(it) }.getOrNull() } ?: emptyList()
        val chosen = from.read(CURRENT_KEY)
        val current = if (blogs.any { it.id == chosen }) chosen else blogs.firstOrNull()?.id
        return blogs to current
    }

    fun write(blogs: List<Blog>, current: String?, to: Notes = notes) {
        to.write(LIST_KEY, EngineJson.encodeToString(blogs))
        to.write(CURRENT_KEY, current)
    }

    /**
     * The list with one blog a step up or down it. A step off either
     * end, or of a blog that is not there, leaves the list as it was.
     */
    fun shifted(blogs: List<Blog>, id: String, by: Int): List<Blog> {
        val at = blogs.indexOfFirst { it.id == id }
        val to = at + by
        if (at < 0 || to !in blogs.indices) return blogs
        return blogs.toMutableList().apply {
            this[at] = blogs[to]
            this[to] = blogs[at]
        }
    }

    fun current(from: Notes = notes): Blog? {
        val (blogs, current) = read(from)
        return blogs.firstOrNull { it.id == current }
    }
}

/** The blogs as the screens see them: which there are, which one is open. */
class BlogList(
    private val notes: Notes,
    private val hangUp: () -> Unit = {},
    /** Lets go of the posts that wait on the device for a blog. */
    private val forgetWaiting: (String) -> Unit = {},
    private val forgetKey: (String) -> Unit,
) {
    var all by mutableStateOf(emptyList<Blog>())
        private set
    var currentId by mutableStateOf<String?>(null)
        private set

    init {
        val (blogs, current) = BlogShelf.read(notes)
        all = blogs
        currentId = current
    }

    val current: Blog? get() = all.firstOrNull { it.id == currentId }

    /** A change to the blog that is open, written down at once. */
    fun update(change: (Blog) -> Blog) {
        val index = all.indexOfFirst { it.id == currentId }
        if (index < 0) return
        val blog = change(all[index])
        if (blog == all[index]) return
        all = all.toMutableList().apply { this[index] = blog }
        save()
    }

    fun select(id: String) {
        if (id == currentId || all.none { it.id == id }) return
        currentId = id
        save()
    }

    /**
     * A new blog, open: its directory and its key are the settings' to
     * fill in. The server is the one of the blog that was open -- a second
     * blog most often lives beside the first, and what the first is
     * reached through is long to type twice. Every field stays editable.
     */
    fun add(): Blog {
        val beside = current
        val blog = if (beside == null) Blog() else Blog(host = beside.host, port = beside.port, user = beside.user, through = beside.through)
        all = all + blog
        currentId = blog.id
        save()
        return blog
    }

    /**
     * A blog that was let in by a code: it comes whole -- where it is and
     * a key the server already knows -- and is the open one at once.
     */
    fun adopt(blog: Blog) {
        all = all + blog
        currentId = blog.id
        save()
    }

    /**
     * One blog a place up or down the list; at the list's end, nowhere.
     * Which one is open does not change with it.
     */
    fun shift(id: String, by: Int) {
        val shifted = BlogShelf.shifted(all, id, by)
        if (shifted == all) return
        all = shifted
        save()
    }

    /** The blog leaves the app, and its key with it. The blog itself is not touched. */
    fun remove(id: String) {
        val blog = all.firstOrNull { it.id == id } ?: return
        runCatching { forgetKey(blog.keyAccount) }
        // What was being written for it goes with it, and the connection to its server.
        Unsent.forget(id, notes)
        Unsaved.forgetAll(id, notes)
        runCatching { forgetWaiting(id) }
        hangUp()
        all = all.filter { it.id != id }
        if (currentId == id) currentId = all.firstOrNull()?.id
        save()
    }

    private fun save() = BlogShelf.write(all, currentId, notes)
}

/** The one list the app has. */
val Blogs: BlogList by lazy { BlogList(BlogShelf.notes, hangUp = { Engine.hangUp() }, forgetWaiting = { WaitingRoom.removeAll(it) }) { KeyStore.deleteKey(it) } }
