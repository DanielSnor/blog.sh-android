package app.blogsh.android

import app.blogsh.android.model.Delivery
import app.blogsh.android.model.Kept
import app.blogsh.android.model.Markdown
import app.blogsh.android.model.Pictures
import app.blogsh.android.model.Shot
import app.blogsh.android.model.encodedSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A post on its way to the blog: its file, its pictures' names, and how
 * much all of it weighs against the server's limit.
 */
class PostTest {
    // the markdown file

    @Test
    fun theHeaderHoldsWhatTheFormHasFieldsFor() {
        assertEquals("---\ntitle: Hello\ntags: a, b\n---\n\n", Markdown.frontMatter(" Hello ", "a, b"))
        assertEquals("---\ntitle: Hello\npublish: yes\n---\n\n", Markdown.frontMatter("Hello", "", publish = true))
    }

    @Test
    fun nothingFilledInIsNoHeader() = assertEquals("", Markdown.frontMatter("  ", " , "))

    /**
     * A title in quotes or tags written the YAML way would be read by the
     * engine as something else than the words.
     */
    @Test
    fun quotesBracketsAndHashesAreTakenOff() {
        assertEquals("---\ntitle: Hello\n---\n\n", Markdown.frontMatter("\"Hello\"", ""))
        assertEquals("---\ntags: ruby, blog.sh, x\n---\n\n", Markdown.frontMatter("", "#ruby, [blog.sh], 'x'"))
        assertEquals("---\ntags: a, b\n---\n\n", Markdown.frontMatter("", "[a, b]"))
    }

    @Test
    fun theFileIsItsHeaderAndItsTextWithOneNewlineAtTheEnd() {
        assertEquals("---\ntitle: T\n---\n\ntext\n", Markdown.file("T", "", "\n  text \n\n"))
        assertEquals("text\n", Markdown.file("", "", "text"))
    }

    /** A text that itself opens with --- would be read as a header. */
    @Test
    fun aTextOpeningWithDashesGetsAnEmptyHeaderBeforeIt() {
        assertEquals("---\n---\n\n--- and so on\n", Markdown.file("", "", "--- and so on"))
    }

    @Test
    fun theFileIsNamedByItsTitleFolded() {
        assertEquals("zlutoucky-kun-upel.md", Markdown.fileName("Žluťoučký kůň úpěl", "x"))
        assertEquals("one-two-three-four-five-six.md", Markdown.fileName("", "one two three four five six seven"))
        assertEquals("post.md", Markdown.fileName("", ""))
        assertEquals("post.md", Markdown.fileName("!!!", ""))
        assertEquals(43, Markdown.fileName("abcdefghij ".repeat(6), "").length)
    }

    // names of pictures and videos

    @Test
    fun aPictureKeepsItsOwnNameFolded() {
        assertEquals("img-1234.jpg", Pictures.safeName("IMG 1234.HEIC", 1))
        assertEquals("zaba-na-prameni.jpg", Pictures.safeName("Žába na prameni.png", 1))
        assertEquals("a-b-c.jpg", Pictures.safeName("a.b.c.jpeg", 1))
    }

    @Test
    fun aPictureWithoutANameIsNumbered() {
        assertEquals("photo-3.jpg", Pictures.safeName(null, 3))
        assertEquals("photo-2.jpg", Pictures.safeName("???.jpg", 2))
        assertEquals("video-1.mp4", Pictures.safeName(null, 1, stem = "video", ext = "mp4"))
    }

    @Test
    fun twoPicturesFoldingToOneNameAreToldApart() {
        assertEquals("a.jpg", Pictures.freeName("a.jpg", emptyList()))
        assertEquals("a-2.jpg", Pictures.freeName("a.jpg", listOf("a.jpg")))
        assertEquals("a-3.jpg", Pictures.freeName("a.jpg", listOf("a.jpg", "a-2.jpg")))
    }

    // the mark of a shot in the text

    private fun shot(name: String, bytes: Int = 10, alt: String = "", kind: Shot.Kind = Shot.Kind.Picture) =
        Shot(name, ByteArray(bytes), 10, 10, alt, kind)

    @Test
    fun aPictureIsOneMarkAVideoTwo() {
        assertEquals("![a cat](cat.jpg)", shot("cat.jpg", alt = " a cat ").mark)
        assertEquals("!![](clip.mp4)", shot("clip.mp4", kind = Shot.Kind.Video).mark)
    }

