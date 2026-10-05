package app.blogsh.android.model

import app.blogsh.android.BlogshApp

/**
 * The app's words, for the code that is not a screen: an error has to say
 * itself in the reader's language wherever it is thrown. A test puts its
 * own source here, since it has no device to ask.
 */
object Spoken {
    var source: (Int, Array<out Any>) -> String = { id, args -> BlogshApp.context.getString(id, *args) }

    fun say(id: Int, vararg args: Any): String = source(id, args)
}
