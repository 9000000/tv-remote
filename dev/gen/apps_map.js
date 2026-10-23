#!/usr/bin/env node
// Writes the mermaid map of how the apps connect into README.md (en) and README.vi.md (vi), between
// <!-- apps-map:start --> and <!-- apps-map:end -->, from docs/data/apps-map.json (the one source; docs/apps.html draws the same file).
// Run after editing the JSON. app/shared/tests/apps_map.test.js fails when a README differs from this output.
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '../..');
const FILES = { en: 'README.md', vi: 'README.vi.md' };
const START = '<!-- apps-map:start -->', END = '<!-- apps-map:end -->';
const ARROW = { ok: '-->', partial: '-.->', silent: '==>' };         // told: solid, dashed, thick

const load = () => JSON.parse(fs.readFileSync(path.join(ROOT, 'docs/data/apps-map.json'), 'utf8'));
const q = (s) => String(s).replace(/"/g, '&quot;');

function mermaid(map, lang) {
  const out = ['```mermaid', 'flowchart LR'];
  for (const g of map.groups) {
    out.push(`  subgraph ${g.id}["${q(g.name[lang])}"]`);
    for (const n of map.nodes.filter((x) => x.group === g.id)) {
      const sub = n.sub ? `<br/><small>${q(n.sub[lang])}</small>` : '';
      out.push(`    ${n.id}["${q(n.name[lang])}${sub}"]`);
    }
    out.push('  end');
  }
  map.edges.forEach((e, i) => {
    out.push(`  ${e.from} ${ARROW[e.told]}|"${i + 1} ${q(e.tag[lang])}"| ${e.to}`);
  });
  out.push('```');
  return out.join('\n');
}

function render(text, map, lang) {
  const a = text.indexOf(START), b = text.indexOf(END);
  if (a < 0 || b < a) throw new Error('markers missing');
  return text.slice(0, a + START.length) + '\n' + mermaid(map, lang) + '\n' + text.slice(b);
}

module.exports = { mermaid, render, load, FILES, ROOT };

if (require.main === module) {
  const map = load();
  for (const [lang, f] of Object.entries(FILES)) {
    const p = path.join(ROOT, f);
    const cur = fs.readFileSync(p, 'utf8'), next = render(cur, map, lang);
    if (cur !== next) fs.writeFileSync(p, next);
    console.log(f, cur !== next ? 'updated' : 'unchanged');
  }
}
