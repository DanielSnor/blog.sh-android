package app.blogsh.android

import app.blogsh.android.model.Begun
import app.blogsh.android.model.BlogList
import app.blogsh.android.model.Desk
import app.blogsh.android.model.Preview
import app.blogsh.android.model.Unsaved
import app.blogsh.android.model.Unsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

private fun blog(): String = UUID.randomUUID().toString()

/**
 * The post being written, kept on the device until it is sent. A mistake
 * here is an evening's writing gone with a closed app -- or an old post
 * coming back into a form that was already sent.
 */
class UnsentTest {
    private val written = Unsent("Pes v trávě", "pes, zahrada", "Plazí se.\n\n![Pes](photo-1.jpg)\n", 1_791_400_000_000)

    @Test
    fun whatIsWrittenIsKeptAndReadBack() {
        val notes = MemoryNotes()
        val blog = blog()
        written.keep(blog, notes)
        assertEquals(written, Unsent.kept(blog, notes))
    }

    /**
     * A title alone is not a post the blog takes: it refuses one with
     * nothing under its header. The form's key waits for words.
     */
    @Test
    fun aPostWithATitleAloneIsNotOneToSend() {
        assertFalse(Unsent("Jen titulek", "", "").canBeSent)
        assertFalse(Unsent("Jen titulek", "štítek", "  \n\t ").canBeSent)
        assertFalse(Unsent().canBeSent)
        assertTrue(Unsent("", "", "Slova bez titulku.").canBeSent)
        assertTrue(Unsent("Titulek", "", "![Pes](photo-1.jpg)").canBeSent)
    }

    /** A post belongs to the blog it was written for. */
    @Test
    fun eachBlogKeepsItsOwn() {
        val notes = MemoryNotes()
        val one = blog()
        val other = blog()
        written.keep(one, notes)
        assertNull(Unsent.kept(other, notes))
        written.copy(title = "Jiný blog").keep(other, notes)
        assertEquals("Pes v trávě", Unsent.kept(one, notes)?.title)
        assertEquals("Jiný blog", Unsent.kept(other, notes)?.title)
    }

    /** Sent, or cleared by hand: nothing is left to bring back. */
    @Test
    fun anEmptiedFormKeepsNothing() {
        val notes = MemoryNotes()
        val blog = blog()
        written.keep(blog, notes)
        Unsent().keep(blog, notes)
        assertNull(Unsent.kept(blog, notes))
        assertNull(notes.read(Unsent.key(blog)))
        Unsent(title = "  ", tags = "", text = "\n\n").keep(blog, notes)
        assertNull(notes.read(Unsent.key(blog)))
    }

    @Test
    fun oneFieldIsEnoughToBeKept() {
        val notes = MemoryNotes()
        val blog = blog()
        Unsent(text = "Jen věta.").keep(blog, notes)
        assertEquals("Jen věta.", Unsent.kept(blog, notes)?.text)
        Unsent(title = "Jen titulek").keep(blog, notes)
        assertEquals("Jen titulek", Unsent.kept(blog, notes)?.title)
    }

    @Test
    fun whatDoesNotReadIsNothing() {
        val notes = MemoryNotes()
        val blog = blog()
        notes.write(Unsent.key(blog), "not json")
        assertNull(Unsent.kept(blog, notes))
        notes.write(Unsent.key(blog), "\"a string\"")
        assertNull(Unsent.kept(blog, notes))
    }

    @Test
    fun forgottenItIsGone() {
        val notes = MemoryNotes()
        val blog = blog()
        written.keep(blog, notes)
        Unsent.forget(blog, notes)
        assertNull(Unsent.kept(blog, notes))
    }

    /** The pictures are not kept; the form says so where the text names one. */
    @Test
    fun itKnowsWhetherItsTextNamesPictures() {
        assertTrue(written.namesPictures)
        assertTrue(Unsent(text = "Před.\n\n!![Klip](clip-1.mp4)\n").namesPictures)
        assertFalse(Unsent(text = "Jen text s [odkazem](https://example.org).").namesPictures)
        assertFalse(Unsent(text = "Vykřičník! [a závorka]").namesPictures)
        assertFalse(Unsent().namesPictures)
    }

