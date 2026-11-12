package app.blogsh.android.model

/**
 * The app's words, for the code that is not a screen: an error has to say
 * itself in the reader's language wherever it is thrown. Each app puts
 * its own source here when it starts -- it knows where its words are
 * written -- and a test puts its own, since it has no device to ask.
 */
object Spoken {
    var source: (Word, Array<out Any>) -> String = { word, _ -> word.name }

    fun say(word: Word, vararg args: Any): String = source(word, args)
}

/**
 * The words the model says itself, under the names they have in the
 * app's files of words (`values/strings.xml` and the rest): an app looks
 * each one up by its name.
 */
@Suppress("EnumEntryName")
enum class Word {
    something_else_holds_the_blog_just_now,
    the_app_has_no_key_yet,
    the_blog_said_no_2,
    the_engine_did_not_answer_as_data,
    the_file_could_not_be_read,
    the_file_is_too_large_to_be,
    the_server_closed_the_connection_without_an,
    the_server_did_not_answer_is_this,
    the_server_does_not_know_this_blog,
    the_server_does_not_know_this_blog_2,
    the_server_is_not_set_up_yet,
    the_server_s_key_changed_if_the,
    the_site_does_not_show_this_change,
    there_is_no_text_in_the_file,
    this_file_is_not_plain_text_a,
    this_is_the_practice_blog_it_lives,
    two_posts_in_different_years_share_this,
}
