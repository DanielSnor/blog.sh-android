package app.blogsh.android.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Duration
import java.time.Instant

/**
 * A tag the blog has used, with two counts: all time, and the last twelve
 * months.
 */
data class TagUse(val name: String, val count: Int, val recent: Int)

/**
 * The tags the blog has used, offered as the author types -- the mechanism
 * of the /write/ page, rule for rule. There the build hands the counts out
 * in site.js; here they are counted from the rows `list` answers with, the
 * published ones, as the build counts them.
 */
object TagStore {
    var tags by mutableStateOf(emptyList<TagUse>())
        private set
    private var asked = false

    /** Another blog is open: its tags are not this one's. */
    fun reset() {
        tags = emptyList()
        asked = false
    }

    /**
     * Once per blog; a screen that already holds the archive
     * hands its rows over with `take` instead.
     */
    suspend fun loadIfNeeded() {
        if (asked) return
        asked = true
        val answer = runCatching { Engine.call<ListAnswer>(listOf("list")) }.getOrNull()
        if (answer == null) {
            asked = false
            return
        }
        take(answer.posts)
    }

    fun take(posts: List<PostRow>, now: Instant = Instant.now()) {
        asked = true
        tags = counted(posts, now)
    }

    fun counted(posts: List<PostRow>, now: Instant = Instant.now()): List<TagUse> {
        val since = now.minus(Duration.ofDays(365))
        val counts = LinkedHashMap<String, IntArray>()
        for (post in posts) {
            if (post.state != PostState.Published) continue
            val recent = engineInstant(post.date)?.let { !it.isBefore(since) } ?: false
            for (tag in post.tags) {
                val use = counts.getOrPut(tag) { IntArray(2) }
                use[0] += 1
                if (recent) use[1] += 1
            }
        }
        return counts.map { TagUse(it.key, it.value[0], it.value[1]) }
    }

    /**
     * The tags field as the author has it: the ones finished, and the one
     * being typed after the last comma.
     */
    fun parts(value: String): Pair<List<String>, String> {
        val pieces = value.split(",").toMutableList()
        val typing = pieces.removeAt(pieces.size - 1).trim(' ', '\t')
        val done = pieces.map { it.trim(' ', '\t') }.filter { it.isNotEmpty() }
        return done to typing
    }

    /**
     * Those that begin with what is typed first, then those that merely
     * contain it, each group by how often the blog has used them. With
     * nothing typed yet, the ones used in the last twelve months, most
     * used first -- not the most used of all time, which on an imported
     * archive are the places it came from, and nobody tags a new post
     * with those. Never one the post already carries.
     */
    fun suggest(typed: String, taken: List<String>, limit: Int = 8, from: List<TagUse> = tags): List<TagUse> {
        val query = typed.trim(' ', '\t').lowercase()
        val have = taken.map { it.lowercase() }.toSet()
        val byName = compareBy<TagUse> { it.name.lowercase() }
        val byUse = compareByDescending<TagUse> { it.count }.then(byName)
        val byRecent = compareByDescending<TagUse> { it.recent }.then(byUse)
        // Nor the one that is already typed out in full: there is nothing left to offer.
        val free = from.filter { !have.contains(it.name.lowercase()) && it.name.lowercase() != query }
        if (query.isEmpty()) {
            return free.filter { it.recent > 0 }.sortedWith(byRecent).take(limit)
        }
        val starts = free.filter { it.name.lowercase().startsWith(query) }.sortedWith(byUse)
        val holds = free.filter { !it.name.lowercase().startsWith(query) && it.name.lowercase().contains(query) }.sortedWith(byUse)
        return (starts + holds).take(limit)
    }
}
