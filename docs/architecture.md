# Architecture

Forage is a single-module Android app (`app`, package `me.dougals.forage`): Kotlin, Jetpack Compose, Hilt, Room.
Diagrams are Mermaid sources in [`diagrams/`](diagrams); regenerate the SVGs with `diagrams/render.sh`.

## Layers

![Layers](diagrams/layers.svg)

- Dependencies point inward: `ui` and `presentation` use `domain` interfaces only, and `data` implements them. Neither `ui`
  nor `presentation` imports `data`, and `domain` imports nothing from the other layers or from Android.
  Only `di` (Hilt) knows the concrete classes.
- Examples of interfaces implemented in `data`: `SessionStore` (`PrefsSessionStore`), `CredentialStore`,
  `NextcloudLoginFlow` (`OkHttpNextcloudLoginFlow`) and `OpmlExchange` (`OpmlTransfer`).
- `ui/WebUrl.kt` and `ui/Links.kt` are small UI helpers (`isWebUrl`, the Nextcloud News link).

## Repositories

![Repositories](diagrams/repository.svg)

Room is the source of truth for the UI: every `observe*` call reads from it. `ModeAwareFeedRepository` is the only
`FeedRepository` the app injects. It reads through `LocalFeedRepository`, and sends writes and `refresh()` to the
implementation chosen by the current `SyncMode` (`LOCAL` or `NEXTCLOUD`). Both extend `RoomFeedRepository`, which holds the shared
read and state-change logic. See [sync](sync.md) for the Nextcloud path and [ADR 0001](adr/0001-room-as-source-of-truth.md).

**Local refresh** (`LocalFeedRepository.refresh`): fetch each feed through `FeedFetcher`, parse it with `FeedParser`,
and insert new articles with `insertIgnore` (unique on `feedId, guid`). One failing feed does not stop the others;
the first error is returned at the end.

## Data model

![Room schema](diagrams/schema.svg)

`ForageDatabase` is at version 2 (`MIGRATION_1_2` adds `articles.imageUrl`). Exported schemas are in
`app/schemas/`. `remoteId` columns hold Nextcloud ids and stay null for items that exist only locally.

## Background work

`WorkManagerSchedulers` (in `data/work/Workers.kt`) schedules three workers, all requiring a network connection:

| Worker | Trigger | Does |
| --- | --- | --- |
| `RefreshWorker` | periodic (1, 3, 6 or 12 hours, or off), applied from the settings by `ForageApp` | `FeedRepository.refresh()` for the current mode; gives up after three attempts until the next period |
| `FlushWorker` | after a Nextcloud-mode read or saved change | pushes `pending_changes` |
| `ArchiveWorker` | when an article is saved | caches a cleaned copy of the page (`OfflineArchiver`); up to 5 attempts |

## Dependency injection

`di/AppModule.kt` provides singletons. Two `OkHttpClient`s are qualified so credentials cannot leak across uses:
`@WebClient` (feeds and pages; adds the per-host session cookie) and `@ApiClient` (Nextcloud API and image fetching;
no cookie interceptor, and Nextcloud Basic auth is added only by the API provider). `ReaderEntryPoint` gives the reader
`WebViewClient`, which is not Hilt-managed, access to `ImageResourceFetcher`. See [security](security.md).

## Decisions

Recorded in [`adr/`](adr).
