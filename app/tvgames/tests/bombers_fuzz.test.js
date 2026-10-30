// Bombers fuzz (docs/games/web/bombers/rules.js): real-time matches of 2-4 players stepped with jittery dt (a TV hiccups),
// bots plus "humans" whose stick and bomb button are random, humans dropping out to the bot mid-match. Seeded; FUZZ=n for more.
// Invariants: nobody ever stands in a pillar or crate or outside the arena; every bomb sits on its own cell and p.out equals the
// bombs that player has on the board; flames only on open cells and never on pillars; an alive player is never left standing
// in fire; crates burn and pillars never do; power-ups stay within their caps; the dead stay dead and still; sudden death
// ends every match (a bounded time, nobody left alive), and nothing throws.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const BM = require(path.join(__dirname, '../../../docs/games/web/bombers/rules.js'));
const { rng, games } = require('./_fuzz.js');
const { COLS, ROWS, N } = BM;

function check(w, tag, pillars) {
  const on = new Map();
  w.bombs.forEach((b, k) => {
    if (!b.on) return;
    const i = b.r * COLS + b.c;
    assert.ok(b.c >= 0 && b.c < COLS && b.r >= 0 && b.r < ROWS, tag + ' bomb outside');
    assert.strictEqual(w.bombAt[i], k, tag + ' bombAt mismatch at ' + i);
    assert.ok(!on.has(i), tag + ' two bombs on one cell'); on.set(i, k);
    assert.ok(w.grid[i] !== 1, tag + ' bomb inside a pillar');
    on.set('o' + b.owner, (on.get('o' + b.owner) || 0) + 1);
  });
  for (let i = 0; i < N; i++) if (w.bombAt[i] >= 0) assert.ok(w.bombs[w.bombAt[i]].on && w.bombs[w.bombAt[i]].r * COLS + w.bombs[w.bombAt[i]].c === i, tag + ' stale bombAt');
  for (const i of pillars) assert.strictEqual(w.grid[i], 1, tag + ' a pillar burnt away');
  for (let i = 0; i < N; i++) {
    assert.ok(w.grid[i] <= 2, tag);
    if (w.flame[i]) assert.notStrictEqual(w.grid[i], 1, tag + ' fire in a pillar');
    if (w.power[i]) assert.strictEqual(w.grid[i], 0, tag + ' power-up in a wall');
  }
  for (const p of w.players) {
    assert.strictEqual(p.out, on.get('o' + p.i) || 0, tag + ' p' + p.i + ' out ' + p.out + ' vs bombs on the board');
    assert.ok(p.out >= 0 && p.maxB >= 1 && p.maxB <= 6 && p.range >= 2 && p.range <= 9 && p.speed >= 3.4 - 1e-9 && p.speed <= 5.4 + 1e-9, tag + ' stats');
    assert.ok(Number.isFinite(p.x) && Number.isFinite(p.y), tag + ' NaN position');
    if (!p.alive) continue;
    const c = Math.floor(p.x), r = Math.floor(p.y);
    assert.ok(c >= 0 && c < COLS && r >= 0 && r < ROWS, tag + ' p' + p.i + ' left the arena ' + p.x + ',' + p.y);
    assert.strictEqual(w.grid[r * COLS + c], 0, tag + ' p' + p.i + ' stands in a wall at ' + c + ',' + r + ' t=' + w.T.toFixed(2));
    assert.ok(!w.flame[r * COLS + c], tag + ' p' + p.i + ' alive in fire');
  }
}

test('bombers fuzz: matches of 2-4 players end, state stays consistent', () => {
  const N_GAMES = games(150);
  let kills = 0, blasts = 0, picks = 0, crates = 0, ended = 0;
  for (let g = 0; g < N_GAMES; g++) {
    const r = rng(90000 + g), tag = 'seed ' + (90000 + g), n = 2 + (g % 3);
    const humans = Array.from({ length: n }, () => r() < 0.5);
    const w = BM.createWorld(n, humans, r);
    const pillars = []; for (let i = 0; i < N; i++) if (w.grid[i] === 1) pillars.push(i);
    for (const [cc, rr] of BM.CORNERS) assert.strictEqual(w.grid[rr * COLS + cc], 0, tag);
    const c0 = w.grid.reduce((a, v) => a + (v === 2 ? 1 : 0), 0);
    w.on.die = () => { kills++; }; w.on.boom = () => { blasts++; }; w.on.pick = () => { picks++; };
    const dead = new Set(), where = [];
    const END = BM.SD_AT + 0.25 * BM.SPIRAL.length + 5;
    let steps = 0;
    while (w.T < END) {
      if (++steps > 100000) assert.fail(tag + ' too many steps');
      // humans: a random stick that changes now and then, a bomb button; one may drop to the bot at any moment
      for (const p of w.players) if (p.human && p.alive) {
        if (r() < 0.1) BM.axisDir(p, r() * 2 - 1, r() * 2 - 1);
        if (r() < 0.04) p.bomb = true;
        if (r() < 0.0005) p.human = false;
      }
      const dt = r() < 0.02 ? 0.05 + r() * 0.05 : 1 / 60 + r() * (1 / 30);
      for (const p of w.players) if (!p.alive) where[p.i] = [p.x, p.y];
      BM.update(w, dt);
      w.players.forEach((p) => {
        if (dead.has(p.i)) { assert.ok(!p.alive, tag + ' risen from the dead'); assert.deepStrictEqual([p.x, p.y], where[p.i], tag + ' a dead player moved'); }
        if (!p.alive) dead.add(p.i);
      });
      check(w, tag, pillars);
      if (BM.alive(w) === 0) break;
    }
    // sudden death closes every cell: nobody can still be alive well after the spiral is done
    assert.strictEqual(BM.alive(w), 0, tag + ' alive after sudden death at ' + w.T.toFixed(1) + 's');
    assert.ok(w.sdN === BM.SPIRAL.length || w.T < BM.SD_AT + 0.25 * BM.SPIRAL.length, tag);
    crates += c0; ended++;
  }
  assert.strictEqual(ended, N_GAMES); assert.ok(kills > 0 && blasts > 0 && crates > 0, `kills ${kills} blasts ${blasts} picks ${picks}`);
});
