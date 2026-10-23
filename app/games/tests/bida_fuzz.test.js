// Bi-a fuzz (docs/games/web/bida/rules.js): whole 8-ball games where each shot is the bot (any level), a random shot, or a
// random hand-placement attempt; the verdict of every shot is checked against the documented rules by an independent
// reading of what happened. Seeded; FUZZ=n for more games.
// Invariants: balls never leave the table or overlap badly or turn NaN, a shot stops within 60 s, 16 balls are always either
// on the table or potted; potted balls stay potted (except the cue ball and the 8 spotted on the break); a scratch is a foul
// with the other player holding the ball in hand; a foul passes the turn; a clean shot keeps it only when it potted a ball of
// the shooter's (or any, on the open table); groups are given by the first legal pot after the break and are opposite; the 8
// wins only when potted cleanly after the shooter's group was cleared, else loses; every game ends.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const BD = require(path.join(__dirname, '../../../docs/games/web/bida/rules.js'));
const { rng, games } = require('./_fuzz.js');

function tableOk(g, tag, st) {
  for (let i = 0; i < 16; i++) {
    assert.ok(Number.isFinite(g.x[i]) && Number.isFinite(g.y[i]) && Number.isFinite(g.vx[i]) && Number.isFinite(g.vy[i]), tag + ' NaN ball ' + i);
    if (g.pot[i]) continue;
    assert.ok(g.x[i] >= 10 - 1e-9 && g.x[i] <= 790 + 1e-9 && g.y[i] >= 10 - 1e-9 && g.y[i] <= 390 + 1e-9, tag + ' ball ' + i + ' off the table ' + g.x[i] + ',' + g.y[i]);
    for (let j = i + 1; j < 16; j++) if (!g.pot[j]) { const d = Math.hypot(g.x[i] - g.x[j], g.y[i] - g.y[j]); st.minGap = Math.min(st.minGap, d); assert.ok(d >= 15, tag + ' balls ' + i + ',' + j + ' merged: ' + d.toFixed(2)); }
  }
}

