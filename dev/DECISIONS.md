# Decisions worth remembering

Hard-won facts behind code that looks odd. Each: what was seen, what was decided. Add to it when a bug took a day.

## Picture

- **Measure lag at the sender, by the oldest picture packet's age** (`picture/CatchUp.kt`). Kernel buffers hid a backlog of
  tens of seconds; the video socket's `SO_SNDBUF` is 64 KB so the backlog stays in our queue, where it can be seen.
  Past 600 ms: drop frames up to the newest key frame, or drop all and ask scrcpy for one (`RESET_VIDEO`, 17).
- **Only picture packets count.** Checking `q.peek()` stalled catch-up for good once a "caught up" notice (kind 5) sat at the
  head. One notice per stall: a 30 s stall once gave 20 of them.
- **WebCodecs only in a secure context**: the computer app (127.0.0.1) and the phone app's Manage (127.0.0.1 in its WebView)
  have one; the TV web page over LAN http does not and keeps JMuxer. Chrome on loopback never fell behind (big buffers);
  `fellBehind()` was tested directly (960 → 720, picture kept playing).
- **The picture steps back up** (`picNext` in `js/screen.js`): 30 s without a catch-up notice = one size up, never above the person's pick
  (`picChosen`, set by the level dialog only). Fell behind again within 60 s of a step up: the next wait doubles (cap 8 min). The
  phone app's native screen has no downsizing, so nothing to mirror there.
- **"Nhịp hình: Đều hơn" (33 ms pacing) is off by default.** With Wi-Fi bunching (bursts every 150 ms) off drew ~78% of
  frames, on ~98%; the rest of the unevenness is the TV's own (gaps up to ~300 ms). Not worth 33 ms for everyone.
- **The JMuxer path's delay (~60-140 ms in Chrome at 10 fps, ~140 ms at 10 fps and ~180 ms at 30 fps in WebKit) is not ours to
  cut from JS; nothing was changed.** Measured with `?jm=1&stats=1` (JMuxer on the computer app's page; `window.JT`: arrival ->
  shown per frame through `requestVideoFrameCallback`, `waiting` events, gaps between shown frames), TV emulator, D-pad keys
  for motion, 15-25 s per run, Chrome and Playwright WebKit. Arrival -> shown, median / p90 ms:

  | | Chrome 10 fps | Chrome 30 fps | WebKit 10 fps | WebKit 30 fps |
  |---|---|---|---|---|
  | as is (4-9 runs) | 50-147 / 100-185 (mean ~100) | 3-16 / 38-70 | 135-186 / 158-225 | 169-195 / 295 (8-9 gaps > 400 ms) |

  The Chrome figure has two states that a run falls into: the newest frame shown at once (~55-90 ms), or one frame held back
  (`buffered.end - mediaTime` of the shown frame = 2 frames, ~140 ms). Tried, all in Chrome unless noted:
  - **Seeking to the live edge** at 1.5 frame intervals (from the rVFC callback) or at 20-40 ms behind (every 40 ms): no gain
    in the held-back state, and the seek decodes again from the last key frame: freezes of 0.8-1.7 s (`gapMax`), up to 10 per
    run. In WebKit `buffered.end` never comes below 2 frames ahead, so a seek has nothing to catch up to.
  - **`playbackRate` 1.5 / 2 while a newer frame is buffered**: p90 about 130 vs 145 ms over 9 runs each, median the same;
    within the noise between runs (the TV's own pacing moves them more), so not kept. Same in WebKit (125-208 ms).
  - **Shorter frame durations** (10-50% of the interval: the clock reaches the end at once and the video stalls on every frame):
    median 105 vs 100 ms over 6 pairs. No gain.
  - **SPS rewritten with `max_num_reorder_frames 0`**: scrcpy's SPS (Baseline 4.1) already has the VUI restriction (reorder 0,
    `max_dec_frame_buffering` 1; `ffmpeg -bsf:v trace_headers`), so there is nothing to tell the decoder.
  - `flushingTime 0` was already set; `maxDelay` only runs when `flushingTime` is not 0; Chrome and WebKit have no
    `latencyHint` for MSE. The 300 ms edge check never acts because the buffer never grows that far; lowering it is the seek case.
  The hold-back is the browser's own video pipeline. Where it matters, WebCodecs (14 ms) is the answer, and it needs a secure
  context: serve the TV web page over https if the delay ever has to go.
- **The phone app needs no key frame request after the screen comes back**: the surface is recreated, the session restarts,
  and scrcpy opens every session with a config and a key frame.

- **Where the picture's delay goes (measured on the TV emulator, computer app, Chrome on loopback; `TVR_TRACE=file` on the
  server and `?stats=1` on the page log every stage).** TV capture to server 30 ms median / 60-77 p90; server queue 0 ms;
  server to page 1 ms; WebCodecs decode 2-3 ms + wait for the next frame draw 10 ms: server to painted 14 ms median / 22 p90.
  JMuxer (TV web page) adds about one frame interval on top: 124 ms median at 10 fps, 51 ms at 30 fps (WebCodecs: 14 / 12).
  So the browser and server are not where the time is: the frame rate (10 fps = up to 100 ms before a change is even sent) and the
  TV's encoder are. Start of a picture: 0.93 s median, of which app_process 0.55 s, encoder start 0.23 s, ADB shells 0.13 s; the
  jar is pushed only when its size differs (cold push +0.26 s). Emulator numbers: only the page and server stages transfer.

## Waits

- **Waits and watchdogs use `System.nanoTime()`**, never the wall clock: a laptop lid closed mid-wait, or the TV setting its
  clock from the network, ended waits early. The page restarts picture and sound once after the device slept (`afterWake`).

## Network

- **Android VPNs with `bypassable=false` (Tailscale, exit node on) make `Network.bindSocket` fail with EPERM**: going around
  the VPN on the Wi-Fi only works with bypassable VPNs. With Tailscale the TV's own 100.x address works anyway (tested on a
  real phone with an exit node). Otherwise the error says to allow the LAN in the VPN app (`Lan.BLOCKED`).
- **Every length read off the wire is bounded** (fuzz tests, `ParserFuzzTest`): a WebSocket length ≥ 2^63 went negative and
  passed the check; SSDP descriptions were read without limit from anyone on the Wi-Fi.

## Browsers

- **The page needs Chrome 80 / Safari 14** (`?.`, `catch {}`, object spread: hundreds of uses). An Android 8 WebView never
  updated (Chrome 58) showed empty tabs. `js/old.js`, in ES5, shows "update Android System WebView" instead.

## The TV's "Allow USB debugging?"

- **Questions queue, the dead ones too.** One TV Setup start left about 20 questions for its own key, all for connections already
  closed ("authorization received for deleted transport"). Before answering one: TV Setup's own fingerprint is
  `am broadcast -n dev.tvremote.setup/.TrustReceiver --es want adbkey`; the trusted keys are `user_keys` in `dumpsys adb`
  (MD5 of each base64 blob). Never Allow a key you cannot match.
- **On the Google TV emulator the "Set up Google TV" reminder hides the question**, and TV Setup steps aside for a question it
  cannot see. Bring the question up (`cmd activity stack remove <its task>`, then start TV Setup again).

## ISP boxes that lock Developer options (FPT Play, Viettel)

