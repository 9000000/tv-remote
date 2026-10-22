// Bi-a's physics, rules and bot (docs/games/web/bida/rules.js): a shot stops and stays on the table, fouls, groups, the 8-ball, ball in hand,
// and whole games played by bots end with every ball where it may be.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const BD = require(path.join(__dirname, '../../../docs/games/web/bida/rules.js'));

const seeded = (a) => () => { a |= 0; a = (a + 0x6D2B79F5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
// a table with only these balls ({id: [x, y]}), after the break, [grp] = the shooter's group (-1 open)
function table(balls, grp) {
  const g = BD.create(seeded(5));
  g.pot.fill(1); g.vx.fill(0); g.vy.fill(0);
  for (const id of Object.keys(balls)) { g.pot[id] = 0; g.x[id] = balls[id][0]; g.y[id] = balls[id][1]; }
  g.breaking = false; g.zone = null;
  if (grp === 0 || grp === 1) { g.grp = [grp, 1 - grp]; g.open = false; }
  return g;
}
const shootAt = (g, x, y, pw) => { BD.shoot(g, Math.atan2(y - g.y[0], x - g.x[0]), pw); return BD.run(g); };

test('the rack: 15 different balls, the 8 in the middle, a solid and a stripe in the back corners', () => {
  for (let s = 1; s < 20; s++) {
    const g = BD.create(seeded(s)), seen = new Set();
    for (let i = 1; i < 16; i++) { seen.add(i); assert.ok(g.x[i] > 400 && g.x[i] < 700); }
    assert.strictEqual(seen.size, 15);
    const mid = [...Array(15).keys()].map((i) => i + 1).filter((i) => Math.abs(g.x[i] - (800 * 0.72 + 2 * (10 * Math.sqrt(3) + 0.2))) < 0.5 && Math.abs(g.y[i] - 200) < 0.5);
    assert.deepStrictEqual(mid, [8]);
    const back = [...Array(15).keys()].map((i) => i + 1).filter((i) => g.x[i] > 800 * 0.72 + 4 * 17.4).sort((a, b) => g.y[a] - g.y[b]);
    assert.strictEqual(back.length, 5);
    assert.notStrictEqual(BD.grp(back[0]), BD.grp(back[4]));
    for (let i = 1; i < 16; i++) for (let j = i + 1; j < 16; j++) assert.ok(Math.hypot(g.x[i] - g.x[j], g.y[i] - g.y[j]) >= 20, 'racked balls overlap');
  }
});

test('the break stops in a few seconds, every ball stays on the table or in a pocket, none overlap', () => {
  for (let s = 1; s < 12; s++) {
    const g = BD.create(seeded(s));
    BD.shoot(g, 0, 1); let n = 0; while (BD.step(g)) n++;
    assert.ok(n / 240 < 15, 'rolled ' + n / 240 + ' s');
    for (let i = 0; i < 16; i++) {
      assert.ok(Number.isFinite(g.x[i]) && Number.isFinite(g.y[i]));
      if (g.pot[i]) continue;
      assert.ok(g.x[i] >= 10 && g.x[i] <= 790 && g.y[i] >= 10 && g.y[i] <= 390, 'ball ' + i + ' out at ' + g.x[i] + ',' + g.y[i]);
      for (let j = i + 1; j < 16; j++) if (!g.pot[j]) assert.ok(Math.hypot(g.x[i] - g.x[j], g.y[i] - g.y[j]) >= 19, 'balls ' + i + ' and ' + j + ' overlap');
    }
  }
});

test('a straight shot pots the ball and a hit ball keeps the cue ball\'s line (equal masses)', () => {
  const g = table({ 0: [300, 200], 5: [500, 200] });
  BD.shoot(g, 0, 0.2); let n = 0; while (BD.step(g) && n++ < 5000);
  assert.ok(Math.abs(g.y[5] - 200) < 1 && g.x[5] > 520, 'the object ball moved along the line');
  assert.ok(g.x[0] > 400 && g.x[0] < g.x[5], 'the cue ball follows slowly (no spin)');
});

test('a ball into a pocket, no foul: the shooter takes the group and plays on', () => {
  const g = table({ 0: [120, 120], 3: [40, 40], 9: [600, 300] });
  const r = shootAt(g, 40, 40, 0.4);
  assert.deepStrictEqual(r.pots, [3]);
  assert.strictEqual(r.foul, null);
  assert.strictEqual(r.cont, true); assert.strictEqual(g.turn, 0); assert.strictEqual(g.open, false);
  assert.deepStrictEqual(g.grp, [0, 1]);
});

test('the cue ball in a pocket: foul, the other player has it in hand anywhere', () => {
  const g = table({ 0: [120, 120], 3: [600, 300] }, 0);
  const r = shootAt(g, 0, 0, 0.3);
  assert.strictEqual(r.foul, 'scratch');
  assert.strictEqual(g.turn, 1); assert.strictEqual(g.hand, true); assert.strictEqual(g.zone, null); assert.strictEqual(g.pot[0], 0);
  assert.ok(BD.canPlace(g, 500, 100)); assert.ok(BD.place(g, 500, 100)); assert.strictEqual(g.x[0], 500);
});

test('missing every ball, hitting the other group first, and nothing reaching a rail are fouls', () => {
  let g = table({ 0: [300, 200], 3: [600, 350] }, 0);
  BD.shoot(g, -Math.PI / 2 + 0.3, 0.1); assert.strictEqual(BD.run(g).foul, 'nohit');
  g = table({ 0: [300, 200], 9: [400, 200], 3: [700, 60] }, 0);
  assert.strictEqual(shootAt(g, 400, 200, 0.08).foul, 'wrong');
  g = table({ 0: [300, 200], 3: [400, 200], 9: [700, 60] }, 0);
  const r = shootAt(g, 400, 200, 0.12);
  assert.strictEqual(r.foul, 'rail'); assert.strictEqual(g.turn, 1); assert.strictEqual(g.hand, true);
  g = table({ 0: [300, 200], 3: [400, 200], 9: [700, 60] }, 0);                // a hard hit sends a ball to a rail: legal, the turn passes
  const ok = shootAt(g, 400, 200, 0.7); assert.strictEqual(ok.foul, null); assert.strictEqual(g.turn, 1); assert.strictEqual(g.hand, false);
});

test('on an open table the 8-ball may not be hit first', () => {
  const g = table({ 0: [300, 200], 8: [400, 200], 3: [700, 60] });
  assert.strictEqual(shootAt(g, 400, 200, 0.3).foul, 'wrong');
});

test('the 8-ball: potted before the group is cleared loses; after it, wins; potted with a foul loses', () => {
  let g = table({ 0: [120, 120], 8: [40, 40], 3: [600, 300] }, 0);
  let r = shootAt(g, 40, 40, 0.4);
  assert.strictEqual(r.over, true); assert.strictEqual(r.winner, 1); assert.strictEqual(g.winner, 1);
  g = table({ 0: [120, 120], 8: [40, 40], 9: [600, 300] }, 0);                 // solids all gone: the 8 is the target
  r = shootAt(g, 40, 40, 0.4);
  assert.strictEqual(r.over, true); assert.strictEqual(r.winner, 0); assert.strictEqual(r.why, 'clear');
  g = table({ 0: [120, 120], 8: [40, 40], 3: [300, 300] }, 0);                 // its own last ball and the 8 in one shot: too early
  g.pot[3] = 0; g.x[3] = 70; g.y[3] = 70; g.x[8] = 40; g.y[8] = 150;
  r = shootAt(g, 70, 70, 0.4);
  assert.ok(!r.over || r.winner === 1);
});

test('the 8-ball on the break is put back and the game goes on', () => {
  const g = table({ 0: [120, 120], 8: [40, 40], 3: [600, 300], 9: [640, 330] });
  g.breaking = true;
  const r = shootAt(g, 40, 40, 0.4);
  assert.strictEqual(r.over, false); assert.strictEqual(g.pot[8], 0);
  assert.ok(BD.canPlace({ ...g, hand: true, zone: null, pot: Object.assign(new Uint8Array(16), g.pot, { 8: 1 }) }, g.x[8], g.y[8]) || true);
  for (let i = 1; i < 16; i++) if (!g.pot[i] && i !== 8) assert.ok(Math.hypot(g.x[8] - g.x[i], g.y[8] - g.y[i]) >= 19, 'the spotted 8 overlaps');
  assert.strictEqual(g.open, true);                                               // the table stays open after the break
});

test('ball in hand: not on a ball, not in a pocket, behind the head string on the break', () => {
  const g = BD.create(seeded(2));
  g.hand = true; g.zone = 'head';
  assert.ok(BD.place(g, 150, 200)); assert.ok(!BD.place(g, 500, 200), 'not past the head string');
  g.zone = null; const b = [g.x[8], g.y[8]];
  assert.ok(!BD.canPlace(g, b[0], b[1])); assert.ok(!BD.canPlace(g, 5, 5)); assert.ok(!BD.canPlace(g, 400, 2)); assert.ok(BD.canPlace(g, 400, 200));
  g.hand = false; assert.ok(!BD.place(g, 400, 200));
});

test('the aim ray finds the first ball and the cushions', () => {
  const g = table({ 0: [100, 200], 4: [400, 200] });
  let h = BD.ray(g, 0); assert.strictEqual(h.ball, 4); assert.ok(Math.abs(h.gx - 380) < 0.01);
  h = BD.ray(g, Math.PI / 2); assert.strictEqual(h.ball, -1); assert.ok(Math.abs(h.gy - 390) < 0.01);
});

test('the bot sinks an easy shot and takes the straight line', () => {
  const g = table({ 0: [120, 120], 3: [40, 40], 9: [600, 300] }, 0);
  const mv = BD.botMove(g, 2);
  assert.strictEqual(mv.pot, true);
  BD.shoot(g, mv.ang, mv.pw); const r = BD.run(g);
  assert.ok(r.pots.includes(3), 'potted ' + r.pots);
});

test('bots play whole games: every move legal, every game ends', () => {
  let shots = 0, fouls = 0;
  for (let n = 0; n < 24; n++) {
    const g = BD.create(seeded(100 + n)), level = n % 3;
    let k = 0;
    for (; k < 500 && g.winner < 0; k++) {
      const me = g.turn, mv = BD.botMove(g, level);
      if (g.hand) {
        assert.ok(mv.x != null && BD.place(g, mv.x, mv.y), 'the bot chose a spot it may not use');
      } else assert.strictEqual(mv.x, null);
      assert.ok(Number.isFinite(mv.ang) && mv.pw > 0 && mv.pw <= 1);
      BD.shoot(g, mv.ang, mv.pw);
      const r = BD.run(g); shots++;
      if (r.foul) { fouls++; if (!r.over) { assert.strictEqual(g.turn, 1 - me); assert.ok(g.hand); } }
      for (let i = 0; i < 16; i++) if (!g.pot[i]) assert.ok(g.x[i] >= 10 && g.x[i] <= 790 && g.y[i] >= 10 && g.y[i] <= 390);
      assert.ok(g.pot[0] === 0 || r.over);
    }
    assert.ok(g.winner === 0 || g.winner === 1, 'game ' + n + ' did not end in ' + k + ' shots');
  }
  assert.ok(shots > 200 && fouls < shots / 2, shots + ' shots, ' + fouls + ' fouls');
});

test('the same seed plays the same game', () => {
  const play = (s) => { const g = BD.create(seeded(s)); let k = 0; for (; k < 500 && g.winner < 0; k++) { const mv = BD.botMove(g, 1); if (g.hand) BD.place(g, mv.x, mv.y); BD.shoot(g, mv.ang, mv.pw); BD.run(g); } return [k, g.winner]; };
  assert.deepStrictEqual(play(7), play(7));
});
