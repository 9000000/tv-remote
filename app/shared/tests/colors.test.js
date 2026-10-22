// Bài màu / Color Cards rules and bots (docs/games/web/colors/rules.js): the deck, what may be played, the special cards,
// drawing, and whole bot games that end with only legal moves.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const UL = require(path.join(__dirname, '../../../docs/games/web/colors/rules.js'));

const seeded = (s) => () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
// a hand-made game: top card red 5, red current, seat 0 to play
function game(n, hands, o) {
  let id = 1000; const mk = ([c, k]) => ({ id: id++, c, k });
  const st = { n, hands: hands.map((h) => h.map(mk)), draw: Array.from({ length: 30 }, () => mk([UL.BLUE, 1])), discard: [mk([UL.RED, 5])], color: UL.RED, dir: 1, turn: 0, drawn: null, pend: -1, winner: -1, last: null };
  return Object.assign(st, o || {});
}
const R = UL.RED, Y = UL.YELLOW, B = UL.BLUE, Wl = UL.WILD;

test('the deck has 108 cards with unique ids', () => {
  const d = UL.deck();
  assert.strictEqual(d.length, 108);
  assert.strictEqual(new Set(d.map((c) => c.id)).size, 108);
  assert.strictEqual(d.filter((c) => c.c === Wl).length, 8);
  assert.strictEqual(d.filter((c) => c.c === R && c.k === 0).length, 1);
  assert.strictEqual(d.filter((c) => c.c === R && c.k === 7).length, 2);
});

test('a new game: 7 cards each, a non-wild start card', () => {
  for (let i = 0; i < 50; i++) {
    const n = 2 + (i % 3), st = UL.newGame(n, seeded(i + 1));
    st.hands.forEach((h, k) => assert.strictEqual(h.length, k === 0 && UL.topCard(st).k === UL.DRAW2 ? 9 : 7));   // a +2 start card is drawn by the first player
    assert.notStrictEqual(UL.topCard(st).c, Wl);
    assert.strictEqual(st.hands.flat().length + st.draw.length + st.discard.length, 108);
  }
});

test('what may be played: colour, number, kind, wild', () => {
  const st = game(3, [[[R, 9], [Y, 5], [B, UL.SKIP], [Y, 3], [Wl, UL.WILDK]]]);
  const ok = st.hands[0].map((c) => UL.canPlay(st, c));
  assert.deepStrictEqual(ok, [true, true, false, false, true]);
  st.discard.push({ id: 5000, c: Y, k: UL.SKIP }); st.color = Y;       // a Skip on the table: any Skip, any yellow
  assert.deepStrictEqual(st.hands[0].map((c) => UL.canPlay(st, c)), [false, true, true, true, true]);
});

test('illegal plays are refused and change nothing', () => {
  const st = game(3, [[[Y, 3], [R, 1]], [[R, 2]], [[R, 3]]]);
  assert.strictEqual(UL.play(st, 0, 1000).why, 'illegal');
  assert.strictEqual(UL.play(st, 1, 1002).why, 'turn');
  assert.strictEqual(UL.play(st, 0, 4242).why, 'card');
  assert.strictEqual(st.hands[0].length, 2); assert.strictEqual(st.turn, 0);
  assert.ok(UL.play(st, 0, 1001).ok); assert.strictEqual(st.turn, 1); assert.strictEqual(st.color, R);
});

test('Skip, Reverse, +2 and the two-player Reverse', () => {
  let st = game(4, [[[R, UL.SKIP], [R, 1]], [[R, 2]], [[R, 3]], [[R, 4]]]);
  UL.play(st, 0, 1000); assert.strictEqual(st.turn, 2);
  st = game(4, [[[R, UL.REV], [R, 1]], [[R, 2]], [[R, 3]], [[R, 4]]]);
  UL.play(st, 0, 1000); assert.strictEqual(st.dir, -1); assert.strictEqual(st.turn, 3);
  st = game(2, [[[R, UL.REV], [R, 1]], [[R, 2]]]);
  UL.play(st, 0, 1000); assert.strictEqual(st.turn, 0);                 // acts as Skip
  st = game(3, [[[R, UL.DRAW2], [R, 1]], [[R, 2]], [[R, 3]]]);
  UL.play(st, 0, 1000); assert.strictEqual(st.hands[1].length, 3); assert.strictEqual(st.turn, 2);
});

