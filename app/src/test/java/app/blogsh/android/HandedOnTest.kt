package app.blogsh.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a screen hands its editor: the model's tests are in core/. */
class HandedOnTest {
    /**
     * The editor is handed the text the post's screen read only where it
     * was read in this visit and is this post's under this name: a text
     * read before the screen was left and come back to may be an old one.
     */
    @Test
    fun theEditorIsHandedOnlyWhatWasJustRead() {
        val entry = app.blogsh.android.model.EditEntry(
            slug = "venku", title = "Venku", date = "2026-10-01T10:00:00+02:00", scheduled = false, editable = true,
            text = "text", media = emptyList(), preview = "/draft/x/", base = "K1",
        )
        assertEquals("K1", app.blogsh.android.ui.screens.handedOn(entry, fresh = true, slug = "venku")?.base)
        assertNull(app.blogsh.android.ui.screens.handedOn(entry, fresh = false, slug = "venku"))
        assertNull(app.blogsh.android.ui.screens.handedOn(entry, fresh = true, slug = "outside"))
        assertNull(app.blogsh.android.ui.screens.handedOn(null, fresh = true, slug = "venku"))
    }
}