    /**
     * A form that was only opened was not written in: what is kept
     * keeps the time it was written.
     */
    @Test
    fun keepingTheSameWordsAgainKeepsTheirTime() {
        val notes = MemoryNotes()
        val blog = blog()
        written.keep(blog, notes)
        var again = written.copy(at = 1_791_500_000_000)
        again.keep(blog, notes)
        assertEquals(written.at, Unsent.kept(blog, notes)?.at)
        again = again.copy(text = again.text + " A dál.")
        again.keep(blog, notes)
        assertEquals(again.at, Unsent.kept(blog, notes)?.at)
    }

    /**
     * What the form kept under its old name, before it was kept here, is
     * moved once: an update of the app loses nobody's words.
     */
    @Test
    fun wordsKeptUnderTheOldNameAreCarriedOverOnce() {
        val notes = MemoryNotes()
        val blog = blog()
        assertNull(Unsent.carriedOver(blog, notes, 5))
        notes.write("compose.draft.$blog", """{"title":"Pes","tags":"","text":"Plazí se."}""")
        val carried = Unsent.carriedOver(blog, notes, 5)
        assertEquals(Unsent("Pes", "", "Plazí se.", 5), carried)
        assertEquals(carried, Unsent.kept(blog, notes))
        assertNull(notes.read("compose.draft.$blog"))
        assertNull(Unsent.carriedOver(blog, notes, 9))
        // Nothing worth keeping is not carried anywhere, and does not stay behind either.
        val other = blog()
        notes.write("compose.draft.$other", """{"title":"","tags":"","text":""}""")
        assertNull(Unsent.carriedOver(other, notes, 5))
        assertNull(Unsent.kept(other, notes))
        assertNull(notes.read("compose.draft.$other"))
    }
}

/**
 * Changes to a post the blog has, kept until they are saved. A mistake
 * here is an edit lost with a closed app -- or one post's changes coming
 * back into another, or into another language of the same.
 */
class UnsavedTest {
    private val changed = Unsaved("---\ntitle: Venku\n---\n\nNový odstavec.\n", "abc123", 1_791_400_000_000)
    private val text = Unsaved.What.Text
    private fun language(code: String) = Unsaved.What.Language(code)

    @Test
    fun changesAreKeptForTheirPostAndReadBack() {
        val notes = MemoryNotes()
        val blog = blog()
        changed.keep(blog, "venku", text, notes)
        assertEquals(changed, Unsaved.kept(blog, "venku", text, notes))
    }

    /**
     * Each to its own: another post, another language of the same post,
     * another blog with a post of the same name.
     */
    @Test
    fun nothingComesBackIntoAnotherPlace() {
        val notes = MemoryNotes()
        val blog = blog()
        changed.keep(blog, "venku", text, notes)
        assertNull(Unsaved.kept(blog, "doma", text, notes))
        assertNull(Unsaved.kept(blog, "venku", language("en"), notes))
        assertNull(Unsaved.kept(blog(), "venku", text, notes))
        changed.copy(text = "A new paragraph.").keep(blog, "venku", language("en"), notes)
        assertNull(Unsaved.kept(blog, "venku", language("de"), notes))
        assertEquals("A new paragraph.", Unsaved.kept(blog, "venku", language("en"), notes)?.text)
        assertEquals(changed, Unsaved.kept(blog, "venku", text, notes))
    }

    @Test
    fun forgottenTheyAreGoneAndOnlyThey() {
        val notes = MemoryNotes()
        val blog = blog()
        changed.keep(blog, "venku", text, notes)
        changed.keep(blog, "venku", language("en"), notes)
        Unsaved.forget(blog, "venku", text, notes)
        assertNull(Unsaved.kept(blog, "venku", text, notes))
        assertNotNull(Unsaved.kept(blog, "venku", language("en"), notes))
    }

    /**
     * A blog that leaves the app takes all of its own with it, and no
     * other blog's.
     */
    @Test
    fun aBlogThatLeavesTakesItsOwn() {
        val notes = MemoryNotes()
        val leaving = blog()
        val staying = blog()
        changed.keep(leaving, "venku", text, notes)
        changed.keep(leaving, "doma", language("en"), notes)
        changed.keep(staying, "venku", text, notes)
        Unsaved.forgetAll(leaving, notes)
        assertNull(Unsaved.kept(leaving, "venku", text, notes))
        assertNull(Unsaved.kept(leaving, "doma", language("en"), notes))
        assertEquals(changed, Unsaved.kept(staying, "venku", text, notes))
    }

