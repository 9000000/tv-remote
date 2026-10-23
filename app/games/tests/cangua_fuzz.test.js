// Cờ cá ngựa fuzz: thousands of random legal games with 2-4 seats (random and bot players), seeded so a failure replays.
// Invariants: 4 horses a seat, progress in -1..56, a non-safe track cell never holds two seats, a kick sends home exactly
// the others on that cell, a pass happens only when nothing is legal, the game ends, a winner has all four home.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const LU = require(path.join(__dirname, '../../../docs/games/web/cangua/rules.js'));
const { rng, games } = require('./_fuzz.js');

function check(pos, seats, tag) {
  assert.strictEqual(pos.length, seats, tag);
  const on = new Map();
  pos.forEach((hs, s) => {
    assert.strictEqual(hs.length, LU.HORSES, tag);
    hs.forEach((p) => {
      assert.ok(Number.isInteger(p) && p >= -1 && p <= LU.END, tag + ' progress ' + p);
      const c = LU.cell(s, p);
      if (c >= 0 && !LU.isSafe(c)) { if (on.has(c) && on.get(c) !== s) assert.fail(tag + ' two seats share unsafe cell ' + c); on.set(c, s); }
    });
  });
}

test('cangua fuzz: random games, 2-4 seats, never stuck, never illegal', () => {
  const N = games(1500);
  let turns = 0, kicks = 0, passes = 0, wins = 0;
  for (let g = 0; g < N; g++) {
    const r = rng(1000 + g), seats = 2 + (g % 3), tag = 'seed ' + (1000 + g);
    const botSeat = [0, 1, 2, 3].map(() => r() < 0.5);
    const pos = LU.fresh(seats);
    let seat = r.int(seats), n = 0, winner = -1;
    while (winner < 0) {
      if (++n > 100000) assert.fail(tag + ' did not end');
      let again = true, sixes = 0;
      while (again) {
        const d = LU.roll(pos, seat, r), ms = LU.legal(pos, seat, d);
        assert.ok(d >= 1 && d <= 6, tag);
        again = d === 6;
        if (!ms.length) {
          passes++;
          // a pass is right only when every horse is in the stable (no 6) or would overshoot
          pos[seat].forEach((p) => assert.ok(p < 0 ? d !== 6 : p + d > LU.END, tag + ' passed with a move'));
          break;
        }
        const h = botSeat[seat] ? LU.bot(pos, seat, d, r) : r.pick(ms);
        assert.ok(ms.includes(h), tag + ' illegal pick');
        const before = JSON.stringify(pos);
        const res = LU.apply(pos, seat, h, d);
        turns++; kicks += res.kicked.length;
        // exactly the horses of others that sat on the landing cell (if unsafe) are home now
        const c = LU.cell(seat, res.to), old = JSON.parse(before);
        const want = [];
        if (c >= 0 && !LU.isSafe(c)) old.forEach((hs, s) => { if (s !== seat) hs.forEach((p, k) => { if (LU.cell(s, p) === c) want.push(s + ':' + k); }); });
        assert.deepStrictEqual(res.kicked.map((x) => x.join(':')).sort(), want.sort(), tag + ' kick set');
        res.kicked.forEach(([s, k]) => assert.strictEqual(pos[s][k], -1, tag));
        // nothing else moved
        old.forEach((hs, s) => hs.forEach((p, k) => { if (!(s === seat && k === h) && !want.includes(s + ':' + k)) assert.strictEqual(pos[s][k], p, tag + ' stray move'); }));
        check(pos, seats, tag);
        if (LU.done(pos, seat)) { winner = seat; break; }
        if (d === 6 && ++sixes > 200) assert.fail(tag + ' endless sixes');
      }
      seat = (seat + 1) % seats;
    }
    assert.ok(LU.done(pos, winner), tag); wins++;
  }
  assert.strictEqual(wins, N); assert.ok(turns > 0 && kicks > 0 && passes > 0);
});
