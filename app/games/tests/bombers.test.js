// Bombers' rules and bots (docs/games/web/bombers/rules.js): the flame's cross, chains, the danger map, the escape check, and whole
// matches played by bots (they end, and they seldom blow themselves up).
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const BM = require(path.join(__dirname, '../../../docs/games/web/bombers/rules.js'));
const { COLS, ROWS, N, INF } = BM;

const empty = () => new Uint8Array(N);
const at = (c, r) => r * COLS + c;
const bomb = (c, r, t, range, owner) => ({ on: true, c, r, t, range: range || 2, owner: owner || 0, pass: 0 });
const pool = (...b) => { const a = b.slice(); while (a.length < 8) a.push({ on: false, c: 0, r: 0, t: 0, range: 2, owner: 0, pass: 0 }); return a; };
const seeded = (s) => () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
const cells = (g, c, r, range) => { const o = new Int16Array(N), n = BM.blast(g, c, r, range, o); return Array.from(o.slice(0, n)).sort((a, b) => a - b); };

test('the flame is a cross of the bomb\'s range', () => {
  const g = empty();
  assert.deepStrictEqual(cells(g, 5, 5, 2), [at(5, 3), at(5, 4), at(3, 5), at(4, 5), at(5, 5), at(6, 5), at(7, 5), at(5, 6), at(5, 7)].sort((a, b) => a - b));
  assert.strictEqual(cells(g, 5, 5, 4).length, 17);
});

test('the flame stops at a pillar, burns the first crate and goes no further, and stays in the arena', () => {
  const g = empty(); g[at(6, 5)] = 1; g[at(5, 6)] = 2; g[at(5, 7)] = 2;
  const c = cells(g, 5, 5, 3);
  assert.ok(!c.includes(at(6, 5)) && !c.includes(at(7, 5)), 'pillar blocks');
  assert.ok(c.includes(at(5, 6)) && !c.includes(at(5, 7)), 'first crate burns, the one behind is shielded');
  assert.deepStrictEqual(cells(g, 0, 0, 3).filter((i) => i % COLS >= COLS || i >= N), []);
  assert.strictEqual(cells(empty(), 0, 0, 2).length, 5);
});

test('a bomb in the flame goes off with it: a chain, in one pass', () => {
  const g = empty(), flame = empty(), res = BM.newRes();
  const bombs = pool(bomb(2, 2, 0, 2), bomb(4, 2, 2, 2), bomb(4, 4, 2.4, 2), bomb(12, 9, 1, 2));
  assert.strictEqual(BM.detonate(g, bombs, flame, res), 3);
  assert.ok(!bombs[0].on && !bombs[1].on && !bombs[2].on && bombs[3].on);
  assert.ok(flame[at(4, 6)] && flame[at(0, 2)] && !flame[at(12, 9)]);
});

test('a crate shields the next bomb from the chain; the crate is gone afterwards', () => {
  const g = empty(), flame = empty(), res = BM.newRes(); g[at(3, 2)] = 2;
  const bombs = pool(bomb(2, 2, 0, 3), bomb(4, 2, 2, 2));
  assert.strictEqual(BM.detonate(g, bombs, flame, res), 1);
  assert.ok(bombs[1].on && flame[at(3, 2)] && !flame[at(4, 2)]);
  assert.strictEqual(g[at(3, 2)], 0); assert.strictEqual(res.nbr, 1);
});

test('danger map: seconds to fire, a chain takes the earlier time, flames are 0', () => {
  const g = empty(), flame = empty(), dg = new Float32Array(N);
  const bombs = pool(bomb(2, 2, 1, 2), bomb(4, 2, 2.5, 2));
  BM.danger(g, bombs, flame, dg, null);
  assert.strictEqual(dg[at(2, 4)], 1); assert.strictEqual(dg[at(4, 2)], 1, 'the second one is hit at 1 s');
  assert.strictEqual(dg[at(6, 2)], 1, 'and its own cross is lit at 1 s too');
  assert.strictEqual(dg[at(7, 2)], INF); assert.strictEqual(dg[at(3, 3)], INF);
  flame[at(9, 9)] = 1; BM.danger(g, bombs, flame, dg, null); assert.strictEqual(dg[at(9, 9)], 0);
  g[at(3, 2)] = 2; BM.danger(g, bombs, flame, dg, null);
  assert.strictEqual(dg[at(4, 2)], 2.5, 'a crate between: no chain');
});

