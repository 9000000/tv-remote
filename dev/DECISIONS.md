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
