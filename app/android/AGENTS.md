# app/android: the Phone app

Read with the root [AGENTS.md](../../AGENTS.md) (rules, build, tests, releases apply here too).

## What it is

| Name | Code | `data-core` | What it is |
|---|---|---|---|
| **Phone app** (App điện thoại) | `app/android` | `phone` | Android app "TV Remote"; its remote screen is native Kotlin, its Manage screen is the web UI (in-app HTTP core) |

## Files

```
app/android/.../phone/   phone app, one file per part, as extension functions on MainActivity: MainActivity (state, lifecycle),
                         Ui, Net, PickerScreen, RemoteScreen, Typing, InfoScreens, KeysOnly, AdbHelp, Media, Managing (the
                         web UI in a WebView), Updater + QuietUpdate (silent on Android 12+), Listen + ListenService (🎧), Automation (timer, scenes, voice),
                         SceneStore, Headless + SceneActivity/SceneService (widget, tiles, shortcuts), PadMap (a game controller → keys; the page's is `js/gamepad.js`), AirMouse (the gyroscope as a pointer: a virtual HID mouse through scrcpy, `Tv.mouseOpen`; LG's pointer by `Remote.pointer`), Diagnosing (the self-check's screen), Tiles, RemoteWidget, ManyTvs (the list of TVs:
                         install / remove / restart on several at once), SetupBanner (a new TV Setup), Drawing (✏️ on the picture), NowPlayingBar
                         (the now playing strip, the TV's clipboard), IrRemote + Ir + IrCodes (the infrared remote on the phone's IR LED: screen,
                         encoders NEC / Samsung32 / SIRC / RC5 / RC6 / RCA / raw, `assets/ir/codes.json` made by `dev/gen/ir_codes.py` from Flipper-IRDB, CC0)
```

## Remote screen

Its keys and menus come from `app/shared/web/remote.json` (`PhoneRemote.kt`, `phoneActions`): the rule is in
[app/shared/web/AGENTS.md](../shared/web/AGENTS.md) "The remote screen is drawn from one file".
