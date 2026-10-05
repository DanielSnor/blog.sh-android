package app.blogsh.android

import android.app.Application
import android.content.Context
import app.blogsh.android.model.Engine

class BlogshApp : Application() {
    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        Engine.installCrypto()
    }

    companion object {
        /** The application's own context: what the model asks for its words and its files. */
        lateinit var context: Context
            private set
    }
}
