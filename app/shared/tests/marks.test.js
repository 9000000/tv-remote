// node --test app/shared/tests/   The native apps draw text marks (✓ ✗ ⏻ ...) as line icons from one map in Marks.kt, looked up
// by name: an app that builds Marks.kt without one of those drawables would show the bare character, so that is caught here.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const app = path.join(__dirname, '../..');
const marks = fs.readFileSync(path.join(app, 'shared/src/marks/kotlin/dev/tvremote/tvui/Marks.kt'), 'utf8');
const icons = [...new Set([...marks.matchAll(/'.' to "(ic_\w+)"/g)].map((m) => m[1]))];

test('Marks.kt maps the marks to icon names', () => {
  assert.ok(icons.length >= 10, 'no ICONS map found in Marks.kt');
});

test('every app that builds Marks.kt has each mark icon', () => {
  const missing = [];
  for (const mod of fs.readdirSync(app)) {
    const gradle = path.join(app, mod, 'build.gradle.kts');
    if (!fs.existsSync(gradle) || !fs.readFileSync(gradle, 'utf8').includes('src/marks/kotlin')) continue;
    for (const n of icons) if (!fs.existsSync(path.join(app, mod, 'src/main/res/drawable', n + '.xml'))) missing.push(mod + ': ' + n);
  }
  assert.deepEqual(missing, [], 'run python3 dev/gen/gen_icons.py (an app drawing only some icons lists them, like GAMES there)');
});

test('no app keeps its own mark → icon map', () => {
  const own = [];
  const walk = (d) => { for (const f of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, f.name);
    if (f.isDirectory()) { if (f.name !== 'build') walk(p); }
    else if (f.name.endsWith('.kt') && /Marks\.text\([^)]*\)\s*\{|fun markIcon/.test(fs.readFileSync(p, 'utf8'))) own.push(path.relative(app, p));
  } };
  walk(app);
  assert.deepEqual(own, [], 'call Marks.text(context, s): the one map is in Marks.kt');
});
