// The Ad blocking card (TV Home) and the TV card on the Setup tab name the same ad-blocking Private DNS hosts:
// HomeConfig.ADBLOCK_DNS (Kotlin) and DNS_SETS.ads (js/tvset.js) are one list kept in two languages.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const kt = fs.readFileSync(path.join(root, 'src/home/kotlin/dev/tvremote/home/HomeConfig.kt'), 'utf8');
const js = fs.readFileSync(path.join(root, 'web/js/tvset.js'), 'utf8');
const hosts = (s) => [...s.matchAll(/"([a-z0-9.-]+)"/g)].map((m) => m[1]);

test('HomeConfig.ADBLOCK_DNS equals DNS_SETS.ads in tvset.js', () => {
  const k = kt.match(/val ADBLOCK_DNS = listOf\(([^)]*)\)/);
  const j = js.match(/ads: \[([^\]]*)\]/);
  assert.ok(k && j);
  assert.deepEqual(hosts(k[1]), [...j[1].matchAll(/'([a-z0-9.-]+)'/g)].map((m) => m[1]));
});

test('the cards are in the page and the Kotlin list alike', () => {
  const page = fs.readFileSync(path.join(root, 'web/js/home.js'), 'utf8');
  const w = kt.match(/val WIDGETS = listOf\(([^)]*)\)/)[1];
  for (const k of ['recent', 'adblock']) {
    assert.ok(w.includes(`"${k}"`));
    assert.ok(page.includes(`'${k}'`));
  }
});
