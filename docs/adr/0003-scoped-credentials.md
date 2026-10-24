# 0003. Credentials are encrypted and sent only to their own host

Status: Accepted

## Context

Forage holds two kinds of secret: a Nextcloud app password and session cookies for sites that need a login. Both must not
reach other hosts, including through redirects, and the app also fetches attacker-influenced URLs.

## Decision

- Both are stored in app-private `SharedPreferences` (`securePrefs`). Auto-backup is disabled (`allowBackup="false"`).
  They were previously in `EncryptedSharedPreferences`, which Jetpack has deprecated; the recommended replacement is plain
  prefs under Android's file-based encryption. That is a weaker posture (a rooted device can read them), accepted to avoid
  a deprecated dependency or hand-rolled crypto. Old encrypted files are copied across once at first launch (`migrateLegacyPrefs`),
  and the security-crypto dependency can be removed once that has shipped for a release or two.
- Two `OkHttpClient`s: `@WebClient` (feeds and pages) carries `SessionCookieInterceptor`; `@ApiClient` (Nextcloud, images)
  does not. Nextcloud Basic auth is added only by its own API client.
- Both interceptors are network interceptors, so they run per redirect hop, match the exact host, and attach only over HTTPS
  (Nextcloud Basic auth can be allowed over HTTP via the provider's `allowInsecure` flag).
- Session cookies are matched by exact host. The WebView supplies a bare cookie string without Domain or Path, so the app cannot
  tell host-only cookies from domain cookies and does not widen them to subdomains.

## Consequences

- Users must sign in from the host that serves the feed.
- A redirect to another host never receives the original host's credentials.
- The stores are tested through `PrefsSessionStore` and `PrefsCredentialStore`; `migrateLegacyPrefs` is unit-tested, but opening the legacy encrypted file needs a real device and is not.