    @Test
    fun aShotsMarkIsFoundWhateverItsDescriptionSays() {
        val pattern = shot("cat.jpg").markPattern
        assertTrue(pattern.containsMatchIn("before ![a cat](cat.jpg) after"))
        assertTrue(pattern.containsMatchIn("!![](cat.jpg)"))
        assertFalse(pattern.containsMatchIn("![a cat](cat2.jpg)"))
        // The dot of the name is a dot, not any letter.
        assertFalse(pattern.containsMatchIn("![a cat](catxjpg)"))
    }

    // a description, on its card and in the text -- one thing

    private fun List<Shot>.saying(index: Int, words: String): List<Shot> = mapIndexed { i, shot -> if (i == index) shot.withAlt(words) else shot }
    private val List<Shot>.alts: List<String> get() = map { it.alt }

    /** Typed on a card after the picture went into the text: the mark there takes it, letter by letter. */
    @Test
    fun aDescriptionTypedOnACardGoesIntoItsMark() {
        var text = "Before.\n\n![](cat.jpg)\n\nAfter."
        text = Kept.typed(text, "cat.jpg", "a")
        text = Kept.typed(text, "cat.jpg", "a cat")
        assertEquals("Before.\n\n![a cat](cat.jpg)\n\nAfter.", text)
    }

    /** The other way: typed into the mark in the text, it is on the card. */
    @Test
    fun aDescriptionTypedIntoTheTextGoesOntoItsCard() {
        val cards = listOf(shot("dog.jpg"), shot("clip.mp4", alt = "old", kind = Shot.Kind.Video), shot("unused.jpg", alt = "kept"))
        val heard = Kept.heard(cards, "![A dog in the grass](dog.jpg)\n\n!![the clip](clip.mp4)\n")
        assertEquals(listOf("A dog in the grass", "the clip", "kept"), heard.alts)
        // A mark that says nothing empties its card; a picture the text
        // does not name keeps its own words for when it is put in.
        assertEquals("", Kept.heard(listOf(shot("dog.jpg", alt = "x")), "![](dog.jpg)")[0].alt)
        assertEquals("x", Kept.heard(listOf(shot("dog.jpg", alt = "x")), "No picture here.")[0].alt)
    }

    /**
     * The report this is pinned for, both halves of it. Written in the
     * text, the words are on the card; on the card they are added to,
     * not typed again from the start; and added to in the text once
     * more, the card has that too.
     */
    @Test
    fun writtenHereOrThereItIsOneDescription() {
        var cards = listOf(shot("dog.jpg"))
        var text = "![Pes v trávě](dog.jpg)\n\nText.\n"
        cards = Kept.heard(cards, text)
        assertEquals("Pes v trávě", cards[0].alt)

        // On the card: ", plazí se" after what is there.
        var before = cards.alts
        cards = cards.saying(0, cards[0].alt + ", plazí se")
        text = Kept.retitled(text, cards, before)
        assertEquals("![Pes v trávě, plazí se](dog.jpg)\n\nText.\n", text)
        // ...which the card hears back and has nothing to change.
        assertEquals(cards.alts, Kept.heard(cards, text).alts)

        // Back in the text: " a schovává se".
        text = text.replace("plazí se]", "plazí se a schovává se]")
        cards = Kept.heard(cards, text)
        assertEquals("Pes v trávě, plazí se a schovává se", cards[0].alt)

        // And from the card once more.
        before = cards.alts
        cards = cards.saying(0, "Pes")
        text = Kept.retitled(text, cards, before)
        assertEquals("![Pes](dog.jpg)\n\nText.\n", text)
    }

    /**
     * Neither way undoes a space just typed at the end of a word: what
     * says the same but for its spaces is left as it is, in both places.
     */
    @Test
    fun aSpaceTypedAtTheEndIsNotTakenBack() {
        // In the text: "![Pes ](dog.jpg)" while the card still has "Pes".
        val card = shot("dog.jpg", alt = "Pes")
        assertEquals("Pes", Kept.heard(listOf(card), "![Pes ](dog.jpg)")[0].alt)
        assertEquals("![Pes ](dog.jpg)", Kept.typed("![Pes ](dog.jpg)", "dog.jpg", "Pes"))
        // On the card: "Pes " while the text still has "![Pes](dog.jpg)".
        assertEquals("![Pes](dog.jpg)", Kept.typed("![Pes](dog.jpg)", "dog.jpg", "Pes "))
        // The next letter goes through, from either side.
        assertEquals("![Pes v](dog.jpg)", Kept.typed("![Pes](dog.jpg)", "dog.jpg", "Pes v"))
        assertEquals("Pes v", Kept.heard(listOf(card), "![Pes v](dog.jpg)")[0].alt)
        assertEquals("![Pes v](dog.jpg)", Kept.typed("![Pes ](dog.jpg)", "dog.jpg", "Pes v"))
    }

