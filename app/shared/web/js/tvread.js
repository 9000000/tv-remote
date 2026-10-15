// Reading what the TV printed for the Apps tab and the TV card (the recipes are app/web/tvshell.py and TvShell.kt). Kept apart
// from the page so node can test it: node --test app/web/tests/
((root) => {
  const STAMP = /^\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}$/;

  // 🧹 "free blocks, block size" before and after: the bytes that came back (never below zero: apps keep writing meanwhile)
  function cleanFreed(text) {
    const n = String(text || '').trim().split(/\s+/).map(Number);
    if (n.length < 4 || n.some((x) => !Number.isFinite(x))) return null;
    return Math.max(0, n[2] * n[3] - n[0] * n[1]);
  }

  // 📜 one poll of an app's log: "pid <n>" then threadtime lines. The next poll asks from the last line's stamp on, so lines
  // with that stamp come again; `st` ({ last, seen }) remembers them so each line is shown once.
  function logNext(st, text) {
    const rows = String(text || '').replace(/\r/g, '').split('\n');
    const pid = (rows.shift() || '').replace(/^pid\s*/, '').trim().split(/\s+/)[0] || '';
    const lines = [];
    for (const l of rows) {
      if (!l.trim() || l.startsWith('---------')) continue;            // "--------- beginning of main"
      const at = l.slice(0, 18);
      if (STAMP.test(at)) {
        const newYear = st.last.startsWith('12-') && at.startsWith('01-');   // the stamps have no year
        if ((at < st.last && !newYear) || (at === st.last && st.seen.has(l))) continue;
        if (at !== st.last) { st.last = at; st.seen = new Set(); }
        st.seen.add(l);
      }
      lines.push(l);
    }
    return { pid, lines };
  }
  const logLevel = (l) => (/^\S+ \S+\s+\d+\s+\d+ ([VDIWEF]) /.exec(l) || [])[1] || '';

  // ⚙️ the TV card's "name value" lines
  function readTvset(text) {
    const o = { sdk: 0, dnsMode: '', dnsHost: '', sleep: null, saver: false, saverAfter: null, physical: 0, override: 0, home: '', launchers: [] };
    const val = (v) => (v === 'null' || v === undefined ? '' : v);
    for (const raw of String(text || '').replace(/\r/g, '').split('\n')) {
      const l = raw.trim(), sp = l.indexOf(' '), k = sp < 0 ? l : l.slice(0, sp), v = sp < 0 ? '' : l.slice(sp + 1).trim();
      if (k === 'sdk') o.sdk = parseInt(v, 10) || 0;
      else if (k === 'dns_mode') o.dnsMode = val(v);
      else if (k === 'dns_host') o.dnsHost = val(v);
      else if (k === 'sleep') o.sleep = /^-?\d+$/.test(v) ? Number(v) : null;
      else if (k === 'saver') o.saver = v === '1';
      else if (k === 'saver_after') o.saverAfter = /^\d+$/.test(v) ? Number(v) : null;
      else if (/^Physical density:/.test(l)) o.physical = parseInt(l.split(':')[1], 10) || 0;
      else if (/^Override density:/.test(l)) o.override = parseInt(l.split(':')[1], 10) || 0;
      else if (k === 'home') o.home = v.includes('/') ? v : '';
      else if (k === 'launcher' && v.includes('/') && !/FallbackHome/.test(v)) o.launchers.push(v);
    }
    if (o.home && !o.launchers.includes(o.home)) o.launchers.unshift(o.home);
    return o;
  }

  // 🔠 the sizes offered, from the screen's own density: bigger density = bigger everything
  function densityChoices(physical) {
    if (!physical) return [];
    return [['Nhỏ', Math.round(physical * 0.9)], ['Vừa', 'reset'], ['Lớn', Math.round(physical * 1.1)], ['Rất lớn', Math.round(physical * 1.2)]];
  }

  root.cleanFreed = cleanFreed; root.logNext = logNext; root.logLevel = logLevel; root.readTvset = readTvset; root.densityChoices = densityChoices;
  if (typeof module !== 'undefined') module.exports = { cleanFreed, logNext, logLevel, readTvset, densityChoices };
})(typeof window !== 'undefined' ? window : globalThis);
