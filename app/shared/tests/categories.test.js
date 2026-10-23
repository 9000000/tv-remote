// Ready-made channel categories per country (docs/channels/categories/<cc>.json) and the shared norm() vectors.
// The apps know no country: a file here is all it takes to give one its categories.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..', '..', '..');
const dir = path.join(root, 'docs', 'channels', 'categories');
const files = fs.readdirSync(dir).filter((f) => f.endsWith('.json'));
const vectors = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'norm.json'), 'utf8'));

// Reference norm(): NFD, drop combining marks, d-stroke -> d, lowercase, keep [a-z0-9], strip trailing hd|fhd|uhd|sd|4k.
function norm(name) {
  let s = name.normalize('NFD').replace(/\p{M}/gu, '').replace(/[đĐ]/g, 'd').toLowerCase().replace(/[^a-z0-9]/g, '');
  for (let p = null; p !== s;) { p = s; s = s.replace(/(hd|fhd|uhd|sd|4k)$/, ''); }
  return s;
}
const VN_ORDER = ['vtv', 'htv', 'vtvcab', 'sctv', 'vtc', 'local', 'sports', 'movies', 'kids', 'docs', 'music', 'entertainment', 'news', 'intl'];
const load = (f = 'vn.json') => JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
const cats = (n) => load().categories.filter((c) => c.match.some((m) => m.startsWith('re:') ? new RegExp(m.slice(3)).test(n) : m === n)).map((c) => c.id);

test('norm vectors', () => {
  assert.ok(vectors.norm.length >= 10);
  for (const v of vectors.norm) assert.strictEqual(norm(v.in), v.out, JSON.stringify(v.in));
});

test('vn order', () => {
  assert.deepStrictEqual(load().categories.map((c) => c.id), VN_ORDER);
});

for (const f of files) test('schema ' + f, () => {
  assert.match(f, /^[a-z]{2}\.json$/);                               // the country code the apps ask for
  const j = load(f);
  assert.strictEqual(j.v, 1);
  assert.match(j.updated, /^\d{4}-\d{2}-\d{2}$/);
  assert.strictEqual(new Set(j.categories.map((c) => c.id)).size, j.categories.length);
  for (const c of j.categories) {
    assert.deepStrictEqual(Object.keys(c).sort(), ['en', 'id', 'match', 'vi']);
    assert.ok(c.vi && c.en && c.match.length > 0, c.id);
    assert.strictEqual(new Set(c.match).size, c.match.length, 'duplicate match in ' + c.id);
    for (const m of c.match) {
      assert.strictEqual(typeof m, 'string');
      if (m.startsWith('re:')) assert.doesNotThrow(() => new RegExp(m.slice(3)), m);
      else assert.strictEqual(norm(m), m, 'plain entry must be normalized: ' + m);
    }
  }
});

test('vn: "also" names other countries\' iptv-org ids, once each; titles are unique', () => {
  const j = load();
  assert.ok(Array.isArray(j.also) && j.also.length > 20);
  const ids = j.also.map((e) => (typeof e === 'string' ? e : e.id));       // a bare id, or {id, name} for a card name the index spells oddly
  for (const e of j.also) if (typeof e !== 'string') assert.deepStrictEqual(Object.keys(e).sort(), ['id', 'name']);
  for (const id of ids) assert.match(id, /^[A-Za-z0-9]+\.[a-z]{2}$/, id);
  assert.strictEqual(new Set(ids).size, ids.length);
  const t = j.categories.map((c) => c.vi.toLowerCase());
  assert.strictEqual(new Set(t).size, t.length, 'two categories with one title');
  for (const n of ['cartoonnetworkasia', 'nickjr', 'babytv', 'disneyjunior', 'hbo', 'cinemax', 'espn', 'cnninternational', 'nationalgeographic', 'discoverychannel', 'mtv']) assert.ok(cats(n).length > 0, n);
  assert.ok(!cats('foxsports1').includes('movies'));
});

test('known channels land where expected', () => {
  const want = { 'vtv1': ['vtv', 'news'], 'vtv6': ['vtv', 'sports'], 'htv9': ['htv', 'news'], 'sctv9': ['sctv', 'movies'], 'onsports': ['vtvcab', 'sports'], 'cartoonnetwork': ['kids', 'intl'],
    'thvl1': ['local'], 'danang1': ['local'], 'dongnai3': ['local'], 'vtc10': ['vtc'], 'vtv5taynambo': ['vtv'], 'htvcphim': ['htv', 'movies'], 'hbo': ['movies', 'intl'], 'vtvcab5': ['vtvcab'], 'khanhhoa1': ['local'], 'onsportsnews': ['vtvcab', 'sports'] };
  for (const [n, ids] of Object.entries(want)) { const got = cats(n); for (const i of ids) assert.ok(got.includes(i), n + ' should be in ' + i + ', got ' + got); }
  assert.ok(!cats('onsportsnews').includes('news'));
  assert.deepStrictEqual(cats('vohfm999mhz'), []);
});
