# app/karaoke: TV Karaoke (shelved)

Read with the root [AGENTS.md](../../AGENTS.md) (rules, build, tests, releases apply here too).

## What it is

| Name | Code | `data-core` | What it is |
|---|---|---|---|
| **TV Karaoke** | `app/karaoke` | | songs on the TV, queue filled from phones (`dev.tvremote.karaoke`): its own server on 8790 + a QR, YouTube's embedded player in a WebView (ads stay, no download); not yet in release.sh, the site or the clients |

## Files

```
app/karaoke/             TV Karaoke: MainActivity (queue screen + WebView player), KaraokeServer (8790: pad page `assets/pad`, WebSocket), Session (queue, key, volume), KaraokeQueue + Search (`SearchParse`: API / InnerTube / HTML, unit-tested) + KeyRules; plain Views, no :shared
```
