// A country's channels in ready-made categories (docs/channels/categories/<cc>.json, the file TV Channels reads too; no file, no categories).
// ChanCat.norm() makes a channel name comparable, ChanCat.categorize() sorts a list into the file's categories. The rules are fixed by
// the file's schema and tests/fixtures/norm.json (the TV app's Kotlin passes the same ones): keep both sides equal.
const ChanCat = {
  // NFD, no combining marks, đ (and the look-alike eth) -> d, lower case, only a-z0-9, then trailing "hd" / "fhd" / "uhd" / "sd" / "4k" taken off, again and again
  norm(name) {
    let s = String(name || '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[đĐ\u00f0\u00d0]/g, 'd').toLowerCase().replace(/[^a-z0-9]/g, '');
    for (let m; (m = /(fhd|uhd|hd|sd|4k)$/.exec(s));) s = s.slice(0, m.index);
    return s;
  },
  // the entries of one category as testers, in order: "re:<regex>" tests the normalized name, anything else must equal it
  matchers(cat) {
    return (cat.match || []).map((m) => {
      if (/^re:/.test(m)) { try { const re = new RegExp(m.slice(3)); return (n) => re.test(n); } catch (e) { return () => false; } }
      return (n) => n === m;   // the file's entries are normalized already
    });
  },
  // channels: [{ n: name, ... }]; doc: the parsed categories file. -> { cats: [{ id, vi, en, list }] (only the ones with a channel), other: [] }
  // A channel goes in every category it matches; inside one, by the index of its first matching entry, then by name.
  categorize(channels, doc) {
    // one shelf per category: the same id, or the same title in every language both carry, is one (its entries joined, as the TV app does)
    const merged = [];
    for (const c of (doc && doc.categories) || []) {
      const same = merged.find((m) => m.id === c.id || (['vi', 'en'].some((k) => m[k] && c[k]) && ['vi', 'en'].every((k) => !(m[k] && c[k]) || String(m[k]).toLowerCase() === String(c[k]).toLowerCase())));
      if (same) same.match = same.match.concat(c.match || []); else merged.push({ id: c.id, vi: c.vi, en: c.en, match: (c.match || []).slice() });
    }
    const cats = merged.map((c) => ({ id: c.id, vi: c.vi, en: c.en, tests: ChanCat.matchers(c), hit: [] }));
    const other = [];
    for (const ch of channels) {
      const n = ChanCat.norm(ch.n); let any = false;
      for (const c of cats) {
        const i = c.tests.findIndex((t) => t(n));
        if (i >= 0) { c.hit.push([i, ch]); any = true; }
      }
      if (!any) other.push(ch);
    }
    const byName = (a, b) => String(a.n).localeCompare(String(b.n));
    return {
      cats: cats.filter((c) => c.hit.length).map((c) => ({ id: c.id, vi: c.vi, en: c.en, list: c.hit.sort((a, b) => a[0] - b[0] || byName(a[1], b[1])).map((x) => x[1]) })),
      other: other.sort(byName),
    };
  },
};
