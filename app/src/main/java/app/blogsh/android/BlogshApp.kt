package app.blogsh.android

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import app.blogsh.android.model.AppLanguage
import app.blogsh.android.model.Engine
import app.blogsh.android.model.NetworkWatch
import app.blogsh.android.model.PreferenceNotes
import java.util.Locale

class BlogshApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        speak(base)
    }

    override fun onCreate() {
        super.onCreate()
        // The words the model says -- an error, what the herald reports -- in the
        // language the screens are in.
        val chosen = chosenLocale(this)
        speak(this)
        context = if (chosen == null) applicationContext
        else applicationContext.createConfigurationContext(Configuration(resources.configuration).apply { setLocale(chosen) })
        Engine.installCrypto()
        NetworkWatch.begin(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        speak(this)
    }

    companion object {
        /** The application's own context: what the model asks for its words and its files. */
        lateinit var context: Context
            private set

        /**
         * The language chosen in the settings as it was when the app
         * started -- like a language chosen in the system's own settings,
         * it is taken up then, not in the middle of a screen. None for
         * the system's.
         */
        private var started: Locale? = null
        private var read = false

        fun chosenLocale(base: Context): Locale? {
            if (!read) {
                started = AppLanguage.read(PreferenceNotes(base)).locale
                read = true
            }
            return started
        }

        /**
         * Dates, numbers and the case of letters follow the chosen language
         * too. Said again and again -- when the app starts, when a screen
         * is made, at every change of the configuration: the system puts
         * its own language back each time.
         */
        fun speak(base: Context) {
            chosenLocale(base)?.let { Locale.setDefault(it) }
        }

        /** The language the app's words are in: the one chosen, or the system's. */
        val spoken: Locale get() = started ?: Locale.getDefault()
    }
}