    /** Removed from the list of blogs: its writing goes, and the connection to its server. */
    @Test
    fun aBlogRemovedFromTheListTakesItsWritingAndItsConnection() {
        val notes = MemoryNotes()
        var hungUp = 0
        val list = BlogList(notes, hangUp = { hungUp += 1 }) { }
        val leaving = list.add().id
        val staying = list.add().id
        Unsent(title = "Odchází", at = 1).keep(leaving, notes)
        changed.keep(leaving, "venku", text, notes)
        Unsent(title = "Zůstává", at = 1).keep(staying, notes)
        list.remove(leaving)
        assertTrue(Begun.all(leaving, notes).isEmpty())
        assertEquals(listOf("Zůstává"), Begun.all(staying, notes).map { it.title })
        assertEquals(1, hungUp)
    }

    @Test
    fun keepingTheSameWordsAgainKeepsTheirTime() {
        val notes = MemoryNotes()
        val blog = blog()
        changed.keep(blog, "venku", text, notes)
        var again = changed.copy(at = 1_791_500_000_000, base = "def456")
        again.keep(blog, "venku", text, notes)
        assertEquals(changed, Unsaved.kept(blog, "venku", text, notes))
        again = again.copy(text = again.text + "Ještě věta.\n")
        again.keep(blog, "venku", text, notes)
        assertEquals(again, Unsaved.kept(blog, "venku", text, notes))
    }

    @Test
    fun whatDoesNotReadIsNothing() {
        val notes = MemoryNotes()
        val blog = blog()
        notes.write(Unsaved.key(blog, "venku", text), "not json")
        assertNull(Unsaved.kept(blog, "venku", text, notes))
    }

    /**
     * The pictures the post has are there to be named; one chosen on the
     * device for these changes was not kept with them.
     */
    @Test
    fun itKnowsWhenItsTextNamesAPictureThePostDoesNotHave() {
        val kept = changed.copy(text = "Text.\n\n![Les](photo-1.jpg)\n\n!![Klip](clip-1.mp4)\n")
        assertFalse(kept.namesPictures(listOf("photo-1.jpg", "clip-1.mp4")))
        assertTrue(kept.namesPictures(listOf("photo-1.jpg")))
        assertTrue(kept.namesPictures(emptyList()))
        assertFalse(changed.namesPictures(emptyList()))
    }
}

/**
 * What the first screen lists as begun and not finished. A mistake here
 * is writing that waits in a form nobody knows to open -- or another
 * blog's writing offered under this one.
 */
class BegunTest {
    private fun at(seconds: Long): Long = 1_791_400_000_000 + seconds * 1000

    @Test
    fun withNothingKeptNothingIsBegun() {
        assertTrue(Begun.all(blog(), MemoryNotes()).isEmpty())
    }

    /**
     * The new post first, however long ago it was written; then the
     * changes to posts, the last written first.
     */
    @Test
    fun theNewPostComesFirstThenTheChangesTheLastWrittenFirst() {
        val notes = MemoryNotes()
        val blog = blog()
        Unsent("Pes v trávě", "", "Plazí se.", at(100)).keep(blog, notes)
        Unsaved("změna", "b", at(300), "Venku").keep(blog, "venku", Unsaved.What.Text, notes)
        Unsaved("change", "b", at(200), "Doma").keep(blog, "doma", Unsaved.What.Language("en"), notes)
        val all = Begun.all(blog, notes)
        assertEquals(listOf(Begun.What.New, Begun.What.Text("venku"), Begun.What.Language("doma", "en")), all.map { it.what })
        assertEquals(listOf("Pes v trávě", "Venku", "Doma"), all.map { it.title })
        assertEquals(listOf(at(100), at(300), at(200)), all.map { it.at })
    }

    @Test
    fun anotherBlogsWritingIsNotThisOnes() {
        val notes = MemoryNotes()
        val mine = blog()
        val other = blog()
        Unsent("Cizí", "", "", at(0)).keep(other, notes)
        Unsaved("x", "b", at(0), "Cizí").keep(other, "venku", Unsaved.What.Text, notes)
        assertTrue(Begun.all(mine, notes).isEmpty())
        assertEquals(2, Begun.all(other, notes).size)
    }

