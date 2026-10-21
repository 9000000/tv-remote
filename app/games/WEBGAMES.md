# Web games (dev notes)

A web game is a page the TV's WebView loads full screen (`WebGameActivity`). Phones join through the same QR as for NES games.

## Add a link
- Phone/computer page `/add` -> "Thêm link game" (name + URL), or `POST /weblink?k=<token>&name=..&url=..` (https, or http on the LAN). Kept in `files/web.json`; long-press a WEB tile to remove it.
- Built-in: `assets/webgames/quiz/` is served at `/webgames/quiz/` (first request with `?k=` sets cookie `tvk`, path `/webgames/`).

## Three ways a page can read the pads (use any)
1. **Keyboard (player 1 only).** D-pad = arrows, A=Z, B=X, X=A, Y=S, L=Q, R=W, L2=E, R2=R, C=C, D=V, START=Enter, SELECT=Shift_Left. The pad's stick also sends arrows for player 1. The TV remote works natively.
2. **Gamepad API (every player, index = slot).** Standard mapping: A0 B1 X2 Y3 L4 R5 L2 6 R2 7 SELECT8 START9 UP12 DOWN13 LEFT14 RIGHT15, plus C17 and D18. Axes 0/1 = the stick, or derived from the D-pad when the stick is idle. `gamepadconnected` / `gamepaddisconnected` fire on join / leave.
3. **`window.tvpad`** (also gives per-player layouts):
   - `tvpad.on('join'|'leave'|'button'|'text', fn)`: join/leave `{player}`; button `{player, button, down}`; text `{player, text}`.
   - `tvpad.players()` -> list of connected players.
   - `tvpad.layout(player | 'all', {buttons:[..], choices:['A','B','C','D'], text:true, dpad:false})` sets what the phone shows (`null` resets).
   - `tvpad.vibrate(player, ms)`, `tvpad.say(player, text)` (a line on the phone).

## Pad look
The TV sends the system on join and when the game changes: `nes`, `snes`, `gba`, `ps` (table `SYS` in `assets/pad/pad.js`, keyed by `Sys.pad` in `Systems.kt`), `web` (= ps look unless `layout` says otherwise). The left side is a floating analog stick or a D-pad (menu switch, kept per look). Analog goes to libretro analog, to Gamepad axes 0/1 for web games, or to 8-way D-pad keys (dead zone 0.35) for cores without analog.

## Safety
The bridge (`TVPadNative`) and the shim are injected only for the game's own origin (`addDocumentStartJavaScript`, origin checked again in each call). The start card shows the page URL.
