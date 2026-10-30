// Cờ cá ngựa's rules and bot (docs/games/web/cangua/rules.js): leaving the stable, kicks, safe cells, the home stretch,
// and whole games played by bots end with every move legal.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const LU = require(path.join(__dirname, '../../../docs/games/web/cangua/rules.js'));

test('a horse leaves the stable only with a 6', () => {
  const pos = LU.fresh();
  for (let d = 1; d <= 5; d++) assert.deepStrictEqual(LU.legal(pos, 0, d), []);
  assert.deepStrictEqual(LU.legal(pos, 0, 6), [0, 1, 2, 3]);
  const r = LU.apply(pos, 0, 2, 6);
  assert.strictEqual(pos[0][2], 0); assert.strictEqual(r.from, -1);
  assert.strictEqual(LU.cell(0, 0), 39);                       // seat 0 enters on its own start
  assert.deepStrictEqual(LU.legal(pos, 0, 3), [2]);
});

test('track cells: each seat starts a quarter round, 52 distinct cells in 4 x 13', () => {
  assert.deepStrictEqual(LU.START.map((c, i) => LU.cell(i, 0)), [39, 0, 13, 26]);
  assert.strictEqual(LU.cell(1, 50), 50);                      // the last track cell before its stretch
  assert.strictEqual(LU.cell(1, 51), -1);                      // the stretch is private
  assert.strictEqual(LU.cell(0, 13), 0);                       // seat 0 wraps past 51
});

test('exact roll to get home, no overshoot', () => {
  const pos = LU.fresh(); pos[1][0] = 54;
  assert.deepStrictEqual(LU.legal(pos, 1, 3), []);
  assert.deepStrictEqual(LU.legal(pos, 1, 2), [0]);
  assert.ok(LU.apply(pos, 1, 0, 2).home);
  assert.strictEqual(pos[1][0], LU.END);
  assert.ok(!LU.legal(pos, 1, 1).includes(0));                 // a horse at home does not move
  assert.strictEqual(LU.step(50, 6), 56);
  assert.strictEqual(LU.step(51, 6), null);
});

test('kicks send every horse on the cell home, safe cells kick nobody', () => {
  const pos = LU.fresh();
  pos[0][0] = 0;                                               // seat 0, cell 39
  pos[1][0] = 39;                                              // seat 1 progress 39 = cell 39: safe (seat 0's start)
  assert.strictEqual(LU.cell(1, 39), 39);
  pos[0][1] = 0; const r0 = LU.apply(pos, 0, 1, 1);            // moves to 40 (not 39), nothing there
  assert.deepStrictEqual(r0.kicked, []);
  // a horse of seat 2 on cell 45 (progress 32), another of seat 3 on 45 (progress 19): seat 0 lands there from cell 40 with 5
  pos[2][0] = 32; pos[3][1] = 19; pos[3][0] = 19;
  assert.strictEqual(LU.cell(2, 32), 45); assert.strictEqual(LU.cell(3, 19), 45);
  const r = LU.apply(pos, 0, 1, 5);
  assert.strictEqual(r.kicked.length, 3);
  assert.strictEqual(pos[2][0], -1); assert.strictEqual(pos[3][1], -1); assert.strictEqual(pos[3][0], -1);
  // cell 47 is safe: two horses side by side, nobody kicked
  pos[0][1] = 6;                                               // cell 45? progress 6 from 39 = 45
  pos[2][1] = 34;                                              // seat 2: 13 + 34 = 47
  assert.ok(LU.isSafe(47));
  const s = LU.apply(pos, 0, 1, 2);                            // 45 + 2 = 47
  assert.deepStrictEqual(s.kicked, []); assert.strictEqual(pos[2][1], 34);
});

test('a horse does not kick its own side', () => {
  const pos = LU.fresh(); pos[0][0] = 3; pos[0][1] = 5;
  assert.deepStrictEqual(LU.apply(pos, 0, 0, 2).kicked, []);
  assert.deepStrictEqual(pos[0].slice(0, 2), [5, 5]);
});

test('path lists the steps', () => {
  assert.deepStrictEqual(LU.path(-1, 0), [0]);
  assert.deepStrictEqual(LU.path(4, 7), [5, 6, 7]);
});

test('rolls are 1-6 and favour a 6 only while nothing is out', () => {
  const pos = LU.fresh(); let six = 0;
  for (let i = 0; i < 4000; i++) { const d = LU.roll(pos, 0); assert.ok(d >= 1 && d <= 6); if (d === 6) six++; }
  assert.ok(six > 4000 * 0.45 && six < 4000 * 0.65, 'sixes ' + six);
  pos[0][0] = 5; six = 0;
  for (let i = 0; i < 4000; i++) if (LU.roll(pos, 0) === 6) six++;
  assert.ok(six < 4000 * 0.25, 'sixes ' + six);
});

test('the bot: finishes, kicks, leaves the stable, runs from danger', () => {
  let pos = LU.fresh(); pos[0][0] = 50; pos[0][1] = 3; pos[0][2] = -1;
  assert.strictEqual(LU.bot(pos, 0, 6), 0);                    // 50 + 6 = home beats leaving the stable
  pos = LU.fresh(); pos[0][0] = 3; pos[0][1] = -1; pos[2][0] = 29;   // seat 2 at 13+29 = 42 = seat 0 progress 3
  assert.strictEqual(LU.cell(2, 29), 42); assert.strictEqual(LU.cell(0, 3), 42);
  pos[0][0] = 1;                                               // seat 0 horse at cell 40; roll 2 lands on 42 and kicks
  pos[0][3] = 20;
  assert.strictEqual(LU.bot(pos, 0, 2), 0);
  pos = LU.fresh(); pos[0][0] = 10; assert.strictEqual(pos[0][LU.bot(pos, 0, 6)], -1);   // out of the stable (any horse there), not just the runner
  pos = LU.fresh(); pos[0][0] = 10; pos[0][1] = 20; pos[1][0] = 46;                // seat 1's horse on cell 46, 3 behind seat 0's on 49
  assert.strictEqual(LU.cell(0, 10), 49); assert.ok(LU.danger(pos, 0, 49) >= 1);
  assert.strictEqual(LU.bot(pos, 0, 4, () => 0), 0);           // the one in danger runs
});

test('bot-only games end, every move legal, a winner has all four home', () => {
  let totalTurns = 0;
  for (let g = 0; g < 200; g++) {
    const pos = LU.fresh(); let seat = g % 4, turns = 0, winner = -1;
    while (winner < 0) {
      if (++turns > 20000) assert.fail('game did not end');
      let again = true;
      while (again) {
        const d = LU.roll(pos, seat), ms = LU.legal(pos, seat, d);
        again = d === 6;
        if (!ms.length) { again = false; break; }
        const h = LU.bot(pos, seat, d);
        assert.ok(ms.includes(h), 'bot picked an illegal horse');
        const r = LU.apply(pos, seat, h, d);
        r.kicked.forEach(([s, k]) => assert.strictEqual(pos[s][k], -1));
        pos.forEach((hs) => hs.forEach((p) => assert.ok(p >= -1 && p <= LU.END)));
        if (LU.done(pos, seat)) { winner = seat; break; }
      }
      seat = (seat + 1) % 4;
    }
    assert.ok(LU.done(pos, winner)); totalTurns += turns;
  }
  assert.ok(totalTurns > 0);
});
