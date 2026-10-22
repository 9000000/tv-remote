'use strict';
// Gomoku (docs/games/web/caro/ai.js): five or more wins, and the bot wins, blocks and always finishes a game.
const test = require('node:test');
const assert = require('node:assert');
const C = require('../../../docs/games/web/caro/ai.js');
const at = (x, y) => y * C.N + x;
function board(xs, os) { const b = C.create(); (xs || []).forEach(([x, y]) => { b[at(x, y)] = 1; }); (os || []).forEach(([x, y]) => { b[at(x, y)] = 2; }); return b; }
const line = (x, y, dx, dy, n) => Array.from({ length: n }, (_, k) => [x + dx * k, y + dy * k]);

test('five in a row wins in all four directions', () => {
  for (const [dx, dy] of C.DIRS) {
    const b = board(line(4, 4, dx, dy, 5));
    assert.equal(C.winLine(b, at(4 + dx * 2, 4 + dy * 2)).length, 5, `dir ${dx},${dy}`);
  }
});
test('four is not a win, a broken five is not a win', () => {
  assert.equal(C.winLine(board(line(3, 3, 1, 0, 4)), at(3, 3)), null);
  assert.equal(C.winLine(board([[0, 0], [1, 0], [2, 0], [4, 0], [5, 0]]), at(2, 0)), null);
});
test('six or more in a row also wins and the whole run is returned', () => {
  const w = C.winLine(board(line(2, 7, 1, 0, 6)), at(4, 7));
  assert.equal(w.length, 6);
  const edge = C.winLine(board(line(9, 9, 1, 0, 6)), at(14, 9));      // against the edge of the board
  assert.equal(edge.length, 6);
});
test('an enemy stone cuts the run', () => {
  const b = board([[0, 0], [1, 0], [3, 0], [4, 0]], [[2, 0]]);
  assert.equal(C.winLine(b, at(1, 0)), null);
});
test('the bot takes a win when it has one', () => {
  const b = board([[7, 7], [8, 7], [9, 7], [10, 7]].concat([]), [[3, 3], [3, 4], [3, 5], [3, 6]]);   // both have a four; O to move wins at (3,2) or (3,7)
  const m = C.bestMove(b, 2);
  assert.ok([at(3, 2), at(3, 7)].includes(m), 'took its own five rather than blocking, got ' + m);
});
test('the bot blocks an open four', () => {
  const b = board(line(5, 5, 1, 0, 4), [[8, 9], [9, 9]]);
  // an open four cannot be stopped, but with one end already shut it is a plain four and must be blocked
  b[at(4, 5)] = 2;
  assert.equal(C.bestMove(b, 2), at(9, 5));
});
test('the bot blocks a four in a column and a diagonal', () => {
  assert.equal(C.bestMove(board(line(6, 3, 0, 1, 4), [[0, 0]]).map((v, i) => (i === at(6, 2) ? 2 : v)), 2), at(6, 7));
  const b = board(line(4, 4, 1, 1, 4), [[0, 0]]); b[at(3, 3)] = 2;
  assert.equal(C.bestMove(b, 2), at(8, 8));
});
test('the bot blocks an open three', () => {
  for (const [dx, dy] of C.DIRS) {
    const sx = 6, sy = dy < 0 ? 9 : 5, three = line(sx, sy, dx, dy, 3);
    const b = board(three, [[0, 0], [14, 14]]);
    const m = C.bestMove(b, 2), ends = [[sx - dx, sy - dy], [sx + dx * 3, sy + dy * 3]].map(([x, y]) => at(x, y));
    assert.ok(ends.includes(m), `dir ${dx},${dy}: blocked at ${m % C.N},${(m / C.N) | 0}`);
  }
});
test('the bot blocks a split three (X X _ X)', () => {
  const b = board([[5, 5], [6, 5], [8, 5]], [[0, 0], [14, 14]]);
  assert.ok([at(7, 5), at(4, 5), at(9, 5)].includes(C.bestMove(b, 2)));
});
test('the bot answers on an empty board in the middle, and is fast', () => {
  assert.equal(C.bestMove(C.create(), 1), at(7, 7));
  const b = board(line(4, 4, 1, 0, 3).concat(line(5, 6, 1, 1, 3)), line(6, 4, 0, 1, 3).concat([[9, 9], [3, 8]]));
  const t = Date.now(); C.bestMove(b, 1); assert.ok(Date.now() - t < 300);
});
test('50 bot-vs-bot games end with a five or a full board', () => {
  for (let g = 0; g < 50; g++) {
    const b = C.create(); let p = 1 + (g & 1), won = false, n = 0;
    while (n < b.length) {
      const m = C.bestMove(b, p); assert.ok(m >= 0 && !b[m]);
      b[m] = p; n++;
      if (C.winLine(b, m)) { won = true; break; }
      p = 3 - p;
    }
    assert.ok(won || C.isFull(b));
  }
});