    // The two places as the forms wire them: a change of the text is
    // heard by the cards, a change of a card is typed into the text, each
    // until nothing moves.
    private class Two(var text: String, var cards: List<Shot>) {
        var rounds = 0

        fun settle() {
            for (round in 0 until 8) {
                rounds = round
                val before = cards.map { it.alt }
                cards = Kept.heard(cards, text)
                val next = Kept.retitled(text, cards, before)
                if (next == text && cards.map { it.alt } == before) return
                text = next
            }
            rounds = 8
        }

        /** A card written on, as its field does it. */
        fun write(index: Int, words: String) {
            val before = cards.map { it.alt }
            cards = cards.mapIndexed { i, shot -> if (i == index) shot.withAlt(words) else shot }
            text = Kept.retitled(text, cards, before)
        }
    }

    /** The same sequence every run: a test that fails must fail again. */
    private class Dice(private var seed: ULong) {
        fun next(bound: Int): Int {
            seed = seed * 6364136223846793005uL + 1442695040888963407uL
            return ((seed shr 33) % bound.toULong()).toInt()
        }
    }

    /**
     * A square bracket in a description: the engine takes it (its own
     * pattern for a picture reads the description up to the last "](" of
     * the line), so the two places must stay one through it.
     */
    @Test
    fun aBracketInADescriptionDoesNotPartTheTwo() {
        val two = Two("Before.\n\n![a](dog.jpg)\n\nAfter.", listOf(shot("dog.jpg", alt = "a")))
        for (typed in listOf("a]", "a]b", "a]b [c]", "a]b [c] d")) {
            two.write(0, typed)
            two.settle()
            assertEquals("Before.\n\n![$typed](dog.jpg)\n\nAfter.", two.text)
            assertEquals(typed, two.cards[0].alt)
        }
        // ...and from the text's side.
        two.text = "![x [y] z](dog.jpg)"
        two.settle()
        assertEquals("x [y] z", two.cards[0].alt)
    }

    /**
     * Typed at random into either place, a letter at a time: after each
     * letter the two say the same, what was just typed stands as typed,
     * and they stop moving at once.
     */
    @Test
    fun typedAtRandomIntoEitherPlaceTheTwoStayOne() {
        val dice = Dice(20261007uL)
        val letters = "ab č,. ]".toList()
        repeat(300) {
            val two = Two("One.\n\n![](dog.jpg)\n\nTwo.\n\n!![](clip.mp4)\n", listOf(shot("dog.jpg"), shot("clip.mp4", kind = Shot.Kind.Video)))
            for (step in 0 until 40) {
                val which = dice.next(2)
                val name = two.cards[which].name
                two.rounds = 0
                if (dice.next(2) == 0) {
                    // On the card: a letter at its end, or its last letter taken off.
                    val was = two.cards[which].alt
                    val typed = if (dice.next(5) == 0 && was.isNotEmpty()) was.dropLast(1) else was + letters[dice.next(letters.size)]
                    two.write(which, typed)
                    two.settle()
                    assertEquals(typed, two.cards[which].alt)
                } else {
                    // In the text: a letter at the end of the picture's description there.
                    val was = Kept.described(name, two.text) ?: continue
                    val grown = was + letters[dice.next(letters.size)]
                    two.text = two.text.replace("[$was]($name)", "[$grown]($name)")
                    val typed = two.text
                    two.settle()
                    assertEquals(typed, two.text)
                }
                assertTrue(two.rounds <= 1)
                for (card in two.cards) {
                    val said = Kept.described(card.name, two.text)
                    assertTrue(said != null)
                    assertEquals(Kept.oneLine(card.alt), Kept.oneLine(said ?: ""))
                }
            }
        }
    }