    /** Sent, saved or put away: gone from the list with it. */
    @Test
    fun whatIsFinishedIsNoLongerListed() {
        val notes = MemoryNotes()
        val blog = blog()
        Unsent("Pes", "", "", at(0)).keep(blog, notes)
        Unsaved("x", "b", at(1), "Venku").keep(blog, "venku", Unsaved.What.Text, notes)
        Unsent().keep(blog, notes)
        assertEquals(listOf<Begun.What>(Begun.What.Text("venku")), Begun.all(blog, notes).map { it.what })
        Unsaved.forget(blog, "venku", Unsaved.What.Text, notes)
        assertTrue(Begun.all(blog, notes).isEmpty())
    }

    /** A slug is whatever the blog made it; one with a dot in it is still one slug. */
    @Test
    fun aPostIsFoundByItsWholeSlug() {
        val notes = MemoryNotes()
        val blog = blog()
        Unsaved("x", "b", at(0), null).keep(blog, "verze-1.10", Unsaved.What.Language("de"), notes)
        val all = Begun.all(blog, notes)
        assertEquals(listOf<Begun.What>(Begun.What.Language("verze-1.10", "de")), all.map { it.what })
        // Kept without a title -- by an earlier build of the app: its slug stands in.
        assertEquals("verze-1.10", all.firstOrNull()?.title)
    }

    @Test
    fun aPostWithoutATitleIsCalledByItsFirstWords() {
        assertEquals("Pes", Unsent(title = "  Pes  ", text = "Text.").headline)
        assertEquals("Nadpis uvnitř", Unsent(text = "\n\n## Nadpis uvnitř\n\nA dál.").headline)
        assertEquals("První věta.", Unsent(text = "![Les](photo-1.jpg)\n\nPrvní věta.").headline)
        assertEquals(60, Unsent(text = "slovo ".repeat(30)).headline.length)
        assertEquals("", Unsent(tags = "jen, štítky").headline)
    }

    /** Changes kept by a build that did not keep the title yet still read. */
    @Test
    fun changesKeptWithoutATitleStillRead() {
        val notes = MemoryNotes()
        val blog = blog()
        notes.write(Unsaved.key(blog, "venku", Unsaved.What.Text), """{"text":"x","base":"b","at":0}""")
        assertNull(Unsaved.kept(blog, "venku", Unsaved.What.Text, notes)?.title)
        assertNotNull(Unsaved.kept(blog, "venku", Unsaved.What.Text, notes))
        assertEquals("venku", Begun.all(blog, notes).firstOrNull()?.title)
    }
}

/**
 * What an audit of the keeping found: writing that could be lost, or
 * said to be unsent when it was sent.
 */
class KeepingTest {
    private fun moment(seconds: Long): Long = (1_791_400_000 + seconds) * 1000

    /**
     * Changes brought back stay changes over the version they were begun
     * over, whatever is typed after: that is what the screen compares
     * with the blog's version to say the post has moved on.
     */
    @Test
    fun changesBroughtBackKeepTheBaseTheyWereBegunOver() {
        val begun = Unsaved("old A", "K0", moment(0), "Venku")
        val typed = Unsaved.now("old AB", "Venku", "K1", begun, moment(60))
        assertEquals("K0", typed.base)
        assertEquals("old AB", typed.text)
        // Nothing brought back: the changes begin over what the blog has now.
        assertEquals("K1", Unsaved.now("new B", "Venku", "K1", null, moment(60)).base)
    }

    /** A save forgets what it saved, and nothing written after it. */
    @Test
    fun aSaveForgetsOnlyWhatItSaved() {
        val notes = MemoryNotes()
        val blog = blog()
        Unsaved("saved", "K0", moment(0), null).keep(blog, "venku", Unsaved.What.Text, notes)
        Unsaved.forget(blog, "venku", Unsaved.What.Text, "something else", notes)
        assertEquals("saved", Unsaved.kept(blog, "venku", Unsaved.What.Text, notes)?.text)
        Unsaved.forget(blog, "venku", Unsaved.What.Text, "saved", notes)
        assertNull(Unsaved.kept(blog, "venku", Unsaved.What.Text, notes))
    }

