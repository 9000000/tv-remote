# app/desktop/native

The Computer app's window: [webview](https://github.com/webview/webview) 0.12.0 (MIT), the system's own web view behind one
small C API (WebKit on Mac, WebView2 on Windows, WebKitGTK on Linux). `Window.kt` loads it with JNA.

| File | For |
|---|---|
| `mac/libwebview.dylib` | Mac, arm64 and x64 in one file (macOS 11+) |
| `linux-x64/libwebview.so`, `linux-arm64/libwebview.so` | Linux (built on Ubuntu 22.04, so newer systems run it too; needs WebKitGTK 4.1) |
| `windows-x64/webview.dll` | Windows 10 and 11 (WebView2 is part of them) |
| `mac.m` | what a Cocoa app has and webview does not: the menu bar (without an Edit menu, Cmd+C / Cmd+V do nothing in text fields) and a window that keeps its size and place |
| `build.sh` | builds them all |

They are built here and kept in git, so building the app needs no C compiler. Build again only to change webview's version
or `mac.m`:

```
app/desktop/native/build.sh           # all of them
app/desktop/native/build.sh mac       # mac | linux | windows
```

The Mac one is built on a Mac; Linux and Windows in Docker (Windows with MinGW). The source is downloaded into `.src/`
(not in git) and checked against its SHA-256 (`SUM` in the script): a new version needs its new sum.
