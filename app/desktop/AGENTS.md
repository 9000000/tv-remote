# app/desktop: the Computer app

Read with the root [AGENTS.md](../../AGENTS.md) (rules, build, tests, releases apply here too). What is where:

```
app/desktop/             the computer app on the shared Kotlin core (JVM, no adb/Python/ffmpeg), the "pc" core: Server.kt
                         (the routes only the computer has) + Manage.kt; Pass.kt (a key per start, in the link the window and browser open: tests pass `TVR_PASS` and `?k=`); Updater.kt (updates itself from releases, swapped in by a script once it quits); Window.kt shows the page in the system's web view
                         (native/: webview, kept in git, native/README.md). `./gradlew :desktop:installDist`, then
                         `app/desktop/build/install/desktop/bin/desktop --port 8769 [--window]` (inside the repo: its tvs.json, keys in
                         .cache/keys). `./gradlew :desktop:packageApp` -> the app for this system + zip / tar.gz (own Java via jlink;
                         data in the user's app data folder); .github/workflows/desktop.yml builds and opens it on Mac, Windows, Linux
```
