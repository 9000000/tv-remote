'use strict';
// Cờ cá ngựa (Ludo): the rules and the bot, no screen (node tests load this file too: app/games/tests/cangua.test.js).
// Ported from gamehub24's Ludo.ts (the same author): 52 track cells, 4 horses a seat, a 6 takes a horse out of the stable,
// landing on a cell with others' horses kicks them home unless the cell is safe (starts and the star 8 cells on), a 6 rolls
// again, exact roll to reach the end. gamehub's rolls favour a 6 while no horse is out; so do these.
// One change: a horse is kept as its PROGRESS p (-1 stable, 0..50 track from its own start, 51..55 home stretch, 56 home).
// gamehub turned into the stretch after 51 steps from the cell before its start, which on a drawn board would skip a cell.
(function (root) {
  const TRACK = 52, END = 56, HORSES = 4;
  const START = [39, 0, 13, 26];                     // seat -> the track cell it enters on: bottom-left, top-left, top-right, bottom-right
  const SAFE = [0, 8, 13, 21, 26, 34, 39, 47];

  /** A fresh game: pos[seat][horse] = progress. */
  const fresh = (seats) => Array.from({ length: seats || 4 }, () => Array(HORSES).fill(-1));
  /** The track cell of progress p (0..50), else -1. */
  const cell = (seat, p) => (p >= 0 && p <= 50 ? (START[seat] + p) % TRACK : -1);
  const isSafe = (c) => SAFE.includes(c);
  /** Where a horse at p goes with roll d: the new progress, or null when it cannot (stable without a 6, past the end). */
  function step(p, d) {
    if (p < 0) return d === 6 ? 0 : null;
    const q = p + d;
    return q > END ? null : q;
  }
  /** The horses [seat] can move with roll d. */
  const legal = (pos, seat, d) => [0, 1, 2, 3].filter((h) => step(pos[seat][h], d) != null);
  const out = (pos, seat) => pos[seat].some((p) => p >= 0);
  const done = (pos, seat) => pos[seat].every((p) => p === END);
  const total = (pos, seat) => pos[seat].reduce((a, p) => a + p + 1, 0);
  /** A roll; while no horse is out half of them are a 6 (gamehub's). */
  function roll(pos, seat, rnd) {
    rnd = rnd || Math.random;
    return !out(pos, seat) && rnd() < 0.5 ? 6 : Math.floor(rnd() * 6) + 1;
  }
  /** Who is on track cell c apart from [seat]: [[seat, horse], ...]. */
  function at(pos, seat, c) {
    const r = [];
    pos.forEach((hs, s) => { if (s !== seat) hs.forEach((p, h) => { if (cell(s, p) === c) r.push([s, h]); }); });
    return r;
  }
  /** Moves horse h of [seat] by d (must be legal): {from, to, kicked: [[seat, horse]], home}. Kicked horses go to the stable. */
  function apply(pos, seat, h, d) {
    const from = pos[seat][h], to = step(from, d);
    if (to == null) throw new Error('illegal');
    pos[seat][h] = to;
    const c = cell(seat, to), kicked = c >= 0 && !isSafe(c) ? at(pos, seat, c) : [];
    kicked.forEach(([s, k]) => { pos[s][k] = -1; });
    return { from, to, kicked, home: to === END };
  }
  /** The cells a move passes through, in order (progress values after each step; a horse leaving the stable has one). */
  const path = (from, to) => (from < 0 ? [0] : Array.from({ length: to - from }, (_, i) => from + i + 1));

  /** How many opponents' horses could land on track cell c with their next roll (1-6 away, still on their way round). */
  function danger(pos, seat, c) {
    if (c < 0 || isSafe(c)) return 0;
    let n = 0;
    pos.forEach((hs, s) => {
      if (s === seat) return;
      hs.forEach((p) => {
        const oc = cell(s, p);
        if (oc < 0) return;
        const dist = (c - oc + TRACK) % TRACK;
        if (dist >= 1 && dist <= 6 && p + dist <= 50) n++;
      });
    });
    return n;
  }
  /** The bot's horse for roll d: finish > kick > leave the stable > run from danger / get safe > the furthest. rnd breaks ties. */
  function bot(pos, seat, d, rnd) {
    rnd = rnd || Math.random;
    let best = -1, bs = -1e9;
    legal(pos, seat, d).forEach((h) => {
      const p = pos[seat][h], q = step(p, d), c = cell(seat, q), kick = c >= 0 && !isSafe(c) ? at(pos, seat, c) : [];
      let s = q + rnd() * 0.5;                                         // further along is better
      if (q === END) s += 120;
      if (kick.length) s += 90 + kick.reduce((a, [ks, kh]) => a + pos[ks][kh], 0) * 0.5;
      if (p < 0) s += 70;
      if (q > 50 && p <= 50) s += 40;                                  // into the home stretch: nobody can reach it
      const was = danger(pos, seat, cell(seat, p)), now = kick.length ? 0 : danger(pos, seat, c);
      s += (was ? 30 : 0) - now * 30;
      if (c >= 0 && isSafe(c) && p !== q) s += 8;
      if (s > bs) { bs = s; best = h; }
    });
    return best;
  }

  const api = { TRACK, END, HORSES, START, SAFE, fresh, cell, isSafe, step, legal, out, done, total, roll, at, apply, path, danger, bot };
  if (typeof module === 'object' && module.exports) module.exports = api; else root.LU = api;
})(this);
