# 0002. Untrusted URLs go through `GuardedHttp`; the reader WebView has no network

Status: Accepted

## Context

Feed URLs arrive from OPML files, user input and redirects. Article pages and images are linked by feed content.
All of it is attacker-influenced and could point at the local network or a cloud metadata address (SSRF).

## Decision

- Feeds, article pages (offline archive) and images use `GuardedHttp`: it rejects non-public addresses for IP literals
  and for resolved names (`PublicOnlyDns`), follows redirects manually so every hop is checked, and disables proxies.
  Bodies are size-bounded.
- A user setting, *Allow local network*, turns the address check off for people who host feeds on their LAN.
- The reader WebView has JavaScript and file access off and a CSP of `default-src 'none'`. `shouldInterceptRequest`
  fetches images through `ImageResourceFetcher` and blocks other subresources, so the WebView never opens a socket itself.

## Consequences

- One implementation of the address rules, covered by tests (`PublicAddressTest`, `FeedFetcherTest`, `ImageResourceFetcherTest`).
- Reader pages cannot load scripts, frames or remote styles, so some feed content renders plainer.
- Images are fetched on the app's side, so they are subject to the size limit and image-only content-type check.
- The check is address-based: with *Allow local network* on, nothing stops requests to private addresses.

See [security](../security.md).
