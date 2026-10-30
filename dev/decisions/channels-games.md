# Decisions: TV Channels and TV Games

Why some code looks odd ([the list](../DECISIONS.md)).

## Phone pads: why links dropped and lagged (`GameServer.kt`, `pad.js`)
- iPhone drops: iOS freezes a page's timers and socket (screen dimmed, locked, other app, Low Power Mode). On waking, "no pong for 5 s" was true of the page, not the link, so a healthy link was closed every wake. A gap between ticks now means "slept": ping, fresh window, dead only after 8 s.
- Lag: a frame was 3-4 writes (one TLS record / TCP segment each) and a broadcast wrote phones in turn on one thread. Now one write per frame from a writer thread per phone (256-frame queue). The TV holds a Wi-Fi lock while serving (power save parks frames ~100 ms).
- Same IP is one phone only if the same kind (iOS / Android / other): a repeater or hotspot puts both behind one address.
- Emulator loopback cannot show the gain (p50 1-2 ms before and after); real Wi-Fi is the test.

## Ready-made channel categories are data, per country (`Categories.kt`, `Shelves.kt`, `chancat.js`)
- Users mostly in Vietnam could not find channels among iptv-org's groups and built their own slowly. The apps now open on rows by category when the site has `docs/channels/categories/<cc>.json`; no country is named in code, so a new one is a file and a push.
- Matching is by normalized channel NAME (NFD, no marks, đ→d, [a-z0-9], trailing hd/fhd/uhd/sd/4k stripped; `re:` = regex), never by stream URL: links die weekly, names do not. Shared vectors: `app/shared/tests/fixtures/norm.json`.
- Not signed (unlike providers.json): it holds names only, so a tampered copy can only misfile channels.
- Coverage 2026-10-24: 285 of 298 real names (iptv-org vn + TV360 + VieON) land in a category; the rest (radio, VBS TV) go to "Others".
- The page cannot read the site (no CORS header), so the TV/desktop server proxies it: `GET /api/channels/categories?cc=`, a day's cache per country, `{}` for a 404.
- Focus on the rows is a ring only (no scale): 24 D-pad moves on the emulator 307 frames, 7.8% janky, p99 34 ms, vs the old grid's 10.2% / 65 ms.

## TV Games: lobby and columns cost (`GameActivity.still()`, `LobbyView.kt`, `LibraryActivity.kt`)
- The ROM lobby was slow because libretro's GLThread kept drawing every vsync behind it, plus a pulse and a spinner always running: emulator 589 CPU ticks / 239 frames → 38 / 23 with RENDERMODE_WHEN_DIRTY while waiting and no pulse. The QR takes 1-6 ms: not the cause; it is cached anyway.
- Changing columns rebuilt every tile: now the same views are re-measured in place (215 → 34-41 ticks).
- Pills (tvui `pill`) change colour only on focus: a scale needs a hardware layer per focus, ~7x the CPU on the FPT box (same finding as Customize).
- Lobby layout: QR card 320 dp (~614 px at 1080p) left, title/players/start right, one Back hint; the web games' in-page lobby mirrors it.
- Still slow: typing in search (4 letters: 129 ticks, 95% janky); not yet measured on the FPT box.

## TV Channels first open: show iptv-org's list first, the rest behind (`BrowseActivity.load`, `Catalog.iptv`, `Providers.warm`, `TvState.loading`)

The first open (no files kept) did five requests one after another on the I/O thread before anything showed: the country's m3u,
`providers.json` (+ its `.sig`), each provider's list (VieON pages), the categories file, `countries.json`. Measured on the TV emulator
(Vietnam, wall-clock from the start of `load`, 3 runs): m3u 0.2-0.4 s, providers list +0.4 s, provider channels +1.0-1.1 s, categories
+0.3-0.5 s, country name +0.07 s: **2.27-2.39 s** to the first card (a slow box and network took almost 5 s). Now: the m3u alone is shown
(**0.17-0.25 s**, 88 channels, no provider logos, the country as "VN"), then the providers (asked together, one thread each) and the
categories (beside them) land by 2.6-3.2 s and `show(..., first = false)` swaps the list in place without taking the focus. When every
file is kept (`Providers.warm`, fresh categories) nothing waits and it is one show, as before. The bar is determinate when Content-Length
is known (`Accept-Encoding: identity` on that GET: with gzip the bytes counted would pass the length); otherwise the spinner stays.
Throttling the emulator (`adb emu network speed`) delays the handshake, not the body, so the bar was seen by sleeping 250 ms per chunk
once (not kept); screenshot: the bar at 3 % of the 326 KB United States list.

