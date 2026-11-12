package app.blogsh.android

import app.blogsh.android.model.Updates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a newer version is out, as GitHub's newest release says it. A
 * mistake here is a phone told to update to what it already runs, or
 * never told of 0.10 because 0.9 reads larger.
 */
class UpdatesTest {
    private fun answer(tag: String, page: String = "https://github.com/DanielSnor/blog.sh-android/releases/tag/$tag") =
        """{"tag_name":"$tag","html_url":"$page","prerelease":false,"assets":[{"name":"blogsh-android.apk"}]}"""

    @Test
    fun versionsAreRankedByTheirNumbersPartByPart() {
        assertTrue(Updates.isNewer("v0.8.0", "0.7.5"))
        assertTrue(Updates.isNewer("v0.10.0", "0.9.9"))
        assertTrue(Updates.isNewer("1.0", "0.99.99"))
        assertTrue(Updates.isNewer("0.7.5.1", "0.7.5"))
        assertFalse(Updates.isNewer("v0.7.5", "0.7.5"))
        assertFalse(Updates.isNewer("0.7.5", "0.7.5.0"))
        assertFalse(Updates.isNewer("v0.7.4", "0.7.5"))
    }

    /** At the same numbers, the finished one comes after its release candidates, those after the betas, the betas after the alphas. */
    @Test
    fun aFinishedVersionComesAfterItsPreReleases() {
        assertTrue(Updates.isNewer("v0.8.0", "0.8.0-rc.2"))
        assertTrue(Updates.isNewer("v0.8.0-rc.2", "0.8.0-rc.1"))
        assertTrue(Updates.isNewer("v0.8.0-rc.1", "0.8.0-beta.3"))
        assertTrue(Updates.isNewer("v0.8.0-beta.1", "0.8.0-alpha.2"))
        assertFalse(Updates.isNewer("v0.8.0-beta.1", "0.8.0"))
        assertTrue(Updates.isNewer("v0.8.1-alpha.1", "0.8.0"))
    }

    @Test
    fun githubsNewestReleaseIsReadAgainstWhatRunsHere() {
        assertEquals(
            Updates.Said.Newer("0.8.0", "https://github.com/DanielSnor/blog.sh-android/releases/tag/v0.8.0"),
            Updates.said(answer("v0.8.0"), "0.7.5"),
        )
        assertEquals(Updates.Said.Newest, Updates.said(answer("v0.7.5"), "0.7.5"))
        // A build newer than anything released -- one made at the desk -- is not told to go back.
        assertEquals(Updates.Said.Newest, Updates.said(answer("v0.7.5"), "0.8.0"))
    }

    /** What cannot be read is said as such, and no page outside GitHub is ever offered to open. */
    @Test
    fun anAnswerThatCannotBeReadOffersNothing() {
        assertEquals(Updates.Said.Unknown, Updates.said("<html>rate limited</html>", "0.7.5"))
        assertEquals(Updates.Said.Unknown, Updates.said("""{"message":"Not Found"}""", "0.7.5"))
        assertEquals(Updates.Said.Unknown, Updates.said(answer("v0.8.0", page = "https://example.org/blogsh.apk"), "0.7.5"))
        assertEquals(Updates.Said.Unknown, Updates.said(answer(""), "0.7.5"))
    }

    @Test
    fun theReleasesAskedAreThisApps() {
        assertEquals("https://api.github.com/repos/DanielSnor/blog.sh-android/releases/latest", Updates.LATEST)
    }
}