test('bida fuzz: mixed bot / random shots, rules checked per shot, every game ends', () => {
  const N = games(120), st = { minGap: 99, shots: 0, fouls: 0, scratches: 0, wins: 0, early: 0, spotted: 0, assigned: 0, hands: 0 };
  for (let n = 0; n < N; n++) {
    const r = rng(110000 + n), tag = 'seed ' + (110000 + n), g = BD.create(r);
    const randomness = r() * 0.5, level = n % 3;
    let k = 0;
    for (; k < 1500 && g.winner < 0; k++) {
      const me = g.turn, op = 1 - me, wasHand = g.hand, wasOpen = g.open, wasBreak = g.breaking, grpBefore = g.grp.slice();
      const potBefore = Array.from(g.pot);
      let mv = BD.botMove(g, level);
      if (r() < randomness) mv = { x: null, y: null, ang: r() * Math.PI * 2, pw: r() };
      if (wasHand) {
        // the table says where the ball may go: bot's spot, or a random try (illegal tries must be refused and leave the ball alone)
        let ok = mv.x != null && BD.place(g, mv.x, mv.y);
        for (let t = 0; t < 5 && !ok; t++) {
          const x = 10 + r() * 780, y = 10 + r() * 380, cx = g.x[0], cy = g.y[0], can = BD.canPlace(g, x, y);
          ok = BD.place(g, x, y); assert.strictEqual(ok, can, tag + ' place vs canPlace');
          if (!ok) { assert.strictEqual(g.x[0], cx, tag); assert.strictEqual(g.y[0], cy, tag); }
        }
        if (!ok) { const p = BD.freeSpot(g, 200, 200, g.zone); assert.ok(p[0] >= 0, tag + ' no free spot'); assert.ok(BD.place(g, p[0], p[1]), tag + ' freeSpot gave an illegal spot'); }
        st.hands++;
      } else assert.ok(!BD.place(g, 300, 200), tag + ' placed without the ball in hand');
      const onEight = g.grp[me] >= 0 && BD.cleared(g, g.grp[me]);
      BD.shoot(g, mv.ang, mv.pw);
      let steps = 0; while (BD.step(g)) { if (++steps > 240 * 60) assert.fail(tag + ' balls still rolling after 60 s'); }
      const potted = Array.from(g.shot.pots.slice(0, g.shot.n)), first = g.shot.first, rail = g.shot.rail;
      const res = BD.settle(g); st.shots++;
      tableOk(g, tag, st);
      // ---- the verdict, from the documented rules
      assert.strictEqual(new Set(potted).size, potted.length, tag + ' a ball potted twice');
      const scratch = potted.includes(0), eight = potted.includes(8), objs = potted.filter((b) => b !== 0 && b !== 8);
      if (scratch) { st.scratches++; assert.strictEqual(res.foul, 'scratch', tag); }
      else if (first < 0) assert.strictEqual(res.foul, 'nohit', tag);
      else {
        const fg = BD.grp(first), wrong = wasOpen ? fg === 2 : !(fg === grpBefore[me] || (fg === 2 && onEight));
        if (wrong) assert.strictEqual(res.foul, 'wrong', tag + ' first=' + first); else if (!wasBreak && !rail && !potted.length) assert.strictEqual(res.foul, 'rail', tag); else assert.strictEqual(res.foul, null, tag + ' unexpected foul ' + res.foul);
      }
      if (res.foul) st.fouls++;
      if (eight && !wasBreak) {                                       // the game is decided
        const win = !res.foul && onEight;
        assert.ok(res.over && g.winner === (win ? me : op) && res.winner === g.winner, tag + ' 8-ball verdict (foul ' + res.foul + ', onEight ' + onEight + ')');
        if (win) st.wins++; else st.early++;
        break;
      }
      assert.ok(!res.over && g.winner < 0, tag + ' ended without the 8');
      if (eight && wasBreak) { st.spotted++; assert.strictEqual(g.pot[8], 0, tag + ' 8 not spotted'); assert.ok(g.x[8] > 0, tag); }
      // potted balls stay potted
      for (let i = 1; i < 16; i++) if (potBefore[i]) assert.strictEqual(g.pot[i], 1, tag + ' ball ' + i + ' came back');
      assert.strictEqual(g.pot[0], 0, tag + ' the cue ball is not back on the table');
      // groups
      if (wasOpen && !res.foul && !wasBreak && objs.length) {
        st.assigned++; assert.ok(!g.open && g.grp[me] === BD.grp(objs[0]) && g.grp[op] === 1 - g.grp[me], tag + ' groups');
      } else assert.deepStrictEqual(g.grp, grpBefore, tag + ' groups changed');
      // who plays next
      if (res.foul) { assert.strictEqual(g.turn, op, tag + ' foul keeps the turn'); assert.ok(g.hand && g.zone === null, tag + ' no ball in hand after a foul'); }
      else {
        const mine = (b) => g.open || BD.grp(b) === g.grp[me];
        const cont = objs.length > 0 && (wasOpen ? true : objs.some(mine));
        assert.strictEqual(g.turn, cont ? me : op, tag + ' turn (potted ' + potted + ')');
        assert.ok(!g.hand, tag);
      }
      assert.strictEqual(g.breaking, false, tag);
      for (let i = 1; i < 16; i++) if (g.pot[i] && !potBefore[i]) assert.ok(potted.includes(i), tag + ' ball vanished');
    }
    assert.ok(g.winner === 0 || g.winner === 1, tag + ' did not end in ' + k + ' shots');
  }
  assert.ok(st.shots > 100 && st.fouls > 0 && st.scratches > 0 && st.hands > 0, JSON.stringify(st));
  assert.ok(st.wins + st.early >= N);
  assert.ok(st.minGap > 0, 'gap');
});