test('escape check: open ground yes, a dead end shorter than the flame no, boxed in no', () => {
  const g = empty(), flame = empty(), none = pool();
  assert.ok(BM.canEscape(g, none, flame, 5, 5, 2, 2.5, 3.4));
  // a corridor 1 wide, 2 long, closed at the end: pillars all round
  const k = empty(); for (let i = 0; i < N; i++) k[i] = 1;
  for (const [c, r] of [[3, 3], [4, 3], [5, 3]]) k[at(c, r)] = 0;                // a 3-long corridor; bomb in the dead end (3,3) range 4
  assert.ok(!BM.canEscape(k, none, flame, 3, 3, 4, 2.5, 3.4), 'the flame fills the whole corridor');
  assert.ok(BM.canEscape(k, none, flame, 3, 3, 1, 2.5, 3.4), 'range 1 lights only the next cell: two steps get out');
  for (let c = 4; c <= 8; c++) k[at(c, 3)] = 0;
  assert.ok(!BM.canEscape(k, pool(bomb(8, 3, 1, 3)), flame, 3, 3, 1, 2.5, 3.4), 'another bomb\'s fire fills the way out sooner than we get there');
  assert.ok(BM.canEscape(k, pool(), flame, 3, 3, 1, 2.5, 3.4));
  const box = empty(); for (const [c, r] of [[5, 4], [5, 6], [4, 5], [6, 5]]) box[at(c, r)] = 2;
  assert.ok(!BM.canEscape(box, none, flame, 5, 5, 2, 2.5, 3.4));
});

test('escape check: too slow to outrun a short fuse, and a closer wait is fine', () => {
  const g = empty(), flame = empty(), none = pool();
  assert.ok(!BM.canEscape(g, none, flame, 5, 5, 2, 0.4, 3.4), '0.4 s is not enough to leave a range 2 cross');
});

test('the grid: pillars on odd/odd, corners clear, every start can escape its own bomb', () => {
  const g = BM.makeGrid(seeded(7), 1), flame = empty();
  for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) assert.strictEqual(g[at(c, r)] === 1, c % 2 === 1 && r % 2 === 1);
  for (const [c, r] of BM.CORNERS) {                                              // even with a crate in every other cell, two steps in from the corner is a safe place to bomb from
    assert.strictEqual(g[at(c, r)], 0);
    const bc = c === 0 ? 2 : COLS - 3, br = r === 0 ? 0 : ROWS - 1, bc2 = r === 0 ? bc : c, br2 = r === 0 ? br : (r === 0 ? 0 : r);
    assert.ok(BM.canEscape(g, pool(), flame, c === 0 ? 2 : COLS - 3, r, 2, 2.5, 3.4), 'corner ' + c + ',' + r);
  }
});

test('sudden death spiral visits every cell once, outside in', () => {
  assert.strictEqual(BM.SPIRAL.length, N); assert.strictEqual(new Set(BM.SPIRAL).size, N);
  assert.strictEqual(BM.SPIRAL[0], 0); assert.ok(BM.SPIRAL[N - 1] % COLS > 0 && BM.SPIRAL[N - 1] % COLS < COLS - 1);
});

test('walking: stops at walls, slides round a corner, a fresh bomb is walked off but then blocks', () => {
  const w = BM.createWorld(2, [true, true], seeded(1)); w.grid.fill(0);
  const p = w.players[0]; p.x = 3.5; p.y = 3.5;
  for (let i = 0; i < 200; i++) BM.step(w, p, 1, 0, 0.1, false);
  assert.ok(Math.abs(p.x - (COLS - BM.H)) < 0.01, 'stops at the edge: ' + p.x);
  p.x = 3.5; p.y = 3.5; w.grid[at(4, 3)] = 1;                                      // a pillar ahead, the body 0.3 off the lane
  p.y = 3.5 + 0.3; for (let i = 0; i < 100; i++) BM.step(w, p, 1, 0, 0.05, false);
  assert.ok(p.x > 4.9, 'slid past the corner: ' + p.x + ',' + p.y);
  w.grid.fill(0); p.x = 3.5; p.y = 3.5; assert.ok(BM.place(w, p));
  BM.step(w, p, 1, 0, 0.05, false); assert.ok(p.x > 3.5, 'walks off its own bomb');
  w.bombs[0].pass = 0; p.x = 4.6;
  const q = w.players[1]; q.x = 4.6; q.y = 3.5; for (let i = 0; i < 20; i++) BM.step(w, q, -1, 0, 0.05, false);
  assert.ok(q.x >= 3 + 1 + BM.H - 0.01, 'another bomb is a wall: ' + q.x);
});

