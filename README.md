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

1. Build and install the app (below), open **Settings** under the gear and
   enter the server's host, the account and the port.
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
accent, what waits in the queue.

## What is where

| the wizard says | the app |
|---|---|
| New post | a title, the text with the marks `/write/` offers over it, a key that opens it over the whole screen for writing and a preview of the post as the blog would show it, the tags, pictures and video with their descriptions -- sent as one delivery, the post arrives as a draft |
| A post -- edit the text, its properties and the actions on it | the last fifty posts -- under them, that they are the last fifty and that the archive has the rest -- then the crossroads: how the post begins, to know it is the one meant, and under that the text, a language, the properties with every key of that screen |
| The scheduled-post queue | the rows in publish order; up, down, carry to a position -- by its number or by dragging the row there -- publish now, another time, return to drafts |
| The archive | newest first, with the type, state and tag filters, the search, and a post opening to its crossroads |
| Trash | what is in it, a row restoring its post; under the rows the clearing out the terminal has two commands for -- empty the trash, remove the older versions -- each said in numbers and asked before it is done |
| The site | rebuild and deploy, with the two switches the command has |

A picture or a video goes with a post only when the text names it: one
picked and never put into the text stays on the device, and its card says
so. On a site of more than one language a post not written in all of them
is asked about before it is published or scheduled, the way the terminal
wants `--allow-partial` typed. What an action has to say and what it has
to ask next come as one message -- a post deleted says so, asks about the
rebuild, and its screens are left.

Where the terminal asks a question, the app asks it too; where it
rebuilds without asking, so does the app; where it asks whether to
rebuild now, the app asks.

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

## How it looks

Paper and ink by day, ink on black by night, and one accent: the blog's
own, which the engine says with its identity (`version --json`), the way
`/write/` wears it -- with the blog's favicon beside its name. The first screen is the blog at one glance -- what
waits in the queue, how many drafts are in progress -- over the six
entries of the menu; a list is a name, a count, its filters as pills and
its rows; every other screen is plates on paper -- rows that belong
together on one card, a hairline between them -- with one filled button
for the one thing the screen is for, and what cannot be taken back set
apart in a colour of its own.

Three voices of type: a terminal's face in lower case for what a screen
is -- IBM Plex Mono -- a sans for what it holds -- Work Sans -- and a
typewriter face for what the engine says -- Courier Prime. All three ride
in the app from `app/src/main/res/font/`, their licences (SIL Open Font
License 1.1) in `licenses/`.

Under the search the first screen says the blog in numbers: posts and the
year of the first, words and the hours it takes to read them, tags, media,
and what the trash and the versions hold -- those two are keys to the
trash. The archive is counted (`stats --json`) after the screen itself is
up, and the numbers are kept with the blog, so the next launch shows them
at once.

## Where it is not the iOS app

- **A row's actions are behind a long press.** Where the iOS app also
  offers them on a swipe to the side, here they are lines of the row's
  menu, all of them. In the queue a long press lifts the row: carried, it
  is dropped at its new position; let go where it was, its menu opens.
- **The back key asks.** A screen holding words that have not been sent
  asks before it is left, and a new post half written is kept on the
  phone until it is sent or emptied -- Android may close an app in the
  background, and the post would be gone with it.
- **One icon.** An Android app cannot choose among icons the way an iOS
  one can, so the icon keeps the look's own accent whatever the blog's is.
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
video into what travels, Reorderable for carrying a row of the queue.
Android carries a cut-down BouncyCastle of its own under the same name;
the app puts the whole one in its place when it starts, and
`app/proguard-rules.pro` keeps the shrinker from taking out the
algorithms it loads by name.

## Tests

What the app works out by itself is tested: the marks over the text,
against the `/write/` page's own answers on some fifteen hundred cases;
the preview, against what that page's own JavaScript renders;
the line for `authorized_keys`; the engine's answers as they are read;
the blogs as they are written down; a post's file, its pictures' names,
and the weight of a delivery against the server's limit.
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
