# Architecture

Forage is a single-module Android app (`app`, package `me.dougals.forage`): Kotlin, Jetpack Compose, Hilt, Room.
Diagrams are Mermaid sources in [`diagrams/`](diagrams); regenerate the SVGs with `diagrams/render.sh`.

## Layers

![Layers](diagrams/layers.svg)

- Dependencies point inward: `ui` and `presentation` use `domain` interfaces only, and `data` implements them. Neither `ui`
  nor `presentation` imports `data`, `presentation` does not import `ui`, and `data` does not import `ui`, `presentation`, `di` or the
  app classes (`ForageApp`, `MainActivity`). `domain` is plain Kotlin: no other layer, no Android, no HTTP/HTML/DI library.
  Only `di` (Hilt) and the root package (`ForageApp`, `MainActivity`, `AppNotificationTargets`) know the concrete classes.
  `LayerRulesTest` fails when a file breaks these rules.
- Examples of interfaces implemented in `data`: `SessionStore` (`PrefsSessionStore`), `CredentialStore`,
  `NextcloudLoginFlow` (`OkHttpNextcloudLoginFlow`), `OpmlExchange` (`OpmlTransfer`), `ArticleDownloader` (`OfflineArchiver`)
  and `ImageFetcher` (`ImageResourceFetcher`), which is what the reader uses to load images and the full article.
- Notifications: `data/notify` defines `NotificationTargets` (which intent a notification opens) and the notifiers read
  `AppVisibility` (`domain/app`, updated by `MainActivity`) to know whether the app is on screen. `AppNotificationTargets`, next to
  the activity, builds the intents, so `data` never names `MainActivity`.
- `ui/WebUrl.kt` and `ui/Links.kt` are small UI helpers (`isWebUrl`, the Nextcloud News link).

## Repositories

![Repositories](diagrams/repository.svg)

Room is the source of truth for the UI: every `observe*` call reads from it. `ModeAwareFeedRepository` is the only
`FeedRepository` the app injects. It reads through `LocalFeedRepository`, and sends writes and `refresh()` to the
implementation chosen by the current `SyncMode` (`LOCAL` or `NEXTCLOUD`). Both extend `RoomFeedRepository`, which holds the shared
read and state-change logic. Refreshes (and Nextcloud `addFeed`, which refreshes) run one at a time behind a mutex in
`ModeAwareFeedRepository`; a caller that arrives mid-refresh waits, then runs its own. See [sync](sync.md) for the Nextcloud path and [ADR 0001](adr/0001-room-as-source-of-truth.md).

**Local refresh** (`LocalFeedRepository.refresh`): fetch the feeds through `FeedSource`, at most six at a time, parse each with `FeedParser`,
and insert new articles with `insertIgnore` (unique on `feedId, guid`). Requests are conditional: the `ETag` and `Last-Modified` of the
last stored body (`feeds.etag`, `feeds.lastModified`) go back as `If-None-Match` / `If-Modified-Since`, and a 304 means nothing new.
The validators are saved only after the new body is stored. One failing feed does not stop the others; the first error in feed order is
returned at the end, with the report of what the others brought in. `RefreshProgress` counts feeds completed.

## Data model

![Room schema](diagrams/schema.svg)

`ForageDatabase` is at version 3 (`MIGRATION_1_2` adds `articles.imageUrl`; `MIGRATION_2_3` adds `feeds.etag` and `feeds.lastModified`, indices on `articles.remoteId`,
`(isRead, publishedAt)` and `(feedId, publishedAt)`, drops the old `isRead` index, and adds `articles.snippet`). Exported schemas are in
`app/schemas/`. State changes that queue a pending change (`setRead`, `setSaved`, `markFeedRead`, `setReadBatch`) write the article and the queue row in one
Room transaction. `remoteId` columns hold Nextcloud ids and stay null for items that exist only locally.

### Article lists and the reader

A card shows `articles.snippet`, a plain-text preview computed when the article is stored (`ArticleSnippets`, by local refresh and
the Nextcloud pull). List queries select the snippet and leave `summary` and `contentHtml` out; the reader loads the full row through
`observeById`. Rows stored before the column existed start with a NULL snippet and are filled in the background at launch
(`SnippetBackfill`). `ArticleListViewModel` builds its list on `Dispatchers.Default`.

`ReaderViewModel` sanitises the article body (`ReaderHtml.sanitise`, Jsoup `Safelist.relaxed()`) on `Dispatchers.Default` and exposes it as a
`SafeHtml`, the only type `ReaderHtml.wrap` accepts, so the screen cannot render unsanitised markup. It sanitises again only when the
article's HTML changes, not on a font or size change.

## Background work

`WorkManagerSchedulers` (in `data/work/Workers.kt`) schedules three workers, all requiring a network connection:

| Worker | Trigger | Does |
| --- | --- | --- |
| `RefreshWorker` | periodic (1, 3, 6 or 12 hours, or off), applied from the settings by `ForageApp` | `FeedRepository.refresh()` for the current mode; gives up after three attempts until the next period |
| `FlushWorker` | after a Nextcloud-mode read or saved change | pushes `pending_changes` |
| `ArchiveWorker` | when an article is saved | caches a cleaned copy of the page (`OfflineArchiver`); up to 5 attempts |

### New-article digest

After a background refresh, `RefreshWorker` gives the `SyncReport` to `NewArticleDigest`, which keeps the categories the user
opted into and builds one `DigestLine` per category (name, folder id, count, the feeds that gained articles, busiest
first, and the newest headlines across them). Both refresh paths fill `SyncReport.newHeadlines` with the newest
`HEADLINES_PER_FEED` (3) titles of each feed's new articles, so the report stays small even on a first Nextcloud pull, which
brings down the whole backlog. The newest headline across the digest leads the collapsed notification, with the category
summary in its header (sub-text); expanded, it quotes three headlines for a lone category and two each when categories
share it. `DigestNotifier` is the domain interface; `AndroidDigestNotifier` implements it. Nothing is posted while the app is
visible or on the first Nextcloud pull, whose whole unread backlog would count as new.

Tapping the notification starts `MainActivity` (`singleTop`) with the folder ids in `EXTRA_DIGEST_FOLDERS`. The activity
turns them into a `FolderRequest` (`ui/FolderRequest.kt`), handled in two steps: `ForageNavScaffold` pops back to the
Unread list, then `ArticleListScreen` calls `ArticleListViewModel.selectFolders` and reports the request handled so it
is not applied again. Uncategorized feeds use `UNCATEGORISED_FOLDER_ID` (-1), the same id as the Unread list's chip.

## Dependency injection

`di/AppModule.kt` provides singletons. Two `OkHttpClient`s are qualified so credentials cannot leak across uses:
`@WebClient` (feeds and pages; adds the per-host session cookie) and `@ApiClient` (Nextcloud API and image fetching;
no cookie interceptor, and Nextcloud Basic auth is added only by the API provider). `ReaderViewModel` exposes the `ImageFetcher` the reader's
`WebViewClient` (not Hilt-managed) uses for images. See [security](security.md).

## Decisions

Recorded in [`adr/`](adr).
