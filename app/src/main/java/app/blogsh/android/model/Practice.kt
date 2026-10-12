package app.blogsh.android.model

import app.blogsh.android.R
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

/**
 * The practice blog: a blog that lives in the app. It has no server --
 * what it answers was said once by a real engine about a small blog of
 * seven posts, and is carried in the app (`Practice.json`). It is there
 * to be looked around in before a blog of one's own is connected: by
 * somebody who has none yet, and by whoever is to judge the app without
 * a server to try it on.
 *
 * It can be read, and nothing can be written to it: every command that
 * would change a blog is answered with a no that says why. An answer
 * that pretended -- "published" over a list that stayed as it was --
 * would be a lie the next screen gives away.
 */
object Practice {
    /** What the blog says to what cannot be done to it. */
    val refusal: String get() = Spoken.say(R.string.this_is_the_practice_blog_it_lives)

    /** Each answer as it would come over the wire, under the command it answers. */
    private val answers: Map<String, JsonElement> by lazy {
        runCatching {
            val text = checkNotNull(Practice::class.java.classLoader?.getResourceAsStream("Practice.json")).use { it.readBytes().toString(Charsets.UTF_8) }
            (EngineJson.parseToJsonElement(text) as JsonObject).toMap()
        }.getOrNull() ?: emptyMap()
    }

    /** All of it as it is carried, for whoever checks what it holds. */
    internal val carried: String
        get() = checkNotNull(Practice::class.java.classLoader?.getResourceAsStream("Practice.json")).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun data(element: JsonElement): ByteArray = element.toString().toByteArray(Charsets.UTF_8)

    private val no: ByteArray
        get() = data(
            buildJsonObject {
                put("ok", false)
                put("error", "practice")
                put("message", refusal)
            }
        )

    /** The answer to one command, as the engine's door would give it. */
    fun answer(args: List<String>): ByteArray {
        // A search is the list, with the rows the words are found in.
        if (args.firstOrNull() == "list") {
            args.drop(1).firstOrNull { it.startsWith("--search=") }?.let { return data(found(it.removePrefix("--search="))) }
        }
        answers[args.joinToString(" ")]?.let { return data(it) }
        // The devices a code let in: none, and nobody to tell apart.
        if (args == listOf("pair", "--list")) {
            return data(
                buildJsonObject {
                    put("ok", true)
                    put("devices", JsonArray(emptyList()))
                    put("self", JsonNull)
                }
            )
        }
        return no
    }

    /** A delivery: nothing is taken. */
    val delivered: List<ByteArray> get() = listOf(no)

    private fun found(words: String): JsonElement {
        val list = answers["list"] as? JsonObject
        val posts = list?.get("posts") as? JsonArray
            ?: return buildJsonObject {
                put("ok", true)
                put("posts", JsonArray(emptyList()))
            }
        val wanted = words.lowercase(Locale.ROOT).split(" ").filter { it.isNotEmpty() }
        fun said(post: JsonObject, key: String) = (post[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
        val kept = posts.filter { one ->
            val post = one as? JsonObject ?: return@filter false
            val tags = (post["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
            val hay = (listOf(said(post, "title"), said(post, "slug")) + tags).joinToString(" ").lowercase(Locale.ROOT)
            wanted.all { hay.contains(it) }
        }
        return JsonObject(list + ("posts" to JsonArray(kept)))
    }
}
