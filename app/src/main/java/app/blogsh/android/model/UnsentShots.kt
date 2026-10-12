package app.blogsh.android.model

import app.blogsh.android.BlogshApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * The pictures and videos of the post being written, kept on the device
 * beside its words (`Unsent`): chosen, not sent yet. The form holds them
 * in memory, and a form left -- a way back taken, another tile opened,
 * another blog, an app stopped behind another -- took them along; the
 * words came back and the pictures had to be found again.
 *
 * The same holds for pictures added to a post the blog already has,
 * while its text is being changed: kept until the change is saved.
 *
 * A directory to a blog, and in it one to each writing -- `new` for the
 * new post, of which a blog has one at a time, and `edits/<slug>` for a
 * post being changed: `shots.json`, the list in the form's order, and
 * beside it the files as they will travel.
 */
object UnsentShots {
    /** One picture or video, as the form's card has it. */
    @Serializable
    data class Piece(
        val name: String,
        val alt: String = "",
        val video: Boolean = false,
        val width: Int = 0,
        val height: Int = 0,
        val converted: Boolean = true,
    )

    val home: File get() = File(BlogshApp.context.filesDir, "unsent")

    /** The new post's room, or that of the post whose text is being changed. */
    private fun room(blog: String, slug: String?, home: File): File {
        val blogs = File(home, File(blog).name)
        if (slug == null) return File(blogs, "new")
        // One name, whatever the slug holds: a directory of this blog's edits.
        val name = slug.replace("/", "_").replace("..", "_")
        return File(File(blogs, "edits"), name.ifEmpty { "_" })
    }

    /** A name as a file of the room's own directory, and of no other. */
    private fun file(name: String, under: String, room: File) = File(File(room, under), File(name).name)

    /** Written beside, then put in its place: a file is whole or it is not there. */
    private fun write(bytes: ByteArray, to: File) {
        val beside = File(to.parentFile, to.name + ".part")
        beside.writeBytes(bytes)
        if (!beside.renameTo(to)) {
            to.delete()
            if (!beside.renameTo(to)) throw java.io.IOException("could not write " + to.name)
        }
    }

    private fun list(room: File): List<Piece> = runCatching {
        EngineJson.decodeFromString(ListSerializer(Piece.serializer()), File(room, "shots.json").readText(Charsets.UTF_8))
    }.getOrNull() ?: emptyList()

    /**
     * Keeps what the form holds now: a file is written once, when its
     * shot is new; one whose shot is gone from the form goes too. With
     * nothing in the form, nothing is kept.
     */
    @Synchronized
    fun keep(shots: List<Shot>, blog: String, slug: String? = null, home: File = this.home) {
        val room = room(blog, slug, home)
        if (shots.isEmpty()) {
            room.deleteRecursively()
            return
        }
        try {
            for (directory in listOf("media", "posters")) {
                val place = File(room, directory)
                if (!place.mkdirs() && !place.isDirectory) throw java.io.IOException("could not make " + directory)
            }
            val wanted = shots.map { File(it.name).name }.toSet()
            for (directory in listOf("media", "posters")) {
                for (name in File(room, directory).list() ?: emptyArray()) {
                    if (name !in wanted) File(File(room, directory), name).delete()
                }
            }
            for (shot in shots) {
                val media = file(shot.name, "media", room)
                if (!media.exists()) write(shot.data, media)
                val poster = file(shot.name, "posters", room)
                val frame = shot.poster
                if (frame != null && !poster.exists()) write(frame, poster)
            }
            val pieces = shots.map { Piece(it.name, it.alt, it.kind == Shot.Kind.Video, it.width, it.height, it.converted) }
            // Last, and only when it says something new: a description
            // is typed a letter at a time.
            if (pieces != list(room)) {
                write(EngineJson.encodeToString(ListSerializer(Piece.serializer()), pieces).toByteArray(Charsets.UTF_8), File(room, "shots.json"))
            }
        } catch (e: Exception) {
            // Not kept, then: the form still has them, and says so itself
            // when it comes back without. Kept where a device's log can be read.
            runCatching { android.util.Log.w("blogsh", "shots not kept", e) }
        }
    }

    /**
     * What is kept for a blog, as the form holds its shots; one whose
     * file does not read is left out.
     */
    @Synchronized
    fun shots(blog: String, slug: String? = null, home: File = this.home): List<Shot> {
        val room = room(blog, slug, home)
        return list(room).mapNotNull { piece ->
            val data = runCatching { file(piece.name, "media", room).readBytes() }.getOrNull() ?: return@mapNotNull null
            Shot(
                piece.name, data, piece.width, piece.height, alt = piece.alt,
                kind = if (piece.video) Shot.Kind.Video else Shot.Kind.Picture,
                poster = runCatching { file(piece.name, "posters", room).readBytes() }.getOrNull(),
                converted = piece.converted,
                thumb = if (piece.video) null else Media.thumbnail(data),
            )
        }
    }

    /** When they were last kept, in milliseconds since 1970; null where nothing is. */
    fun kept(blog: String, slug: String? = null, home: File = this.home): Long? =
        File(room(blog, slug, home), "shots.json").takeIf { it.isFile }?.lastModified()?.takeIf { it > 0 }

    /** The post was sent, put by or thrown away; the change was saved or given up. */
    @Synchronized
    fun forget(blog: String, slug: String? = null, home: File = this.home) {
        room(blog, slug, home).deleteRecursively()
    }

    /** The post was renamed: what was kept for it follows it to its new name. */
    @Synchronized
    fun move(blog: String, from: String, to: String, home: File = this.home) {
        val old = room(blog, from, home)
        val new = room(blog, to, home)
        if (from == to || !old.exists()) return
        new.deleteRecursively()
        new.parentFile?.mkdirs()
        old.renameTo(new)
    }

    /** Everything kept for a blog that leaves the app. */
    @Synchronized
    fun forgetAll(blog: String, home: File = this.home) {
        File(home, File(blog).name).deleteRecursively()
    }
}
