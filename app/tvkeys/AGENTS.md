# app/tvkeys: TV Setup Keys

Read with the root [AGENTS.md](../../AGENTS.md) (rules, build, tests, releases apply here too). What is where:

```
app/tvkeys/               TV Setup Keys (`dev.tvremote.setup.keys`): the ONLY place with an accessibility service (KeyCatch, KeyFixService), thin: it hands each key of `KeysProtocol.INTEREST` to TV Setup's `KeysBridge` and does what it answers (the logic stays in KeyFix / HoldKeys / Rules). TV Setup declares none, because Play Protect blocks an app that does when it comes from a browser or Downloader (DECISIONS.md). No number of its own: TV Setup's `app/tv/VERSION`, same signing key (`app/tv/signing.jks`; a signature permission lets them trust each other); gradle `packKeys*` puts its APK in tv-setup.apk as `assets/tvkeys.apk`, `KeysAddon.kt` installs it over TV Setup's own ADB link when a feature needs it (and again when the version differs)
```
