# dev

Tools for people working on the code, one folder per kind of work. Each script says more in its first lines.
Run them from the repo root (`dev/test/emu.sh`, `python3 dev/gen/gen_icons.py`); each finds the root itself.

| Folder | For |
|---|---|
| `test/` | emulators, checks of the page and the routes, the site served locally |
| `gen/` | files made from a recipe: icons, wallpapers, IR codes. Edit the script, never its output |
| `shots/` | the site's screenshots and the pictures composed from them |
| `sign/` | the signed lists on the site (`docs/**.json` + `.sig`) |
| `optimize_images.py` | every tracked PNG / JPEG / WebP made smaller (dry run; `--write` applies; needs Pillow, numpy for the lossy step). Skips the hash-pinned `docs/home/` and `docs/launchers/` |
| `themes/` | the community theme queue (Cloudflare Worker + D1 `tvr-themes`); reviewed with `sign/review_themes.py` |
| `release/` | releases, update keys and their backup (only when the maintainer says so) |

Notes beside them: `DECISIONS.md` (why some code looks odd), `../app/DESIGN-TV.md` (the TV apps' shared look),
`bloat-sources.md` (where Optimize TV's lists come from). Playwright for the Node scripts: `npm install --prefix dev playwright`.

## test/

| Script | Does |
|---|---|
| `emu.sh` | the two test emulators (Google TV and phone, Android 14): `create` once, then no argument to start them and install the apps, `install`, `reset`, `stop` (AGENTS.md "Testing") |
| `light.sh <cmd>` | runs a heavy step (ffmpeg, Gradle, audit) at low priority, one at a time, once load and memory leave room (waits up to `LIGHT_WAIT` s) |
| `media.sh` | test clips for the video / GIF backgrounds (1080p60 12 Mbps, 1080p30, 720p30, long GOP, two GIFs), made once in `.cache/media` |
| `smoke.py [tv\|URL]` | every `/api` route over a real network path (the TV emulator through `emu redir`, not adb forward): each must answer in full, without 500; exit 1 if not |
| `layout.js [URL]` | the page in Chrome **and** WebKit × 1100 / 400 px × every tab + Settings, English and Vietnamese: sideways scroll, cut words, wrapped labels, things out of their card, overlapping cards, wrapped chips; screenshots in `/tmp/tvr-layout` |
| `audit.js` | the whole page pressed through by a script (every tab, dialog, menu and chip, down to `--depth`), no TV: `/api` is the live demo's made-up TV. Each screen checked for layout, contrast, the UI rules, words, touch size, script errors; report with boxed screenshots in `/tmp/tvr-audit/index.html` (~20 min; `--only=apps`, `--budget=` s per tab) |
| `contrast.py [URL]` | colour contrast on the rendered page, both themes × 1100 / 400 px × every tab; exit 1 under 4.5:1 (needs Chrome) |
| `check_quiz.py` | the TV Games quiz bank (`docs/games/web/quiz/bank.json`) |
| `fakegh.sh <tv-home.apk> <version>` | a fake GitHub release on 127.0.0.1:8099 (signed with a throwaway key in `.cache/fakegh`) for trying updates and the launcher gallery end to end; prints how to start the computer app against it |
| `faketv.py <ip> <ports> <cert> <key>` | a fake TV with only Wireless debugging (mDNS + adbd's TLS start, then "not paired"), for the pairing flow without such a TV |
| `serve_docs.py [port]` | the site (`docs/`) on localhost:8000 as Cloudflare Pages serves it (`/guide` is `guide.html`), never cached |

## gen/

| Script | Makes |
|---|---|
| `gen_icons.py` | the line icons: the web sprite and `res/drawable/ic_*.xml` of the apps. Add an icon here |
| `round_icons.py` | the logo's rounded corners everywhere it is a picture, and `app/desktop/icon.icns` / `.ico` |
| `channels_art.py`, `games_art.py` | TV Channels' and TV Games' launcher icons and banners |
| `wallpapers.py` | TV Home's wallpaper gallery (`docs/home/`), signed |
| `gen_wallspec.py` | `WallSpec.kt`, the wallpapers TV Home paints, from the page's recipes |
| `ir_codes.py` | `assets/ir/codes.json` of the phone app, from Flipper-IRDB (CC0) |

## shots/

| File | Does |
|---|---|
| `shots.py` | the site's screenshots from the emulators (`docs/README.md`) |
| `home-<lang>.html`, `Roboto.ttf` | what `shots.py` puts on the TV's screen: a made-up home screen per language, and its font |
| `games/` | the TV Games picture of the home page: the TV, the library and two phones as pads. `node dev/shots/games/compose.js` → `docs/img/tv-games-<lang>[-640].webp` |

## sign/

Each checks its list, then signs it with the launcher list's key (`~/.tv-remote-release/launchers.key`, apart from the
release key; its public half is `Launchers.KEYS`). Run after every edit of the list and commit both files.

| Script | List |
|---|---|
| `sign_launchers.py` | `docs/launchers/launchers.json`, and each APK's SHA-256 and thumbnail. Makes the key when absent |
| `sign_games.py` | `docs/games/games.json`: TV Games' cores, suggested web games, homebrew |
| `sign_providers.py` | `docs/channels/providers.json`: TV Channels' extra sources, e.g. VieON |
| `sign_bloat.py` | `docs/optimize/bloat.json`: Optimize TV's suggestions added from the site |
| `review_themes.py` | (also behind `dev/admin.py`) reviews the community themes sent from TV Home (HTML page, then approve / reject / skip each): saves the pictures, adds the theme to `docs/home/themes.json`, signs it; commits nothing |
| `sign_themes.py` | `docs/home/themes.json`: TV Home's ready-made themes (names per language, looks; a theme may set `wurl`, so only a signed list may). Packed in the APK by `:home` `packThemes`; `HomeThemesTest` keeps the copy equal; stamps `rev` (the higher one wins, so an undeployed site never overwrites a newer APK). Web backgrounds go through `docs/home/loop.html` (host allow-list); each theme names its `src` + licence: Sa Pa photo = Wikimedia Commons CC0 `File:Rice terraces in Sapa, Vietnam.jpg`; beach video = CC0 `File:Sunset timelapse on the beach in Libreville, Gabon.webm`; city video = CC0 `File:Shanghai Bund, Lujiazui Skyline Timelapse.webm` (480p transcodes) |
| `sign_downloader.py` | `docs/data/downloader.json`: the Downloader codes |

## admin.py

`python3 dev/admin.py [--port N]`: the owner's local admin page (Vietnamese), instead of terminal prompts. Opens `http://127.0.0.1:<port>/?k=<random key per run>`;
127.0.0.1 only, every request (page, API, pictures) must carry the key and a local Host. The tokens (`~/.tv-remote-release/themes-admin-token`, `crash-token`) are read here, never sent to the browser.
Tab **Chủ đề chờ duyệt**: a card per pending community theme (preview, wall photo, sender, settings, red flags, free slots of `READY_MAX`); Duyệt runs `review_themes.py`'s own `approve()` (pictures, `themes.json`, signature) and shows the files + the `git add` / `git commit` lines to copy; nothing is committed or pushed. Bỏ rejects (the reason is asked but the API has no field: not stored).
Tab **Lỗi**: the census worker's crashes by count, filter by app / version, a click shows the stack and device, Xóa removes it (all versions).
Tests: `THEMES_URL`, `THEMES_TOKEN_FILE`, `CENSUS_URL`, `CRASH_TOKEN` point it at local `wrangler dev` workers (`dev/themes`, `dev/census`).

## release/

| Script | Does |
|---|---|
| `release.sh` | the whole release: version numbers, CHANGELOG, tag, APKs, GitHub release, the computer apps through GitHub Actions, then `sums.sh` (AGENTS.md "Versions and releases") |
| `sums.sh <tag>` | `SHA256SUMS` of a release, signed, attached. Run again after replacing any file of a release by hand: the apps refuse a file the list does not match |
| `code_changed.py <tag> <paths>` | did an app's code change since a tag, beyond comments, `.md`, `build.sh`, tests: `release.sh` bumps an app's number only then |
| `guide_stale.py` | the guide's topics whose app files changed since their `data-v`; `release.sh` stops on them |
| `stamp_since.py` | `docs/data/since.json`: "next" becomes the version and its date |
| `churn.sh` | how often the `/api` still changes, week by week; `release.sh` prints it and says when 1.0 is near |
| `cores.sh` | downloads the libretro cores TV Games lists from the buildbot, prints their sha256 / size entries and the `gh release upload cores-1` command (run by hand) |

**Keys.** The apps install an update only if its `SHA256SUMS` is signed (Ed25519) by a key in `Release.KEYS`.

- `java dev/release/Sign.java new|pub|sign`: makes a key, prints its public half, signs a file. No build needed.
- The keys live in `~/.tv-remote-release/`: `ed25519.pem` in use, `ed25519-spare.pem` kept elsewhere. Never on GitHub.
  Losing both means no installed app takes an update again.
- `dev/release/keys.sh backup` packs the keys and every `signing.jks` (the APKs' signatures) into one password-locked file
  (`dev/release/Keys.java`, AES-GCM, `*.tvrkeys`, never committed) to keep off this computer. `dev/release/keys.sh list <file>` checks a
  copy opens; `restore <file>` puts the keys back.