## TV Channels: other countries' channels in a country's categories (`"also"` in `docs/channels/categories/<cc>.json`, `Categories.alsoFetch`, `GlobalIndex.byIds`)

- **Why Cartoon Network never showed on the Vietnam screen:** the screen reads `iptv/countries/vn.m3u` (88 streams, all `.vn`), and the categories only sort what that list holds. The `match` rules already named cartoon / hbo / espn / cnn, but no such channel was in the list. Measured 2026-10-24 on iptv-org's API: Cartoon Network has streams under `.us` (3), `.sg` Asia (1), `.in`, `.pk`, `.ae`, Latin America; none under `.vn`.
- **Design:** the file carries `"also": ["CartoonNetworkAsia.sg", {"id": "DW.de", "name": "DW"}, ...]` (iptv-org ids; no country in code). The TV app picks those ids out of `index.country.m3u` (3 MB, the search index it already keeps a day), keeps just them in `files/also-<cc>.tsv` (~12 KB, key = hash of the ids and names) and adds them to the country's list; the name rules then place them. Kept copy is read on the first screen (no network); the index is fetched after the screen is up and released unless a search holds it. One card per id (feeds merged, geo-blocked / flaky streams last), card name from `name` or the shortest name with "(United States)" cut (the index names "DW" as "DW Arabic", "Cinemax West", "BBC News UK HD"). The phone / computer / TV web lists get them through `Catalog.channels` (kept copy; the `list` broadcast fetches first).
- **Reachability, measured from Vietnam (my IP is VN) 2026-10-24, GET of each stream's first bytes, `#EXTM3U` expected:** 257 of 430 streams of ~250 foreign candidate ids answer. Ids kept all had at least one answering stream: Cartoon Network Asia, Cartoonito Asia, Boomerang.us, BabyTV, Nick Jr., Nickelodeon, Nicktoons, Disney Junior, National Geographic (+Wild), Animal Planet.us, Discovery Channel.in, Discovery World, TLC.in, History Asia, HBO.us, HBO Hits, Cinemax.us, Star Movies.in, ESPN, ESPN 2, Fox Sports 1/2, MTV, MTV Live, BBC News, CNN, CNN International, Euronews English, DW, France 24, NHK World-Japan, Arirang TV, Bloomberg TV, CNA. No answering stream (or none in iptv-org at all): AXN Asia, HBO Asia, Cinemax Asia, CBeebies UK, CBBC, BBC Earth, Discovery Channel US, Disney Channel (English: none; only Polish, German, Latin American feeds), BBC World News (not in iptv-org). Only the playlist was fetched, not playback; one card (Boomerang) was played on the emulator and shows video. Streams change daily; geo-blocked ones are flagged by iptv-org (`geo`) and sort last.
- **The two "VTV" rows could not be reproduced:** the shipped file has one `vtv` category and the emulator shows VTV (12), HTV, VTVcab, SCTV... once each. Most likely reading: "VTV" and "VTVcab" (which holds ON channels, no VTVcab ones). The row is now "VTVcab (ON)", and `Categories.parse` / `ChanCat.categorize` join categories with the same id or the same title so a file can never show one twice. A person's own group named "VTV" would also show as a second row (not changed: it is the person's data).
- **Not signed:** the categories file names channels only (a wrong name only moves a card), so there is no signature to redo. The site has no `channels/categories/vn.json` deployed yet (pages.dev answers the app's page for that path), so the new file reaches TVs on the next site deploy.

## TV Games / TV Setup D-pad + UI pass (2026-10-24, emulator tvremote_gtv3, 1080p, density 320)
- **Measured:** TV Games library cold start 570 ms (`am start -W`), TV Setup main 355 ms; Manage on the TV shows its first tab ~3 s after OK (WebView + `/api` calls on the emulator, not re-measured on a real TV).
- **The `.tabs` shadow was the clipped header:** `box-shadow:0 -9px 0 8px` reaches 9+8 = 17px above the bar, `main`'s gap is 14px, so it painted 3px over the TV-name card's bottom border on every client; now `0 -8px 0 6px` (14px up, nothing below).
- **`js/ontv.js` skipped the tab bar going Up:** the tab bar is in no `.card`, so "the same column of cards first" kept only the header's buttons; the tab bar is now taken when it is the next row. Back still goes to the tabs; closing a dialog now goes back to what opened it (`opener`), else the tabs.
- **Games search field:** a plain `EditText`, so Android's `focusSearch(RIGHT)` picked the grid's wide tile whose far edge is past the field's; `nextFocusRightId = itself`. (`TvField` in tvui has the same `focusSearch` path; Channels' search is not checked, left alone.)
- **Seen and left, with why:** Setup's system AlertDialogs (Optimize / Connection / Update) are Known drift #4; the selected filter chip being near-white is the documented chip look (§4); "Or choose an .apk file from this computer" and "drop them here" show on the TV's own Manage screen too (the file chooser exists there, the words are the computer's); web games' own title sits 30px from the top (docs/games/web, not in this pass).
- **ADB on the emulator:** TV Setup connects to 127.0.0.1:5555, so `adb -s <emu> tcpip 5555` is needed; the "Allow USB debugging?" asked with fingerprint CE:0F:40:D4:DD:68:E4:E5:A8:7A:9E:48:46:2D:34:D6, computed from `files/adbkey.x509` of TV Setup (MD5 of adb's public key blob) = the asking key, then allowed.

## TV Channels: VTV from VTVgo's free player (`Providers.Vtvgo`, `"vtvgo"` in `docs/channels/providers.json`)

- Why (measured 2026-10-24): all 12 VTV URLs in iptv-org's vn.m3u were dead. The `vips-livecdn.fptplay.net` links return 403 INVALID_TOKEN, and `dethich.pw` does not answer. VieON carries no VTV channel. TV360 only gives a guide: its streams need a browser token.
- Other apps were checked: the TV apps in the user's `ximao_tv` emulator. QuanTV reads a GitHub m3u with the same dead FPT links plus TV360 `bpk-token` URLs, which return 403. HongTV uses `livesct.vtvprime.vn` DASH, which has DRM. Others use `fptplay53.net` restreams, which are unofficial. None of these is used: no DRM keys, no restreams.
- VTVgo (vtvgo.vn) plays VTV free without an account, from its own API:
  - The channel list: `display/v21.0/channels/details?channelIDs=…`. The ids are listed in the JSON. `mustLogin` channels are left out.
  - The source: a POST of `live-channel/v21.0/playback/source` with `{"channelId","platform":"webPC"}`. It answers with HLS sources, about 4 h signed. Only `isVip` 0 and `drmTypeID` "none" are kept.
  - It is used exactly as the vtvgo.vn page uses it.
- Each answer gives 2–4 servers. On 2026-10-24, one (`vtvgolive-ssaimh`) answered 404 or timed out, so `play` checks each master playlist and takes the first that answers.
- Result: VTV2–VTV10, VTV5 TNB and VTV5 TN played on 2–4 servers each, with segments of 200 KB–1.2 MB. They were played on the FPT box from both the category and a saved group. VTV1 also stays on iptv-org.
- `"first": true` puts VTVgo ahead of iptv-org's stream of the same channel.
- Other ids found and not added yet: QPVN 103, ANTV 89, Hà Nội 1 111, Hà Nội 2 17, about 55 local stations (117–255).
- More channels from VTVgo (2026-10-24): all 57 other ids were played through master → variant → segment.
  - 32 played. The others were 404, 403, gone from DNS, or failed TLS (an incomplete chain, and 404 behind it).
  - 237 (Bình Định) shows Gia Lai's GTV since the provinces merged, so it is left out.
  - The list call takes at most 20 ids (21 or more: 400 TOO_MANY_CHANNELS), so `Vtvgo.list` asks in parts of 20. A missing part gives no list, so the last whole one is kept.
  - "strip" takes "Truyền hình " / "Đài PTTH tỉnh " off the names. With that, `merge` can match them, and "aliases" covers the rest (QPVN HD, An Ninh TV, HanoiTV1, Huế…).
- `merge` also matches a provider's channel to an earlier provider's channel, not only to iptv-org's.
  - Why: VieON already has about 28 of the same local stations. Without this, each one showed two cards.
  - A "first" provider goes ahead there too, so VTVgo is the first stream and VieON the backup.
  - Checked on the FPT box: of 164 VN cards, no name appears twice. Nghệ An, Bình Định/Gia Lai and QPVN played.
- What the other apps in the `ximao_tv` emulator show is not taken. Their extra channels are:
  - dead FPT links;
  - football restreams (cltvap.cc, chuoichientv.net, watchtivo);
  - film sites (phim.nguonc.com, saigonphim.net).
- Saved channels (favourites, groups) keep the streams they had when saved. `BrowseActivity.live` puts the country's current streams of the same id first, so a provider added later reaches them too. Before this, a saved "VTV" group stayed greyed out with only the dead FPT links.
