# Third-party material

## Fonts (SIL Open Font License 1.1)

- **Google Sans Flex**, Google. Variable: weight, width, optical size, grade, slant, roundness.
- **Roboto Flex**, Google. Thirteen axes, including stroke thickness (XOPQ, YOPQ), x-height (YTLC), capital height (YTUC), ascender (YTAS), descender (YTDE), figure height (YTFI).
- **Instrument Serif**, Instrument.
- **Playfair Display Italic**, Claus Eggers Sørensen. Bundled as the fallback script face.

The fonts are downloaded at build time from [google/fonts](https://github.com/google/fonts); the OFL text is in each family's folder there.

## Optional fonts

Playlist Script and Playlist Ornament are not part of this repository. Put your own licensed copies in `app/src/main/assets/fonts/`.

## Symbols

The dingbat stamps are characters from the phone's own symbol fonts (Noto Sans Symbols, SIL OFL), set as text. Nothing is bundled.

## Libraries

| Library | Licence |
|---|---|
| Jetpack Compose, Material 3, Activity, Core, Palette, Graphics Shapes (AndroidX) | Apache 2.0 |
| Google ML Kit (text recognition, image labelling, subject segmentation) | Google ML Kit terms |
| MediaPipe Tasks (text) | Apache 2.0 |
| LiteRT-LM | Apache 2.0 |
| kotlinx.serialization, kotlinx.coroutines | Apache 2.0 |
| Google Play services Auth | Android Software Development Kit licence |

## Models

- Gemma 3n E2B is released by Google under the [Gemma Terms of Use](https://ai.google.dev/gemma/terms). The app downloads it on the user's request; it is not part of this repository.
- Universal Sentence Encoder (MediaPipe model), Apache 2.0.

## Data sources

Film data and images come from The Movie Database (this product uses the TMDB API but is not endorsed or certified by TMDB). Book data from Google Books and Open Library, food data from Open Food Facts (ODbL), lyrics from LRCLIB.
