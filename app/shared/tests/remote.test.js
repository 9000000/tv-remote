// node --test app/shared/tests/   The remote screen exists twice: native Kotlin in the Phone app, and the web page (the Computer app
// and the TV web page). This keeps them from drifting apart: every key and menu entry the phone has, the web has too, in the same
// order with the same icon and the same words (Vietnamese and English), unless remote_parity.json says why not.
// Both sides spell an entry the same way, '@icon|Words', so it can be read out of the source of each.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '../../');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');
const allow = JSON.parse(fs.readFileSync(path.join(__dirname, 'remote_parity.json'), 'utf8'));
const KT = 'android/src/main/kotlin/dev/tvremote/phone/';

// the page's own English, as the EN button shows it
global.window = {}; global.document = { documentElement: {}, addEventListener() {}, body: null };
global.localStorage = { getItem: () => 'en' }; global.location = { search: '' }; global.navigator = { language: 'en' };
(0, eval)(read('shared/web/js/i18n.js'));
const en = (vi) => window.tr(vi);

const swap = (s, pairs) => pairs.reduce((a, [from, to]) => a.split(from).join(to), s).replace(/\s*\/\s*/g, '/').trim();
const norm = (e) => ({ icon: e.icon, vi: swap(e.vi, allow.words.vi), en: e.icon in allow.enDiffers ? '-' : e.en && swap(e.en, allow.words.en) });

// Kotlin: "@icon|" + t("vi", "en")   or   "@icon|Words" (the same in both languages)
function kotlinEntries(file, only) {
  const out = [];
  for (const line of read(KT + file).split('\n')) {
    if (only && !only.test(line)) continue;
    for (const m of line.matchAll(/"@(\w+)\|(?:"\s*\+\s*t\("([^"]+)",\s*"([^"]+)"\)|([^"]+)")/g)) out.push(m[4] ? { icon: m[1], vi: m[4], en: m[4] } : { icon: m[1], vi: m[2], en: m[3] });
  }
  return out;
}
// JS: '@icon|Words'
const webMenu = (file) => [...read('shared/web/js/' + file).matchAll(/'@(\w+)\|([^']+)'/g)].map((m) => ({ icon: m[1], vi: m[2], en: en(m[2]) }));
const gone = (list) => list.filter((e) => !(e.icon in allow.phoneOnly));
const show = (list) => list.map((e) => `${e.icon}|${e.vi}|${e.en}`);

test('the remote keys are the same on the phone and on the web page, in the same order', () => {
  const phone = kotlinEntries('RemoteScreen.kt', /captioned\(keys\(/);
  const html = read('shared/web/index.html');
  const rc = html.slice(html.indexOf('aria-label="Điều hướng"'), html.indexOf('id="scenecard"'));
  const web = [...rc.matchAll(/<button class="rb lab[^"]*"[^>]*><svg[^>]*><use href="#i-(\w+)"\/><\/svg><small>([^<]+)<\/small>/g)].map((m) => ({ icon: m[1], vi: m[2], en: en(m[2]) }));
  assert.ok(phone.length > 15 && web.length > 15, 'found the keys');
  assert.deepEqual(show(web.map(norm)), show(gone(phone).map(norm)));
});

test('the menus behind "More keys" are the same on the phone and on the web page', () => {
  const phone = [...kotlinEntries('Media.kt'), ...kotlinEntries('Automation.kt')];
  assert.ok(phone.length > 10, 'found the menu entries');
  assert.deepEqual(show(webMenu('remote.js').map(norm)), show(gone(phone).map(norm)));
});

test('every phone-only exception is still a phone entry', () => {
  const phone = [...kotlinEntries('Media.kt'), ...kotlinEntries('Automation.kt'), ...kotlinEntries('RemoteScreen.kt')].map((e) => e.icon);
  for (const k of Object.keys(allow.phoneOnly)) assert.ok(phone.includes(k), k + ' is no longer on the phone: drop it from remote_parity.json');
});
