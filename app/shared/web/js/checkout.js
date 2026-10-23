// 🧳 Check-out (Trả phòng) on the TVs ticked in the several-TVs card: the part with no page in it, so node can test it
// (tests/checkout.test.js). The words come from fleet.js (T); the Kotlin twin is core/ontv/Checkout.kt (the token) + ManyTvs.kt.
(function (root) {
  const WEB = { old: 'fleet.co_web_old', missing: 'fleet.co_web_missing', failed: 'fleet.co_web_failed' };
  const STEP = { clear: 'fleet.co_step_clear', volume: 'fleet.co_step_volume', web: 'fleet.co_step_web', home: 'fleet.co_step_home' };
  // "checkout:c=3,f=1,v=25,w=ok,n=2,h=1" (Checkout.Result.token): cleared, failed, volume, web (ok|old|missing|failed), browsers forgotten, home
  function checkoutParse(msg) {
    if (!/^checkout:/.test(msg || '')) return null;
    const kv = {};
    String(msg).slice(9).split(',').forEach((p) => { const i = p.indexOf('='); if (i > 0) kv[p.slice(0, i)] = p.slice(i + 1); });
    return { cleared: +kv.c || 0, failed: +kv.f || 0, volume: +kv.v || 0, web: kv.w || '', forgot: +kv.n || 0, home: kv.h === '1' };
  }
  // the TV's line as [word id, args] pieces, joined by the caller: a TV Setup too old (or none) is said, never left out
  function checkoutPieces(msg) {
    const r = checkoutParse(msg);
    if (!r) return null;
    const out = [];
    if (r.cleared || !r.failed) out.push(['fleet.co_cleared', { n: r.cleared }]);
    if (r.failed) out.push(['fleet.co_failed', { n: r.failed }]);
    out.push(['fleet.co_vol', { v: r.volume }]);
    out.push(r.web === 'ok' ? (r.forgot ? ['fleet.co_web_ok', { n: r.forgot }] : ['fleet.co_web_0', {}]) : [WEB[r.web] || WEB.failed, {}]);
    if (r.home) out.push(['fleet.co_home', {}]);
    return out;
  }
  // a step under way: "checkout clear 2/5" -> its words' id and args
  function checkoutStep(msg) {
    const m = /^checkout (clear|volume|web|home)(?: (\d+)\/(\d+))?$/.exec(msg || '');
    return m ? [STEP[m[1]], { i: +m[2] || 0, n: +m[3] || 0 }] : null;
  }
  // the apps to offer: those on the TVs ticked (not the blocked ones), most common first; ticked are the owner's saved list, or,
  // never chosen (saved null), the suggestions that are installed. Returns [{ pkg, count, on }].
  function checkoutRows(tvs, blocked, saved, suggest) {
    const count = new Map(), no = new Set(blocked || []);
    (tvs || []).forEach((t) => (t.apps || []).concat(t.also || []).filter((p, i, a) => a.indexOf(p) === i).forEach((p) => { if (!no.has(p)) count.set(p, (count.get(p) || 0) + 1); }));
    const want = new Set(saved || suggest || []);
    return [...count].map(([pkg, n]) => ({ pkg, count: n, on: want.has(pkg) }))
      .sort((a, b) => b.on - a.on || b.count - a.count || a.pkg.localeCompare(b.pkg));
  }
  root.checkoutParse = checkoutParse;
  root.checkoutPieces = checkoutPieces;
  root.checkoutStep = checkoutStep;
  root.checkoutRows = checkoutRows;
  if (typeof module !== 'undefined') module.exports = { checkoutParse, checkoutPieces, checkoutStep, checkoutRows };
})(typeof window !== 'undefined' ? window : globalThis);
