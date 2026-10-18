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
- **"Nhịp hình: Đều hơn" (33 ms pacing) is off by default.** With Wi-Fi bunching (bursts every 150 ms) off drew ~78% of
  frames, on ~98%; the rest of the unevenness is the TV's own (gaps up to ~300 ms). Not worth 33 ms for everyone.
- **The JMuxer path's delay (~60-140 ms in Chrome at 10 fps, ~140 ms at 10 fps and ~180 ms at 30 fps in WebKit) is not ours to
  cut from JS; nothing was changed.** Measured with `?jm=1&stats=1` (JMuxer on the computer app's page; `window.VT`: arrival ->
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
