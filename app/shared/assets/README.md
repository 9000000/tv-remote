# app/shared/assets

The two files that run on the TV.

The Phone app and the Computer app (and TV Setup, for its own TV) copy both to the TV over ADB and run them with `app_process` as the shell, which may do what an ordinary app may not.

| File | From | Does |
|---|---|---|
| `scrcpy-server.jar` | [scrcpy](https://github.com/Genymobile/scrcpy) v5.0.1 (Apache-2.0) as published, unchanged | picture, sound, touch and keys (`picture/Tv.kt`) |
| `tvr-helper.jar` | built by this repo | app names and pictures, the TV's hardware, what is playing, the screen off |

**`tvr-helper.jar`** (a few KB) is compiled from 4 Java files in `app/shared/src/main/java/dev/tvremote/core/`:

- `Meta`: app names and pictures (`ontv/RemoteLabels.kt`).
- `Caps`: what the TV's hardware can do: codecs, HDR, display modes, sound out over HDMI (the Health tab).
- `Playing`: what the TV plays, play / pause / seek (the now playing bar, `ontv/NowPlaying.kt`).
- `Screen`: the screen off while the sound goes on, back on at any key (`ontv/ScreenOff.kt`).

Java, not Kotlin, so the file stays small and needs no Kotlin library on the TV. The `:shared:tvHelper` task (`app/shared/build.gradle.kts`) dexes them with the Android SDK's `d8` on every Android build and writes the file only when it changed. It is kept in git so the Computer app builds without the Android SDK. **After changing one of those 4 Java files, build Android again and commit `tvr-helper.jar` too.**

Updating scrcpy: replace `scrcpy-server.jar` and `VER` in `picture/Tv.kt` (the server and the app must have the same number).
