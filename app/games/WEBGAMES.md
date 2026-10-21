# Web games (dev notes)

A web game is a page the TV's WebView loads full screen (`WebGameActivity`). Phones join through the same QR as for NES games.

## Add a link
- Phone/computer page `/add` -> "Thêm link game" (name + URL), or `POST /weblink?k=<token>&name=..&url=..` (https, or http on the LAN). Kept in `files/web.json`; long-press a WEB tile to remove it.
- Built-in: `assets/webgames/quiz/` and `padtest/` are served at `/webgames/<name>/` (first request with `?k=` sets cookie `tvk`, path `/webgames/`); `_lib/` holds files shared by the pad page and the games (`pingmath.js`).

## Three ways a page can read the pads (use any)
1. **Keyboard (player 1 only).** D-pad = arrows, A=Z, B=X, X=A, Y=S, L=Q, R=W, L2=E, R2=R, C=C, D=V, START=Enter, SELECT=Shift_Left. The pad's stick also sends arrows for player 1. The TV remote works natively.
2. **Gamepad API (every player, index = slot).** Standard mapping: A0 B1 X2 Y3 L4 R5 L2 6 R2 7 SELECT8 START9 UP12 DOWN13 LEFT14 RIGHT15, plus C17 and D18. Axes 0/1 = the stick, or derived from the D-pad when the stick is idle. `gamepadconnected` / `gamepaddisconnected` fire on join / leave.
3. **`window.tvpad`** (also gives per-player layouts):
   - `tvpad.on('join'|'leave'|'button'|'axis'|'ping'|'text', fn)`: join/leave `{player}`; button `{player, btn, down, lat, at}`; axis `{player, x, y}` (the stick, -1..1, y down); ping `{player, ms}`; text `{player, text}`.
     `lat` = the press's delay phone -> TV in ms (the phone stamps each press with its clock, the TV moves it by the clock offset measured in the ping exchange; `null` while not known), `at` = `performance.now()` when the page got it.
   - `tvpad.ping(player)` -> the phone's round trip to the TV in ms (median of its last 5, measured every 2 s), -1 = not known yet.
   - `tvpad.players()` -> list of connected players.
   - `tvpad.name(player)` -> the name the player typed on the phone (max 16 characters, emoji ok), `''` while it is the default: word "Player N" yourself. `join` events carry `name`; a `rename` event `{player, name}` fires when it changes (`''` = back to default).
   - `tvpad.joinUrl()` -> the link phones open to join (the QR's), `tvpad.qr(px)` -> that QR as a PNG data URL (no smoothing when scaled). Show it while `players()` is empty.
   - Phones keep their slot and name across reconnects and TV Games restarts (the link's code is saved; "Đổi mã kết nối" in Add game rotates it, old phones then ask to scan again). If the TV's IP changes, phones must scan again.
   - `tvpad.layout(player | 'all', {buttons:[..], choices:['A','B','C','D'], text:true, dpad:false})` sets what the phone shows (`null` resets).
   - `tvpad.vibrate(player, ms)`, `tvpad.say(player, text)` (a line on the phone).

## Pad test
`assets/webgames/padtest/` ("Pad test" / "Kiểm tra tay cầm" in the library) is the sample for the events above: it draws the phone's pad for every player, lights a button the moment its event arrives, and shows `lat` (last, median of 30, max, count) and the ping. Select + Start together reset the numbers.

## Navigate mode (the phone's pad page, not a web game's API)
Outside a game the pad shows a remote (D-pad, OK, Back, "TV Games" = back to the library) and the TV Games screen in front gets those keys (`GameServer.nav`: the key is dispatched to the resumed activity, an unused D-pad key moves the focus). In a game the pad shows the game's layout; the top bar's 🎮 / 🧭 button flips between the two (Back in navigate mode opens the pause card; its Resume / Quit are chosen with the same keys). The TV tells the phones when a game starts or ends (`{"game":bool}`) and the pad follows.

## Pad look
The TV sends the system on join and when the game changes: `nes`, `snes`, `gba`, `ps` (table `SYS` in `assets/pad/pad.js`, keyed by `Sys.pad` in `Systems.kt`), `web` (= ps look unless `layout` says otherwise). The left side is a floating analog stick or a D-pad (menu switch, kept per look). Analog goes to libretro analog, to Gamepad axes 0/1 for web games, or to 8-way D-pad keys (dead zone 0.35) for cores without analog.

## Safety
The bridge (`TVPadNative`) and the shim are injected only for the game's own origin (`addDocumentStartJavaScript`, origin checked again in each call). The start card shows the page URL.
