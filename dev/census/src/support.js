// Support for a TV nobody here can reach (an FPT Play behind an ISP's lock, 2026-10-25: AnyDesk, then Tailscale, then a pairing
// code read over TeamViewer, an evening for four answers). TV Setup sends its facts on a button press and shows the code it
// gets back; with a second button it lets the owner read its own /api for a while: it asks here every 2 s for what to GET
// and posts the answers. Only the owner (CRASH_TOKEN) reads reports and asks; the TV holds a key for its code.
import { hashId, text, str, sameSecret } from "./logic.js";

export const MAX_REPORT = 64 * 1024;
export const MAX_ANSWER = 256 * 1024;
export const SENDS_PER_DAY = 20;
export const ALL_PER_DAY = 2000;
export const LIVE_MAX_MIN = 60;
export const KEEP_DAYS = 14;
const ASK_TTL_MS = 60_000;                                            // a question the TV did not take within a minute is dropped

const hex = (n) => [...crypto.getRandomValues(new Uint8Array(n))].map((x) => x.toString(16).padStart(2, "0")).join("");
const digits = () => String(crypto.getRandomValues(new Uint32Array(1))[0] % 100_000_000).padStart(8, "0");
const UUID4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

// What the owner may ask for: the TV's own read-only routes (TV Setup checks again on its side, Support.kt).
export function askable(path) {
  return typeof path === "string" && path.length <= 300 && /^\/api\/[a-z0-9/_-]*(\?[\w=&%.,:-]*)?$/i.test(path);
}

async function body(req, max) {
  if (Number(req.headers.get("content-length") || 0) > max) return null;
  const t = await req.text();
  if (t.length > max) return null;
  try { const b = JSON.parse(t); return b && typeof b === "object" && !Array.isArray(b) ? b : null; } catch { return null; }
}

// The TV's code and key -> its row, or null.
async function own(env, b) {
  if (typeof b.code !== "string" || !/^\d{8}$/.test(b.code) || typeof b.key !== "string") return null;
  const row = await env.DB.prepare("SELECT * FROM support WHERE code = ?1").bind(b.code).first();
  return row && (await sameSecret(await hashId(b.key), row.key)) ? row : null;
}

