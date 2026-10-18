# Architecture decision records

Short records of decisions that shape the code. Format: Context, Decision, Consequences. Add a new file
(`NNNN-title.md`) rather than rewriting an old one; mark a replaced record as superseded.

| # | Decision | Status |
| --- | --- | --- |
| [0001](0001-room-as-source-of-truth.md) | Room is the UI's source of truth; one mode-aware repository | Accepted |
| [0002](0002-guarded-http-and-reader-sandbox.md) | All untrusted URLs go through `GuardedHttp`; the reader WebView has no network | Accepted |
| [0003](0003-scoped-credentials.md) | Credentials are stored encrypted and sent only to their own host | Accepted |
