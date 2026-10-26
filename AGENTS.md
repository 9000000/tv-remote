# AGENTS.md

A guide for agents (and people) changing this repo. User docs: [README.md](README.md) / [README.vi.md](README.vi.md).
Why some code looks odd: [dev/DECISIONS.md](dev/DECISIONS.md). This file holds the rules for every app; each folder's
`AGENTS.md` (loaded as its `CLAUDE.md` when you work there) holds its files and its own rules: read it before changing that part.

| Folder | Its AGENTS.md has |
|---|---|
| `app/shared/web/` | the page's rules: `remote.json`, the tab shapes, icons, the page's state, i18n |
| `app/shared/` | the Kotlin core's packages, other brands (LG, Samsung), the Downloader codes list |
| `app/android/` `app/tv/` `app/tvkeys/` `app/home/` `app/channels/` `app/games/` `app/desktop/` `app/karaoke/` | that app's files (TV Home: which path draws each background; TV Setup: the rules on the TV) |
| `dev/test/` | emulators by hand, speed budgets, smoke / layout / audit / contrast |
| `dev/release/` | the six version numbers, the user guide's `data-v`, release.sh, SHA256SUMS |

## What this is

Remote control for Android TV / Google TV with no server in between. Always called by these names (README, comments, the site):

| Name | Code | `data-core` | What it is |
|---|---|---|---|
| **Phone app** (App điện thoại) | `app/android` | `phone` | "TV Remote" for Android: native remote screen, Manage screen = the web UI |
| **Computer app** (App máy tính) | `app/desktop` (JVM) | `pc` | "TV Remote" for Mac, Windows, Linux: `Server.kt` on `127.0.0.1:8765` in its own window |
| **TV app** (App TV) | `app/tv` | | on the TV, shown as "TV Setup" (keep that label: installed TVs, Downloader codes, file names) |
| **TV web page** (Web của TV) | `app/tv` `RemoteService` | `tv` | the web UI TV Setup serves on 8765 for any browser, after a 6-digit code |
| **TV Home** | `app/home` | | the project's launcher (`dev.tvremote.home`), set up from every client over ADB |
| **TV Channels** | `app/channels` | | iptv-org's free channels; others reach it only by its links (`tvchannels://…`) |
| **TV Games** | `app/games` | | retro (libretro, GPL-3) and web games, phones as pads, own server on 8780 |
| **TV Karaoke** | `app/karaoke` | | shelved prototype: not in release.sh, the site or the clients |

What each can do: README "Three apps and a web page" / README.vi "Ba app và một trang web"; keep that table true when a
feature lands on only some of them. The Phone, TV and Computer apps speak ADB themselves (`Adb.kt`) and push `scrcpy-server`
for video, touch and keys; Phone and Computer also send keys without ADB (Android TV Remote protocol, `Atv.kt`), and LG /
Samsung through the same `Remote` interface. A feature that needs ADB: `needs: ["adb"]` / `data-needs="adb"`.

`app/gradle/` is the Gradle build of everything Kotlin. `dev/`: test/, gen/ (icons, wallpapers), shots/, sign/ (the site's signed
lists), release/; `optimize_images.py` never touches `docs/home/` and `docs/launchers/` (their SHA-256 is signed); dev/README.md.

## Rules that matter

- **UI follows the design system, read before touching any screen:** [app/shared/web/DESIGN.md](app/shared/web/DESIGN.md)
  (the web UI), [app/DESIGN-TV.md](app/DESIGN-TV.md) (TV Setup, TV Home, TV Channels, TV Games, the Phone app's native
  screens), [docs/games/web/DESIGN.md](docs/games/web/DESIGN.md) (web games, the phone pad). A look they lack is added there first.
