# blog.sh for Android

The whole of `./blog.sh` on a phone: the wizard's menu, entry for entry
and key for key. A new post with photographs, a post's text in
every language the site publishes, its properties and the actions on it,
the scheduled-post queue, the archive with its filters and search, the
trash, and the site's rebuild.

It is the [iOS app](https://github.com/DanielSnor/blog.sh-ios) carried
over, screen for screen and word for word: the same menu, the same
questions, the same commands to the engine. What differs is said below,
under *Where it is not the iOS app*.

It is a thin client. Nothing of the engine is reimplemented: the app
opens an SSH connection to the machine the blog lives on and runs
`./blog.sh <command> --json` there, the same commands a person types,
and draws the answers. The archive, the build and the deploy stay where
they are. That is why the engine grew a `--json` form for every screen
and every key (1.10): one object per answer, every key always present, a
refusal as an object -- a contract a program can rely on.

Requires [blog.sh](https://github.com/DanielSnor/blog.sh) 1.10 on the
server and Android 10 or later on the phone.

## Setting it up

There are two ways in, both behind **Add a blog** in the list of blogs.

**With a code.** On the server, `./blog.sh pair` shows a code -- a
picture, and under it the same thing as a line of text. The app reads
the picture with the camera, or is given the line (pasted, or as a
`blogsh://` link opened from another app), asks "Connect to …?" and is
connected: nobody types an address, an account or a key. Under the code
the app is told where the blog is, which machine is to answer there
(its keys' fingerprints -- another machine is turned away before
anything is sent to it) and a key good once, for ten minutes, for one
thing: handing in the public half of a key the app makes itself. The
key the app lives on never leaves the phone, and the one that was on a
screen opens nothing once it has been used. The camera is asked for
only to read the code; refused, the line of text does the same.

**By hand**, for a blog that lives in a container or a server whose
`authorized_keys` somebody keeps themselves:

1. Build and install the app (below), tap the name on the first screen, add
   a blog there by hand and enter the server's host, the account and the port.
2. **Make the app's key.** It is made on the phone and never leaves it:
   an ed25519 key, kept in the app's own files, encrypted with a key that
   lives in the Android Keystore, and left out of every backup.
3. Say where the blog is on the server -- its directory -- and the app
   writes the line for the account's `~/.ssh/authorized_keys`. The line
   carries a forced command in front of the key, `scripts/remote.sh` in
   the blog's directory, which is all the key may ever run: one engine
   command at a time, or a delivery of files into `incoming/`. It never
   gets a shell. A blog inside a container is reached *through* the
   command that enters it (`sudo docker exec -i blog`), and a Ruby that is
   not on the server's own `PATH` through `env PATH=…`; the app puts
   either in front of the script and hands it the word SSH was asked for.
   See *Driving the engine from a program* in the engine's
   `docs/operations.md` for what the command allows and refuses.
4. **Test the connection.** The identity block the terminal shows above
   every screen appears; the server's key is remembered on first use and
   has to be the same every time after.

## More than one blog

The app holds as many blogs as you write. The name on the first screen is
the switch: tap it for the list, pick another, or add one. Each blog has
its own server, its own directory and its own key -- a key's forced
command names one blog's `scripts/remote.sh`, so one key is one blog and
nothing more, even when two blogs share a server and an account. A new
blog starts with the server of the one that was open -- a second blog
most often lives beside the first -- and asks for its directory and a key
of its own; removing a blog removes its key from the phone, and the line
on the server is then yours to delete.
Switching changes everything the screen wears: the name, the favicon, the
colours, what waits in the queue.

## Settings

What is the app's own and no blog's is under the gear; a blog's settings
-- where it is, its key -- are behind the key at the end of its row in
the list of blogs. The app's own are three: the colours (under *How it
looks*), and these two. The size of the type: the app follows the size
the system has, and under **Text size** it can be set one to four steps
above that -- in longer strides on a tablet, which is read from further
away. The gaps between the parts of a screen grow with the type. Rows
that hold a name beside a value put the value under the name at the
largest sizes, and the preview of a post is enlarged with the rest. And
the language: the app is written in English, Czech and German
and speaks the system's unless told another under **Language** -- taken
up when the app is next started.

At the foot of the settings stand the engine's mark, when this copy of
the app was built and from which commit (with a plus for a tree that had
changes not committed): what is read out when one copy has to be told
from another.

## What is where

| the wizard says | the app |
|---|---|
| New post | a title, the text with the marks `/write/` offers over it, a key that opens it over the whole screen for writing and a preview of the post as the blog would show it, the tags, pictures and video with their descriptions -- sent as one delivery, the post arrives as a draft |
| A post -- edit the text, its properties and the actions on it | the last fifty posts, counted beside the screen's name -- under them, that they are the last fifty and that the archive has the rest -- then the crossroads: what the post is, in one table, and how it begins, to know it is the one meant, and under that the text, a language, the properties with every key of that screen; the crossroads asks what the post is now every time it is come to, so a post renamed or retitled from the screens above it is the same post under its new name |
| The scheduled-post queue | the rows in publish order; a row opens its post, as in the archive; up, down, carry to a position -- by its number or by dragging the row there -- publish now, reschedule, cancel the schedule: the last two under the names the properties screen has for them, with a line under the rows that says the keys are behind a hold |
| The archive | newest first, with the search and the type, state and tag filters, which stay put over the rows however far down the archive has been read, and a post opening to its crossroads |
| Trash | what is in it, a row restoring its post; under the rows the clearing out the terminal has two commands for -- empty the trash, remove the older versions -- each said in numbers and asked before it is done |
| The site | rebuild and deploy, with the two switches the command has, each with what it is for said under it; and under them the two looks that only read -- `check` through the archive, `doctor` through the installation: what either finds, the problems first, each with the blog's own advice, a finding about a post a way to that post. An engine too old to offer `doctor` to a program is said so, not shown as an error |

A picture or a video goes with a post only when the text names it: one
picked and never put into the text stays on the device, and its card says
so. On a site of more than one language a post not written in all of them
is asked about before it is published or scheduled, the way the terminal
wants `--allow-partial` typed.

The app says what it did and asks nothing back. What was done -- a post
published, a plan cancelled, a row of the queue moved and when it goes
out now -- is said in a line under whichever screen is open, and goes by
itself. A change the site does not show yet (a pin, a rename, a property,
a post deleted or restored, the queue in another order) is owed a build,
and the app builds the site itself: it waits a moment for the next such
change, as the terminal's queue waits for the way out, then runs, and
the same line says so while it does. Nobody is asked whether the site
should be rebuilt; only a build that failed says something that wants an
answer -- where to try again. While a build runs, the keys that need the
engine's lock wait. A screen that cannot be touched says what it is
doing once the wait is long enough to wonder about, and a form's page
goes to its answer, so a result is never under the edge of the screen.

A post's address can be handed to somebody from wherever the post is on
the screen: a key in the bar of its crossroads, its properties and its
preview, and **Share the link** in the menu of a row of the archive. It
opens the system's own sheet with the address and the title. The address
is the engine's to say (`props --json`, `url`): a published post's own,
and for a draft the hidden page the build keeps for it.

What is written is kept on the phone at every letter: a new post until it
is sent, changes to a post's text or to one of its languages until they
are saved. Nothing is asked on the way out of a form -- leaving it loses
nothing, and neither does Android closing an app that is out of sight.
The next time the form opens the writing is back in it, with a line that
says so and when it was written, and one key that puts it away. Pictures
chosen for it are not kept, and the line says that too where the text
names one; a post that has changed on the blog meanwhile is said as well.
The first screen lists what was begun and not finished -- an unsent new
post, unsaved changes, an unsaved translation -- each a way straight back
to where it waits.

One connection to the server is opened with the first call and kept for
the ones after it: a server counts the connections an address opens, and
turns the ones over its count away. Every command is a channel of its own
on it. It is closed after five minutes unused, when another blog is
opened, when the key or the server's key is changed, and when the app
leaves the screen.

## Looking before sending

Two looks, before anything leaves the device. The preview -- under the text
of a new post, of a post being edited, of a translation -- is the post as
the blog would show it, near enough: the text rendered the way the
`/write/` page renders it -- the same markdown, the same boxes where a
picture is missing or not on a line of its own -- in the blog's own
stylesheets. Two things it draws the blog's way rather than that page's:
the line `//--more--//`, which cuts a post in two, is a hairline, not
words; and pictures in a row are the gallery they are on the site -- two
side by side, an odd last one across both. And a picture chosen for a post is a key: behind it the
shots stand one to a page and large, each with the line that describes it
under it, to be written while looking at what it describes.

## What travels

What `/write/` sends, made on the device. A photograph is shrunk to
2560 px on its long edge and written as JPEG whatever it was, HEIC
included; a video is exported as H.264 in an MP4 at 720p, which every
browser plays. Neither carries where it was taken. The whole delivery has
to stay under the server's limit (`version --json` says it, `max_mb`),
measured on the encoded stream; the form says what is on the way and
refuses to send what the server would refuse.

A picture's description is one thing with two places to write it -- its
card and the mark the text has for it: written in either, the other
follows at every letter, so it can be begun in the text, added to on the
card and finished in the text again. A card whose picture the text does
not name yet keeps its words for when the mark is put in. The text is
sent as it stands.

The text of a post is as tall as it asks, up to what stays in sight:
with a keyboard up it has everything down to the keyboard -- the tags
under it are not needed while writing -- and past that it moves inside
its own frame, so the caret is never behind the keyboard; without one it
has half the page. Every screen says what it is in its bar, beside the
way back, and leaves the page to what it holds: a screen of the app's
its name and how many it holds, a screen about one post the post's
title -- on one line, cut at its end where it does not fit between the
way back and the key that shares the post, and whole at the head of the
page, in one table with what the engine calls the post. Over the whole
screen the text has the whole width of the screen, however wide. A
picture's mark goes into the text where the caret is -- a paragraph of
its own, a blank line on each side and none doubled, the rule of
`/write/` -- and at the end only when the text was never touched. The
two keys on a picture's card are as tall as a finger needs.

## How it looks

A ground and the ink on it, by day and by night, and one accent: the
open blog's own. The engine says them with its identity (`version
--json`: `site.accent` and `site.palette`, the ground, the text, the text
beside it and the rules, for light and for dark), so each blog looks in
the app as its pages do -- with its favicon beside its name. A blog whose
engine says no palette yet, and the app before any blog, wear the app's
own: the blue the engine ships with. Under **Colours** in Settings the
app wears the open blog's colours, its own default scheme whatever blog
is open -- for eyes a blog's palette does not serve -- or ones of your
own: five colours by day and five by night, each a swatch that opens a
picker, starting from the ones worn until then. They hold on the device,
for every blog. Colours chosen so that the writing cannot be told from
its ground lock nobody out: the part of the settings they are chosen in
then keeps to the default scheme until they can be read.

The first screen is the blog at one glance -- what waits, in the order
it wants a hand: the drafts in progress, what was begun on this phone and
not finished (on the colour of what cannot be taken back, thinned to a
wash), and last the queue, which goes out by itself -- over the six
entries of the menu, the search, and a key that opens the blog in the
browser; a list is its filters as pills and
its rows; every other screen is plates on its ground -- rows that belong
together on one card, a hairline between them -- with one filled button
for the one thing the screen is for, and what cannot be taken back set
apart in a colour of its own.

What is in the accent goes by the rules of the blog's own pages: the
accent is an action or something the engine says. So the marks of keys
and tiles, the names of sections, the dates in a list, a pin, the filter
that is on and the size of type that is chosen (those two filled with
it) are in the accent; a title, a text and a plain number -- how many a
list holds, how many wait on a card -- are in ink or grey.

Three voices of type: a terminal's face in lower case for what a screen
is (German keeps its capitals: its nouns are read by them) -- IBM Plex Mono -- a sans for what it holds -- Work Sans -- and a
typewriter face for what the engine says -- Courier Prime. All three ride
in the app from `app/src/main/res/font/`, their licences (SIL Open Font
License 1.1) in `licenses/`.

Under the search the first screen says the blog in numbers: posts and the
year of the first, words and the hours it takes to read them, tags, media,
and what the trash and the versions hold -- those two are keys to the
trash. A line whose number is nought is not said. The archive is counted (`stats --json`) after the screen itself is
up, and the numbers are kept with the blog, so the next launch shows them
at once.

## Where it is not the iOS app

- **A row's actions are behind a long press.** Where the iOS app also
  offers them on a swipe to the side, here they are lines of the row's
  menu, all of them. In the queue a long press lifts the row: carried, it
  is dropped at its new position; let go where it was, its menu opens.
  The line under the queue says so in words of its own -- the iOS one
  speaks of a swipe.
- **The language is the app's own to keep.** iOS keeps an app's language
  where the system keeps it, so a choice made in the system's settings
  shows in the app and the other way round. Here it is kept with the
  app's other settings, on every Android the app runs on; the system's
  own per-app language, where there is one, is another setting.
- **The steps of the type** multiply the system's size (by 1.12, 1.24,
  1.35 and 1.65; on a tablet by 1.24, 1.65, 1.94 and 2.35), where iOS
  moves up the system's own scale.
- **The colour picker is the app's own.** Android has none to offer: a
  strip for the hue, one for how far from grey, one for how far from
  black, and the number as a palette writes it. It covers the foot of
  the screen, so the app is seen changing over it, and keeps to the
  default scheme whatever is worn.
- **Sharing** opens Android's sheet with the address as text and the
  title as its subject; there is no preview of the link in it.
- **One icon.** An Android app cannot choose among icons the way an iOS
  one can, so the icon is one, whatever the open blog's accent is.
- **No pointer, no menu to hide.** On a Mac and an iPad a key answers to
  the pointer over it, and the menu's column has a key that hides it;
  both wait for the tablet's layout here.
- **A phone.** The two-column layout an iPad has is not here yet; a tablet
  shows the phone's screens.
- **The typewriter face** is Courier Prime, where iOS has the system's
  Courier New, and the marks are Google's Material Symbols, where iOS has
  Apple's SF Symbols -- neither of Apple's may travel.

## Kept in step with the iOS app

Two tools carry over what the iOS app already has, so the two cannot
drift apart by hand:

- `tools/import-strings.rb <path to blog.sh-ios>` writes every string of
  the iOS catalog, in every language it has, as Android resources. A
  resource is named after its English; `tools/name-of <words>` finds one.
  What only this app needs to say is in `res/values/android*.xml`.
- `tools/import-symbols.rb` reads `tools/symbols.tsv` -- which SF Symbol
  the iOS app uses, which Material Symbol stands for it here -- fetches
  the ones that are missing and writes `ui/Symbols.kt`, where each mark is
  called by its iOS name.

The fixtures of the tests (`app/src/test/resources/fixtures/`) are the
iOS app's own, copied: both apps answer to the same cases.

## Building

Android Studio, or from a shell:

```
tools/gw :app:assembleDebug        # app-debug.apk
tools/gw :app:assembleShrunk       # the release as it ships, shrunk, signed with the debug key
```

`tools/gw` is the Gradle wrapper with Android Studio's own Java when no
other is named. A working copy in a folder something syncs (iCloud,
Dropbox) should not hold a build: a line `build.root=/some/dir` in
`local.properties` puts the output and Gradle's project cache there
instead.

The dependencies: sshj with BouncyCastle for SSH, Media3 for making a
video into what travels, Reorderable for carrying a row of the queue,
CameraX for the camera and ZXing for reading a pairing code out of its
picture -- on the device, with nothing asked of anybody's services.
Android carries a cut-down BouncyCastle of its own under the same name;
the app puts the whole one in its place when it starts, and
`app/proguard-rules.pro` keeps the shrinker from taking out the
algorithms it loads by name.

## Tests

What the app works out by itself is tested: the marks over the text,
against the `/write/` page's own answers on some fifteen hundred cases;
the preview, against what that page's own JavaScript renders;
the line for `authorized_keys`; the engine's answers as they are read,
what `check` and `doctor` found among them;
the blogs as they are written down; a post's file, its pictures' names,
a description in its two places -- letter by letter, at random, with
several pictures -- a mark put where the caret is, and the weight of a
delivery against the server's limit; a row as the post it stands for is
now, and a post's link; the colours a blog says and the ones the app
wears, the ones chosen on the device and the line under which they
cannot be read; a pairing code as it is read -- from its link, and out
of a camera's picture -- and what the engine answers to it; the size of the type and the language as they are kept;
the site built by the app itself; the connection kept to the server;
writing kept until it is sent or saved, and what the first screen lists
of it; the stamp of a build.
The screens are not -- they are looked at.

```
tools/gw :app:testDebugUnitTest
```

The tests run on the desk, without a phone and without a connection to
anybody's blog. What the engine answers is its own to test, and it does,
in its own suite.

## License

MIT, see [LICENSE](LICENSE). The typefaces in `app/src/main/res/font/`
are their authors', under the SIL Open Font License 1.1 (`licenses/`).
The marks in `app/src/main/res/drawable/ic_*.xml` are Google's Material
Symbols, under the Apache License 2.0.