test('a human gets an extra bomb, range and speed from the three power-ups', () => {
  const w = BM.createWorld(2, [true, true], seeded(1)); w.grid.fill(0);
  const p = w.players[0];
  for (const t of [1, 2, 3]) { w.power[at(0, 0)] = t; BM.update(w, 1 / 60); }
  assert.strictEqual(p.maxB, 2); assert.strictEqual(p.range, 3); assert.ok(p.speed > 3.4);
});

test('a bomb kills the player standing in the cross, and the owner gets the cause; the round ends with one left', () => {
  const w = BM.createWorld(2, [true, true], seeded(1)); w.grid.fill(0);
  w.players[1].x = 3.5; w.players[1].y = 0.5; w.players[0].x = 0.5; w.players[0].y = 5.5;
  const dead = []; w.on.die = (p, by) => dead.push([p.i, by]);
  w.players[0].x = 1.5; w.players[0].y = 0.5; BM.place(w, w.players[0]); w.players[0].y = 5.5;
  for (let i = 0; i < 200; i++) BM.update(w, 1 / 60);
  assert.deepStrictEqual(dead, [[1, 0]]); assert.strictEqual(BM.alive(w), 1);
});

// whole matches, bots only
function play(n, seed, wantLog) {
  const rand = seeded(seed), w = BM.createWorld(n, new Array(n).fill(false), rand);
  const deaths = []; let bombs = 0, steps = 0; w.on.die = (p, by) => deaths.push({ i: p.i, by, t: w.T }); w.on.place = () => bombs++;
  const moved = new Float32Array(n), last = w.players.map((p) => [p.x, p.y]);
  while (BM.alive(w) > 1 && steps < 60 * 150) {
    BM.update(w, 1 / 60); steps++;
    for (const p of w.players) { moved[p.i] += Math.abs(p.x - last[p.i][0]) + Math.abs(p.y - last[p.i][1]); last[p.i][0] = p.x; last[p.i][1] = p.y; }
  }
  return { w, deaths, bombs, steps, moved, alive: BM.alive(w) };
}

test('bots play whole rounds: they end, they bomb, they move', () => {
  for (const n of [2, 3, 4]) for (let s = 1; s <= 6; s++) {
    const r = play(n, s * 31 + n);
    assert.ok(r.alive <= 1, n + 'p seed ' + s + ' did not end in 150 s');
    assert.ok(r.bombs >= 3, 'bots bombed ' + r.bombs);
    assert.ok(r.moved.every((m) => m > 3), 'nobody stood still: ' + Array.from(r.moved));
  }
});

test('bots seldom blow themselves up: a death in the fire of a bomb nobody could have added to', () => {
  // A bot trapped by a foe's later bomb dies in its own flame too (the first bomb of a chain owns the cell), so only deaths where
  // no other bomb was dropped after the bot's own count as its mistake.
  let clean = 0, deaths = 0;
  for (let s = 1; s <= 40; s++) {
    const n = 2 + (s % 3), w = BM.createWorld(n, new Array(n).fill(false), seeded(s * 977)), drops = [];
    w.on.place = (p) => drops.push({ t: w.T, i: p.i });
    w.on.die = (p, by) => {
      deaths++; if (by !== p.i) return;
      const mine = drops.filter((d) => d.i === p.i).pop();
      if (!drops.some((d) => d.t > mine.t + 0.01 && d.t < w.T - 0.01)) clean++;
    };
    for (let st = 0; BM.alive(w) > 1 && st < 60 * 150; st++) BM.update(w, 1 / 60);
  }
  assert.ok(deaths > 40, 'deaths ' + deaths);
  assert.ok(clean / deaths < 0.15, 'plain self-kills ' + clean + ' of ' + deaths);
});

test('a round with bots only ends well before the sudden death has eaten the whole arena', () => {
  let long = 0;
  for (let s = 1; s <= 20; s++) { const r = play(2 + (s % 3), s * 53); if (r.w.T > BM.SD_AT + 30) long++; }
  assert.ok(long <= 3, long + ' of 20 rounds ran 30 s into sudden death');
});
