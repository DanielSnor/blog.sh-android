package app.blogsh.android.model

import app.blogsh.android.BlogshApp
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/**
 * A post that was finished while its blog could not be reached, put by
 * on the device to be sent when it can: its words, and the pictures and
 * videos they name, as they will travel. Unlike the post being written
 * (`Unsent`), of which a blog has one, any number can wait -- each is
 * done with, and the form is free for the next.
 *
 * What it holds of a picture is what a delivery needs and what its card
 * in the form needs to come back; the description is in the text's own
 * mark for it, as it always is.
 */
@Serializable
data class Waiting(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val tags: String = "",
    val text: String = "",
    /** When it was put by: the date the post is given on the blog. */
    val at: Long = 0,
    val pieces: List<Piece> = emptyList(),
    /** Why the blog turned it away the last time it was sent, in its own words. */
    val problem: String? = null,
    /**
     * The name its delivery goes under, the same every time it is sent:
     * a post the server took just before the app was stopped is sent
     * again the next time, and the blog knows it for the one it has.
     */
    val receipt: String? = null,
    /**
     * Taken back into the form to be written on. It waits for nothing
     * then -- it is not listed and not sent -- but its files stay where
     * they are until the form has sent it, put it by again or been
     * emptied: the form holds pictures in memory only, and a form left,
     * or an app stopped, would otherwise take them along.
     */
    val held: Boolean? = null,
) {
    /** One picture or video, as the post names it. */
    @Serializable
    data class Piece(val name: String, val video: Boolean = false, val width: Int = 0, val height: Int = 0, val converted: Boolean = true)

    /** What to call it where it is listed, as the post being written is called. */
    val headline: String get() = Unsent(title, tags, text).headline
}

/**
 * Where the posts that wait are kept: a directory to a blog, one to a
 * post in it -- `post.json`, and beside it the files the post goes with.
 * Files, not preferences: a delivery is megabytes.
 */
object WaitingRoom {
    val home: File get() = File(BlogshApp.context.filesDir, "waiting")

    private fun room(blog: String, home: File) = File(home, File(blog).name)

    private fun place(id: String, blog: String, home: File) = File(room(blog, home), File(id).name)

    /** A name as a file of the post's own directory, and of no other. */
    private fun file(name: String, under: String, place: File) = File(File(place, under), File(name).name)

    /** Written beside, then put in its place: a file is whole or it is not there. */
    private fun write(bytes: ByteArray, to: File) {
        val beside = File(to.parentFile, to.name + ".part")
        beside.writeBytes(bytes)
        if (!beside.renameTo(to)) {
            to.delete()
            if (!beside.renameTo(to)) throw java.io.IOException("could not write " + to.name)
        }
    }

    /**
     * Puts a post by with its files. All of it or none: a post without
     * one of its pictures is not a post that waits.
     */
    fun put(post: Waiting, shots: List<Shot>, blog: String, home: File = this.home) {
        val place = place(post.id, blog, home)
        try {
            if (!File(place, "media").mkdirs() && !File(place, "media").isDirectory) throw java.io.IOException("could not make " + place.name)
            if (!File(place, "posters").mkdirs() && !File(place, "posters").isDirectory) throw java.io.IOException("could not make " + place.name)
            val whole = post.copy(
                // Given here at the latest, and written down with the post.
                receipt = post.receipt?.takeIf { Receipt.isOne(it) } ?: Receipt.mint(),
                pieces = shots.map { Waiting.Piece(it.name, it.kind == Shot.Kind.Video, it.width, it.height, it.converted) },
            )
            for (shot in shots) {
                write(shot.data, file(shot.name, "media", place))
                shot.poster?.let { write(it, file(shot.name, "posters", place)) }
            }
            // Last: what is listed is what has all of its files.
            write(EngineJson.encodeToString(Waiting.serializer(), whole).toByteArray(Charsets.UTF_8), File(place, "post.json"))
        } catch (e: Exception) {
            place.deleteRecursively()
            throw e
        }
    }

