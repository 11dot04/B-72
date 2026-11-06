# Privacy

B-72 has no account, no analytics and no server of its own. Your notes, photos, voice notes, screenshots and artifacts are files inside the app's private storage on your phone.

## What stays on the device

- Text recognition, image labelling and cut-outs run on the device (Google ML Kit).
- The assistant (Gemma 3n E2B through LiteRT-LM) runs on the device. Its model file is downloaded once, then nothing about your notes is sent anywhere.
- Related-note matching uses a small on-device sentence model.

## When the app uses the network

Only when you do something that needs it, and only to the service named:

| Action | Service | What is sent |
|---|---|---|
| Look up a film | The Movie Database (api.themoviedb.org, image.tmdb.org) | The title you typed |
| Look up a book, or a book cover | Google Books, Open Library | The title or ISBN |
| Look up a song, album or playlist | Spotify, iTunes Search, music.163.com | The title and artist, or the link you shared |
| Fetch lyrics | LRCLIB, lyrics.ovh | The title and artist |
| Look up a food or product label | Open Food Facts | The barcode or name |
| Look up a sports fixture | TheSportsDB | The team name |
| Read a page for a preview or an article summary | The site itself, Wikipedia | A normal page request |
| Show a train or flight status | RailYatri, Indian Railways, Flightradar24 | Opened in your browser when you tap the button |
| Sign in to Google and back up | Google (Auth, Drive) | Only the app's own backup folder, when you turn it on |
| Download the assistant model | Hugging Face | A file request; no data about you |

## What the app reads from your phone

- Photos you pick, and (with permission) your screenshots, to build the Screens tab. Deleting a screenshot always asks the system first.
- The microphone, only while you record or dictate.
- Notifications, for reminders and trip alerts.

## Backups and the lock

Backups are yours: they go to your own Drive and can be switched off. The optional app lock uses the phone's own screen lock.

## Removing your data

Uninstalling the app removes every file it made. Backups on Drive stay until you delete them there.
