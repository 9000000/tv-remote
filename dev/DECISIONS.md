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
- **Power-on app fires once per power-on, by a 120 s guard** keyed on (boot count, elapsedRealtime): the boot and the screen coming on right
  after it are one power-on. It looks at the app on screen (UsageStats) and keeps it when the TV woke into another app (HDMI-CEC, cast).
  Home pressed later never re-fires: the trigger is a power event only. No `SetupNeed.MIN` entry: the setting asks TV Setup itself and
  says "install TV Setup" when it gets no answer; the first TV Setup with "keys" is the next one.
