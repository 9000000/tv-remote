// node --test app/shared/tests/   js/chancat.js (ChanCat): norm() gives what tests/fixtures/norm.json says (the TV app's Kotlin reads the
// same table), and categorize() puts a channel in every category it matches, in the order the file lists its entries.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const sandbox = {};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../web/js/chancat.js'), 'utf8').replace(/^(let|const) /gm, 'var ') + '\nthis.ChanCat = ChanCat;', sandbox);
const V = sandbox.ChanCat;

const fx = path.join(__dirname, 'fixtures/norm.json');
const j = fs.existsSync(fx) ? JSON.parse(fs.readFileSync(fx, 'utf8')) : [];
const cases = Array.isArray(j) ? j : j.norm || [];   // the table, bare or under "norm"
for (const c of cases) test(`norm ${JSON.stringify(c.in)}`, () => assert.equal(V.norm(c.in), c.out));

test('norm: accents, đ, case, punctuation, repeated quality suffixes', () => {
  assert.equal(V.norm('VTV1 HD'), 'vtv1');
  assert.equal(V.norm('Đài Hà Nội 1 - FHD HD'), 'daihanoi1');
  assert.equal(V.norm('HBO 4K'), 'hbo');
  assert.equal(V.norm(''), '');
  assert.equal(V.norm(null), '');
});

const doc = { v: 1, categories: [
  { id: 'vtv', vi: 'VTV', en: 'VTV', match: ['vtv1', 'vtv3', 're:^vtv\\d+$'] },
  { id: 'news', vi: 'Tin tức', en: 'News', match: ['vtv1', 'vnews', 're:('] },
  { id: 'none', vi: 'Trống', en: 'Empty', match: ['zzz'] },
] };
const names = (l) => Array.from(l, (c) => c.n);   // arrays made inside the vm are of another realm
const ch = (n) => ({ n, id: n });

test('categorize: every matching category, entry order then name, the rest in others, empty categories left out', () => {
  const r = V.categorize([ch('VTV5'), ch('VTV3 HD'), ch('VTV1 FHD'), ch('VTV2'), ch('VNews'), ch('Zed')], doc);
  assert.deepEqual(Array.from(r.cats, (c) => c.id), ['vtv', 'news']);
  assert.deepEqual(names(r.cats[0].list), ['VTV1 FHD', 'VTV3 HD', 'VTV2', 'VTV5']);   // vtv1, vtv3, then the regex ones by name
  assert.deepEqual(names(r.cats[1].list), ['VTV1 FHD', 'VNews']);                     // a channel can be in two; a bad regex matches nothing
  assert.deepEqual(names(r.other), ['Zed']);
});

test('categorize: no file, nothing breaks', () => {
  const r = V.categorize([ch('A')], null);
  assert.equal(r.cats.length, 0);
  assert.equal(r.other.length, 1);
});
