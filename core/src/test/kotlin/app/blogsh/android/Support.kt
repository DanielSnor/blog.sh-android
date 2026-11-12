package app.blogsh.android

/** A file from the fixtures the iOS app's tests use too: the two apps answer to the same cases. */
object Fixture {
    fun text(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name.json")) { "no fixture $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }
}

/**
 * The app's English, read from the Android app's resources as they are
 * written: a test has no device, and what an error says is part of what
 * is tested.
 */
object English {
    private val texts: Map<String, String> by lazy {
        // The words carried over from the iOS app, and the ones only the Android app has.
        val xml = java.io.File("../app/src/main/res/values/strings.xml").readText() + java.io.File("../app/src/main/res/values/android.xml").readText()
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).associate { m ->
            var text = m.groupValues[2]
            if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length - 1)
            text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\")
            m.groupValues[1] to text
        }
    }
    /** The words under this name, with what they say filled in. */
    fun say(name: String, args: Array<out Any>): String {
        val text = texts.getValue(name)
        return if (args.isEmpty()) text.replace("%%", "%") else String.format(text, *args)
    }

    fun say(word: app.blogsh.android.model.Word, args: Array<out Any>): String = say(word.name, args)

    /** Called by a test whose subject speaks. */
    fun speak() {
        app.blogsh.android.model.Spoken.source = ::say
    }
}

/** A few words kept in memory, where the app keeps them in its preferences. */
class MemoryNotes : app.blogsh.android.model.Notes {
    private val map = HashMap<String, String>()
    override fun read(key: String): String? = map[key]
    override fun keys(): Set<String> = map.keys.toSet()
    override fun write(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}