    /**
     * Several pictures in one text, their names alike and their marks
     * one after another: each card is its own picture's and no other's.
     */
    @Test
    fun severalPicturesEachKeepToTheirOwnMark() {
        val two = Two(
            "![one](photo-1.jpg)\n\n![eleven](photo-11.jpg)\n\n![other](a-photo-1.jpg)\n\n!![film](clip.mp4)\n",
            listOf(shot("photo-1.jpg"), shot("photo-11.jpg"), shot("a-photo-1.jpg"), shot("clip.mp4", kind = Shot.Kind.Video)),
        )
        two.settle()
        assertEquals(listOf("one", "eleven", "other", "film"), two.cards.alts)

        // The second card written on: only the second mark moves.
        two.write(1, "eleven, changed")
        two.settle()
        assertEquals("![one](photo-1.jpg)\n\n![eleven, changed](photo-11.jpg)\n\n![other](a-photo-1.jpg)\n\n!![film](clip.mp4)\n", two.text)
        assertEquals(listOf("one", "eleven, changed", "other", "film"), two.cards.alts)

        // The third mark written in: only the third card moves.
        two.text = two.text.replace("![other]", "![other one]")
        two.settle()
        assertEquals(listOf("one", "eleven, changed", "other one", "film"), two.cards.alts)

        // Two cards given the same words stay two descriptions.
        two.write(0, "same")
        two.write(2, "same")
        two.write(0, "same, first")
        two.settle()
        assertEquals("![same, first](photo-1.jpg)\n\n![eleven, changed](photo-11.jpg)\n\n![same](a-photo-1.jpg)\n\n!![film](clip.mp4)\n", two.text)
    }

    /**
     * Two marks on one line -- which the engine refuses, but the text
     * may hold while it is being written: each is still read as itself.
     */
    @Test
    fun twoMarksOnOneLineAreReadEachAsItself() {
        val text = "![x](a.jpg) ![y](b.jpg)\n\n![p](c.jpg)![q](d.jpg)"
        assertEquals("x", Kept.described("a.jpg", text))
        assertEquals("y", Kept.described("b.jpg", text))
        assertEquals("p", Kept.described("c.jpg", text))
        assertEquals("q", Kept.described("d.jpg", text))
        assertEquals("![x](a.jpg) ![why](b.jpg)\n\n![p](c.jpg)![q](d.jpg)", Kept.typed(text, "b.jpg", "why"))
    }

    /** The same at random, with three pictures whose marks stand in a row. */
    @Test
    fun typedAtRandomWithSeveralPicturesEachStaysItsOwn() {
        val dice = Dice(7uL)
        val letters = "ab č,.]".toList()
        repeat(200) {
            val two = Two("![](photo-1.jpg)\n\n![](photo-11.jpg)\n\n![](photo-2.jpg)\n", listOf(shot("photo-1.jpg"), shot("photo-11.jpg"), shot("photo-2.jpg")))
            val mine = mutableListOf("", "", "")
            for (step in 0 until 45) {
                val which = dice.next(3)
                val name = two.cards[which].name
                two.rounds = 0
                if (dice.next(2) == 0) {
                    val typed = two.cards[which].alt + letters[dice.next(letters.size)]
                    mine[which] = typed
                    two.write(which, typed)
                } else {
                    val was = Kept.described(name, two.text) ?: continue
                    val grown = was + letters[dice.next(letters.size)]
                    two.text = two.text.replace("[$was]($name)", "[$grown]($name)")
                    mine[which] = grown
                }
                two.settle()
                assertTrue(two.rounds <= 1)
                // Every picture says what was last written of IT, in both places.
                two.cards.forEachIndexed { index, card ->
                    assertEquals(Kept.oneLine(mine[index]), Kept.oneLine(card.alt))
                    assertEquals(Kept.oneLine(mine[index]), Kept.oneLine(Kept.described(card.name, two.text) ?: "?"))
                }
            }
        }
    }

    /**
     * The mark taken out of the text: the card keeps its words, and
     * gives them back when the picture is put in again.
     */
    @Test
    fun aCardKeepsItsWordsWhileItsPictureIsOutOfTheText() {
        val two = Two("Nothing.\n", listOf(shot("dog.jpg", alt = "a dog")))
        two.settle()
        assertEquals("a dog", two.cards[0].alt)
        two.write(0, "a dog, asleep")
        assertEquals("Nothing.\n", two.text)
        two.text = Kept.placed(two.cards[0].mark, two.text, null).text
        two.settle()
        assertEquals("Nothing.\n\n![a dog, asleep](dog.jpg)\n", two.text)
        assertEquals("a dog, asleep", two.cards[0].alt)
    }

