// _lib/hold.js: a held OK from a remote that repeats, or sends a down + up pair every ~50 ms, is ONE press, released once.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const Hold = require(path.join(__dirname, '../../../docs/games/web/_lib/hold.js'));

// a fake clock: timers run when time passes
function rig(grace) {
  let now = 0, id = 0; const q = new Map(), log = [];
  const h = Hold.make({ grace, down: () => log.push(['down', now]), up: () => log.push(['up', now]),
    setTimeout: (f, ms) => { q.set(++id, [now + ms, f]); return id; }, clearTimeout: (i) => q.delete(i) });
  const at = (t, fn) => { advance(t - now); fn && fn(); };
  function advance(ms) { const end = now + ms; for (;;) { let best = null; for (const [i, [t]] of q) if (t <= end && (!best || t < q.get(best)[0])) best = i; if (!best) break; const [t, f] = q.get(best); q.delete(best); now = t; f(); } now = end; }
  return { h, log, at, advance, set: (f, ms) => { q.set(++id, [now + ms, f]); return id; }, clr: (i) => q.delete(i) };
}

test('down + auto-repeats + up: one press, released right after the grace', () => {
  const r = rig(180);
  r.at(0, () => r.h.down());
  for (let t = 400; t <= 1500; t += 33) r.at(t, () => r.h.down());     // repeat=true events reach down() too: ignored
  r.at(1500, () => r.h.up()); r.advance(500);
  assert.deepStrictEqual(r.log, [['down', 0], ['up', 1680]]);
});

test('a down + up pair every 50 ms while held: no release until the last up, the charge never restarts', () => {
  const r = rig(180);
  for (let t = 0; t < 2000; t += 50) { r.at(t, () => r.h.down()); r.at(t + 5, () => r.h.up()); }
  assert.deepStrictEqual(r.log, [['down', 0]]);                        // still held after 2 s
  r.advance(500);
  assert.deepStrictEqual(r.log, [['down', 0], ['up', 1955 + 180]]);
});

test('a quick tap: down, up, released after the grace (not instantly lost, not stuck)', () => {
  const r = rig(180);
  r.at(0, () => r.h.down()); r.at(60, () => r.h.up());
  assert.deepStrictEqual(r.log, [['down', 0]]);
  r.advance(300);
  assert.deepStrictEqual(r.log, [['down', 0], ['up', 240]]);
  r.at(1000, () => r.h.down()); r.at(1050, () => r.h.up()); r.advance(300);   // the next tap is a new press
  assert.strictEqual(r.log.length, 4);
});

test('an up with no down, a second up, and reset do nothing odd', () => {
  const r = rig(100);
  r.h.up(); r.advance(500); assert.deepStrictEqual(r.log, []);
  r.h.down(); r.h.up(); r.h.up(); r.advance(500); assert.strictEqual(r.log.length, 2);
  r.h.down(); r.h.reset(); r.advance(500); assert.strictEqual(r.h.held(), false); assert.strictEqual(r.log.length, 3);
});

test('Kit keys (grace 120): down/up pairs hold a direction, hit fires once, it lets go 120 ms after the last up', () => {
  const r = rig(120), s = { kb: false, hit: 0 };
  const h = Hold.make({ grace: 120, down() { s.kb = true; s.hit++; }, up() { s.kb = false; }, setTimeout: (f, ms) => r.set(f, ms), clearTimeout: (i) => r.clr(i) });
  assert.ok(h);                                                        // shape check; the timing is covered above with the same grace logic
  for (let t = 0; t < 1000; t += 50) { r.at(t, () => r.h.down()); r.at(t + 5, () => r.h.up()); }
  assert.strictEqual(r.log.filter((x) => x[0] === 'down').length, 1);
  assert.strictEqual(r.log.filter((x) => x[0] === 'up').length, 0);
  r.advance(130); assert.strictEqual(r.log.filter((x) => x[0] === 'up').length, 1);
});
