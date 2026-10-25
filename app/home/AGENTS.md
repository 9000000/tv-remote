# app/home: TV Home

Read with the root [AGENTS.md](../../AGENTS.md) (rules, build, tests, releases apply here too).

## What it is

| Name | Code | `data-core` | What it is |
|---|---|---|---|
| **TV Home** | `app/home` | | the project's own launcher (`dev.tvremote.home`), first in the launcher gallery; set up from every client over ADB |

## Files

```
app/home/                TV Home, the launcher: plain Views, no :shared (it would pack the web UI; its "Manage from phone" page is packed from app/shared/web by packHomeWeb and served by ManageServer); HomeActivity (clock, dock,
                         grid, long-press menu), Widgets (system / usage / lunar / timer / note cards; usage, timer and CPU from TV
                         Setup's HomeInfo provider, which answers only TV Home by its signing key; also its live tiles, `apps/Tiles.kt` here / `HomeTiles.kt` there, keys `tile:<pkg>/<id>`, a `layout` each: gauge / next / toggle; a second provider, TV Channels' own HomeInfo, both pinned by signing key), Lunar (the lunar calendar, offline), Apps, Store (prefs), HomeReceiver (`--es set <b64 json>`, DUMP-protected, like
                         RulesReceiver). Its Kotlin (`dev.tvremote.home`) is grouped by who uses whom: the root keeps the entry points (HomeApp, HomeActivity, HomeReceiver, Store, Customize, Lang, Font, Power, the screensaver HomeDream / DreamSettings / Dream) and the components other apps start by name (PinActivity, OpenActivity: a moved class would break `screensaver_components` and TV Setup's `HomeKeys`); subpackages `widgets/` (cards + Weather, Lunar, MonthGrid, QrCard), `wall/` (backgrounds), `apps/` (Apps, Pins, Tiles, Badges, Nav, InputOpen), `keys/` (Keys, QuickKeys, Targets, Picker), `manage/` (ManageServer, HomeApi, Access, PhotoScreen), `ui/` (TopBar, Drop, Zoom, Frost). The ready-made themes are the site's signed list `docs/home/themes.json` (`dev/sign/sign_themes.py`; `Themes.kt` reads site, GitHub, kept copy, packed copy; `HomeConfig.parseReady` cleans each look; `/api/home` `themesBuiltin` carries `names`). Its settings' shape is ONE file, `app/shared/src/home/kotlin/.../HomeConfig.kt`, compiled
                         into :shared, :desktop and :home; the clients' side is `core/ontv/Home.kt` + `/api/home` + `js/home.js`.
                         In the gallery it is kind "self": its APK is the latest release's `tv-home.apk`, pinned by SHA256SUMS
```

## Backgrounds

Which path draws what (read before adding a theme or a wall kind):

| `wall` | Drawn by | Rotates (`walls` + `wevery`) |
|---|---|---|
| gradients, drawn scenes, `bing`, `photo*`, `vid:<id>` | native (`wall/Wall.kt`, `WallArt`, `VideoWall`) | yes (`HomeConfig.next`) |
| `web` + `loop.html?i=<jpg>` (the site's photo themes, the gallery) | native picture, cached (`wall/NetPicture.kt` `direct()`) | no: `web` is never in `walls` |
| `web` + `loop.html?v=<video>` (the site's video themes) | native player (`wall/WebVideo.kt` `direct()`) | no |
| `web` + `loop.html?s=<list>` (a slideshow, `docs/home/<list>.json`) | native in new TV Homes; older ones run loop.html's own slideshow | inside the slideshow |
| `web` + any other page (Immich Kiosk, a dashboard) | a WebView (`wall/WebWall.kt`) | no |

A WebView wall was measured slow on the FPT Play box (cold start + uncached download), so every site theme goes native; loop.html
stays only as the fallback for older TV Homes and the page's preview. Its allowed hosts are listed twice: loop.html `HOSTS` and the
`HOSTS` of NetPicture / WebVideo. Themes live in `docs/home/themes.json` (signed, `dev/sign/sign_themes.py`, bump `rev`), photos come
from `dev/gen/wallpapers.py` (Wikimedia Commons, licence + author kept), and a TV Home asks for the list again on Home (a day old) and
on opening Themes (an hour), `Themes.refresh`.
