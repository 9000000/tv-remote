// Ping and clock offset between a phone and the TV, from the ping/pong exchange (pure; used by the pad page and its node test).
// A sample: the phone sent at t0 (its clock), got the TV's reply at t1 (its clock), and the TV stamped the reply tv (the TV's clock).
// rtt = t1 - t0; the TV's clock minus the phone's = tv - (t0 + rtt / 2) (the reply is assumed to take half the trip).
(function (root) {
  'use strict';
  function median(a) {
    if (!a.length) return null;
    const s = a.slice().sort((x, y) => x - y), m = s.length >> 1;
    return s.length % 2 ? s[m] : Math.round((s[m - 1] + s[m]) / 2);
  }
  function Tracker(n) {
    const keep = n || 5, rows = [];
    return {
      add(t0, t1, tv) {
        const rtt = Math.max(0, t1 - t0);
        rows.push({ rtt, off: tv == null ? null : tv - (t0 + rtt / 2) });
        if (rows.length > keep) rows.shift();
      },
      ping() { return median(rows.map((r) => r.rtt)); },
      // the sample with the shortest trip has the least error in its offset
      offset() { let b = null; rows.forEach((r) => { if (r.off != null && (!b || r.rtt < b.rtt)) b = r; }); return b ? Math.round(b.off) : null; },
      size() { return rows.length; },
    };
  }
  // green under 30 ms, amber under 80, red above
  function level(ms) { return ms == null ? 'none' : ms < 30 ? 'good' : ms < 80 ? 'mid' : 'bad'; }
  const api = { median, Tracker, level };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.PingMath = api;
})(typeof self !== 'undefined' ? self : this);
