package app.blogsh.android.model

import java.time.Instant

/**
 * What time it is: the device's clock -- and in a test that draws a
 * screen, the moment the test says, so that "Thursday" is not "Oct 8" a
 * week later.
 */
object Now {
    @Volatile
    var clock: () -> Instant = { Instant.now() }

    fun instant(): Instant = clock()
}
