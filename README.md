![Forage](docs/banner.jpg)

# Forage

A native Android RSS reader with optional [Nextcloud News](https://apps.nextcloud.com/apps/news) sync.
Local-first, no accounts, no tracking.

## Features

- **Feeds:** RSS 2.0, RDF and Atom. OPML import and export. Folders.
- **Suggested feeds:** a bundled, UK-leaning catalogue of technology and Linux feeds (`app/src/main/assets/suggestions.json`), ranked on-device by what you already follow. Nothing is sent anywhere.
- **Swipe cards:** swipe right to toggle read, left to toggle saved, with haptics and an undo snackbar.
- **Mark older as read:** long-press a card to mark every older unread article in the same feed as read, with an undo snackbar.
- **Background refresh:** hourly by default; choose 1, 3, 6 or 12 hours, or off, and optionally Wi-Fi only.
- **Tabs:** Unread, Categories, Saved, Settings.
- **Reader:** Serif, Sans or OpenDyslexic (reader only) with adjustable text size. Scripts are off and
  images are fetched by the app, not the page.
- **Saving, three ways:** offline archive of the full article, sync to Nextcloud as a starred item, or
  share through the Android share sheet.
- **Authenticated feeds:** paste a tokenized feed URL, or sign in to a site in a built-in browser; the
  session cookie is kept per domain in encrypted storage and sent only to that exact host.
- **Two modes:**
  - *Local:* Forage fetches feeds itself.
  - *Nextcloud:* Forage syncs with your Nextcloud News server (API v1-3). Read and saved changes are queued
    offline and sent when you're connected; **Sync now** in Settings pushes immediately and reports what changed.
    Feeds added in local mode are created on the server when you connect.

## Connecting to Nextcloud

1. Install the News app on your Nextcloud server.
2. In Nextcloud, go to Settings → Security → Devices & sessions and create an **app password**.
3. In Forage, open Settings → Connect Nextcloud, enter your server (HTTPS only), username and the app password.

The app password is stored only in encrypted preferences. Your account password is never needed.

## Security

Feeds are untrusted input. Requests go through a guarded HTTP client that refuses private, loopback and
link-local addresses (including after DNS resolution and redirects) unless you turn on *Allow local network*
in Settings. Cleartext HTTP feeds are blocked. Cookies are never sent to other hosts, or with image fetches.

## Building

Requires JDK 17+ and the Android SDK (compileSdk 37). minSdk 26.

```sh
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # unit and Robolectric tests
./gradlew :app:assembleRelease      # unsigned; sign it yourself
```

Install on a connected device with Google's [Android CLI](https://developer.android.com/tools/agents/android-cli):

```sh
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

Single `app` module, MVVM with a clean-architecture split under `me.dougals.forage`:

| Package | Contents |
| --- | --- |
| `domain` | Models and the interfaces the UI layers depend on (`FeedRepository`, `SettingsRepository`, the credential and session stores, `OpmlExchange`) |
| `data` | Implementations of those interfaces: Room, DataStore, feed parsing, OPML, the Nextcloud client, WorkManager workers, the HTTP security layer |
| `presentation` | ViewModels and UI state |
| `ui` | Jetpack Compose screens and navigation |
| `di` | Hilt modules |

Room is always the source of truth for the UI. A mode-aware repository sends writes to the local or
Nextcloud implementation, and Nextcloud changes go through a pending-change queue flushed by WorkManager.

More detail: [architecture](docs/architecture.md), [Nextcloud sync](docs/sync.md), [security model](docs/security.md)
and [decision records](docs/adr/README.md).

Stack: Kotlin, coroutines and Flow, Compose with Material 3, Hilt, Room, DataStore, OkHttp, Retrofit,
kotlinx-serialization, WorkManager, EncryptedSharedPreferences.

The project was built test-first. Tests use JUnit 5, MockK, Turbine, MockWebServer and Robolectric.

## Status

Early (0.1.0). Not yet verified against a wide range of Nextcloud versions. Known gaps: strings are
English only, swipe cards snap back without animation, and there is no release signing config.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for third-party attributions.