- **FPT Play 650** (Hisense IP940N, Android 14, measured 2026-10-13): tapping Build 7 times asks "password for ADB". No public
  password (a Voz thread for the Box S: 0000 / 1234 wrong). Got ADB without it:
  1. Pair the Android TV Remote protocol (6-character code on screen): keys work, no ADB needed.
  2. Send TV Setup's download link as a link over that protocol: the box asks "Open with" → Downloader (Downloader itself from its
     Play Store page: the box refused opening apps by package name).
  3. Allow Downloader to install unknown apps → Install → open TV Setup.
  4. TV Setup starts `ACTION_APPLICATION_DEVELOPMENT_SETTINGS` itself (`app/tv/.../setup/MainActivity.kt` ~684): the screen opens
     without the guarded 7 taps. Turn on **USB debugging** only (Wireless debugging is not used) → port 5555 → "Always allow" per device.
- Settings intents sent from outside (over the remote protocol) were ignored by the box: it has to be an app on the box that opens it.
  This is the "Bật ADB giúp tôi" flow / Downloader code (README.vi.md "Bước này không bắt buộc").
- **Viettel box** (user report, 2026-10-24, not tested here): after the taps the whole box showed a lock screen with an ISP QR and a
  password. So for ISP boxes: do not tap Build; install TV Setup first and let it open Developer options.

## Start-up speed (computer app, TV emulator, `TVR_TRACE`)

- **One shell call for the start** (`Tv.startFacts`): wake, model, SDK, `ip addr`, the jar's size and MD5 were 3-5 calls, ~127 ms
  from "ADB ready" to "jar checked"; now one, 55-120 ms. The jar is compared by MD5 (a changed jar of the same size was never
  pushed; tested by overwriting it with zeros of the same size: pushed again). No `md5sum` on the TV: falls back to the size.
- **The socket poll is 25 ms for the first 60 tries**, then 100 ms (until 20 s, by `nanoTime`). A refused `localabstract:` open
  costs the TV nothing. app_process to a listening scrcpy takes 380-480 ms here (13-16 tries).
- **Click to first key frame, 10 cold starts each (HTTP request to first kind-2 packet; the emulator is bimodal, ~750 ms or
  ~2 s, so best and median both shown):** picture before 729 / 832 ms, after 649 / 882; sound before 745 / 1066, after 755 / 839;
  both at once before 748 / 1805, after 796 / 865 (the "before" runs had a slow stretch of the emulator, so read the best).
- **Sound and picture stay two sessions.** scrcpy fixes `audio=` when it starts; adding sound to an open picture means a
  second app_process or restarting the picture. Capturing sound by default would silence the TV's speakers. A joined session
  would only help when the page asks for both in the same moment (after waking), which needs both fetches to name each other.

## Reading the TV's state over ADB

