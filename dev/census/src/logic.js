// Pure functions of the census Worker (no Worker APIs, so node can test them).
export const MAX_BODY = 4096;
const UUID4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const APPS = ["setup", "phone", "pc"];
const OKS = ["adb", "video", "optimize"];

// A string cleaned for storage: no control characters, trimmed, at most 64 characters. Not a string -> "".
export function str(x, max = 64) {
  if (typeof x !== "string") return "";
  return x.replace(/[\u0000-\u001f\u007f]/g, " ").replace(/\s+/g, " ").trim().slice(0, max);
}

const flag = (x) => (x === true ? 1 : x === false ? 0 : null);

// body text -> the row to store, or null (malformed). Unknown fields are dropped.
export function validate(text) {
  if (typeof text !== "string" || text.length > MAX_BODY) return null;
  let b;
  try { b = JSON.parse(text); } catch { return null; }
  if (!b || typeof b !== "object" || Array.isArray(b)) return null;
  if (typeof b.id !== "string" || !UUID4.test(b.id)) return null;
  if (!APPS.includes(b.app)) return null;
  const t = b.tv;
  if (!t || typeof t !== "object" || Array.isArray(t)) return null;
  const brand = str(t.brand), model = str(t.model);
  if (!brand || !model) return null;
  const sdk = Number.isInteger(t.sdk) && t.sdk > 0 && t.sdk < 100 ? t.sdk : null;
  const ok = b.ok && typeof b.ok === "object" && !Array.isArray(b.ok) ? b.ok : {};
  const row = {
    id: b.id.toLowerCase(), app: b.app, v: str(b.v, 32),
    brand, model, device: str(t.device), board: str(t.board), hw: str(t.hw), soc: str(t.soc),
    android: str(typeof t.android === "number" ? String(t.android) : t.android, 16), sdk,
    leanback: t.leanback === true ? 1 : 0,
  };
  for (const k of OKS) row[k] = flag(ok[k]);
  return row;
}

export async function hashId(id) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(id));
  return [...new Uint8Array(d)].slice(0, 8).map((x) => x.toString(16).padStart(2, "0")).join("");
}

export const today = (now = new Date()) => now.toISOString().slice(0, 10);

// Kind of device from its names: a projector, a box / stick, else a TV set. `leanback` is not used: the phone and computer apps
// also report LG / Samsung / Roku TVs, which are not leanback.
const PROJ = /projector|\bproj\b|xgimi|wanbo|dangbei|nebula|benq|epson|hy3[0-9]{2}|\bmogo\b|\bhalo\b|horizon pro|\bz6x?\b|lumi/i;
const BOX = /\b(box|stick|stb|dongle|chromecast|streamer|shield|x96|h96|mecool|ugoos|tanix|beelink|s905\w*|s922\w*|amlogic|rockchip|fpt|viettel|vtvcab|mytv|vnpt|k\+|tv ?box|settop|set-top|firetv|fire tv|aftm?\w*|roku (ultra|express|streaming)|apple tv)\b|mi ?box|tv ?box|\bdv\d{4}|\bmxq|\bt95|\bh96|\bkm\d|\bbox\b/i;
export function kindOf(brand, model, device) {
  const s = [brand, model, device].join(" ");
  if (PROJ.test(s)) return "projector";
  return BOX.test(s) ? "box" : "tv";
}

const verKey = (a) => parseFloat(a) || 0;

// rows: SELECT brand, model, android, leanback, COUNT(*) n, SUM(adb=1) adb_y, COUNT(adb) adb_n, ... , MAX(last) last GROUP BY brand, model, device, android, leanback
export function aggregate(rows, now = new Date()) {
  const by = new Map();
  for (const r of rows) {
    const key = r.brand + "\u0000" + r.model;
    let m = by.get(key);
    if (!m) by.set(key, (m = { brand: r.brand, model: r.model, count: 0, android: new Set(), devices: new Set(), tv: 0, box: 0, projector: 0, last: "", c: {} }));
    const n = Number(r.n) || 0;
    m.count += n;
    if (r.android) m.android.add(r.android);
    if (r.device && r.device !== r.model && m.devices.size < 4) m.devices.add(r.device);
    m[kindOf(r.brand, r.model, r.device)] += n;
    if ((r.last || "") > m.last) m.last = r.last;
    for (const k of OKS) {
      const c = (m.c[k] ||= { y: 0, n: 0 });
      c.y += Number(r[k + "_y"]) || 0;
      c.n += Number(r[k + "_n"]) || 0;
    }
  }
  const models = [...by.values()].map((m) => ({
    brand: m.brand, model: m.model, count: m.count,
    android: [...m.android].sort((a, b) => verKey(b) - verKey(a)),
    devices: [...m.devices].sort(),
    kind: ["tv", "box", "projector"].reduce((k, x) => (m[x] > m[k] ? x : k), "tv"),
    ok: Object.fromEntries(OKS.map((k) => [k, { n: m.c[k].n, rate: m.c[k].n ? Math.round((m.c[k].y / m.c[k].n) * 100) / 100 : null }])),
    last: m.last,
  })).sort((a, b) => b.count - a.count || a.brand.localeCompare(b.brand) || a.model.localeCompare(b.model));
  return { updated: today(now), installs: models.reduce((s, m) => s + m.count, 0), models };
}

// rows: SELECT day, app, active, new FROM days ORDER BY day -> one entry a day, apps side by side, and the running total of installs
export function series(rows, now = new Date()) {
  const by = new Map();
  for (const r of rows) {
    let d = by.get(r.day);
    if (!d) by.set(r.day, (d = { day: r.day, active: {}, new: {} }));
    if (r.active != null) d.active[r.app] = Number(r.active) || 0;
    d.new[r.app] = Number(r.new) || 0;
  }
  let total = 0;
  const days = [...by.values()].sort((a, b) => (a.day < b.day ? -1 : 1)).map((d) => {
    total += Object.values(d.new).reduce((s, n) => s + n, 0);
    return { ...d, total };
  });
  return { updated: today(now), installs: total, days };
}