    @Test
    fun aVideosTwoMarksFollowItsCardTheSameWay() {
        assertEquals("!![a clip](clip.mp4)\n", Kept.typed("!![](clip.mp4)\n", "clip.mp4", "a clip"))
        assertEquals("a clip", Kept.heard(listOf(shot("clip.mp4", kind = Shot.Kind.Video)), "!![a clip](clip.mp4)")[0].alt)
    }

    /**
     * A picture standing in the text twice: the card is the first mark's;
     * the second follows while it says the same, and keeps its own words
     * once it has some.
     */
    @Test
    fun aPictureNamedTwiceFollowsWhileItSaysTheSame() {
        assertEquals(
            "![b](cat.jpg)\n\n![b](cat.jpg)\n\n![mine](cat.jpg)\n",
            Kept.typed("![a](cat.jpg)\n\n![a](cat.jpg)\n\n![mine](cat.jpg)\n", "cat.jpg", "b"),
        )
        assertEquals("first", Kept.described("cat.jpg", "![first](cat.jpg)\n\n![second](cat.jpg)"))
    }

    @Test
    fun onlyTheCardsThatChangedTouchTheText() {
        val cards = listOf(shot("one.jpg", alt = "first"), shot("two.jpg", alt = "the second"))
        val text = "![first](one.jpg)\n\n![second](two.jpg)\n"
        assertEquals("![first](one.jpg)\n\n![the second](two.jpg)\n", Kept.retitled(text, cards, listOf("first", "second")))
        // A shot added or taken away between the two: nothing to compare.
        assertEquals(text, Kept.retitled(text, cards, listOf("first")))
        // A card of a picture the text does not name changes nothing in it.
        assertEquals(text, Kept.typed(text, "three.jpg", "x"))
    }

    /** A line break inside a mark is a picture the engine refuses. */
    @Test
    fun aDescriptionIsOneLineWhateverItsFieldHeld() {
        assertEquals("a cat on a wall", Kept.oneLine("  a cat\non  a\twall \n"))
        assertEquals("![a cat on a wall](cat.jpg)", shot("cat.jpg", alt = "a cat\non a wall").mark)
        assertEquals("![a cat on a wall](cat.jpg)", Kept.typed("![](cat.jpg)", "cat.jpg", "a cat\non a wall"))
    }

    @Test
    fun whatTheTextSaysOfAPictureIsRead() {
        val text = "![a cat on a wall](cat.jpg)\n\n!![the clip](clip.mp4)\n\n![](bare.jpg)\n"
        assertEquals("a cat on a wall", Kept.described("cat.jpg", text))
        assertEquals("the clip", Kept.described("clip.mp4", text))
        assertEquals("", Kept.described("bare.jpg", text))
        assertNull(Kept.described("absent.jpg", text))
    }

    // a mark put where the caret is

    private val cat = "![a cat](cat.jpg)"

    /** The text never touched: the mark goes to its end, as before. */
    @Test
    fun withNoCaretTheMarkGoesToTheEnd() {
        assertEquals("![a cat](cat.jpg)\n", Kept.placed(cat, "", null).text)
        assertEquals("Words.\n\n![a cat](cat.jpg)\n", Kept.placed(cat, "Words.", null).text)
        assertEquals("Words.\n\n![a cat](cat.jpg)\n", Kept.placed(cat, "Words.\n", null).text)
        assertEquals("Words.\n\n![a cat](cat.jpg)\n", Kept.placed(cat, "Words.\n\n", null).text)
    }

    /**
     * Between two paragraphs, with the caret on the blank line between
     * them: a paragraph of its own, and no blank line doubled.
     */
    @Test
    fun betweenTwoParagraphsItIsAParagraphOfItsOwn() {
        val text = "One.\n\nTwo."
        assertEquals("One.\n\n![a cat](cat.jpg)\n\nTwo.", Kept.placed(cat, text, 6).text)
        assertEquals("One.\n\n![a cat](cat.jpg)\n\nTwo.", Kept.placed(cat, text, 5).text)
        assertEquals("One.\n\n![a cat](cat.jpg)\n\nTwo.", Kept.placed(cat, text, 4).text)
    }