export async function support(req, env, url, json) {
  const p = url.pathname, now = Date.now();

  if (p === "/v1/support" && req.method === "POST") {                // the TV sends its facts, gets a code to read out
    const b = await body(req, MAX_REPORT);
    if (!b || typeof b.id !== "string" || !UUID4.test(b.id)) return json({ error: "bad body" }, 400);
    const h = await hashId(b.id.toLowerCase());
    const n = await env.DB.prepare("SELECT COUNT(*) AS n FROM support WHERE h = ?1 AND made > ?2").bind(h, now - 86_400_000).first();
    const used = n ? n.n : 0;
    if (used >= SENDS_PER_DAY) return json({ error: "enough for today", left: 0 }, 429);
    // ids are the sender's own: a cap for everyone too, so random ids cannot fill the table
    const all = await env.DB.prepare("SELECT COUNT(*) AS n FROM support WHERE made > ?1").bind(now - 86_400_000).first();
    if (all && all.n >= ALL_PER_DAY) return json({ error: "busy today" }, 429);
    const key = hex(16);
    for (let i = 0; i < 5; i++) {
      const code = digits();
      const r = await env.DB.prepare("INSERT INTO support (code, key, h, app, v, model, report, made, live, seen) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,0,?8) ON CONFLICT DO NOTHING")
        .bind(code, await hashId(key), h, str(b.app, 16), str(b.v, 32), str(b.model, 64), text(b.report, MAX_REPORT), now).run();
      if (r.meta && r.meta.changes > 0) return json({ code, key, keep_days: KEEP_DAYS, left: SENDS_PER_DAY - used - 1 });   // the TV says how many sends today has left
    }
    return json({ error: "busy" }, 503);
  }

  if (p === "/v1/support/live" && req.method === "POST") {           // the TV opens (minutes > 0) or closes (0) its reading
    const b = await body(req, 1024), row = b && (await own(env, b));
    if (!row) return json({ error: "forbidden" }, 403);
    const min = Math.max(0, Math.min(LIVE_MAX_MIN, Number(b.minutes) || 0));
    const live = min ? now + min * 60_000 : 0;
    await env.DB.prepare("UPDATE support SET live = ?2, seen = ?3 WHERE code = ?1").bind(row.code, live, now).run();
    if (!live) await env.DB.prepare("DELETE FROM support_q WHERE code = ?1 AND body IS NULL").bind(row.code).run();
    return json({ live, now });                                       // now: the TV counts the minutes by its own clock
  }

  if (p === "/v1/support/poll" && req.method === "POST") {           // the TV, every 2 s while open: what to GET
    const b = await body(req, 1024), row = b && (await own(env, b));
    if (!row) return json({ error: "forbidden" }, 403);
    await env.DB.prepare("UPDATE support SET seen = ?2 WHERE code = ?1").bind(row.code, now).run();
    if (row.live <= now) return json({ live: 0, now, asks: [] });
    const { results } = await env.DB.prepare("SELECT id, path FROM support_q WHERE code = ?1 AND body IS NULL AND taken = 0 AND asked > ?2 ORDER BY id LIMIT 5")
      .bind(row.code, now - ASK_TTL_MS).all();
    if (results.length) await env.DB.prepare(`UPDATE support_q SET taken = 1 WHERE code = ?1 AND id IN (${results.map((r) => r.id).join(",")})`).bind(row.code).run();
    return json({ live: row.live, now, asks: results });
  }

  if (p === "/v1/support/answer" && req.method === "POST") {         // the TV's answer to one question
    const b = await body(req, MAX_ANSWER + 4096), row = b && (await own(env, b));
    if (!row) return json({ error: "forbidden" }, 403);
    if (!Number.isInteger(b.id)) return json({ error: "bad body" }, 400);
    const status = Number.isInteger(b.status) ? b.status : 0;
    await env.DB.prepare("UPDATE support_q SET status = ?3, body = ?4, answered = ?5 WHERE code = ?1 AND id = ?2")
      .bind(row.code, b.id, status, text(String(b.body ?? ""), MAX_ANSWER), now).run();
    return new Response(null, { status: 204 });
  }

  // ---- the owner's side, behind CRASH_TOKEN like the crashes ----
  const auth = (req.headers.get("authorization") || "").replace(/^Bearer\s+/i, "");
  if (!(await sameSecret(auth, env.CRASH_TOKEN))) return json({ error: "forbidden" }, 403);
  const code = url.searchParams.get("code") || "";

  if (p === "/v1/support" && req.method === "GET") {
    if (!code) {
      const { results } = await env.DB.prepare("SELECT code, app, v, model, made, live, seen FROM support ORDER BY made DESC LIMIT 50").all();
      return json({ now, support: results });
    }
    const row = await env.DB.prepare("SELECT code, app, v, model, report, made, live, seen FROM support WHERE code = ?1").bind(code).first();
    return row ? json({ now, ...row }) : json({ error: "no such code" }, 404);
  }
  if (p === "/v1/support/ask" && req.method === "POST") {
    const b = await body(req, 2048);
    if (!b || !askable(b.path)) return json({ error: "path" }, 400);
    const row = await env.DB.prepare("SELECT live FROM support WHERE code = ?1").bind(String(b.code || "")).first();
    if (!row) return json({ error: "no such code" }, 404);
    if (row.live <= now) return json({ error: "not open: the TV has to press Let support look" }, 409);
    const r = await env.DB.prepare("INSERT INTO support_q (code, id, path, asked, taken) VALUES (?1, (SELECT COALESCE(MAX(id), 0) + 1 FROM support_q WHERE code = ?1), ?2, ?3, 0) RETURNING id")
      .bind(String(b.code), b.path, now).first();
    return json({ id: r.id });
  }
  if (p === "/v1/support/answer" && req.method === "GET") {
    const row = await env.DB.prepare("SELECT path, status, body, asked, answered FROM support_q WHERE code = ?1 AND id = ?2").bind(code, Number(url.searchParams.get("id"))).first();
    if (!row) return json({ error: "no such question" }, 404);
    return json(row.answered ? row : { pending: true, waited_ms: now - row.asked });
  }
  if (p === "/v1/support" && req.method === "DELETE" && code) {
    await env.DB.prepare("DELETE FROM support WHERE code = ?1").bind(code).run();
    await env.DB.prepare("DELETE FROM support_q WHERE code = ?1").bind(code).run();
    return json({ ok: true });
  }
  return json({ error: "not found" }, 404);
}

// The cron: reports kept KEEP_DAYS, questions a day.
export function prune(env, now = Date.now()) {
  return Promise.all([
    env.DB.prepare("DELETE FROM support WHERE made < ?1").bind(now - KEEP_DAYS * 86_400_000).run(),
    env.DB.prepare("DELETE FROM support_q WHERE asked < ?1").bind(now - 86_400_000).run(),
  ]);
}
