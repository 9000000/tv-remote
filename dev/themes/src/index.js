// tvr-themes: a queue of community themes for TV Home. Anyone may send one (POST /v1/themes); only the owner (ADMIN_TOKEN) reads
// the queue and decides, with dev/sign/review_themes.py. Nothing here publishes: an approved theme goes into the signed
// docs/home/themes.json by hand. Stores no raw IP. See ../README.md.
import { MAX_BODY, PER_DAY, MAX_QUEUE_BYTES, MAX_AGE_DAYS, validate, sizeOf, flags, senderHash, today, newId, tokenOk, kind } from "./logic.js";

const CORS = { "access-control-allow-origin": "*" };
const json = (o, status = 200, extra = {}) =>
  new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json; charset=utf-8", ...extra } });
const cors = (r) => { for (const [k, v] of Object.entries(CORS)) r.headers.set(k, v); return r; };

async function submit(req, env) {
  if (Number(req.headers.get("content-length") || 0) > MAX_BODY) return json({ error: "too big" }, 413);
  const text = await req.text();
  const r = validate(text);
  if (!r.ok) return json({ error: r.error }, r.status);
  const t = r.ok, day = today();
  await env.DB.prepare("DELETE FROM sends WHERE day < ?1").bind(day).run();
  const now = Math.floor(Date.now() / 1000);
  await env.DB.prepare("DELETE FROM themes WHERE (at < ?1 AND status = 'pending') OR (at < ?2 AND status != 'pending')").bind(now - MAX_AGE_DAYS * 86400, now - 30 * 86400).run();
  await env.DB.prepare("DELETE FROM wallpics WHERE theme NOT IN (SELECT id FROM themes)").run();
  const used = await env.DB.prepare("SELECT COALESCE(SUM(bytes), 0) AS n FROM themes WHERE status IN ('pending', 'approved')").first();
  if (used.n + sizeOf(t) > MAX_QUEUE_BYTES) return json({ error: "queue_full" }, 429);
  const ip = req.headers.get("cf-connecting-ip") || "local";
  const h = await senderHash(ip, day, env.SALT || "");
  const ok = await env.DB.prepare("INSERT INTO sends (day, h, n) VALUES (?1, ?2, 1) ON CONFLICT (day, h) DO UPDATE SET n = n + 1 WHERE n < ?3 RETURNING n")
    .bind(day, h, PER_DAY).first();
  if (!ok) return json({ error: "At most " + PER_DAY + " themes a day, try again tomorrow." }, 429);
  const id = newId();
  // one statement per picture (a D1 row stays well under its ~2 MB), all or nothing
  await env.DB.batch([
    env.DB.prepare("INSERT INTO themes (id, at, status, name, by, lang, conf, preview, wall, bytes) VALUES (?1, ?2, 'pending', ?3, ?4, ?5, ?6, ?7, ?8, ?9)")
      .bind(id, Math.floor(Date.now() / 1000), t.name, t.by, t.lang, t.confJson, t.preview.buffer, t.wall ? t.wall.buffer : null, sizeOf(t)),
    ...t.photos.map(([k, u]) => env.DB.prepare("INSERT INTO wallpics (theme, key, pic) VALUES (?1, ?2, ?3)").bind(id, k, u.buffer)),
  ]);
  return json({ id }, 202);
}

async function admin(req, env, url) {
  if (!tokenOk(req.headers.get("authorization"), env.ADMIN_TOKEN)) return json({ error: "unauthorized" }, 401);
  const p = url.pathname;
  if (p === "/v1/admin/pending" && req.method === "GET") {
    const { results } = await env.DB.prepare("SELECT id, at, name, by, lang, conf FROM themes WHERE status = 'pending' ORDER BY at LIMIT 100").all();
    const pics = await env.DB.prepare("SELECT theme, key FROM wallpics ORDER BY theme, key").all();
    const keys = {}; for (const p of pics.results) (keys[p.theme] ||= []).push(p.key);
    const old = new Set((await env.DB.prepare("SELECT id FROM themes WHERE status = 'pending' AND wall IS NOT NULL").all()).results.map((x) => x.id));   // the single "wall" of an older app
    return json(results.map((x) => { const conf = JSON.parse(x.conf); return { id: x.id, at: x.at, name: x.name, by: x.by, lang: x.lang, conf, walls: keys[x.id] || [], has_wall: old.has(x.id), flags: flags({ ...x, conf }) }; }));
  }
  const w = /^\/v1\/admin\/([0-9a-f]{8})\/wall\/(photo(?:[2-9]|10)?)$/.exec(p);   // one of the rotation's photos
  if (w && req.method === "GET") {
    const row = await env.DB.prepare("SELECT pic AS b FROM wallpics WHERE theme = ?1 AND key = ?2").bind(w[1], w[2]).first();
    if (!row || !row.b) return json({ error: "no such picture" }, 404);
    return new Response(new Uint8Array(row.b), { headers: { "content-type": "image/jpeg", "cache-control": "no-store" } });
  }
  const m = /^\/v1\/admin\/([0-9a-f]{8})\/(preview|wall|approve|reject|done)$/.exec(p);
  if (!m) return json({ error: "not found" }, 404);
  const [, id, act] = m;
  if (req.method === "GET" && (act === "preview" || act === "wall")) {
    const row = await env.DB.prepare(`SELECT ${act} AS b FROM themes WHERE id = ?1`).bind(id).first();
    if (!row || !row.b) return json({ error: "no such picture" }, 404);
    const u = new Uint8Array(row.b);
    return new Response(u, { headers: { "content-type": kind(u) || "application/octet-stream", "cache-control": "no-store" } });
  }
  if (req.method !== "POST") return json({ error: "not found" }, 404);
  const sql = {
    approve: "UPDATE themes SET status = 'approved' WHERE id = ?1 AND status = 'pending'",
    reject: "UPDATE themes SET status = 'rejected', preview = NULL, wall = NULL, bytes = 0 WHERE id = ?1 AND status = 'pending'",
    done: "UPDATE themes SET status = 'done', preview = NULL, wall = NULL, bytes = 0 WHERE id = ?1 AND status = 'approved'",
  }[act];
  const r = await env.DB.prepare(sql).bind(id).run();
  if (!r.meta.changes) return json({ error: "no such theme in the right state" }, 409);
  if (act !== "approve") await env.DB.prepare("DELETE FROM wallpics WHERE theme = ?1").bind(id).run();   // rejected or saved: the photos go
  return json({ ok: true });
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (url.pathname === "/v1/themes") {
      if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: { ...CORS, "access-control-allow-methods": "POST", "access-control-allow-headers": "content-type", "access-control-max-age": "86400" } });
      if (req.method === "POST") return cors(await submit(req, env));
      return json({ error: "not found" }, 404);
    }
    if (url.pathname.startsWith("/v1/admin/")) return admin(req, env, url);
    return json({ error: "not found" }, 404);
  },
};
