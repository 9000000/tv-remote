# app/android: the Phone app

Read with the root [AGENTS.md](../../AGENTS.md) (rules, build, tests, releases apply here too). What is where:

```
app/android/.../phone/   phone app, one file per part, as extension functions on MainActivity: MainActivity (state, lifecycle),
                         Ui, Net, PickerScreen, RemoteScreen, Typing, InfoScreens, KeysOnly, AdbHelp, Media, Managing (the
                         web UI in a WebView), Updater, Listen + ListenService (🎧), Automation (timer, scenes, voice),
                         SceneStore, Headless + SceneActivity/SceneService (widget, tiles, shortcuts), PadMap (a game controller → keys; the page's is `js/gamepad.js`), AirMouse (the gyroscope as a pointer: a virtual HID mouse through scrcpy, `Tv.mouseOpen`; LG's pointer by `Remote.pointer`), Diagnosing (the self-check's screen), Tiles, RemoteWidget, ManyTvs (the list of TVs:
                         install / remove / restart on several at once), SetupBanner (a new TV Setup), Drawing (✏️ on the picture), NowPlayingBar
                         (the now playing strip, the TV's clipboard), IrRemote + Ir + IrCodes (the infrared remote on the phone's IR LED: screen,
                         encoders NEC / Samsung32 / SIRC / RC5 / RC6 / RCA / raw, `assets/ir/codes.json` made by `dev/gen/ir_codes.py` from Flipper-IRDB, CC0)
```
