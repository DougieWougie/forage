# 0001. Room is the source of truth; one mode-aware repository

Status: Accepted

## Context

Forage works without an account (Local mode) or against a Nextcloud News server (Nextcloud mode). The UI should not
care which, and should keep working offline.

## Decision

- The UI reads only from Room, through `FeedRepository` (`domain`).
- `ModeAwareFeedRepository` is the single injected implementation. It reads via `LocalFeedRepository` and sends writes and
  `refresh()` to the Local or Nextcloud implementation according to `SyncMode`. Both extend `RoomFeedRepository`.
- In Nextcloud mode, read and saved changes are applied to Room first and queued in `pending_changes` for a WorkManager flush.
  Structural changes (feeds, folders) go to the server first.
- On pull, a field with a pending local change keeps its local value.

## Consequences

- Reading, swiping and saving work offline; the UI updates from Room `Flow`s without waiting on the network.
- Switching mode needs no data migration: the same tables serve both. Feeds added in Local mode are created on the server
  when connecting (`NextcloudFeedRepository.pushLocalOnly`, `NextcloudSyncer` adoption by URL).
- Local changes beat the server's, so a change made on another device to an article with an unsent local change is overwritten at the next flush.
- Structural changes fail when offline in Nextcloud mode.

See [sync](../sync.md).
