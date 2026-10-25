# tvr-themes

A queue of community themes for TV Home. A Cloudflare Worker + D1, free tier only. Anyone may send a theme; only the owner reads the
queue and decides (`dev/sign/review_themes.py`). Nothing is published from here: an approved theme is added to the signed
`docs/home/themes.json` by hand.

- `POST /v1/themes` (JSON, at most 1.6 MB, CORS `*`) `{tvtheme:1, name (1-24), by (1-24), lang: vi|en, conf, preview, wall?}` -> `202 {id}`.
  `conf` = only `HomeConfig.THEME_KEYS` (keep `src/logic.js` in step), plain JSON, at most 8 KB. `preview` = base64 jpeg/webp, at most 150 KB decoded
  (a real TV screenshot, 1280x720 JPEG ~q70 fits). `wall` = base64 jpeg, at most 1 MB, only when the wall is the person's own photo. Magic bytes are checked.
  Names are trimmed and stripped of control characters. Errors: 400 `{error}`, 413 too big, 429 `{error}`.
- Limits (no paid product): 3 a day per sender, keyed by sha256(IP + UTC day + the `SALT` secret), kept only that day (older rows deleted on each POST; the IP is never stored).
  Storage: refuse with `429 {error:"queue_full"}` when the waiting pictures (pending + approved, `themes.bytes`) would pass 350 MB (D1 free = 500 MB a database).
  Blobs are set NULL as soon as a row is rejected or `done`; pending rows older than 60 days are deleted, finished ones after 30.
- Admin, header `Authorization: Bearer <ADMIN_TOKEN>`: `GET /v1/admin/pending` -> `[{id, at, name, by, lang, conf, flags[]}]` (flags: web wall off tvremote.pages.dev,
  any http(s) URL in conf, very long name); `GET /v1/admin/:id/preview|wall` (image bytes); `POST /v1/admin/:id/approve|reject|done`
  (`done` = the script saved the files; it frees the pictures).
- The token lives only in `~/.tv-remote-release/themes-admin-token` (and as the worker's secret), never in the repo.

Live: https://tvr-themes.99-hoangtran.workers.dev (account of 99.hoangtran@gmail.com; D1 `tvr-themes`).

```
cd dev/themes
node --test test/                                   # validation, flags, hashing
npx wrangler d1 execute tvr-themes --local --file=schema.sql
echo 'ADMIN_TOKEN=any-local-string-of-16+' > .dev.vars        # gitignored
npx wrangler dev --local --ip 127.0.0.1 --port 8796
npx wrangler deploy                                 # secrets: wrangler secret put ADMIN_TOKEN / SALT
python3 dev/sign/review_themes.py                   # from the repo root; THEMES_URL=http://127.0.0.1:8796 for a local worker
```
Ready-theme ids are `c_<slug>` (underscore, at most 16: TV Home's id rule has no "-"). TV Home shows at most 40 ready themes (`HomeConfig.READY_MAX`).