    /** In the middle of a line the line is broken there: the blog refuses a picture that shares a line with prose. */
    @Test
    fun inTheMiddleOfALineItBreaksTheLine() {
        assertEquals("One \n\n![a cat](cat.jpg)\n\ntwo.", Kept.placed(cat, "One two.", 4).text)
    }

    @Test
    fun atTheVeryStartNothingStandsBeforeIt() {
        assertEquals("![a cat](cat.jpg)\n\nOne.", Kept.placed(cat, "One.", 0).text)
    }

    /** The caret comes back after the mark and its gap: where one goes on writing. */
    @Test
    fun theCaretGoesOnAfterTheMark() {
        val put = Kept.placed(cat, "One.\n\nTwo.", 6)
        assertEquals("Two.", put.text.substring(put.caret))
        val end = Kept.placed(cat, "One.", null)
        assertEquals(end.text.length, end.caret)
    }

    /** Positions are counted the way the text field counts: an emoji is two. */
    @Test
    fun positionsAreCountedAsTheTextFieldCountsThem() {
        val text = "Běh 🏃 hotov.\n\nDál."
        val at = "Běh 🏃 hotov.\n\n".length
        assertEquals("Běh 🏃 hotov.\n\n![a cat](cat.jpg)\n\nDál.", Kept.placed(cat, text, at).text)
        // A caret past the end, or before the start, is the end and the start.
        assertEquals("One.\n\n![a cat](cat.jpg)\n", Kept.placed(cat, "One.", 99).text)
        assertEquals("![a cat](cat.jpg)\n\nOne.", Kept.placed(cat, "One.", -3).text)
    }

    // what an edit may do to a post's pictures

    /**
     * The engine refuses an edit from the app that leaves a post with
     * fewer pictures than it had; the app says so before the save.
     */
    @Test
    fun takingAPictureOutIsFewer() {
        val media = listOf("01.jpg", "02.jpg")
        assertFalse(Kept.fewer(media, emptyList(), "![a](01.jpg) ![b](02.jpg)"))
        assertTrue(Kept.fewer(media, emptyList(), "![a](01.jpg)"))
        assertTrue(Kept.fewer(media, emptyList(), "no pictures at all"))
        assertEquals(listOf("02.jpg"), Kept.dropped(media, "![a](01.jpg)"))
    }

    @Test
    fun oneOutAndAnotherInIsAsManyAsBefore() {
        assertFalse(Kept.fewer(listOf("01.jpg", "02.jpg"), listOf(shot("photo-3.jpg")), "![a](01.jpg) ![c](photo-3.jpg)"))
    }

    /** A picture chosen but not put into the text is not in the post. */
    @Test
    fun aNewPictureCountsOnlyOnceItIsNamed() {
        assertTrue(Kept.fewer(listOf("01.jpg", "02.jpg"), listOf(shot("photo-3.jpg")), "![a](01.jpg)"))
    }

    /**
     * The engine counts pictures and videos apart: a picture does not
     * stand in for a video taken out.
     */
    @Test
    fun aPictureDoesNotStandInForAVideo() {
        val picture = shot("photo-2.jpg")
        val video = shot("clip.mp4", kind = Shot.Kind.Video)
        assertTrue(Kept.fewer(listOf("01.mov"), listOf(picture), "![a](photo-2.jpg)"))
        assertFalse(Kept.fewer(listOf("01.mov"), listOf(video), "!![a](clip.mp4)"))
        assertTrue(Kept.isVideo("01.MOV") && Kept.isVideo("a.mp4") && !Kept.isVideo("a.jpg") && !Kept.isVideo("mov"))
    }

    /**
     * A shot picked and never put into the text would arrive, stand in no
     * post and lie in the blog's incoming/ for good: it does not go.
     */
    @Test
    fun onlyTheShotsTheTextNamesAreSent() {
        val a = shot("photo-1.jpg")
        val b = shot("photo-2.jpg")
        val clip = shot("video-3.mp4", kind = Shot.Kind.Video)
        assertEquals(listOf("photo-2.jpg", "video-3.mp4"), Kept.sent(listOf(a, b, clip), "![x](photo-2.jpg)\n\n!![y](video-3.mp4)").map { it.name })
        assertTrue(Kept.sent(listOf(a, b), "no marks").isEmpty())
        // The name has to be the whole of what the mark names.
        assertTrue(Kept.sent(listOf(a), "![x](other-photo-1.jpg)").isEmpty())
    }

