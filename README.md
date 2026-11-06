# B-72

A notebook, a bookmark shelf and a scrapbook in one Android app. Write a note, share a link into it, keep a film or a book or a song as a card, cut a subject out of a photo and stick it on a canvas.

The look follows Material 3 Expressive: variable type that changes width and weight with the screen, springy shape and motion, and a colour on every note that comes from the note itself. Everything is kept on the phone.

<!-- Add screenshots here: docs/screens/home.png, docs/screens/note.png, docs/screens/canvas.png -->

## What is in it

**Notes**
- Text, checklists, photos, voice notes and drawings in one note; edit in place, no separate edit mode.
- Formatting that goes past bold and italic: bullet and numbered lists that continue when you press Enter, quotes and pull quotes, block and first-line indents, alignment including justified, sub- and superscript, spaced caps, headings, and a centred break (`* * *`) for a scene change.
- Titles set themselves: each note's title fills the width of the page in a typeface personality picked from its id (Google Sans Flex or Roboto Flex, all through the fonts' own axes, nothing drawn on top of the letters). Or choose one by hand.
- Undo and redo for every change, including lists and formatting.
- Notes that find each other: related notes, links between notes, and old notes that come back to the top on a quiet day.

**Collections**
- Films, books, songs and albums become cards with a cover, a description and the details that matter (cast, publisher, pages, release date, track list, lyrics you loved).
- A shelf per kind, with a stack of cards for films and a vinyl for records.
- Places, trips (with a status for the day of travel), receipts and product pages.

**Screens**
- Your screenshots in one place, sorted with on-device text recognition. Keep or delete in a swipe; the text inside a screenshot is searchable but never dumped on the page.

**Artifacts**
- A photo with one clear subject becomes a die-cut sticker. A photo without one becomes a stamp: the whole picture, cut to a shape.
- Stickers get a name, a colour palette and, for food and products, the facts printed on the label.
- A home-screen widget shows one of them with nothing behind it.

**Canvases**
- A board per tag: drag cards around, stick artifacts and stamps on it, draw. Stamps are printers' ornaments (dingbats) in a badge.

**Everything else**
- An optional on-device assistant (Gemma 3n E2B through LiteRT-LM) that names things and answers questions about your notes. Nothing leaves the phone.
- Backup to your own Google Drive; app lock; share into B-72 from any app.
- Home-screen widgets: what matters now, four quick-add buttons, an artifact.

## Build it

You need JDK 17 and the Android SDK with platform 37. Gradle 9.6 is installed by the CI workflow; locally use the wrapper or your own Gradle.

```
gradle :app:assembleDebug
gradle :app:assembleRelease
```

The APKs land in `app/build/outputs/apk/`.

The fonts are not in the repository. The CI workflow downloads them into `app/src/main/res/font` before building:

| File | Font | Licence |
|---|---|---|
| `googlesansflex.ttf` | Google Sans Flex | SIL OFL 1.1 |
| `robotoflex.ttf` | Roboto Flex (optional; Google Sans Flex is used without it) | SIL OFL 1.1 |
| `instrumentserif_regular.ttf`, `instrumentserif_italic.ttf` | Instrument Serif | SIL OFL 1.1 |

To build on your own machine, download the same files (they are in [google/fonts](https://github.com/google/fonts/tree/main/ofl)) and save them under those names.

Two optional fonts, Playlist Script and Playlist Ornament, can be dropped into `app/src/main/assets/fonts/`. They are your own licensed copies and are ignored by git.

### Keys

Both are optional and are read from environment variables (or repository secrets in CI):

- `TMDB_KEY`: a free key from themoviedb.org, for film details.
- `SPOTIFY_CLIENT_ID`: for adding playlists and albums from Spotify.

Without them the rest of the app works as usual.

### Signing

Debug and release builds are signed with the same sideload key in `keystore/`, so a release installs over a debug build and keeps its data. If you publish somewhere that needs your own key, replace the `signingConfigs` block in `app/build.gradle.kts` and read [docs/RELEASING.md](docs/RELEASING.md).

## Releasing

Push a tag that starts with `v` and the release workflow builds the APK and attaches it to a GitHub release. The steps, with the commands, are in [docs/RELEASING.md](docs/RELEASING.md).

## Privacy

Notes, images and the assistant stay on the device. See [PRIVACY.md](PRIVACY.md) for the few places the app does reach the network.

## Licence

[MIT](LICENSE). Fonts and libraries keep their own licences: see [THIRD_PARTY.md](THIRD_PARTY.md).
