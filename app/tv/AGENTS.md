# app/tv: TV Setup

Read with the root [AGENTS.md](../../AGENTS.md) (rules, build, tests, releases apply here too). What is where:

```
app/tv/                  TV Setup: MainActivity (the TV screen), RemoteService (web server), ManageActivity (the web UI on the
                         TV itself: a WebView in its own process, `?ontv=1`, D-pad focus in `js/ontv.js`), PairActivity, Stats, TrustReceiver, Scope (what a shared browser may do: full / remote / view),
                         Rules + Guard + RulesReceiver + WarnActivity (sleep timer, bedtime, app limits, the full memory notice), DnsWatch (turns off a
                         DNS filter that cut the TV off), DrawLayer + DrawReceiver (the drawing over every app), NotifyLayer + NotifyReceiver (the message card in a corner; `Notify.kt` in the shared code is the one place its corner / seconds are clamped), Nightly (2-5 am in standby: caches, `cmd package compile` of the apps used, `stats/nightly.json` for Health), KeepAdb (ADB debugging
                         back on when switched off, with WRITE_SECURE_SETTINGS granted through ADB)
```
