// node --test app/shared/tests/   Sending a file to the TV: the "no room" refusal reads the same in Vietnamese and English,
// and the page has a Stop on every way a file reaches the TV.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const web = path.join(__dirname, '../web');
const read = (f) => fs.readFileSync(path.join(web, f), 'utf8');

// friendly() and its table, cut out of core.js (the rest of it needs the page)
function friendlyOf(lang = 'vi') {
  const src = read('js/core.js');
  const start = src.indexOf('const fmtSize'), end = src.indexOf('\n', start);
  const f0 = src.indexOf('const FRIENDLY = ['), f1 = src.indexOf('const friendly = ');
  const code = src.slice(start, end) + '\n' + src.slice(src.indexOf('const SETUP_NEEDS'), src.indexOf('function needSetup')) + '\n' + src.slice(f0, src.indexOf('\n', f1)) + '\nthis.friendly = friendly;';
  const book = JSON.parse(read('i18n/' + lang + '.json'));
  const T = (id, a) => { const s = book[id]; return s == null ? id : a ? s.replace(/\{(\w+)\}/g, (m, k) => (k in a ? a[k] : m)) : s; };
  const box = { T };
  vm.createContext(box);
  vm.runInContext(code, box);
  return box.friendly;
}
function englishOf() {
  const win = {};
  const box = { window: win, localStorage: { getItem: () => 'en' }, sessionStorage: { getItem: () => null, removeItem() {} }, location: { search: '' },
    navigator: { language: 'en' }, document: { documentElement: {}, body: null, addEventListener() {} }, MutationObserver: class { observe() {} } };
  vm.createContext(box);
  vm.runInContext(read('js/i18n.js'), box);
  return win.tr;
}

const MB = 1e6, GB = 1e9;

test('a file the TV has no room for is refused in words that say the sizes and what to do', () => {
  const friendly = friendlyOf();
  const file = friendly(`storage full: file=${3.4 * GB} free=${1.2 * GB} need=${3.4 * GB + 16 * MB} apk=0`);
  assert.match(file, /TV chỉ còn trống 1\.2 GB/);
  assert.match(file, /tệp 3\.4 GB/);
  assert.match(file, /Xóa bớt app hoặc tệp/);
  const apk = friendly(`storage full: file=${500 * MB} free=${300 * MB} need=${1016 * MB} apk=1`);
  assert.match(apk, /cài APK 500\.0 MB/);
  assert.match(apk, /bản chép và app sau khi cài/);
});

test('the refusal and the Stop have English words', () => {
  const friendly = friendlyOf('en'), tr = englishOf();
  assert.equal(tr(friendly(`storage full: file=${3.4 * GB} free=${1.2 * GB} need=${3.4 * GB + 16 * MB} apk=0`)),
    'The TV has only 1.2 GB free, not enough for this 3.4 GB file (it needs at least 3.4 GB). Remove some apps or files (Health / Files tab), then try again.');
  assert.match(tr(friendly(`storage full: file=${500 * MB} free=${300 * MB} need=${1016 * MB} apk=1`)), /^The TV has only 300\.0 MB free, not enough to install this 500\.0 MB APK/);
  const VI = JSON.parse(read('i18n/vi.json')), EN = JSON.parse(read('i18n/en.json'));   // by id now, or still by the old table
  for (const [vi, en] of [['■ Dừng', '■ Stop'], ['Đã dừng', 'Stopped'], ['đang dừng...', 'stopping...']])
    assert.ok(tr(vi) === en || Object.keys(VI).some((id) => VI[id] === vi && EN[id] === en), vi);
});

test('every way of sending a file has a Stop, and the server has the routes', () => {
  const files = read('js/files.js');
  assert.match(files, /api\('\/api\/cancel'/g);
  assert.match(files, /xhr\.abort\(\)/);
  assert.match(files, /'\/api\/space\?/);                                // asked before a byte is sent
  assert.match(files, /status === 'cancelled'/);                         // a stop is not an error
  assert.match(read('js/fleet.js'), /api\/fleet\/cancel/);
  assert.match(read('index.html'), /id="fleetstop"/);
});
