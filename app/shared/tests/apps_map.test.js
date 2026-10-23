// The apps map (docs/data/apps-map.json) is the one source: the READMEs' mermaid blocks are generated from it
// (node dev/gen/apps_map.js), and every edge, install row and logo must point at something that exists.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const gen = require('../../../dev/gen/apps_map.js');

const map = gen.load();
const ids = new Set(map.nodes.map((n) => n.id));
const groups = new Set(map.groups.map((g) => g.id));
const LANGS = ['vi', 'en'];

test('READMEs hold the generated map (run node dev/gen/apps_map.js)', () => {
  for (const [lang, f] of Object.entries(gen.FILES)) {
    const text = fs.readFileSync(path.join(gen.ROOT, f), 'utf8');
    assert.strictEqual(gen.render(text, map, lang), text, `${f} differs from docs/data/apps-map.json`);
  }
});

test('every edge joins existing nodes or groups and has words in both languages', () => {
  assert.ok(map.edges.length > 0);
  for (const e of map.edges) {
    for (const end of [e.from, e.to]) assert.ok(ids.has(end) || groups.has(end), `edge end ${end} does not exist`);
    assert.ok(['ok', 'partial', 'silent'].includes(e.told), `told ${e.told}`);
    for (const k of ['tag', 'what', 'how', 'missing']) for (const l of LANGS) assert.ok(e[k] && e[k][l], `${e.from}->${e.to} ${k}.${l}`);
    assert.strictEqual(e.p.length, 4);
  }
});

test('nodes: a group that exists, both names, a logo file that exists', () => {
  for (const n of map.nodes) {
    assert.ok(groups.has(n.group), `${n.id} group`);
    for (const l of LANGS) assert.ok(n.name[l], `${n.id} name.${l}`);
    if (n.logo) {
      const f = path.join(gen.ROOT, 'docs', n.logo);
      assert.ok(fs.existsSync(f), `${n.logo} missing`);
      assert.ok(fs.statSync(f).size <= 20 * 1024, `${n.logo} over 20 KB`);
    } else assert.ok(n.glyph, `${n.id} has neither logo nor glyph`);
  }
});

test('install rows name existing apps; codes match the Downloader list', () => {
  const dl = JSON.parse(fs.readFileSync(path.join(gen.ROOT, 'docs/data/downloader.json'), 'utf8'));
  const known = JSON.stringify(dl);
  for (const a of map.install) {
    assert.ok(ids.has(a.app), `install app ${a.app}`);
    for (const l of LANGS) assert.ok(a.other[l], `${a.app} other.${l}`);
    if (a.code) assert.ok(known.includes(a.code), `code ${a.code} is not in downloader.json`);
  }
});