    /**
     * A post that arrived is forgotten whether or not a form saw it go;
     * what was written since is another post's, and stays.
     */
    @Test
    fun aSentPostIsForgottenUnlessMoreWasWritten() {
        val notes = MemoryNotes()
        val blog = blog()
        val sent = Unsent("Hello", "a", "Text", moment(0))
        sent.keep(blog, notes)
        Unsent.forget(blog, Unsent("Hello", "a", "Text"), notes)
        assertNull(Unsent.kept(blog, notes))
        Unsent("Hello", "a", "Text and more", moment(5)).keep(blog, notes)
        Unsent.forget(blog, sent, notes)
        assertEquals("Text and more", Unsent.kept(blog, notes)?.text)
    }

    /**
     * A renamed post takes what was kept for it along: its text and
     * every language, and nothing of another post or another blog.
     */
    @Test
    fun whatIsKeptFollowsARenamedPost() {
        val notes = MemoryNotes()
        val blog = blog()
        val other = blog()
        val en = Unsaved.What.Language("en")
        Unsaved("text", "K0", moment(0), "Venku").keep(blog, "venku", Unsaved.What.Text, notes)
        Unsaved("words", "K0", moment(1), "Venku").keep(blog, "venku", en, notes)
        Unsaved("beside", "B0", moment(2), null).keep(blog, "venku-2", Unsaved.What.Text, notes)
        Unsaved("elsewhere", "E0", moment(3), null).keep(other, "venku", Unsaved.What.Text, notes)
        Unsaved.move(blog, "venku", "outside", notes)
        assertEquals("text", Unsaved.kept(blog, "outside", Unsaved.What.Text, notes)?.text)
        assertEquals("words", Unsaved.kept(blog, "outside", en, notes)?.text)
        assertNull(Unsaved.kept(blog, "venku", Unsaved.What.Text, notes))
        assertNull(Unsaved.kept(blog, "venku", en, notes))
        assertEquals("beside", Unsaved.kept(blog, "venku-2", Unsaved.What.Text, notes)?.text)
        assertEquals("elsewhere", Unsaved.kept(other, "venku", Unsaved.What.Text, notes)?.text)
        // The first screen lists them under the name that opens.
        assertEquals(
            setOf(Begun.What.Text("outside"), Begun.What.Language("outside", "en"), Begun.What.Text("venku-2")),
            Begun.all(blog, notes).map { it.what }.toSet(),
        )
    }

    /** Where the new name has later writing of its own, that stays. */
    @Test
    fun aRenameDoesNotPutOlderWritingOverNewer() {
        val notes = MemoryNotes()
        val blog = blog()
        Unsaved("older", "K0", moment(0), null).keep(blog, "venku", Unsaved.What.Text, notes)
        Unsaved("newer", "K1", moment(60), null).keep(blog, "outside", Unsaved.What.Text, notes)
        Unsaved.move(blog, "venku", "outside", notes)
        assertEquals("newer", Unsaved.kept(blog, "outside", Unsaved.What.Text, notes)?.text)
        assertNull(Unsaved.kept(blog, "venku", Unsaved.What.Text, notes))
    }

    /**
     * Words with no title and no body take the language off the post,
     * whichever key sent them: the screen says so, not "Saved".
     */
    @Test
    fun emptiedWordsTakeTheLanguageOff() {
        assertTrue(Preview.takesOff(""))
        assertTrue(Preview.takesOff("---\ntitle:\n---\n\n"))
        assertTrue(Preview.takesOff("---\ntitle: \nslug: outside\n---\n\n  \n"))
        assertFalse(Preview.takesOff("---\ntitle: Outside\n---\n\n"))
        assertFalse(Preview.takesOff("---\ntitle:\n---\n\nWords.\n"))
        assertFalse(Preview.takesOff("Words."))
    }

    /**
     * A form is left alone while its blog's new post is on its way, and
     * told when the post has arrived.
     */
    @Test
    fun theDeskKnowsWhichBlogsPostIsOnItsWay() {
        val blog = blog()
        val other = blog()
        val before = Desk.arrived
        Desk.began(blog)
        assertTrue(blog in Desk.sending)
        assertFalse(other in Desk.sending)
        Desk.ended(blog, arrived = false)
        assertFalse(blog in Desk.sending)
        assertEquals(before, Desk.arrived)
        Desk.began(blog)
        Desk.ended(blog, arrived = true)
        assertEquals(before + 1, Desk.arrived)
        assertEquals(blog, Desk.arrivedAt)
    }
}
