'use strict';
// Tiến lên miền Nam: the rules and the bots, no screen (node tests load this file too: app/games/tests/tienlen.test.js).
// Ported from gamehub24's Thirteen.ts (the same author): a card is rank * 10 + suit, ranks 3..15 (15 = the 2, "heo"),
// suits 0..3 spades < clubs < diamonds < hearts, so a bigger number is a bigger card. The bots' finders are rewritten to
// look at every way a hand can make a combination (gamehub's took only runs of neighbouring cards, missing 4-5-5-6-7).
(function (root) {
  const TWO = 15, ACE = 14;
  const RANKS = { 11: 'J', 12: 'Q', 13: 'K', 14: 'A', 15: '2' };
  const SUITS = 'scdh';
  const rank = (c) => Math.floor(c / 10), suit = (c) => c % 10;
  const id = (c) => (RANKS[rank(c)] || String(rank(c))) + SUITS[suit(c)];   // 33 -> "3h": what the phone's pad shows
  const fromId = (s) => {
    s = String(s); const r = s.slice(0, -1), su = SUITS.indexOf(s.slice(-1));
    const n = { J: 11, Q: 12, K: 13, A: 14, 2: 15 }[r] || parseInt(r, 10);
    return su < 0 || !(n >= 3 && n <= 15) ? null : n * 10 + su;
  };

  function deck() { const d = []; for (let r = 3; r <= TWO; r++) for (let s = 0; s < 4; s++) d.push(r * 10 + s); return d; }
  function shuffle(a, rnd) { rnd = rnd || Math.random; for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; }
  /** 13 cards to each of n players (2-4), sorted; the rest of the deck stays out. */
  function deal(n, rnd) { const d = shuffle(deck(), rnd); return Array.from({ length: n }, (_, i) => d.slice(i * 13, i * 13 + 13).sort((a, b) => a - b)); }

  // ---- what a set of cards is
  const T = { SINGLE: 'single', PAIR: 'pair', TRIPLE: 'triple', STRAIGHT: 'straight', QUAD: 'quad', PAIRS3: 'pairs3', PAIRS4: 'pairs4' };
  function pairsRun(s, k) {                                     // k consecutive pairs, no 2 in it
    if (s.length !== k * 2) return false;
    for (let i = 0; i < k; i++) {
      const a = rank(s[i * 2]), b = rank(s[i * 2 + 1]);
      if (a !== b || a === TWO) return false;
      if (i && a !== rank(s[i * 2 - 1]) + 1) return false;
    }
    return true;
  }
  /** {type, n, value} or null; value compares two of the same type (a straight's also holds its length). */
  function combo(cards) {
    if (!cards || !cards.length) return null;
    const s = cards.slice().sort((a, b) => a - b), n = s.length, top = s[n - 1];
    if (new Set(s).size !== n) return null;
    if (n === 1) return { type: T.SINGLE, n, value: top };
    if (s.every((c) => rank(c) === rank(s[0]))) {
      if (n === 2) return { type: T.PAIR, n, value: top };
      if (n === 3) return { type: T.TRIPLE, n, value: top };
      if (n === 4) return { type: T.QUAD, n, value: top };
    }
    if (n >= 3 && rank(top) !== TWO && s.every((c, i) => !i || rank(c) === rank(s[i - 1]) + 1)) return { type: T.STRAIGHT, n, value: top * 100 + n };
    if (n === 6 && pairsRun(s, 3)) return { type: T.PAIRS3, n, value: top };
    if (n === 8 && pairsRun(s, 4)) return { type: T.PAIRS4, n, value: top };
    return null;
  }
  /** Can [c] be played on [last] (null = leading)? Chopping: a single 2 falls to a quad or 3+ pairs in a row, a pair of 2s
   *  to a quad or 4 pairs in a row, 3 pairs in a row to a quad or 4 pairs, a quad to 4 pairs in a row. */
  function beats(c, last) {
    if (!c) return false;
    if (!last) return true;
    const two = last.value >= TWO * 10 && last.value < TWO * 10 + 10;
    if (last.type === T.SINGLE && two && (c.type === T.QUAD || c.type === T.PAIRS3 || c.type === T.PAIRS4)) return true;
    if (last.type === T.PAIR && two && (c.type === T.QUAD || c.type === T.PAIRS4)) return true;
    if (last.type === T.PAIRS3 && (c.type === T.QUAD || c.type === T.PAIRS4)) return true;
    if (last.type === T.QUAD && c.type === T.PAIRS4) return true;
    if (c.type !== last.type || c.n !== last.n) return false;
    return c.value > last.value;
  }
  const chop = (c, last) => !!last && c.type !== last.type && beats(c, last);   // "chặt": the table's 2s or hàng cut

  // ---- every play a hand has (the bots and the hint): per kind, the lower ranks take their lowest cards, the top rank
  // every card it has, so each distinct value appears once
  function groups(hand) { const g = {}; for (const c of hand) (g[rank(c)] = g[rank(c)] || []).push(c); for (const k in g) g[k].sort((a, b) => a - b); return g; }
  function plays(hand) {
    const g = groups(hand), out = [], ranks = Object.keys(g).map(Number).sort((a, b) => a - b);
    for (const c of hand) out.push([c]);
    for (const r of ranks) {
      const cs = g[r];
      for (let k = 2; k <= Math.min(4, cs.length); k++) {
        if (k === 4) { out.push(cs.slice()); continue; }
        for (let t = k - 1; t < cs.length; t++) out.push(cs.slice(0, k - 1).concat(cs[t]));   // the top card decides the value
      }
    }
    for (let i = 0; i < ranks.length; i++) {                    // straights: 3..12 long, no 2
      let j = i;
      while (j + 1 < ranks.length && ranks[j + 1] === ranks[j] + 1 && ranks[j + 1] !== TWO) j++;
      if (ranks[i] === TWO) continue;
      for (let e = i + 2; e <= j; e++) {
        const base = ranks.slice(i, e).map((r) => g[r][0]);
        for (const t of g[ranks[e]]) out.push(base.concat(t));
      }
    }
    for (const k of [3, 4]) {                                   // pairs in a row
      for (let i = 0; i + k <= ranks.length; i++) {
        const rs = ranks.slice(i, i + k);
        if (rs.some((r, x) => g[r].length < 2 || r === TWO || (x && r !== rs[x - 1] + 1))) continue;
        const base = [].concat(...rs.slice(0, -1).map((r) => g[r].slice(0, 2))), top = g[rs[k - 1]];
        for (let t = 1; t < top.length; t++) out.push(base.concat(top[0], top[t]));
      }
    }
    return out.map((p) => ({ cards: p.sort((a, b) => a - b), c: combo(p) })).filter((p) => p.c);
  }
  const legal = (hand, last) => plays(hand).filter((p) => beats(p.c, last));

  // ---- bots: three personas as in gamehub (BOT_LOGIC.md): cautious keeps its big cards, aggressive spends them to lead,
  // balanced throws its junk first. Everyone defends when someone is about to go out.
  const HANG = [T.QUAD, T.PAIRS3, T.PAIRS4];
  const usesTwo = (p) => p.cards.some((c) => rank(c) === TWO);
  /** The bot's play, or null to pass. [st]: {last, lastCards, near} (near: an opponent holds 3 cards or fewer). */
  function bot(hand, st, persona) {
    const last = st.last, near = !!st.near, end = hand.length < 5;
    if (!last) return lead(hand, persona, near, end);
    const ok = legal(hand, last);
    if (!ok.length) return null;
    const same = ok.filter((p) => p.c.type === last.type).sort((a, b) => a.c.value - b.c.value);
    const cut = ok.filter((p) => p.c.type !== last.type);       // only a chop: the table holds 2s or hàng; always worth it
    if (cut.length) return cut.sort((a, b) => a.c.n - b.c.n)[0].cards;
    if (persona === 'cautious' && last.value % 1000 >= ACE * 10 && last.type !== T.STRAIGHT && hand.length > 3 && !near) return null;
    // not breaking up hàng or spending a 2 early, unless defending or nearly out
    const keep = (p) => near || end || persona === 'aggressive' || (!usesTwo(p) && !p.cards.some((c) => inHang(hand, c)));
    const pick = same.find(keep);
    if (pick) return pick.cards;
    return near || end ? same[0] && same[0].cards : null;
  }
  function inHang(hand, c) { return plays(hand).some((p) => HANG.includes(p.c.type) && p.cards.includes(c)); }
  function lead(hand, persona, near, end) {
    const all = plays(hand), low = hand[0];
    const whole = all.find((p) => p.cards.length === hand.length); if (whole) return whole.cards;   // the last cards in one go
    const free = (p) => !usesTwo(p) || near || end;
    const st = all.filter((p) => p.c.type === T.STRAIGHT && free(p)).sort((a, b) => b.c.n - a.c.n || a.c.value - b.c.value);
    if (st.length && st[0].cards.includes(low)) return st[0].cards;
    const withLow = all.filter((p) => p.cards.includes(low) && [T.STRAIGHT, T.TRIPLE, T.PAIR].includes(p.c.type) && free(p))
      .sort((a, b) => b.c.n - a.c.n || a.c.value - b.c.value);
    if (withLow.length) return withLow[0].cards;                // the lowest card leaves with the most company it has
    if (st.length && persona !== 'cautious') return st[0].cards;
    if ((near || end || (persona === 'aggressive' && hand.length < 8))) {
      const h = all.filter((p) => HANG.includes(p.c.type)); if (h.length) return h[0].cards;
    }
    if (near) {                                                 // "chốt": a single only after the biggest one
      const tr = all.filter((p) => p.c.type !== T.SINGLE && free(p)).sort((a, b) => b.c.n - a.c.n);
      if (tr.length) return tr[0].cards;
      return [hand[hand.length - 1]];
    }
    return [low];                                               // "tẩu rác"
  }

  /** The phone's Hint: the play holding the last picked card that fits the table (longest, then lowest), or with none
   *  picked the smallest that fits. */
  function hint(hand, picked, last) {
    const ok = legal(hand, last);
    if (!ok.length) return [];
    const piv = picked && picked.length ? picked[picked.length - 1] : null;
    const pool = piv == null ? ok : ok.filter((p) => p.cards.includes(piv));
    if (!pool.length) return [];
    if (piv == null) return (last ? pool.sort((a, b) => (a.c.type !== last.type) - (b.c.type !== last.type) || a.c.value - b.c.value) : pool.sort((a, b) => a.cards[0] - b.cards[0] || b.c.n - a.c.n))[0].cards;
    const hang = (p) => HANG.includes(p.c.type) && !(last && last.type !== p.c.type);   // hàng kept back unless it is the answer
    return pool.sort((a, b) => hang(a) - hang(b) || b.c.n - a.c.n || (b.c.type === T.STRAIGHT) - (a.c.type === T.STRAIGHT) || a.c.value - b.c.value)[0].cards;
  }

  const api = { TWO, ACE, T, rank, suit, id, fromId, deck, shuffle, deal, combo, beats, chop, plays, legal, bot, hint, HANG };
  if (typeof module === 'object' && module.exports) module.exports = api; else root.TL = api;
})(this);