    private fun read(place: File): Waiting? = runCatching {
        EngineJson.decodeFromString(Waiting.serializer(), File(place, "post.json").readText(Charsets.UTF_8))
    }.getOrNull()

    /**
     * What waits for a blog, in the order it was written: that is the
     * order it is sent in. A post held by the form is not among them.
     */
    fun all(blog: String, home: File = this.home): List<Waiting> = everything(blog, home).filter { it.held != true }

    /** Every post kept for a blog, the held ones too. */
    private fun everything(blog: String, home: File): List<Waiting> =
        (room(blog, home).listFiles() ?: emptyArray()).mapNotNull { read(it) }.sortedWith(compareBy<Waiting> { it.at }.thenBy { it.id })

    /** One post by its name, held or not. */
    fun one(id: String, blog: String, home: File = this.home): Waiting? = read(place(id, blog, home))

    /** The post was taken back into the form: held from here on. */
    fun hold(id: String, blog: String, home: File = this.home) {
        mark(id, blog, home) { it.copy(held = true) }
    }

    /**
     * Every held post of a blog waits again -- except the one the form
     * still has. A held post nobody holds any more (the form's words
     * were cleared, the app was stopped at the wrong moment) would
     * otherwise be on the device and nowhere to be seen. Whether any did.
     */
    fun release(blog: String, except: String? = null, home: File = this.home): Boolean {
        var any = false
        for (post in everything(blog, home)) {
            if (post.held != true || post.id == except) continue
            mark(post.id, blog, home) { it.copy(held = null) }
            any = true
        }
        return any
    }

    private fun mark(id: String, blog: String, home: File, change: (Waiting) -> Waiting) {
        val place = place(id, blog, home)
        val post = read(place) ?: return
        val changed = change(post)
        if (changed == post) return
        runCatching { write(EngineJson.encodeToString(Waiting.serializer(), changed).toByteArray(Charsets.UTF_8), File(place, "post.json")) }
    }

    /**
     * The post as a delivery: its pictures first and the markdown last,
     * dated by when it was written.
     */
    fun delivery(post: Waiting, blog: String, home: File = this.home): List<DeliveryFile> {
        val place = place(post.id, blog, home)
        val files = post.pieces.map { DeliveryFile(it.name, file(it.name, "media", place).readBytes()) }
        val markdown = Markdown.file(post.title, post.tags, post.text, written = post.at, receipt = post.receipt)
        return files + DeliveryFile(Markdown.fileName(post.title, post.text), markdown.toByteArray(Charsets.UTF_8))
    }

    /**
     * Its pictures as the form holds them, for a post taken back to be
     * written on; the descriptions are the text's to give them.
     */
    fun shots(post: Waiting, blog: String, home: File = this.home): List<Shot> {
        val place = place(post.id, blog, home)
        return post.pieces.mapNotNull { piece ->
            val data = runCatching { file(piece.name, "media", place).readBytes() }.getOrNull() ?: return@mapNotNull null
            Shot(
                piece.name, data, piece.width, piece.height,
                kind = if (piece.video) Shot.Kind.Video else Shot.Kind.Picture,
                poster = runCatching { file(piece.name, "posters", place).readBytes() }.getOrNull(),
                converted = piece.converted,
                thumb = if (piece.video) null else Media.thumbnail(data),
            )
        }
    }

    /** What the blog said to a post it would not take, kept with the post. */
    fun note(problem: String?, id: String, blog: String, home: File = this.home) {
        mark(id, blog, home) { it.copy(problem = problem) }
    }

    /** Sent, or thrown away: it waits no longer. */
    fun remove(id: String, blog: String, home: File = this.home) {
        place(id, blog, home).deleteRecursively()
    }

    /** Everything kept for a blog that leaves the app. */
    fun removeAll(blog: String, home: File = this.home) {
        room(blog, home).deleteRecursively()
    }
}
