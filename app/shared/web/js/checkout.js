// 🧳 Check-out (Trả phòng) on the TVs ticked in the several-TVs card: the part with no page in it, so node can test it
// (tests/checkout.test.js). The words come from fleet.js (T); the Kotlin twin is core/ontv/Checkout.kt (the token) + ManyTvs.kt.
(function (root) {
  const WEB = { old: 'fleet.co_web_old', missing: 'fleet.co_web_missing', failed: 'fleet.co_web_failed' };
  const STEP = { clear: 'fleet.co_step_clear', volume: 'fleet.co_step_volume', web: 'fleet.co_step_web', home: 'fleet.co_step_home' };
  // "checkout:c=3,f=1,v=25,w=ok,n=2,h=1,a=pkg+pkg,x=pkg" (Checkout.Result.token): cleared, failed, volume, web (ok|old|missing|failed), browsers forgotten, home,
  // then the packages cleared (a) and not cleared (x), when the TV's run knew them
  function checkoutParse(msg) {
    if (!/^checkout:/.test(msg || '')) return null;
    const kv = {};
    String(msg).slice(9).split(',').forEach((p) => { const i = p.indexOf('='); if (i > 0) kv[p.slice(0, i)] = p.slice(i + 1); });
    return { cleared: +kv.c || 0, failed: +kv.f || 0, volume: +kv.v || 0, web: kv.w || '', forgot: +kv.n || 0, home: kv.h === '1',
      apps: kv.a ? kv.a.split('+') : [], failedApps: kv.x ? kv.x.split('+') : [] };
  }
  // the result of one TV as rows, one per step, for the dialog at the end: [{ kind, state: ok|warn|bad, icon, id, args, fix? }], the words
  // of each by id (fleet.co_r_*). [nameOf] turns a package into the app's name. A TV Setup too old (or none) is a warning with its fix.
  function checkoutReport(msg, nameOf) {
    const r = checkoutParse(msg); if (!r) return null;
    const name = nameOf || ((p) => p), list = (a) => a.map(name).join(', ');
    const rows = [];
    if (r.apps.length) rows.push({ kind: 'clear', state: 'ok', icon: 'eraser', id: 'fleet.co_r_clear', args: { apps: list(r.apps) } });
    else if (r.cleared) rows.push({ kind: 'clear', state: 'ok', icon: 'eraser', id: 'fleet.co_cleared', args: { n: r.cleared }, cap: true });
    else if (!r.failed) rows.push({ kind: 'clear', state: 'ok', icon: 'eraser', id: 'fleet.co_r_clear_none', args: {} });
    if (r.failed) rows.push({ kind: 'clear', state: 'bad', icon: 'eraser', ...(r.failedApps.length ? { id: 'fleet.co_r_failed', args: { apps: list(r.failedApps) } } : { id: 'fleet.co_failed', args: { n: r.failed }, cap: true }) });
    rows.push({ kind: 'volume', state: 'ok', icon: 'vol_up', id: 'fleet.co_r_vol', args: { v: r.volume } });
    if (r.web === 'ok') rows.push({ kind: 'web', state: 'ok', icon: 'globe', ...(r.forgot ? { id: 'fleet.co_r_web_ok', args: { n: r.forgot } } : { id: 'fleet.co_r_web_0', args: {} }) });
    else rows.push({ kind: 'web', state: 'warn', icon: 'globe', id: 'fleet.co_r_web_' + (WEB[r.web] ? r.web : 'failed'), args: {}, fix: r.web === 'old' ? 'update' : r.web === 'missing' ? 'install' : null });
    if (r.home) rows.push({ kind: 'home', state: 'ok', icon: 'home', id: 'fleet.co_r_home', args: {} });
    return rows;
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
  root.checkoutReport = checkoutReport;
  root.checkoutRows = checkoutRows;
  if (typeof module !== 'undefined') module.exports = { checkoutParse, checkoutReport, checkoutPieces, checkoutStep, checkoutRows };
})(typeof window !== 'undefined' ? window : globalThis);
