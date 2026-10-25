# tvr-census

An anonymous count of which TVs the apps ran on (like vgs-cleantv's `dem/`). A Cloudflare Worker + D1, free tier only.
The apps send one ping a day; the site's home page and `docs/tvs.html` read the public totals.

- `POST /v1/ping` (JSON, at most 4 KB, answers 204): `{id, app: setup|phone|pc, v, tv: {brand, model, device, board, hw, soc, android, sdk, leanback}, ok: {adb, video, optimize}}`.
  `ok` flags are `true` / `false` / missing (not tried). Unknown fields are dropped, strings trimmed to 64 characters.
- `GET /v1/tvs` -> `{updated, installs, models: [{brand, model, count, android[], devices[], kind: tv|box|projector}]}` (the ok flags and last day stay in D1, not sent); worked out hourly by a cron into the `summary` table (a view reads one row, not every install), cached 10 minutes; no ids.
- `GET /v1/days` -> `{updated, installs, days: [{day, active: {setup, phone, pc}, new: {…}, total}]}` for the growth chart; cached 10 minutes. `active` = installs that pinged that UTC day, `new` = first seen that day, `total` = running sum of `new`. Before 2026-10-25 only `new` exists (rebuilt from `tvs.first`).
- Stored: sha256(id) cut to 16 hex, the model strings, flags, first / last day. **Not stored:** IP, headers, user agent. One row per (install, model);
  a second ping the same UTC day changes nothing. The kind is worked out from the names in `src/logic.js` `kindOf` (not stored).
- Abuse limits: body size, id must be a uuid v4, one counted ping per install per day. No rate-limit product (they cost money).

Live: https://tvr-census.99-hoangtran.workers.dev (account of 99.hoangtran@gmail.com; D1 `tvr-census`).

```
cd dev/census
node --test test/                                   # validation + aggregation
npx wrangler d1 execute tvr-census --local --file=schema.sql && npx wrangler dev --local
npx wrangler deploy                                 # after `npx wrangler login`; a first deploy may need a retry after ~20 s
npx wrangler d1 execute tvr-census --remote --command "DELETE FROM tvs"   # empty the data (test rows)
```
