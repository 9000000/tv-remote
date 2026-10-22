// Tiến lên's rules and bots (docs/games/web/tienlen/rules.js): what a set of cards is, what beats what, and whole games
// played by bots end with every play legal.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const TL = require(path.join(__dirname, '../../../docs/games/web/tienlen/rules.js'));

const c = (s) => s.split(' ').map((x) => { const v = TL.fromId(x); assert.ok(v != null, x); return v; });
const type = (s) => (TL.combo(c(s)) || {}).type || null;
const beats = (a, b) => TL.beats(TL.combo(c(a)), b ? TL.combo(c(b)) : null);

test('ids go both ways', () => {
  for (const v of TL.deck()) assert.strictEqual(TL.fromId(TL.id(v)), v);
  assert.strictEqual(TL.id(30), '3s'); assert.strictEqual(TL.id(153), '2h'); assert.strictEqual(TL.fromId('1x'), null);
});

test('combinations', () => {
  assert.strictEqual(type('7d'), 'single');
  assert.strictEqual(type('7d 7s'), 'pair');
  assert.strictEqual(type('7d 7s 7h'), 'triple');
  assert.strictEqual(type('7d 7s 7h 7c'), 'quad');
  assert.strictEqual(type('3s 4d 5h'), 'straight');
  assert.strictEqual(type('Qs Kd Ah'), 'straight');
  assert.strictEqual(type('Ks Ad 2h'), null);                        // no 2 in a straight
  assert.strictEqual(type('3s 3d 4h 4c 5s 5h'), 'pairs3');
  assert.strictEqual(type('3s 3d 4h 4c 5s 5h 6d 6c'), 'pairs4');
  assert.strictEqual(type('3s 3d 5h 5c 6s 6h'), null);
  assert.strictEqual(type('7d 8s'), null);
  assert.strictEqual(type('7d 7d'), null);
});

test('what beats what', () => {
  assert.ok(beats('7h', '7s'));
  assert.ok(!beats('7s', '7h'));
  assert.ok(beats('8s 8c', '7d 7h'));
  assert.ok(beats('4s 5s 6h', '3s 4d 5h'));
  assert.ok(!beats('4s 5s 6h 7h', '3s 4d 5h'));                     // a straight of another length
  assert.ok(beats('3s 3d 4h 4c 5s 5h', '2s'));                       // chopping a 2
  assert.ok(beats('9s 9d 9h 9c', '2h'));
  assert.ok(!beats('3s 3d 4h 4c 5s 5h', '2s 2h'));                   // a pair of 2s needs a quad or 4 pairs
  assert.ok(beats('9s 9d 9h 9c', '2s 2h'));
  assert.ok(beats('3s 3d 4h 4c 5s 5h 6d 6c', '2s 2h'));
  assert.ok(beats('9s 9d 9h 9c', '3s 3d 4h 4c 5s 5h'));
  assert.ok(beats('3s 3d 4h 4c 5s 5h 6d 6c', '9s 9d 9h 9c'));
  assert.ok(!beats('9s 9d 9h 9c', 'Ah'));                            // a quad on an ace: no
  assert.ok(beats('4s 4d 5h 5c 6s 6h', '3s 3d 4h 4c 5s 5h'));
  assert.ok(beats('3s'));                                            // leading
});

test('plays finds straights through duplicates and the best top card', () => {
  const ps = TL.plays(c('4s 5d 5h 6c 7s'));
  const st = ps.filter((p) => p.c.type === 'straight').map((p) => p.cards.map(TL.id).join(' '));
  assert.ok(st.includes('4s 5d 6c 7s'), st.join('|'));
  assert.ok(ps.some((p) => p.c.type === 'pair' && p.cards.map(TL.id).join(' ') === '5d 5h'));
  assert.ok(TL.plays(c('3s 3d 4h 4c 5s 5h')).some((p) => p.c.type === 'pairs3'));
});

test('hint picks what fits the table, with the chosen card', () => {
  const hand = c('3s 5d 5h 6c 7s 9h');
  assert.deepStrictEqual(TL.hint(hand, c('6c'), null).map(TL.id), ['5d', '6c', '7s']);
  assert.deepStrictEqual(TL.hint(hand, [], TL.combo(c('4s 4c'))).map(TL.id), ['5d', '5h']);
  assert.deepStrictEqual(TL.hint(hand, [], TL.combo(c('2s'))), []);
  assert.deepStrictEqual(TL.hint(c('3s 3c 3d 3h 4h 5c'), c('3s'), null).map(TL.id), ['3s', '4h', '5c']);   // a quad kept back
});

test('bots lead with the low card, chop a 2, and finish in one go', () => {
  assert.deepStrictEqual(TL.bot(c('3s 4d 5h 9c Kd'), { last: null }, 'balanced').map(TL.id), ['3s', '4d', '5h']);
  assert.deepStrictEqual(TL.bot(c('3s 9s 9d 9h 9c Kd'), { last: TL.combo(c('2h')) }, 'cautious').map(TL.id), ['9s', '9c', '9d', '9h']);
  assert.deepStrictEqual(TL.bot(c('Js Qd Kh'), { last: null }, 'cautious').map(TL.id), ['Js', 'Qd', 'Kh']);
  assert.strictEqual(TL.bot(c('3s 4d'), { last: TL.combo(c('2h')) }, 'aggressive'), null);
});

// a seeded random for games the same each run
function rnd(seed) { return () => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648; }; }
test('300 bot games end, every play legal', () => {
  const ps = ['balanced', 'aggressive', 'cautious', 'balanced'];
  for (let n = 0; n < 300; n++) {
    const hands = TL.deal(4, rnd(n + 1)), out = [];
    let turn = hands.findIndex((h) => h.includes(30)), last = null, by = -1, passed = new Set(), moves = 0;
    while (out.length < 3) {
      assert.ok(++moves < 400, 'game ' + n + ' does not end');
      const near = hands.some((h, o) => o !== turn && !out.includes(o) && h.length <= 3);
      const p = TL.bot(hands[turn], { last, near }, ps[turn]);
      if (p) {
        assert.ok(p.every((x) => hands[turn].includes(x)), 'not in hand');
        const cb = TL.combo(p); assert.ok(cb && TL.beats(cb, last), 'illegal ' + p.map(TL.id));
        hands[turn] = hands[turn].filter((x) => !p.includes(x)); last = cb; by = turn;
        if (!hands[turn].length) out.push(turn);
      } else { assert.ok(last, 'passed while leading'); passed.add(turn); }
      const left = [0, 1, 2, 3].filter((t) => t !== by && !out.includes(t) && !passed.has(t));
      if (!left.length) {
        let lead = by; while (out.includes(lead)) lead = (lead + 1) % 4;
        last = null; passed = new Set(); turn = lead;
      } else { let t = turn; do t = (t + 1) % 4; while (!left.includes(t)); turn = t; }
    }
  }
});
