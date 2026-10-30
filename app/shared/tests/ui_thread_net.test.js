// No ADB / socket / HTTP call straight in a click, key or dialog-button handler of the Android apps: there it runs on the main
// thread, Android throws NetworkOnMainThreadException and Guard shows its error box (census 41dd31f7, d789ac92: the launcher
// picker's Play link). The call goes in a Thread { } (or the file's own off-thread helper) and comes back with ui { }.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '../../..');
const DIRS = ['app/tvsetup', 'app/android', 'app/tvhome', 'app/tvchannels', 'app/tvgames', 'app/tvapps'].map((d) => path.join(ROOT, d, 'src/main'));
const HANDLER = /set(OnClickListener|OnKeyListener|OnLongClickListener|OnCheckedChangeListener)\s*\{|onClick\s*=\s*\{|set(Positive|Negative|Neutral)Button\([^)]*\{/g;
const NET = /\b\w+\.shell\(|URL\(|openConnection|Socket\(|Census\.\w+Now\(/;
const OFF = /Thread\s*\{|Thread\(|thread\s*\{|\bgo\s*\{|launch\s*\{|async\s*\{|bg\s*\{|later\s*\{|execute\s*\{|submit\s*\{|work\s*\{/;   // past here it is off the main thread

function kt(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) kt(p, out); else if (e.name.endsWith('.kt')) out.push(p);
  }
  return out;
}

// The handler's body up to its first off-thread wrapper: what runs on the click itself.
function onClick(src, from) {
  let d = 1, j = from;
  while (j < src.length && d) { if (src[j] === '{') d++; else if (src[j] === '}') d--; j++; }
  const body = src.slice(from, j), off = body.search(OFF);
  return off < 0 ? body : body.slice(0, off);
}

function scan(src) {
  const hits = [];
  for (const m of src.matchAll(HANDLER)) {
    const start = m.index + m[0].length, now = onClick(src, start), n = now.search(NET);
    if (n >= 0) hits.push({ line: src.slice(0, start + n).split('\n').length, code: now.slice(n).split('\n')[0].trim() });
  }
  return hits;
}

test('the scan finds a shell call on the click thread', () => {
  assert.strictEqual(scan('r.setOnClickListener {\n  a.shell("am start")\n}').length, 1);
  assert.strictEqual(scan('r.setOnClickListener {\n  Thread { a.shell("am start") }.start()\n}').length, 0);
});

test('no network call straight in a click / key / dialog handler', () => {
  const bad = [];
  for (const f of DIRS.flatMap((d) => kt(d))) {
    for (const h of scan(fs.readFileSync(f, 'utf8'))) bad.push(`${path.relative(ROOT, f)}:${h.line}: ${h.code}`);
  }
  assert.deepStrictEqual(bad, [], 'wrap these in Thread { } (results back with ui { })');
});
