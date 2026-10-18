# 0003. Credentials are encrypted and sent only to their own host

Status: Accepted

## Context

Forage holds two kinds of secret: a Nextcloud app password and session cookies for sites that need a login. Both must not
reach other hosts, including through redirects, and the app also fetches attacker-influenced URLs.

## Decision

- Both are stored in `EncryptedSharedPreferences`. Auto-backup is disabled (`allowBackup="false"`).
- Two `OkHttpClient`s: `@WebClient` (feeds and pages) carries `SessionCookieInterceptor`; `@ApiClient` (Nextcloud, images)
  does not. Nextcloud Basic auth is added only by its own API client.
- Both interceptors are network interceptors, so they run per redirect hop, match the exact host, and attach only over HTTPS
  (Nextcloud Basic auth can be allowed over HTTP via the provider's `allowInsecure` flag).
- Session cookies are matched by exact host. The WebView supplies a bare cookie string without Domain or Path, so the app cannot
  tell host-only cookies from domain cookies and does not widen them to subdomains.

## Consequences

- Users must sign in from the host that serves the feed.
- A redirect to another host never receives the original host's credentials.
- `EncryptedSessionStore` needs a real Keystore, so tests use `PrefsSessionStore` and the encrypted wrapper is not unit-tested.
