// tvr-census: an anonymous count of which TVs the apps ran on. Stores no IP, no headers, no user agent;
// the install id is kept only as the first 8 bytes of its sha256. See ../README.md.
import { MAX_BODY, validate, hashId, today, aggregate, series } from "./logic.js";

const CORS = { "access-control-allow-origin": "*" };
const json = (o, status = 200, extra = {}) =>
  new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json; charset=utf-8", ...CORS, ...extra } });

// A ping is counted once per (install, model) per day: the upsert only fires when `last` is older than today.
const UPSERT = `INSERT INTO tvs (h, brand, model, device, board, hw, soc, android, sdk, leanback, app, v, adb, video, optimize, first, last)
VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?16)
ON CONFLICT (h, brand, model) DO UPDATE SET
  device=excluded.device, board=excluded.board, hw=excluded.hw, soc=excluded.soc, android=excluded.android, sdk=excluded.sdk,
  leanback=excluded.leanback, app=excluded.app, v=excluded.v,
  adb=COALESCE(excluded.adb, adb), video=COALESCE(excluded.video, video), optimize=COALESCE(excluded.optimize, optimize),
  last=excluded.last
WHERE tvs.last < excluded.last
RETURNING first`;

// counts the day's first counted ping: active always, new when the install was first seen today
const DAY = `INSERT INTO days (day, app, active, new) VALUES (?1, ?2, 1, ?3)
ON CONFLICT (day, app) DO UPDATE SET active = COALESCE(active, 0) + 1, new = new + excluded.new`;

const AGG = `SELECT brand, model, device, android, COUNT(*) AS n FROM tvs GROUP BY brand, model, device, android LIMIT 5000`;

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: { ...CORS, "access-control-allow-methods": "GET", "access-control-max-age": "86400" } });

    if (url.pathname === "/v1/ping" && req.method === "POST") {
      if (Number(req.headers.get("content-length") || 0) > MAX_BODY) return json({ error: "too big" }, 413);
      const text = await req.text();
      if (text.length > MAX_BODY) return json({ error: "too big" }, 413);
      const r = validate(text);
      if (!r) return json({ error: "bad body" }, 400);
      const h = await hashId(r.id);
      const day = today();
      const row = await env.DB.prepare(UPSERT)
        .bind(h, r.brand, r.model, r.device, r.board, r.hw, r.soc, r.android, r.sdk, r.leanback, r.app, r.v, r.adb, r.video, r.optimize, day)
        .first();
      if (row) await env.DB.prepare(DAY).bind(day, r.app, row.first === day ? 1 : 0).run();
      return new Response(null, { status: 204 });
    }

    if (url.pathname === "/v1/tvs" && req.method === "GET") {
      const cache = caches.default, key = new Request(url.origin + "/v1/tvs");
      const hit = await cache.match(key);
      if (hit) return hit;
      const { results } = await env.DB.prepare(AGG).all();
      const res = json(aggregate(results), 200, { "cache-control": "public, max-age=600" });
      await cache.put(key, res.clone());
      return res;
    }

    if (url.pathname === "/v1/days" && req.method === "GET") {
      const cache = caches.default, key = new Request(url.origin + "/v1/days");
      const hit = await cache.match(key);
      if (hit) return hit;
      const { results } = await env.DB.prepare("SELECT day, app, active, new FROM days ORDER BY day LIMIT 20000").all();
      const res = json(series(results), 200, { "cache-control": "public, max-age=600" });
      await cache.put(key, res.clone());
      return res;
    }

    return json({ error: "not found" }, 404);
  },
};
