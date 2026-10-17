# app/shared/tests

Tests of the web page's scripts (`app/shared/web/js`), run with Node 18+ and nothing to install:

```
node --test app/shared/tests/
```

Each `*.test.js` says on its first line what it keeps true: `ui.test.js` the UI kit (no inline styles, one size per kind
of control), `remote.test.js` the remote screen's `remote.json` on both sides, `page.test.js` no name declared twice across
the page's scripts, `contrast.test.js` the WCAG ratios of the colour tokens, and so on.

**The case tables** are read by both languages, so the page and the Kotlin core cannot drift apart:

| Table | JS | Kotlin (`app/shared/src/test`) |
|---|---|---|
| `qr_cases.json` | `qr.test.js` (`js/qr.js`) | `QrTest.kt` (`Qr.kt`) |
| `tvshell_cases.json` | (what `js/tvread.js` reads) | `TvShellTest.kt` (`TvShell.kt`) |
| `fleet_cases.json` | | `FleetTest.kt` (`Fleet.kt`) |

A case found on a real TV goes into the table, then both sides are made to pass it. The Kotlin side:
`cd app/gradle && ./gradlew :shared:testDebugUnitTest`.
