<div align="center">

<img src="cardamom.svg" width="112" alt="Cardamom Music logo">

# Cardamom Music

**A minimalist, modern local music player for Android.**

A clean-room, Material You reimagining of [Vanilla Music](https://github.com/vanilla-music/vanilla):
your own files, no accounts, no ads, no analytics.

</div>

---

## Features

- 🎵 **Local library** scanned from `MediaStore` — Artists, Albums, Tracks, Playlists and a Folders browser, with search and per-tab sorting.
- 🎨 **Material 3 / Material You** — dynamic color on Android 12+, a static dark palette below, plus light / dark / system themes.
- ▶️ **Media3 (ExoPlayer) playback** — a single `MediaSessionService` with the system media notification, lock-screen controls, and Bluetooth / headset buttons. Audio focus and auto-pause on headphone disconnect (with optional resume on reconnect).
- 📱 **Now Playing & mini-player** — large artwork, scrubber, shuffle / repeat, queue management, and skip controls on the persistent mini-player.
- 🏠 **Home-screen widget** — artwork, title, play/pause and next.
- 🎚️ **Equalizer** — device presets, per-band gain, bass boost and virtualizer, attached to the player's audio session.
- 🏷️ **Tag editor** — read and write tags with [TagLib](https://taglib.org) through the scoped-storage write flow (no file copying).
- 🌐 **Online metadata** — cover art and tags from MusicBrainz, Cover Art Archive and Deezer, applied on demand.
- 📝 **Lyrics** — embedded lyrics, a Room cache, and [LRCLIB](https://lrclib.net), with optional synced (karaoke) highlighting and tap-to-seek.
- 📋 **Playlists** — create and edit, favorites, M3U/M3U8 import & export, add a whole folder to a playlist, and bulk-import every playlist file in a folder via the Storage Access Framework.
- 📁 **Folder filtering** — exclude folders (recordings, app temp files, ringtones) that `MediaStore` mislabels as music, so the library stays clean.
- 🌍 **Per-app language** — English and Russian, switchable in Settings.
- 🔒 **Private by design** — no ads, no accounts, no tracking, no background scraping.

## Tech stack

- **Language:** Kotlin, coroutines + Flow (no Java, RxJava or LiveData)
- **UI:** Jetpack Compose + Material 3, unidirectional data flow, a `ViewModel` per screen
- **Playback:** Media3 — ExoPlayer + `MediaSessionService`, `MediaController` from the UI
- **Library:** `MediaStore.Audio` scanning with a path-based folder browser
- **Storage:** Room (playlists, favorites, lyrics cache), Jetpack DataStore (settings)
- **DI:** Hilt · **Images:** Coil 3 · **Network:** Retrofit + kotlinx.serialization
- **Tags:** TagLib via JNI (`com.github.Kyant0:taglib`)
- Single Gradle module (`:app`)

## Requirements

- Android 8.0 (API 26) or newer
- JDK 17+ and the Android SDK (compile / target SDK 37)

## Building

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew testDebugUnitTest      # run unit tests (M3U, LRC, import matching, MusicBrainz mapping)
./gradlew lint                   # static analysis
```

The debug APK lands in `app/build/outputs/apk/debug/`.

## Permissions

- **Audio access** — `READ_MEDIA_AUDIO` (API 33+) or `READ_EXTERNAL_STORAGE` (API ≤ 32) to build the library.
- **Notifications** — `POST_NOTIFICATIONS` for the playback notification.
- **Modify audio settings** — required to attach the equalizer effects.
- **Storage writes** — only on API ≤ 29 for tag writing; on API 30+ writes go through `MediaStore` write requests.

Nothing is sent anywhere except the metadata/lyrics lookups you trigger yourself.

## Acknowledgements

- Inspired by [Vanilla Music](https://github.com/vanilla-music/vanilla).
- Tag reading/writing by [TagLib](https://taglib.org) (GNU LGPL v2.1), linked as a shared library.
- Metadata and lyrics from [MusicBrainz](https://musicbrainz.org), [Cover Art Archive](https://coverartarchive.org), [Deezer](https://www.deezer.com) and [LRCLIB](https://lrclib.net) — please respect their terms of use.

## License

Cardamom Music is free software, licensed under the **GNU General Public License v3.0**
(see [LICENSE](LICENSE)). TagLib is used under the GNU LGPL v2.1; because it is linked as
a shared library, you may replace it with a modified version.