test('a wild waits for its colour; +4 draws four and skips', () => {
  const st = game(3, [[[Wl, UL.WILD4], [R, 1]], [[R, 2]], [[R, 3]]]);
  const r = UL.play(st, 0, 1000);
  assert.ok(r.ok && r.color); assert.strictEqual(st.pend, 0); assert.strictEqual(st.turn, 0);
  assert.strictEqual(UL.play(st, 0, 1001).why, 'turn');                   // nothing else until the colour is chosen
  assert.strictEqual(UL.chooseColor(st, 1, B).ok, false);
  assert.strictEqual(UL.chooseColor(st, 0, 7).ok, false);
  assert.ok(UL.chooseColor(st, 0, B).ok);
  assert.strictEqual(st.color, B); assert.strictEqual(st.hands[1].length, 5); assert.strictEqual(st.turn, 2); assert.strictEqual(st.pend, -1);
});

test('drawing: a playable card may be played or passed, a useless one ends the turn', () => {
  let st = game(3, [[[Y, 3]], [[R, 2]], [[R, 3]]], { draw: [{ id: 9001, c: R, k: 8 }] });
  assert.ok(UL.draw(st, 0).ok); assert.strictEqual(st.turn, 0); assert.strictEqual(st.drawn, 9001);
  assert.strictEqual(UL.draw(st, 0).why, 'drawn');
  assert.ok(UL.pass(st, 0).ok); assert.strictEqual(st.turn, 1); assert.strictEqual(st.drawn, null);
  st = game(3, [[[Y, 3]], [[R, 2]], [[R, 3]]], { draw: [{ id: 9002, c: B, k: 8 }] });
  UL.draw(st, 0); assert.strictEqual(st.turn, 1); assert.strictEqual(st.hands[0].length, 2);
  assert.strictEqual(UL.pass(st, 1).why, 'nodraw');
});

test('the draw pile is reshuffled from the discards, the top card stays', () => {
  const st = game(2, [[[Y, 3]], [[R, 2]]], { draw: [], discard: [1, 2, 3, 4].map((i) => ({ id: 7000 + i, c: R, k: i })) });
  st.color = R;
  UL.draw(st, 0);
  assert.strictEqual(st.hands[0].length, 2); assert.strictEqual(st.discard.length, 1); assert.strictEqual(st.discard[0].id, 7004);
  assert.strictEqual(st.draw.length, 2);
});

test('the last card wins, wild or not, and the order follows cards left', () => {
  const st = game(3, [[[Wl, UL.WILDK]], [[R, 2], [R, 3], [R, 4]], [[R, 3]]]);
  assert.ok(UL.play(st, 0, 1000).ok);
  assert.strictEqual(st.winner, 0); assert.strictEqual(st.pend, -1);
  assert.deepStrictEqual(UL.order(st), [0, 2, 1]);
  assert.strictEqual(UL.play(st, 1, 1001).ok, false);
});

test('bots: wild colour is the most common one', () => {
  assert.strictEqual(UL.bestColor([{ c: Y, k: 1 }, { c: Y, k: 2 }, { c: B, k: 3 }, { c: Wl, k: 13 }]), Y);
});

test('bot-only games end, every move legal, cards are conserved', () => {
  let ended = 0;
  for (let i = 0; i < 300; i++) {
    const n = 2 + (i % 3), st = UL.newGame(n, seeded(i * 7 + 3));
    let moves = 0;
    while (st.winner < 0 && moves++ < 3000) {
      const s = st.pend >= 0 ? st.pend : st.turn;
      const a = UL.bot(st, s);
      if (a.type === 'play') assert.ok(UL.canPlay(st, st.hands[s].find((c) => c.id === a.id)));
      const r = UL.doBot(st, s);
      assert.ok(r.ok, JSON.stringify([a, r]));
      assert.strictEqual(st.hands.flat().length + st.draw.length + st.discard.length, 108);
    }
    if (st.winner >= 0) { ended++; assert.strictEqual(st.hands[st.winner].length, 0); assert.strictEqual(UL.order(st).length, n); }
  }
  assert.ok(ended >= 295, 'ended: ' + ended);
});