- **Is someone about to type on the TV** (`core/adb/TvKeyboard.kt`): `dumpsys input_method --proto` is ~450 B and 0.13 s on
  the FPT box (Android 14); the text dump is ~545 KB and 2.4 s there (Gboard dumps its own state into it), which made the phone's
  typing box open 3-4 s late. Android 9 (Bravia) has no proto: the first try costs ~4.5 s, then the reader keeps the grep'd text
  dump (0.86 s) for good. Signal: a focused field is the last `inputType=0x.. … fieldId=` line (the system's StartInput history;
  Gboard's own lines have no fieldId), not only `mInputShown`: a TvField does not raise the keyboard on focus.
- **Injected keys and taps do not reach "Allow USB debugging?"**: the person at the TV must press Allow. A test client
  connected to a real TV pops that dialog there unannounced, so ask the owner first.

## The apps leaning on each other

- **TV Home has no ADB**: its power button (screen off, standby, power off) asks TV Setup through the `HomeInfo` provider
  (signature-checked) with one of three fixed words, never a command line (`setup/HomePower.kt`). When TV Setup's service
  is not running, HomePower opens a link of its own on the same key and closes it after; the screen-off helper is
  `nohup setsid`, so it outlives that link.
- **A feature that needs another app stays visible and says how to get it** (Downloader code or "install from the phone"),
  at the point of use only: no banners. Hiding it reads as a bug. The map of who needs whom: `docs/` apps page.
- **"Open on TV" sends a media link (.m3u8, .mp4 … by the path's extension, `Channels.isStream` = `js/remote.js isStream`)
  to TV Channels' player**: most TVs have nothing that opens a bare stream. Without TV Channels it goes as any link, with a hint.
- **Heavy-app auto stop is opt-in** and never touches music / background audio apps or the keep list: people listen with the
  screen off. `sharedUserId` to share one process between our apps was rejected: adding it to shipped apps breaks updates.
- **Key shortcuts and "open an app at power-on" (TV Home settings, default off) are done by TV Setup, pushed by TV Home.** TV Home has no
  ADB, no key filter, and does not run in standby, so it sends the whole setting (`keyon/keyapps/autoon/autoapp`) through the
  signature-checked HomeInfo provider (method "keys") on every change and start; TV Setup keeps it (`HomeKeys`, prefs) and works after a
  reboot with TV Home not up. The DUMP-protected Rules broadcast was not used: apps cannot call it. Without TV Setup the keys work on
  TV Home's own screen only, and the setting says so.
- **Only free keys can be given an app:** colour keys, keys whose global component is dead (`KeyFix.plan`, same list in `HomeConfig.KEY_DEAD`,
  a test keeps them equal) and, on TV Home's screen only, 0-9. A working key is never taken. `input keyevent` over ADB is injected after
  the accessibility filter, so the service cannot be tested with it; on the TV emulator `adb emu event send EV_KEY:KEY_RED` also does not
  reach it (the emulator has no such key in its layout path), so the service path was checked by its logged plan only.
- **A key a client sends is decided by TV Setup, not injected:** measured on the TV emulator (5554, RED given an app, 178 dead): `input keyevent`
  183 / 178 while Settings was in front did nothing, so a phone / computer that injects a colour key or a dead YouTube / Netflix / Prime /
  All apps key reaches no one. With ADB these keys (`RulesClient.SETUP_KEYS`) go as a `RulesReceiver` broadcast `{"do":"key","code"}`; TV Setup
  answers `{"acted"}` after `KeyFix.decide` (TV Home's map laid over the dead keys' rules, the same plan the filter uses) and opens the app; not
  acted, an old TV Setup (answers "unknown action") or no ADB: the plain key as before, no error. The TV web page decides in-process. LIVE / INPUT
  stay on their old path (an extra broadcast ~0.3 s on every press for a plan that is mostly empty).
- **Power-on app fires once per power-on, by a 120 s guard** keyed on (boot count, elapsedRealtime): the boot and the screen coming on right
  after it are one power-on. It looks at the app on screen (UsageStats) and keeps it when the TV woke into another app (HDMI-CEC, cast).
  Home pressed later never re-fires: the trigger is a power event only. No `SetupNeed.MIN` entry: the setting asks TV Setup itself and
  says "install TV Setup" when it gets no answer; the first TV Setup with "keys" is the next one.

## The error guard (`app/shared/src/guard`, `js/errbox.js`)

- **Why:** a NullPointerException in `PhoneRemote.remoteMenu` from one menu tap killed the whole phone app, and nothing said what
  broke. Goal: no crash, and when something fails the error is on the screen, easy to copy.
- **The main looper is kept alive** (`Guard.install` posts `while (true) { try { Looper.loop(); break } catch ... }` to the main
  thread): a throw inside a click, a posted job or a callback ends only that message, the screen is still drawn and the person
  sees a dialog (title, first 6 stack lines, Copy / Share / Close; the TV's D-pad starts on Close). Other threads: saved and
  shown, the process lives.
- **It is NOT kept alive** when the stack has an Activity lifecycle frame (`ActivityThread.perform*/handle*Activity`,
  `Activity.performCreate`, `TransactionExecutor`; `GuardLogic.inLifecycle`, causes included): an Activity that threw in
  `onCreate` / `onResume` stays half built, a blank screen nothing ever draws into, and the looper would carry on with a window
  that cannot work. There the trace is saved, the app dies as before, and the next start shows "Lần trước app bị lỗi" once
  (`guard-died` flag + `guard-last.txt` in filesDir). Also left to die: 10 throws within 2 s on the main thread (the loop is not recovering).
- Same error again while the dialog is open: counted ("repeated n times"), never a second dialog. The web page does the same
  (`ErrBox`), and ignores resource load errors, the cross-origin "Script error." and the ResizeObserver notice.
- Checked on the emulators (phone, TV Home): a throw on the main and on a background thread left the pid unchanged and showed
  the dialog. A debug-only trigger stays in `Guard.install` (`am broadcast -a dev.tvremote.GUARD_TEST --es on main|bg`, only when
  the APK is debuggable). Native crashes (SIGSEGV) and an ANR are not caught: no JVM exception.


## Hold to use Input / Settings (HoldKeys)

- **The rule is `holdkeys` in `Rules` (not a KeyFix pref)** so every client switches it the same way: `/api/rules {do:"holdkeys",on}` (TV web page,
  computer, phone Manage screen: the card under "one heavy app" in Health), and the TV's own Optimize TV menu. A change calls `KeyFix.sync` +
  `KeyFixService.poke`; `sync` keeps the accessibility service on while a dead key needs it OR this is on (`need = wanted && plan || hold`), off
  when neither. `Rules.active` is true while it is on, so `KeyFixWatch` (in RemoteService) keeps re-adding the service if the system drops it.
- **Time, not repeats:** the key filter sits before the dispatcher's auto-repeat, so it sees a down and an up and no repeats. A press is armed on
  its down (hint toast at once), `HoldKeys.Gate.timer` fires once after 2 s if the key is still down; repeats (a remote that sends them) with
  `eventTime - downTime >= 2 s` fire too, never twice. Down and up of a short press are both eaten; a lost up does not stick (a down with repeat 0
  is always a new press). The hold does what the key does when it is dead (`KeyFixService.plan`, TV Home's key → app included), else
  `KeyFix.RULES` (input picker / Settings); the direct HDMI keys (243-251) land on the input picker, not on their HDMI (no unprivileged way to switch).
- **Keys covered:** 170 TV, 178 TV_INPUT, 176 SETTINGS, 243-251 TV_INPUT_HDMI_1..4 / COMPOSITE / COMPONENT / VGA. A client's `input keyevent` and
  the scrcpy keys skip the filter, so they act at once: the phone / web remote is the escape hatch (said in the switch's sub text).
- **Measured on the TV emulator (5554), the real physical path:** `adb emu event send EV_KEY:KEY_TV` is dropped (the emulator keyboard has no such
  key bit; `EV_SYN` has no code names), `sendevent` needs root (none), `input keyevent` skips the filter. What does reach the filter: the shell is in
  group `uhid`, so `/dev/uhid` is writable: create a HID consumer device (usage 0x0C/0x89 = KEY_TV = Android 170, the Generic.kl map) with one
  `UHID_CREATE2` and `UHID_INPUT2` events, each written by `dd bs=<exact event size> >&3` on ONE open fd (`cat` splits the write and gets EPERM;
  closing the fd removes the device). Script: build the structs with python `struct` (type u32 11 / 12), push, `exec 3>/dev/uhid`.
  Only 170 is reachable this way (Generic.kl has no line for 176 / 178). With it on: a 0.5 s press showed the toast and left Home in front; a 3.5 s
  press opened the app TV Home gave key 170 (TV Games) at 2 s; switched off, a 0.3 s press opened it at once.
- Not covered: a TV whose firmware handles Input before the Android input pipeline (some boxes' own OSD): the filter never sees that key.

## TV Home QR cards

- Encoder: Qr.kt / qr.js now do versions 1-10 (271 bytes, level L, mask 0; blocks interleaved, alignment patterns, version info). Every length 10-271 decoded with zbarimg; a Wi-Fi line with a long password or a menu link did not fit in v5 (106 bytes).
- Model: config `qrs` = {qr1..qr4: {tpl, f: fields, cap, show}} and the keys are ordinary "widgets" (order, size, off for free). The TV builds the payload from the fields (HomeQr.payload), JS mirrors it (home_qr.js); both are held to tests/home_qr_cases.json. A template is only a form; "text" is the raw payload.
- The Wi-Fi password lives only in TV Home's config on the TV (and comes back to a paired client so the card can be edited). WireLog.clean blanks `WIFI:...;;` and "pass" values. Shown as text only with `show` (default off).
- Drawing: white card, dark modules at whole pixels, 2-module quiet zone plus padding, whatever the theme. Measured on emulator-5554 (1920x1080): two cards, each ~260 px code; zbarimg read both from the screenshot.

## Check-out (Trả phòng) for homestays and hotels

- Where: "Several TVs" on the phone (`ManyTvs.kt`) and the computer (`Fleets.checkout`, `/api/fleet/checkout`, `js/checkout.js`). One run per TV: `pm clear` of the chosen apps, volume, forget the browsers paired with TV Setup's web page, Home. The chosen apps and volume are remembered (computer: `tvs.json` "checkout"; phone: prefs "checkout"); never chosen = the suggested apps that are installed (`Checkout.SUGGEST`).
- Block list in code (`Checkout.blocked`, tested): `Setup.mustKeep` (system, launchers, Play, our apps), our extra patterns, `Bloat.factory`, and at run time the TV's current home app. The page and the phone hide them; the server cleans the list again.
- The list is the TV's own apps (`pm list packages -3`) plus the suggested ones that came with the TV (`Checkout.here`): a YouTube or Netflix preinstalled as a system app is not in `-3`, and is the app a guest signs in to. `pm clear` works on system apps.
- Forgetting browsers goes through `RulesReceiver` (`{"do":"forget_web"}`, DUMP: only ADB), not `HomeInfo` (for apps, signature-checked). It drops every "web" pass (a shared QR's too), keeps the owner's "app" passes (Phone / Computer), clears the pending code and invite. A stream a forgotten browser already has open is not cut (it ends with the page). Needs TV Setup 0.19.1 (`SetupNeed.MIN["checkout"]`); an older one is said on that TV's line and the other steps still run.
- Measured on emulator-5554 (TV Setup 0.19.0 and 0.19.1), run from the phone: `ActivityManager: Force stopping com.google.android.youtube.tv ... clear data`; volume 3 -> 9 (60%) and 2 -> 4 (25%, by the keys: the emulator keeps `media_session` volume); a browser paired before got 401 afterwards on 0.19.1 and the old-setup warning on 0.19.0.
- Network: guests on the same Wi-Fi can reach port 8765 of every room's TV, and the pairing code shown on the TV is the only gate. For a business use a separate network or VLAN for the TVs, or client isolation on the access point.
- Not done: a daily time (e.g. 12:00) for the check-out. It needs something always on (the computer app running, or a rule on the TV); asked for later, not built now.

## Live tiles in TV Home's app grid (TV Setup provides them)
- A tile is another app's small live view drawn in TV Home's grid at app-tile size; pressing it runs that app's action. Key `tile:<provider pkg>/<id>` (`HomeConfig.tileKey/isTile`), kept in config `tiles` (on the grid) and usable in dock / hidden / order like a package name. Hooks: `app/home/.../Tiles.kt` (`available`, `fetch`, `run`, `press`, `report`; no view code) and TV Setup `HomeTiles.kt` + `HomeInfo` methods `tiles` / `tile` / `tile_do` (signature-checked, same as the other methods; old TV Setup answers null = "missing", another key = "untrusted").
- Binder calls never wait on ADB: `tile` reads `/proc/meminfo` (allowed for apps), `tile_do` starts a thread and answers `{started}`; TV Home follows with `tile` every 350 ms until the mode leaves `working`. While on screen TV Home reads each tile every 5 s (`tileTick`, removed in onPause/onDestroy).
- Free RAM closes only what `ps -A -o NAME` shows as installed packages, minus `Setup.mustKeep`, the app on screen, launchers, enabled keyboards, accessibility services and `android.*`; it uses per-package `am kill`, which Android only applies to cached processes (a second lock). Android 7 without `ps -A`: falls back to `am kill-all`.
- Honest numbers: before/after are used% from `/proc/meminfo` 1.5 s after the kill; under 16 MB freed is shown as "nothing more to free". Part of the freed RAM returns as services restart: the tile reports the moment, not a promise.
- Low-RAM TV Home (calm motion) skips the count-down animation and just changes the number.
- Not done: a tile bigger than one app tile, the shared "Add to home screen" browser (Customize has a minimal on/off list).
- Layouts (2026-10-23): a tile has `layout`, default `gauge` (the ring above); `next` (TV Channels' "Keep watching": logo, channel, now / next from `Guide.kt`) and `toggle` (TV Setup's "Sleep timer": a pill with the value, a press cycles 30 -> 60 -> 90 -> off). Why layouts, not more `kind`s: the face only needs to know how to draw, and a provider's JSON stays the same shape (title, big, small, mode, tone), so a third provider needs no TV Home release unless it wants a new look. Unknown layout = gauge.
- TV Channels is a second provider (`content://dev.tvremote.channels.home`, same `tiles` / `tile` / `tile_do`). The three apps are signed with three keys (`app/*/signing.jks`), so both sides pin the other's release certificate: the provider answers only TV Home (pinned digest, or its own key = debug builds, which all use one key); TV Home asks a provider only when that app's certificate is the pinned one (or its own), else the tile says "from another source". Before this TV Home trusted whatever owned the authority; a first-installed impostor could have answered.
- A press that opens a screen: Android 14 stops a background app starting an activity from a binder call, and the provider is in the background. So `tile_do` of "next" only says `{started}`; the state carries `open` (a link) and TV Home, the app in front, starts it with `setPackage(provider)` (it can never send the person to another app). Nothing watched yet: `open` is `tvchannels://find`, the channel list.
- The sleep timer tile writes Rules directly (`Rules.change`, the same call the remotes make), no ADB; a press reads the minutes left and picks the next step above them, so a 60 min timer that has run down to 25 min goes to 60 again, never backwards; set longer than 90 elsewhere, a press switches it off.
- The last channel is kept whole (`Store.lastChannel`, name, logo, streams) because the id alone gave a name only after reading a country's list; the guide is read on a thread and shows from the next 5 s read (`Guide.known`).

## TV Home "Manage from phone" (its own settings page)
- One QR in Customize opens TV Home's settings in the phone's browser; the UI is TV Setup's `home.js`, with one source: Gradle `packHomeWeb` copies the needed js/css, an i18n subset (`home.` `homeweb.` `common.` `core.` `apps.`) and home.html (icons sprite from index.html) into `assets/homeweb`. TV Home still has no :shared and no dependency on TV Setup.
- `home.js` asks `homeLink` (js/home_link.js): host = `/api/home*` over ADB, direct = TV Home's `/h/*` (config, photo, photo/drop, app/meta, app/icon). Same HomeConfig rules run on the TV either way (`HomeApi`, shared with `HomeReceiver`). Direct has no wallpaper gallery and no update button (null routes), and `dream` still needs the ADB grant (SecurityException: applied without it).
- `ManageServer` (ServerSocket, port 8790 or any free) runs only while the QR screen is open. `Access`: key in the QR works once, then a cookie session (60 min, HttpOnly, Strict); no session = 401; 30 wrong keys void the key; posts need `X-Tvh`; the server stops after 10 min with no authorised request (one Handler timer, the page does not poll) or on close. Clocks are `nanoTime`.
- Replaced `PhotoServer`/photo.html: the photo and web-page QR screens now use the same server and page.

## One shared UI set for the TV apps (`app/shared/src/tvui`)
- Why (2026-10-23): the four TV apps copied "the same" row, dialog sheet, panel, pill and tv_style.xml by hand, and they drifted: the RAM tile shipped with its own hand-built frame, dialogs and tiles were rebuilt per screen. A text rule (DESIGN-TV.md "copy the values") did not stop it, so the components now exist ONCE and an app calls them.
- `Tv.kt` (`TvUi` tokens + row / sheet / button / field / `TvState`, and the Games-style `panel` / `scrim` / `menuButton` / `pill` extensions) and `res/values/tv_style.xml` are compiled into TV Channels and TV Games (`srcDirs` / `res.srcDirs`). Components take words and drawable ids, never string ids: strings and generated icons stay per app. The tokens are Kotlin constants (the file cannot name an app's `R`); `tv_design.test.js` checks them against tv_style.xml and fails when channels / games define a component tvui has.
- Phase 1 moved Channels and Games. Phase 2 moved TV Setup (no own `tv_style.xml`, `BG/CARD/INK/ring()` are TvUi's) and TV Home: its tile focus (ring, grow, glass, `Motion`) became tvui's `TvTile` / `Glass` / `Motion` so a tile looks the same in every app, and its glass panel became `TvGlass`, which the long-press app menu now uses (it was a flat AlertDialog).
- The System card read RAM from `ActivityManager.availMem` (cached apps counted as free: 64 % vs the RAM tile's 83 %). It reads `/proc/meminfo` MemAvailable now, as TV Setup does; both colour by `HomeConfig.level` (ok < 70, warn 70-84, bad 85+), one function, with a test that `HomeTiles.accent` agrees. The web preview shows no levels, so it needed nothing.
- TV Channels' fields are `TvField` now: the keyboard no longer rises by itself on focus (`raise()` does it where a field opens in front of the person: search, a name); OK raises it elsewhere; the D-pad can leave the field.


## TV Setup declares no accessibility service (TV Setup Keys add-on)
- Why (2026-10-23, measured by a user on a Sony Google TV in Vietnam): installing TV Setup through Downloader was hard-blocked by Google Play Protect's "enhanced fraud protection" ("app blocked to protect your device… sensitive data… financial fraud", only an "I understand" button). Cause: the manifest declared two `BIND_ACCESSIBILITY_SERVICE` services (`KeyCatch`, `KeyFixService`). Play Protect blocks an app that declares accessibility (also a notification listener / SMS) when it is installed from an internet-sideloading source (browser, Downloader, file manager); an install by `adb` / the shell is not that source. A README line telling people to press "Install anyway" cannot work: there is no such button.
- Fix at the root: both services moved into the add-on `app/tvkeys` ("TV Setup Keys", `dev.tvremote.setup.keys`). `tv-debug.apk` has no `BIND_ACCESSIBILITY_SERVICE` (checked with `aapt dump xmltree`, and `KeyFixTest.noAccessibilityServiceInThisApp` keeps it so). The add-on's APK is packed inside tv-setup.apk (`assets/tvkeys.apk`, gradle `packKeys*`, no network, one version) and TV Setup installs it with `pm install` over its own ADB link (`KeysAddon.kt`, the path `KeepAdb.grant` / `KeyFix.prepare` already use) when a feature needs it: `KeyFix.sync` (a dead key to bring back, or the hold rule) and `Screen.java` (the key that wakes the screen asks `RulesReceiver` `do: keys_addon`). A different version (TV Setup updated, or rolled back: the newer add-on is uninstalled first, `pm` refuses a downgrade) is put right on the next `sync`. The add-on is protected from the Apps tab like every `dev.tvremote.*` app.
- **Thin on purpose:** the add-on holds the services and nothing else. `KeyFixService` there asks TV Setup's `KeysBridge` (a bound service guarded by the signature permission `dev.tvremote.setup.permission.KEYS`, so only the same key binds) about each key, with a synchronous binder call, and does what it answers (taken / pass). KeyFix.plan/decide, HoldKeys.Gate, Rules, TV Home's key map stay in TV Setup, so the add-on changes almost never. Keys outside `KeysProtocol.INTEREST` (a fixed superset of the dead-key list, the colour keys and the hold keys; a test keeps it a superset) pass with no call; if TV Setup is not bound / slow / gone the key passes (the remote must never be worse for it). `KeyCatch` needs nothing from TV Setup and stays whole in the add-on, as does the "screen off or KeyCatch waiting" check.
- Same signing key as TV Setup (`app/tv/signing.jks`, debug and release alike): the permission is defined by TV Setup only (a second definer would fail to install), the add-on `uses-permission` it. `ACCESS_RESTRICTED_SETTINGS` / `APP_AUTO_START` are allowed for the add-on's package too (`KeysAddon.prepare`): the restriction is on the app that provides the service.
- Migration: a TV updated from a version with the services inside has `dev.tvremote.setup/...KeyFixService` (or `...KeyCatch`) in `enabled_accessibility_services`. `KeyFix.list` drops those stale entries and writes the add-on's; `sync` runs after every update (BootReceiver / live ADB), so the keys come back within seconds of the update. A client with an older `tvr-helper.jar` (phone / computer not yet updated) looks for `dev.tvremote.setup/...KeyCatch` and finds nothing on an updated TV: the first key after the screen off then also reaches the app, until that client updates. `Screen.java` still uses an older TV Setup's own KeyCatch.
- Removing TV Setup does not remove the add-on (there is no remove path in the clients; TV Setup's own "Remove TV Setup" button asks Android to remove TV Setup only). The add-on without TV Setup is inert: it cannot bind and its services are off.
- Workaround for versions before this one: install from the Phone / Computer app (ADB, no block) or switch Play Protect scanning off for a minute (README "Android calls the app harmful").

## One "open target" for keys, power-on and the home grid (`Target`, `js/target.js`)
- **Format:** one string. `com.example.app` (an app, the only form before), `pin:com.example.app/<shortcut id>` (a launcher shortcut, `LauncherApps.getShortcuts`: static, dynamic or pinned), `tile:<provider>/<id>` (a live tile from `Tiles`). Why a prefix and not JSON: configs already hold plain strings in `dock` / `hidden` / `order` / `keyapps` / `autoapp`; no migration, an older TV Home drops an unknown `pin:` key harmlessly. `Target.kt` and `target.js` share `tests/target_cases.json`.
- **Where:** `keyapps` values, `autoapp`, and `tiles` (the grid's list; `TILES_MAX` 8 -> 24 now that shortcuts share it). An app is on the grid unless it is in `hidden`.
- **Only the default home may read or start another app's shortcuts** (`hasShortcutHostPermission`). So TV Setup never starts a `pin:`/`tile:` itself: `HomeKeys.open` starts TV Home's exported `OpenActivity` with the target, which opens it only if that target is in TV Home's own config (nothing else can ask it to run an arbitrary shortcut). The key filter's rule (`KeyFix`) still starts apps only, so `HomeKeys.keys` hands it app targets; shortcut/tile keys work while TV Home is on screen (`Keys.onKey`). Power-on (`AutoOpen`) uses `HomeKeys.open` and handles all three.
- **"Keys this remote has":** `config.keysHave` = union of `InputDevice.hasKeys` over the non-virtual keyboard devices; `null` = unknown, every slot stays live. A missing key with a target on it stays editable (to clear it); an empty one is dead.
- **Shortcuts to pick from:** Channels `favourites`, `search` (+ dynamic `last`), Games `library`, `padtest`; each opens a deep link of its own app.

## The Phone app's dialogs are the page's (`fromRemote`), but not Wireless pairing

- **Shared:** How to turn on ADB, Share, Notify, Dim, TV Home: the remote opens Manage with `then = "fromRemote(<opener>)"`; closing the dialog posts `/api/phone/back` and the remote is back in one tap. The page is packed in the APK and served by the phone itself: no ADB and no TV Setup needed to show it.
- **Opening on a tab waits for every script** (`DOMContentLoaded` in setup.js): opened straight on `#tv` the page called `loadTvset()` before tvset.js had run (`ReferenceError`), and TV Home never opened from Tools.
- **Manage keeps its port** (`prefs "managePort"`): the WebView's localStorage belongs to the origin, port included; a new random port each start lost Notify's corner and the tab.
- **Wireless pairing stays native** (`AdbHelp.wirelessPair`): the page's `/api/adb/pair` reconnects only Manage's own link; the native remote watches port 5555 alone (`watchAdb`), and a Wireless-debugging TV listens on another port, so after a pair on the page the remote would stay on keys only. Moving it needs a phone route that runs `pairedAt()`, and it cannot be tried on the emulators (the phone emulator reaches only the TV's 5555). Do it with a real Android 11+ TV at hand.

## Opening an app from TV Home (`Zoom.kt`) and updating the apps under it

- Measured on the Google TV emulator (screenrecord, brightness per frame): opening TV Games cold showed ~0.6 s of black.
  It was our own plate: it darkened to near black and the tile's picture faded after 150 ms, then the app's starting window
  (`Theme.Black` there, black in most TV apps) took over. Now the picture keeps its size, glides to the middle and pulses
  until the app draws. YouTube still shows ~0.3 s of its own black starting window over it: a launcher cannot cover
  another app's window. `SPLASH_SCREEN_STYLE_ICON` (Android 13+) is asked for but changed nothing on the emulator.
- "Zoomed, then the zoom went, Home showed, then TV Games": `left` (Home paused since the launch) was reset when the
  growth ended (380 ms), after Home had already paused (~250 ms). On an app that takes >3.4 s to show (a cold start after
  an update, a slow TV), the 3 s guard took it for a failed launch and shrank the plate. Reset before the launch now.
- TV Games closed under the person a moment after opening: TV Setup's AutoUpdate checked that it was not on the screen,
  then pushed and installed for several seconds; opened in between, the install closed it. TV Channels and TV Games are
  now updated by it only while the TV is in standby (their own SelfUpdate still asks in the app).
- **On a weak TV (FPT Play box: 4 cores, 32-bit, 1.8 GB, ~170 MB free; logcat 2026-10-23):** TV Games cold start 2.66 s,
  TV Setup 2.13 s, TV Channels 0.89 s, Stremio 5.56 s; Home back from an app skipped 68 frames. So: (1) the pulse repainted the
  whole plate (bitmap + clipPath) 60 times a second while the app it waited for was starting; now the picture is an ImageView in
  a layer, clipped by its outline, and the pulse only changes its alpha / scale (no drawing), after 350 ms. (2) The app is
  started at 25 % of the growth, not 60 % (~140 ms sooner). (3) The shrink on the way back ran on Home's thread just while Home
  redrew itself, and lost all its frames ("no zoom out"): gone. The plate disappears before Home's first frame and the system's
  own crossfade brings Home in. A launcher's `windowAnimationStyle` is ignored for this on Android 14 (a test 1.5x scale over
  1.5 s never showed on the Google TV emulator), and shrinking the app's own picture is not possible: only the system's launcher
  may take another app's window. `SPLASH_SCREEN_STYLE_ICON` was dropped: it changed nothing.
- The pickers over Home ("Add to home screen", "Quick keys") are a solid page, not a panel over a dimmed, see-through Home: the window
  over Home and the Customize panel was blended again on every frame of a focus move. Emulator, 12 D-pad moves, 3 runs: 14-26% late
  frames (median 31-36 ms) as before; dropping only the gliding glass changed nothing (48% → 48% in one run); solid window, no dim,
  plain panel: 6-11% (25 ms). Home's own grid: 0.4% (16 ms). `solidPage()` in keys/Picker.kt; `TvTile.glassy` false keeps the ring.

## The stock launcher wins over the HOME choice (BRAVIA, Android 9)

- **Measured on a BRAVIA 4K GB ATV3 (Android 9), 2026-10-23:** with `com.google.android.tvlauncher` on, `cmd package set-home-activity dev.tvremote.home/.HomeActivity` prints `Success`, yet the HOME resolver still names tvlauncher and the Home key opens it (twice in a row). With tvlauncher off, Home lands on TV Home. Google TV's launcherx does the same (a Coocaa, the GTV emulator).
- **The list (`Setup.LAUNCHER`) also has leanbacklauncher and Xiaomi's `com.mitv.tvhome`**, from cleanyourtv's `KNOWN_STOCK_LAUNCHERS` (their contributors' TVs); Undo's unrecorded "legacy" set stays the two Google ones.
- **So every way of choosing another launcher runs the whole `LauncherSwap`** whenever any of `Setup.LAUNCHER` is on (`Manage.homeSet`, `stockOn()`), not only with launcherx; the role alone is used only when none is on. `Reoptimize.homeBack` falls back to `take()` when set-home-activity does not stick after a firmware update.
- Android 9's Apps key / long-press Home goes to the system-only HOME resolver (`PhoneWindowManager.launchAllAppsAction`, MATCH_SYSTEM_ONLY): tvlauncher's apps view while it is on, whatever the home. Turning it off covers that too.
- **The Apps key (284) with nobody answering `ACTION_ALL_APPS` is a dead key** (`KeyFix.keyMap` / `withAllApps`): it is not in `global_keys.xml`, so the table could not tell; it gets the HOME rule (TV Home) and joins TV Home's free keys. Checked by asking the package manager, not by brand: with tvlauncher on, its apps view keeps the key. Long-press Home is left alone (on Google TV it is the dashboard). `input keyevent` is injected after the key filter, so on the emulator it was checked through TV Home's Quick keys list ("All apps key" offered), not by a press.
- **TV Home cannot change the home screen itself**: it has no ADB, `HOME_SETTINGS` / `MANAGE_DEFAULT_APPS_SETTINGS` are stubs on the BRAVIA and the emulator, the role dialog is Android 10+, and a stock launcher left on wins anyway. Its "Home screen" entry starts TV Setup with `open=launchers`, which opens the picker once ADB is up.
- **setupwraith goes off whole where its Home screen cannot (cleanyourtv's way, 2026-10-23).** Real Google TVs refuse `pm disable` of one
  component from the shell, so every swap there used to roll back ("home"). Now `LauncherSwap.take` turns the whole package off, first
  in the record (rollback turns it on last: back before launcherx, its RecoveryActivity takes Home; a reboot there factory-reset a
  Xiaomi MiTV-MOSR4), but only when `WIFI_OWNERS` names no network of its own (`cname=<pkg or its uid>`): an FPT Play box lost its
  network and ADB that way. Measured: a BRAVIA (Android 9) prints `ID: n SSID` lines + `cname=android.uid.system:1000`, the FPT box
  (Android 14) the same `cname=` form; both emulators only the `WifiConfigManager` header (no saved network, no setupwraith). No dump,
  or networks without owners → "wifi_unknown", never a guess. Cast hosts (`ro.product.device` sabrina / boreal / kirkwood) → "cast"
  (phones' casts fail "Untrusted device" there). Not run on a device with setupwraith yet: LauncherTest's fake TV covers the paths.

## Customize opens lazily; no second hand (`Customize.kt`, `widgets/Widgets.kt`)
- Measured on the FPT box (217 MB free RAM, swap in use), key to first frame: all seven pages built in `show()` took 0.9–1.6 s; building only the top level and the rest one page a message after the slide-in takes 0.33–0.57 s (same logging, 5 runs each, 2026-10-23).
- `Store.get` costs ~7–14 ms a call there; `refresh()` read it once per row, so it reads it once. A memo of the cleaned conf barely moved the total and was dropped.
- The analog clock's smooth second hand redrew Home ~30 fps (125 frames in 5 s idle) on the same UI thread as any panel over it. Removed; the face redraws on the minute.
- Panel 460 dp, not 600: the rows' text uses under half of 600. Tile titles take 2 lines so nothing is cut at the narrower width.
- Still ~470 ms from the menu row's click to the slide (FPT, timestamps per step): building the top level ~300 ms (seven group rows ~100-190 ms, `Store.get` ~15 ms x2, the photo list ~17 ms), then window + first frame ~140 ms. Now `Customize.prepare` builds the panel on an idle handler 3.5 s after Home starts and 1.5 s after each close; the click only shows it: 170-240 ms. Pages still fill on first use and `refresh()` runs at show, so a change made meanwhile (a phone, a sent photo) is in it. The slide's start offset is set at show, not at build: motion may have been turned off since.
- Reopened on a page (`show(at = "lang")` after a new language rebuilt Home): no slide, the page is simply there with its choice focused. Language is a list (each in its own words, the one in use ticked), not a tile that stepped through them and rebuilt Home on every press.

## A dialog taller than the screen squashes its last row (`TvUi.sheet`, `PhotoScreen.kt`)
- Android caps a WRAP_CONTENT dialog window at the screen height; the content is then laid out short and the last child (the Close/Done button) is squashed until its text vanishes. The FPT box is 540 dp tall; the web-page QR screen measured taller.
- `TvUi.sheet` wraps its rows in a ScrollView (every TV Channels/Games sheet). `PhotoScreen` keeps Close outside the scroll, pinned under it, and lays out in two columns: the QR alone on the left (at most 45% of the width, shrunk to what the height leaves, min 160 dp), words and choices on the right. The QR is drawn unfiltered so the scaled squares stay sharp. On the FPT the QR went from 100 dp (web) / ~195 dp (manage) to ~250 dp.
- Already scrolling: Customize, QuickKeys, Picker, TvGlass.menu, TV Setup's MainActivity. `ui/Drop.kt` has at most 6 rows: no scroll.
- Top-bar menus (`Drop`) hang from the capsule, not from the whole bar: the bar's height includes the focused button's word under it, which left a 38 dp gap. Dim 0.45 (0.15 left Home as bright as the menu). Customize opened from that menu dims the same (`Drop.MENU_DIM`), else the light jumped back between them; its preview pages (bg, look, widgets) set the dim to 0 (not layout: its rows / columns open a stepper of their own with the panel hidden) so a veil or a background is judged as it will be.

## The web page is served gzipped (`WebPage.kt`)
- First load after an update was 1,232,711 bytes (79 requests, browser, cache off); with `Content-Encoding: gzip` it is 413,903. Per file: core.js 49,975 -> 17,433, i18n/en.json 183,393 -> 54,359, index.html 87,894 -> 21,328.
- Compressed once per file and kept next to the plain bytes (a weak TV must not gzip per request); png and files under 1 KB stay plain; `Vary: Accept-Encoding` on the rest. Default gzip level: -9 would save ~100 KB more for a one-time cost not worth it on a TV.
- TV Home's `ManageServer` is a separate server and does not do this yet.

## Settings are cleaned once (`Store.kt`)
- `Store.get` parsed and cleaned the saved JSON (~7 ms on the FPT box) and added the live reports (`effective`, ~6 ms) on every call; `Store.list` paid both for one array. The picker called it per tile (~370 ms of UI thread), Quick keys per key (~400 ms).
- Now the cleaned copy is kept until the text in prefs changes (string compare: any writer, even another process's prefs, is seen). `list` reads it without `effective` (dock, hidden, order, tiles, widgets are all saved keys): ~0.1 ms. `get` still returns a fresh copy with the reports, so callers may change it.
- The picker also reuses Home's loaded apps and banners (copies via `constantState`, so the Home grid's drawables keep their bounds).
- The picker's tiles cost ~10 ms each to make on the FPT box. The first three rows are made on the picker's thread (views not yet on a window, as AsyncLayoutInflater does) and drawn alone; the rest is added a frame later, focus kept. Making all of them off the UI thread (~180 ms) took longer than the wait it saved. First rows on screen: ~0.25–0.3 s, from 0.75–1 s.

## Focus without growth on the Customize pages (`Customize.kt`, `PickTile`)
- Measured on the FPT box, 24 D-pad moves: the picker's growing tiles 8.3–8.5 s of CPU, ~615 frames (50th 32 ms); the ring alone 1.1–1.3 s, ~27 frames. Customize's rows (1.06 pop) 5.3–5.5 s, ~533 frames; the lighter background alone 0.7–1.1 s, ~29 frames. Idle: 0 frames, 60 ms in 10 s.
- So these pages show focus by colour and ring only. Home's grid keeps its growth (the look of the launcher, one tile at a time over a still page).

## Phone pads: why links dropped and lagged (`GameServer.kt`, `pad.js`)
- iPhone drops: iOS freezes a page's timers and socket (screen dimmed, locked, other app, Low Power Mode). On waking, "no pong for 5 s" was true of the page, not the link, so a healthy link was closed every wake. A gap between ticks now means "slept": ping, fresh window, dead only after 8 s.
- Lag: a frame was 3-4 writes (one TLS record / TCP segment each) and a broadcast wrote phones in turn on one thread. Now one write per frame from a writer thread per phone (256-frame queue). The TV holds a Wi-Fi lock while serving (power save parks frames ~100 ms).
- Same IP is one phone only if the same kind (iOS / Android / other): a repeater or hotspot puts both behind one address.
- Emulator loopback cannot show the gain (p50 1-2 ms before and after); real Wi-Fi is the test.

## Session cookies from a QR are SameSite=Lax (`ManageServer.kt`, desktop `Pass.kt`)
- A QR or link opened by another app (camera, Lens, a desktop app) is a cross-site navigation: a Strict cookie set on the 302 is not sent on the redirect, so the phone got 401. Lax is sent on top-level GETs; changes stay protected (X-Tvh header in Home; Sec-Fetch-Site / Origin / localhost checks on desktop).
- The key in the QR works while the QR is on screen (a scanner's preview opens it first), at most 8 sessions.


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

## TV Home scroll: tiles at rest are layers (`TvTile.card`, TV Setup `DimLayer`)

Measured on the FPT box (Mali-G31, Android 14, 50 Hz, the person's 25% Dim on), DOWN/UP between the dock row and the next, focus on a tile:
the app delivered a frame every vsync, but SurfaceFlinger held a frame (2 vsyncs, then one skipped) in 3-10 presses of 8, always in the first
6 frames, where the scroll is fastest. Sideways moves (same focus animation, no scroll): 0-1 in 8. The box composes every layer with the GPU
(`CLIENT` in `dumpsys SurfaceFlinger`, even a lone app), so the Dim window's full-screen blend shares the GPU with the app; with the Dim
hidden for a minute the same scroll held 0-1 in 8. The Dim stays (the person chose it): the app had to cost less.
App GPU per scroll frame (framestats `GpuCompleted`), p50 / p90 ms: as it was 17 / 38; Frost not invalidated, no text shadows, no widget
layer, the web wall: no change; one layer for the whole grid 48 / 70 (re-rendered every frame by the focus animation inside); the
wallpaper a solid colour 15 / 27 (at half or quarter size: no change, the cost is the full-screen pass, not the bitmap); no rounded
clip on the faces 15.5 / 28; **a hardware layer per card 12 / 16** (presses with a held frame: 1 in 8). The focused card has none: a
layer scaled by GROW is soft, and its animation runs `withLayer()` as before. Cost: about 11 MB of render targets for TV Home's 27.
`DimLayer` on a TV is MATCH_PARENT now: twice the long side each way (for phones that turn) was a 3840 x 3840 buffer for one colour; it
did not change the composition (`CLIENT` either way) nor did moving its alpha into the colour.

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

## TV Home's settings page: groups, one save for the order, a session that outlives its QR (`js/home.js`, `js/home_order.js`, `Access.kt`, `WebCheck.kt`)

- **Groups as on the TV** (`GROUPS` in home.js = `Customize.kt`'s seven): everything on one page was a long scroll, and the person setting it up already knows the TV's groups. Each row shows a summary (`sums()`) so most questions are answered without opening it.
- **The app order is saved once.** A write per move made TV Home rebuild its grid at every step and the page wait for each answer. The grid is now a preview (`homeGridOrder` = HomeActivity.show's order: old dock first, then `order`, the rest A-Z); Save sends `{dock: [], order: all}`.
- **Drag listens on the window, not with pointer capture.** Moving the dragged node in the DOM (`before`/`after`) drops its pointer capture: in Chrome the drag stopped after one step and the pointerup went elsewhere (tile stuck "lifted"). Measured on the FPT's page in Chrome 2026-10-24. The app pictures are `<img>`: `dragstart` is cancelled and the img gets `pointer-events:none`, or the browser's own image drag takes the pointer.
- **Closing the QR keeps phones that came in** (`ManageServer.release`, `Access.closeKey`): the person closes the QR to look at the TV while still setting it up. The key stops working (a new QR = a new key, `issue(keep = true)` keeps the sessions); idle 10 min still ends it.
- **The web background is tried where it will be shown** (`WebCheck`): TV Home's own page asks the TV (`/h/web/check`), so a server on the home network that the phone cannot see is judged by the TV; host pages ask from the host (`/api/home/web/check`). FPT measured: dns 173 ms, refused 36 ms, 404 / 200 + title 0.4-0.8 s. A failed check still allows "Use anyway" (a page needing a login on the TV).


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


## Telegram bot in TV Setup (issue #4: `core/web/Telegram.kt`, `TelegramHttp.kt`, `web/js/telegram.js`, `/api/telegram`)
- **Long polling, not a webhook.** `getUpdates` (timeout 50, `allowed_updates` message + callback_query) from the TV itself: nothing listens on the home network or the Internet, no domain, no certificate, no router setting. A webhook needs a public HTTPS address on every owner's TV, which this project refuses to ask for. `deleteWebhook` runs once per start (and again on a 409) because a bot with a webhook cannot poll.
- **Runs in TV Setup**, inside RemoteService's lifetime: it already has the in-process routes (`route()`), the Notify layer for the code on screen, ADB and the prefs. TV Home / Games / Channels are not always running. The phone and computer apps cannot reach TV Setup's prefs, so they show a hint card with a "Check TV Setup" button (the same Install / Update question as other TV Setup features, `SetupNeed.MIN["telegram"] = 0.21.1`); the real card is on the TV's own web page only.
- **Everyone makes their own bot** (@BotFather): no shared bot to run or to abuse, no token of ours in any build. The token is stored only in TV Setup's prefs (`tg_token`), never returned (the API gives `id:••••last4`), and `WireLog.SECRET` blanks `/bot<id>:<secret>`. The HTTP client never puts the address in an error. The API base is overridable only when the app is debuggable (a file `telegram-url.txt` in its external files dir), for the fake server in tests.
- **Who may talk.** Allowlist of `chat.id` (groups are negative ids). A stranger gets no answer at all, not even to `/help`. Pairing: a 6-digit one-time code (5 min, shown in Settings and on the TV screen), `/pair 123456`; 5 wrong tries cancel the code and lock pairing 15 minutes like Hook.kt; making a new code lifts the lock. One command a second per chat (taps 0.4 s). A paired chat gets REMOTE scope (Scope.kt) by default; the owner may set FULL or VIEW per chat. Commands go through `route()` with that scope, so Scope.kt stays the one allow-list; a refusal is told to the chat.
- **Exceptions on purpose.** `/vol 30` (a level) needs a scene step, a FULL route, so that one request is elevated to FULL: the only thing a REMOTE chat does beyond keys, apps, notify, timer. `/open` by name/package from the launcher list for REMOTE; FULL may name any package (otherwise FULL would mean nothing for the bot).
- **At-most-once.** The offset is saved before the batch runs: a crash in the middle drops a command rather than repeating `/off`. Messages older than 120 s are ignored, and the very first start skips the backlog (`offset=-1`): nobody wants yesterday's `/off` on first connect. Errors back off 5 -> 10 -> 20 -> 40 -> 60 s (honouring `retry_after`); 401/404 stop the loop until a new token.
- **Undo.** One off switch (keeps token and chats), "Remove token" (forgets chats and offset too), remove one chat; `/off` has `/on`, `/timer 0` cancels, `/key` is its own reverse. Screenshots go through Telegram's servers: said once in the UI and in the guide.
- **More commands (all through `route(request, scope)` with the chat's scope, nothing re-implemented).** `/apps` (inline buttons; ranked by today's Stats usage, which is FULL only, so REMOTE gets the launcher's first 8), `/usage` (top 5 + total from `/api/stats`, FULL only: a REMOTE chat gets "scope denied"), `/now` (`/api/playing` + a /shot button), `/channel NAME` (`/api/channels/open`; 503 `adb: channels:missing` becomes a "get TV Channels" message), `/type` (the typing route, same as `/say` never kept in a log), `/scenes`, `/bedtime`, `/limits`, `/schedule`, `/alerts`. "Recently opened" is approximated by today's usage: Stats has no open-order. Callback data prefixes: `a:` app, `s:` scene, `l:` alert toggle, `r:bed|blk|unb` rules, `z:` undo, `i:shot`, `k:` keypad (all <=64 bytes).
- **Rules from a chat.** Reads (`GET /api/rules`) are open to REMOTE; changes need FULL (REMOTE gets the read-only text plus a note, no button, and a forged tap does nothing: Scope.kt refuses the POST anyway). Bedtime on/off is the `bedtime` action; "block now" is a `sched` entry with `block:true`, "unblock" removes it. Every change keeps the previous bedtime / sched entry in memory (30 entries, 1 h) behind a `z:<id>` Undo button; a restart forgets them, which is fine for a button on a message.
- **Alerts the TV sends by itself.** `Telegram.alert(kind, args)`, kinds `on off limit bed sleep pair`, opt-in per chat (`/alerts` toggles, or the page), saved in the chat record (`tg_chats`). Hooks: Stats' screen receiver (on/off), Guard (limit/schedule close, bedtime start, sleep timer fired), Paired.add (a new browser or phone: the security one). Debounce: one per kind per 60 s, and the stamp is only set when some chat is opted in (otherwise a chat that switches it on a second later would miss the next one). Words are `telegram.al_<why|kind>`. Sent from a single-thread executor so Guard never waits on the network.
- **`/start <code>`** pairs exactly like `/pair <code>` (same one-time code, same 5-wrong lock), so `https://t.me/<bot>?start=<code>` (a QR) opens the bot and pairs in one tap. A bare `/start` from a paired chat is `/help`; from a stranger, nothing. getMe's username is cached (`tg_bot`).
- **History** (`tg_history`, `Telegram.History`): at most 200 entries and 7 days (the write drops expired ones; reads hide them), newest first, paged 20, per chat or all. Entry = time, chat, command, result (`ok` / `scope` / `error`), and for an alert the kind and the chats it went to. `/type`, `/say`, `/pair`, `/start` keep only the command name, never the words (also true of WireLog); the token is never in it. Removing the token clears it.
- **Status** for the card: `status` (ok / connecting / badtoken / offline / error), `last_ok` (last successful poll, with `now` so a client does not trust its own clock), `error`, `locked` (seconds left of the pairing lock), bot name.
- **TV Home's interface (`HomeInfo`, authority `dev.tvremote.setup.home`, same signing-key trust as its other calls).** `ContentResolver.call(uri, "telegram", arg, null)` with `arg` = `""` (read) or `"code"` (make a fresh pairing code first). The Bundle's `"json"` is `{state, bot, code?, code_left?, link?}`: `state` = `token_missing` (no bot yet), `none` (a token, switched off) or `ready` (listening); `bot` without the `@` (empty until Telegram answered once); `code`, `code_left` (seconds), `link` = `https://t.me/<bot>?start=<code>` only when `ready` and a code is live. Code only starts when ready. Nothing secret is returned.
- **TV Home's Customize row "Telegram" (`manage/TelegramScreen.kt`)** is a plain row at the top level next to "Manage from phone" (no provider call while Customize builds); the screen asks `call("telegram", "")` on a thread when opened. `ready`: QR of `link` + 3 steps + a 1 s countdown of `code_left` + New code (`"code"`); `none` / `token_missing`: @BotFather steps + QR of `http://<lan ip>:8765` (TV Setup's page on this TV: its 6-digit code, then the Telegram card; TV Home cannot take the token itself, it must stay in TV Setup's prefs). No Bundle / no `state` = TV Setup missing or too old: says so with the Downloader code (and an Open Downloader button when it is installed), no silent download. TV Home's own page (`js/home.js`) has no matching section, so nothing was added there. Tested on the TV emulator in all three states with the fake Telegram server (cleartext is allowed only for 127.0.0.1: `adb reverse` + `telegram-url.txt` = `http://127.0.0.1:<port>`).
- **The phone and computer apps' card.** The same page code as the TV's (`js/telegram.js`), so three clients stay in step. `/api/telegram` in Manage forwards `{body, query}` to TV Setup over the shell-only RulesReceiver broadcast (`do:"telegram"`, answered from a thread because Telegram is slow), i.e. the owner's ADB, exactly like the rules; a paired browser of the TV talks to its own `/api/telegram`. TV Setup missing or too old (`unknown action`) = `SetupNeed.Missing("telegram")`, which shows the old hint card with Install / Update. The token goes to the TV once and the answer only has `id:••••last4`. Fleet: the card reads the TV that is open (`onTvChange` reloads it).