    @Test
    fun aPostWithoutPicturesHasNothingToLose() {
        assertFalse(Kept.fewer(emptyList(), emptyList(), "text"))
        assertTrue(Kept.dropped(emptyList(), "text").isEmpty())
    }

    // the weight of a delivery

    @Test
    fun base64IsAThirdLargerAndCountsItsLineBreaks() {
        assertEquals(1, encodedSize(0))
        assertEquals(5, encodedSize(3))
        assertEquals(78, encodedSize(57))
    }

    @Test
    fun aDeliveryOfTextAloneStillWeighsItsEnvelope() = assertEquals(352, Delivery.wireBytes(emptyList(), 0))

    /**
     * The server measures the encoded stream: of a limit of one megabyte
     * about three quarters are room for files.
     */
    @Test
    fun theLimitIsMeasuredOnTheEncodedStream() {
        assertFalse(Delivery.over(listOf(shot("a.jpg", bytes = 700 * 1024)), 100, 1))
        assertTrue(Delivery.over(listOf(shot("a.jpg", bytes = 800 * 1024)), 100, 1))
        assertTrue(Delivery.over(listOf(shot("a.jpg", bytes = 400 * 1024), shot("b.jpg", bytes = 400 * 1024)), 100, 1))
    }

    @Test
    fun aServerThatNamesNoLimitRefusesNothing() {
        assertFalse(Delivery.over(listOf(shot("a.jpg", bytes = 5 * 1_048_576)), 100, 0))
    }

    @Test
    fun sizesAreSaidInKilobytesThenMegabytes() {
        assertEquals("0 kB", Delivery.size(0))
        assertEquals("1 kB", Delivery.size(1))
        assertEquals("2 kB", Delivery.size(1536))
        assertEquals("1023 kB", Delivery.size(1023 * 1024))
        // The decimal mark is the reader's own; the unit is not.
        assertTrue(Delivery.size(5 * 1_048_576).startsWith("5"))
        assertTrue(Delivery.size(5 * 1_048_576).endsWith(" MB"))
    }

    @Test
    fun whatIsOnTheWayEndsWithItsSize() {
        assertTrue(Delivery.describe(emptyList(), 10).endsWith(", 1 kB"))
        val said = Delivery.describe(
            listOf(shot("a.jpg", bytes = 1024), shot("b.jpg", bytes = 1024), shot("c.mp4", bytes = 1024, kind = Shot.Kind.Video)), 0,
        )
        assertTrue(said.startsWith("2 "))
        assertTrue(said.contains(", 1 "))
        assertTrue(said.endsWith(", 3 kB"))
    }

    /**
     * A shot's own mark is found whatever its description holds: with a
     * bracket in it, "Remove" left the mark in the text and the next
     * picture took the removed one's words.
     */
    @Test
    fun aShotsMarkIsFoundWithBracketsInItsDescription() {
        val shot = Shot("photo-1.jpg", ByteArray(0), 1, 1, "Pes [nas] v trave")
        val text = "Before.\n\n${shot.mark}\n\n![other](photo-2.jpg)\n"
        val found = shot.markPattern.findAll(text).toList()
        assertEquals(1, found.size)
        assertEquals(shot.mark, found.first().value)
        // Two marks on one line stay two: the first does not run into the second.
        assertEquals("![b](photo-1.jpg)", shot.markPattern.find("![a](photo-2.jpg) ![b](photo-1.jpg)")?.value)
    }

    /**
     * A title that only begins and ends with a bracket or a quote of its
     * own goes as it was typed; one wrapped whole is unwrapped, as before.
     */
    @Test
    fun aTitleWithBracketsOrQuotesOfItsOwnGoesAsTyped() {
        assertEquals("---\ntitle: [foto] Sobota [Brno]\n---\n\n", Markdown.frontMatter("[foto] Sobota [Brno]", ""))
        assertEquals("---\ntitle: \"Ano\" a \"ne\"\n---\n\n", Markdown.frontMatter("\"Ano\" a \"ne\"", ""))
        assertEquals("---\ntitle: Sobota\n---\n\n", Markdown.frontMatter("[Sobota]", ""))
        assertEquals("---\ntitle: Sobota\n---\n\n", Markdown.frontMatter("'Sobota'", ""))
    }

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