- **Every change reaches the Phone app, the TV web page and the Computer app.** UI goes in `app/shared/web` once; a new `/api`
  route in `Manage.kt` (all three cores), or `Server.kt` / `RemoteService` / the Phone app's Manage screen where only some have it.
  The Phone app's native remote screen is separate: mirror the change by hand, or say why it cannot be.
  **Reuse over copies (the user, 2026-10-24):** a feature is built once where everyone shares it (the web UI, `remote.json`,
  `:shared` Kotlin, TV Setup's rules) and reached from there; no second native version only to save a tap.
- **The remote's keys and menus live once in `app/shared/web/remote.json`** (web: `js/remote.js`, phone: `PhoneRemote.kt`);
  rules in app/shared/web/AGENTS.md.
- **The apps map** lives once in `docs/data/apps-map.json`: `docs/apps.html` draws it; edit it, then `node dev/gen/apps_map.js` (the README mermaid blocks;
  `tests/apps_map.test.js` fails when they differ).
- **Vietnamese is the source language; words go by id** in `app/shared/web/i18n/<lang>.json` (`vi.json` first, `en.json` complete),
  for the page (`T('id')`) and Kotlin (`core/Words.kt`, `T("id")`); no new `t("vi", "en")` pairs. README.md / README.vi.md in
  step; CHANGELOG and release notes in both languages.
- **Users first:** automatic where possible; an error only after every fallback failed, in words a person can act on
  (`Errors.kt`, `friendly()` in `js/core.js`). Choices look like buttons. Long waits show progress and can be cancelled.
- **Never commit** `cert.pem`, `key.pem`, `tvs.json`, `*.jks` (they pair with or sign for real devices). Never expose port 8765 to the Internet (away from home: Tailscale).
- **"Allow USB debugging?"** is only answered for the key whose fingerprint matches (`AdbPrompt.kt`); never Allow an unknown key.
- **Nothing in `docs/` is generated** (GSAP timelines and CSS by hand). `app/*/res/drawable/ic_*.xml` come from
  `dev/gen/gen_icons.py`, the logo's corners and `app/desktop/icon.*` from `dev/gen/round_icons.py`: edit those.
- Comments say why, not what; match the surrounding style (end-of-line comments aligned to column ~70).

## Build and run

- Apps: `app/<x>/build.sh` (JDK 17, Android SDK) → `app/<x>/build/*.apk`; the web UI is packed into the APKs.
- Computer app: `cd app/gradle && ./gradlew :desktop:installDist`; for tests its own port and TV list (real TVs untouched):
  `TVS_FILE=/tmp/x.json TVR_KEYS=/tmp/keys TVR_PASS=testtest app/desktop/build/install/desktop/bin/desktop --port 8769`,
  then `http://127.0.0.1:8769/?k=testtest` (curl: header `x-tvr-pass`). The page is read from disk: a web change needs a reload.
- **A feature that needs a newer TV Setup:** add it to `SetupNeed.MIN` (+ `SetupNeed.name`, `SETUP_NEEDS` in `js/core.js`),
  check before starting (`/api/setup/need?f=`, `setupFor()` on the page, `withSetup()` on the phone) or throw `SetupNeed.Missing`; every client turns
  that into one Update / Install TV Setup question (`needSetup()` in `js/core.js`, `offerSetup()` on the phone).

## Releases and pushing

- Release only when the user says so; batch them; keep GitHub Actions runs few (nothing runs on push). Details: dev/release/AGENTS.md.
- Notes go under `## Unreleased` in `CHANGELOG.md` (**Tiếng Việt** and **English**) as features land. A change to what a
  `docs/guide.html` topic describes: update it and set `data-v="next"`.
- Push without asking once tested and committed (a push deploys `docs/` at once). Sub-agents never push.
- PRs, issues and comments on other people's repos (cleanyourtv …): no "Generated with" line or session link.

## Testing

**Done = run on every platform it touches, logic and UI**: the Phone app (phone emulator), the Computer app's page (Chrome,
1100 and 400 px), the TV web page / Manage on the TV, TV Setup and the TV apps (TV emulator). Logic: it really happens on the TV,
plus errors, Stop, an old TV Setup. UI: a screenshot of each new or changed screen, looked at (nothing cut, wrapped or clipped).
Unit tests alone do not count; report what was tested where and what could not be. A UI fault seen is fixed in the same change, not asked about.

- Emulators, not real TVs: `dev/test/emu.sh` (`create` once; no argument = start + build + install; `install`, `reset`, `stop`,
  `status`). In the phone app the TV is `10.0.2.2`. By hand and the pitfalls: dev/test/AGENTS.md.
- **Keep the computer usable:** at most two emulators; heavy steps (Gradle, ffmpeg, `audit.js`) one at a time through
  `dev/test/light.sh <cmd>`, never several with `&`; no video background left looping on the TV emulator (~9.5 cores).
- Unit tests: `node --test app/shared/tests/ app/games/tests/` (also the pre-push hook: `git config core.hooksPath dev/hooks`),
  `cd app/gradle && ./gradlew :shared:testDebugUnitTest :tv:testDebugUnitTest` (+ the app you changed). A red speed budget
  (`PerfBudgetTest`, `perf_budget.test.js`) is a regression to find and fix, not a baseline to raise.

## Handy facts

- **Finding code: codegraph** (a symbol index). `.mcp.json` gives its tools (`codegraph_explore`, `codegraph_node`, callers,
  impact); the CLI says the same: `codegraph explore "<what>"`, `codegraph node <Symbol>`, `codegraph callers <fn>`. Once per
  clone: `npm i -g @colbymchenry/codegraph && codegraph init .` (`.codegraph/` gitignored); its MCP server keeps the index current.
  grep for words and strings (i18n ids, JSON keys), codegraph for "who calls / what breaks".
- Crashes and community themes: [dev/census/CRASHES.md](dev/census/CRASHES.md), [dev/themes/REVIEW.md](dev/themes/REVIEW.md).
- Downloader codes (latest release): **2397346** tv-setup, **2973135** tv-remote, **4356026** tv-home, **8135635** tv-channels,
  **2294968** tv-games; the signed list and how the apps use it: app/shared/AGENTS.md.
- **Android 14 blocks an app in the background from opening a screen** (logcat: `Background activity launch blocked`, no
  exception): TV Setup's key filter, receivers and binder calls are all "background". TV Setup is exempt through
  `appops set dev.tvremote.setup SYSTEM_ALERT_WINDOW allow` (`KeyFix.prepare`, on every ADB link); elsewhere the app in front
  starts it (a `PendingIntent` handed to TV Home, or a link in the state). Test on the API 34 emulator with that appop at `default`.
- Tell a TV from a phone by `android.software.leanback`, never the screen size (a 1080p TV is only `sw540dp`); TV Setup also runs
  on phones (ADB on 5555 there; Optimize TV refuses them, `Setup.tvOnly`). TV Setup's state on the TV: `/data/local/tmp/tvr-disabled.txt` (Undo),
  `/sdcard/Android/data/dev.tvremote.setup/files/stats/`.
- The site https://tvremote.pages.dev is `docs/` (Cloudflare Pages, on every push to main): `docs/README.md`; keep `docs/llms.txt` true.
- Downloads: `https://github.com/HoangTran0410/tv-remote/releases/latest/download/<tv-setup|tv-remote>.apk`.
