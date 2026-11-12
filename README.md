# B-72

**A notebook, a bookmark shelf and a scrapbook for Android — in one app, all on your phone.**

B-72 keeps the things you want to remember in the shape they arrived in. A thought is a note. A recipe is a card with its photo. A film, a book or a song is a card with its cover and details. A link is a bookmark that shows what it points to. A photo of an object becomes a sticker you can put on a canvas. Nothing needs an account, and nothing leaves your phone unless you ask it to.

It is a native Android app written with Jetpack Compose, designed around Material 3 Expressive: variable type that changes width and weight, springy shapes and motion, and colour that comes from what you are looking at.

<!-- Screenshots: add docs/screens/home.png, docs/screens/note.png, docs/screens/canvas.png and show them here. -->

## Install

1. Open the [Releases](../../releases) page and download the latest `B-72-v….apk`.
2. Open the file on your phone and allow installs from your browser or file manager when Android asks.
3. Requires Android 8.0 (API 26) or newer.

Updates install over the previous version and keep your data.

## A tour

### Notes
- One kind of note holds text, checklists, photos, voice recordings, drawings, quotes, links and cards. There is no separate edit mode: tap and type.
- Formatting goes beyond bold and italic: bullet and numbered lists that continue when you press Enter, quotes and pull quotes, block and first-line indents, left, centre, right and justified alignment, sub- and superscript, spaced capitals, two heading sizes and a centred break (`* * *`). The tools sit in a floating vertical toolbar beside the text.
- Undo and redo cover text, lists and formatting.
- Each note's title is set to fill the page, in a typeface personality chosen from the note itself (Google Sans Flex or Roboto Flex, using the fonts' own weight, width, stroke, height and contrast axes — nothing is drawn on top of the letters). You can also choose a style by hand.
- A photo with one clear subject can be cut out and "popped" in front of the title. A recipe with such a photo gets a full-bleed header with the title behind the dish.
- Several photos in a note are shown in a carousel.
- Web addresses you write in a note are also saved as bookmarks in the Library. A link already saved elsewhere can be added to a note from a list.
- Related notes appear under a note, found by meaning as well as by words. Old notes resurface on quiet days.
- Searching opens its own page with filters by kind of note and a short list of recent searches.

### Collections (the Library)
- **Films**, **books**, **songs and albums** become cards with a cover, a description and the details that matter: cast and runtime, author and publisher, track list, lyrics you loved. Each kind has its own shelf: a stack of cards for films, a vinyl for records.
- **Links** are kept as bookmarks that show the site's icon and name, the kind of page, title, author, date, reading time, price and description, with open, copy and share. The shelf can be filtered by site.
- **Places, trips and tickets**: trips have a status for the day of travel (trains, flights), receipts and product pages are read for their key details.

### Screens
Your screenshots in one place, sorted with on-device text recognition. Keep or delete with a swipe. The text inside a screenshot is searchable without being dumped on the page.

### Artifacts
- A photo with one clear subject becomes a die-cut sticker. A photo without one becomes a stamp: the whole picture cut to a shape.
- Each artifact gets a name, a colour palette and, for food and packaged products, the facts printed on the label.
- Camera or gallery, as you like. A copy can be saved to Pictures/B-72.
- A home-screen widget shows one artifact with nothing behind it.

### Canvases
- A board for each tag: drag cards around, stick artifacts, stamps and text on it, and draw. Lines, circles, boxes and triangles snap into clean shapes; anything else stays as your own drawing.
- Stamps are printers' ornaments and symbols, set as text in the colours of the board.
- Undo and redo (fifty steps) live in a floating bar beside the board.
- The board takes its colours from the photos on it, in light and dark mode.

### Motion
Cards, tiles, search, the Library and canvases open into the screen they belong to with Material container transforms: a tile grows into its note and shrinks back to the same place. Other tiles drift away while it does.

### Everything else
- An optional on-device assistant (Gemma 3n E2B through LiteRT-LM) that names things and answers questions about your notes. It runs entirely on the phone.
- Backup to your own Google Drive and import from Google Keep.
- An app lock that uses the phone's own screen lock.
- Share into B-72 from any app.
- Home-screen widgets: what matters now, four quick-add buttons, and an artifact.
- An expanded navigation rail with a Settings page.

## Privacy

B-72 has no account, no analytics and no server of its own. Notes, images and the assistant stay on the device. The few things that need the network (film and book lookups, lyrics, link previews, backup) are listed in [PRIVACY.md](PRIVACY.md).

## Build it

You need JDK 17 and the Android SDK with platform 37. The CI workflow installs Gradle 9.6; locally use your own Gradle.

```
gradle :app:assembleDebug
gradle :app:assembleRelease
```

The APKs land in `app/build/outputs/apk/`.

### Fonts

The fonts are not in the repository. The CI workflow downloads them into `app/src/main/res/font` before building:

| File | Font | Licence |
|---|---|---|
| `googlesansflex.ttf` | Google Sans Flex | SIL OFL 1.1 |
| `robotoflex.ttf` | Roboto Flex (optional; Google Sans Flex is used without it) | SIL OFL 1.1 |
| `instrumentserif_regular.ttf`, `instrumentserif_italic.ttf` | Instrument Serif | SIL OFL 1.1 |

To build on your own machine, download the same files from [google/fonts](https://github.com/google/fonts/tree/main/ofl) and save them under those names. Two further fonts, Playlist Script and Playlist Ornament, can be placed in `app/src/main/assets/fonts/` if you hold a licence; they are optional and ignored by git.

### Keys

Both are optional and are read from environment variables (or repository secrets in CI):

- `TMDB_KEY`: a free key from themoviedb.org, for film details.
- `SPOTIFY_CLIENT_ID`: for adding playlists and albums from Spotify.

Without them the rest of the app works as usual.

### Signing

Debug and release builds use the same sideload key in `keystore/`, so a release installs over a debug build and keeps its data. To publish somewhere that needs your own key, replace the `signingConfigs` block in `app/build.gradle.kts` and read [docs/RELEASING.md](docs/RELEASING.md).

## Contributing and releases

Pushing a tag that starts with `v` builds the APK and attaches it to a GitHub release; the steps are in [docs/RELEASING.md](docs/RELEASING.md). What changed in each version is in [CHANGELOG.md](CHANGELOG.md).

## Licence

[MIT](LICENSE). Fonts, libraries and models keep their own licences: see [THIRD_PARTY.md](THIRD_PARTY.md).
