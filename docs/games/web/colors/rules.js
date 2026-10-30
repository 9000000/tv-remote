'use strict';
// Bài màu (colour cards) rules and bots, no screen (node tests load this file too: app/tvgames/tests/colors.test.js).
// Ported from gamehub24's Uno.ts (the same author): 108 cards, 7 each, match colour or number / kind, Skip, Reverse (a Skip with
// two players), +2, Wild, Wild +4 (always allowed). Like gamehub, no stacking: a +2 / +4 makes the next player draw and lose the
// turn at once. Drawing: one card; if it can be played, play it or pass. The last card played wins; "Uno!" is called for you.
(function (root) {
  const RED = 0, YELLOW = 1, GREEN = 2, BLUE = 3, WILD = 4;
  const SKIP = 10, REV = 11, DRAW2 = 12, WILDK = 13, WILD4 = 14;            // kinds 0-9 are the numbers
  const HEX = ['#dc2626', '#eab308', '#16a34a', '#2563eb', '#1f2937'];
  const WORD = (k) => (k <= 9 ? String(k) : { 10: '⊘', 11: '⇄', 12: '+2', 13: '🌈', 14: '+4' }[k]);

  // on the phone's card a word of 2+ characters is broken letter by letter (the pad's .cd.own s), so +2 / +4 are keycap emoji: one glyph
  const PWORD = WORD;                                            // the phone shows the TV's words (the pad keeps "+2" on one line)

  function deck() {
    const d = []; let id = 0;
    const add = (c, k) => d.push({ id: id++, c, k });
    for (let c = 0; c < 4; c++) {
      add(c, 0);
      for (let k = 1; k <= 9; k++) { add(c, k); add(c, k); }
      for (const k of [SKIP, REV, DRAW2]) { add(c, k); add(c, k); }
    }
    for (let i = 0; i < 4; i++) { add(WILD, WILDK); add(WILD, WILD4); }
    return d;                                                                // 108
  }
  function shuffle(a, rnd) { rnd = rnd || Math.random; for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; }
  const sortHand = (h) => h.sort((a, b) => a.c - b.c || a.k - b.k || a.id - b.id);

  /** A new game for [n] players (2-4); seat 0 starts (skipped / drawn for when the first card says so). */
  function newGame(n, rnd) {
    const dk = shuffle(deck(), rnd), hands = [];
    for (let s = 0; s < n; s++) hands.push(sortHand(dk.splice(0, 7)));
    let i = dk.findIndex((c) => c.c !== WILD);                               // the first card is never a wild
    const top = dk.splice(i, 1)[0];
    const st = { n, hands, draw: dk, discard: [top], color: top.c, dir: 1, turn: 0, drawn: null, pend: -1, winner: -1, last: null };
    if (top.k === SKIP || (top.k === REV && n === 2)) { st.last = { seat: -1, card: top, ev: 'skip', victim: 0 }; st.turn = step(st, 0, 1); }
    else if (top.k === REV) st.dir = -1;
    else if (top.k === DRAW2) { st.last = { seat: -1, card: top, ev: 'draw', victim: 0, n: 2 }; take(st, 0, 2); st.turn = step(st, 0, 1); }
    return st;
  }
  const step = (st, from, k) => (((from + st.dir * k) % st.n) + st.n * 4) % st.n;
  const topCard = (st) => st.discard[st.discard.length - 1];
  function canPlay(st, card) {
    const t = topCard(st);
    return card.c === WILD || card.c === st.color || (card.k === t.k && t.k < WILDK);
  }
  const playable = (st, seat) => st.hands[seat].filter((c) => canPlay(st, c));
  function take(st, seat, k) {
    let got = null;
    for (let i = 0; i < k; i++) {
      if (!st.draw.length && st.discard.length > 1) { const top = st.discard.pop(); st.draw = shuffle(st.discard.splice(0)); st.discard.push(top); }
      if (!st.draw.length) break;
      got = st.draw.pop(); st.hands[seat].push(got);
    }
    sortHand(st.hands[seat]);
    return got;                                                              // the last card taken, or null
  }
  const OK = { ok: true };
  const no = (why) => ({ ok: false, why });

  /** [seat] plays card [id]. A wild waits for chooseColor (st.pend = seat). Returns {ok} or {ok:false, why:'turn'|'color'|'card'|'illegal'}. */
  function play(st, seat, id) {
    if (st.winner >= 0 || st.pend >= 0 || seat !== st.turn) return no('turn');
    const hand = st.hands[seat], i = hand.findIndex((c) => c.id === id);
    if (i < 0) return no('card');
    const card = hand[i];
    if (!canPlay(st, card)) return no('illegal');
    hand.splice(i, 1); st.discard.push(card); st.drawn = null;
    if (card.c !== WILD) st.color = card.c;
    if (!hand.length) { st.winner = seat; st.last = { seat, card, ev: 'win' }; return OK; }
    if (card.c === WILD) { st.pend = seat; return { ok: true, color: true }; }
    land(st, seat, card); return OK;
  }
  /** The colour a played wild asks for (0-3); the wild's effect follows. */
  function chooseColor(st, seat, col) {
    if (st.pend !== seat || !(col >= 0 && col <= 3)) return no('color');
    const card = topCard(st);
    st.color = col; st.pend = -1; land(st, seat, card); return OK;
  }
  // the card's effect and the turn passing on
  function land(st, seat, card) {
    const next = step(st, seat, 1);
    st.last = { seat, card, ev: '', color: st.color };
    if (card.k === SKIP || (card.k === REV && st.n === 2)) { st.last.ev = 'skip'; st.last.victim = next; st.turn = step(st, seat, 2); }
    else if (card.k === REV) { st.dir = -st.dir; st.last.ev = 'rev'; st.turn = step(st, seat, 1); }
    else if (card.k === DRAW2 || card.k === WILD4) {
      const k = card.k === DRAW2 ? 2 : 4; take(st, next, k); st.last.ev = 'draw'; st.last.victim = next; st.last.n = k; st.turn = step(st, seat, 2);
    } else st.turn = next;
  }
  /** Draw one card; one that cannot be played ends the turn at once. */
  function draw(st, seat) {
    if (st.winner >= 0 || st.pend >= 0 || seat !== st.turn) return no('turn');
    if (st.drawn != null) return no('drawn');
    const got = take(st, seat, 1);
    st.last = { seat, card: null, ev: 'took' };
    if (got && canPlay(st, got)) { st.drawn = got.id; return OK; }          // it may be played now, or passed
    st.drawn = null; st.turn = step(st, seat, 1); return OK;
  }
  function pass(st, seat) {
    if (st.winner >= 0 || st.pend >= 0 || seat !== st.turn) return no('turn');
    if (st.drawn == null) return no('nodraw');
    st.drawn = null; st.turn = step(st, seat, 1); st.last = { seat, card: null, ev: 'pass' }; return OK;
  }

  /** Most common colour in a hand (a wild hand: red). */
  function bestColor(hand) {
    const n = [0, 0, 0, 0]; hand.forEach((c) => { if (c.c < 4) n[c.c]++; });
    let b = 0; for (let c = 1; c < 4; c++) if (n[c] > n[b]) b = c; return b;
  }
  /** A bot's next action: {type:'color', color} | {type:'play', id} | {type:'draw'} | {type:'pass'}. gamehub's order: +4, +2, Skip, Reverse, Wild, numbers high to low. */
  function bot(st, seat) {
    if (st.pend === seat) return { type: 'color', color: bestColor(st.hands[seat]) };
    const ok = playable(st, seat);
    if (!ok.length) return { type: st.drawn != null ? 'pass' : 'draw' };
    const order = [WILD4, DRAW2, SKIP, REV, WILDK];
    for (const k of order) { const c = ok.find((x) => x.k === k); if (c) return { type: 'play', id: c.id }; }
    ok.sort((a, b) => b.k - a.k); return { type: 'play', id: ok[0].id };
  }
  /** Run one bot action on [st]; returns the play/color result (for tests and the page). */
  function doBot(st, seat) {
    const a = bot(st, seat);
    return a.type === 'color' ? chooseColor(st, seat, a.color) : a.type === 'play' ? play(st, seat, a.id)
      : a.type === 'draw' ? draw(st, seat) : pass(st, seat);
  }
  /** Winner first, then the others by cards left (fewest first). */
  function order(st) {
    const rest = st.hands.map((h, s) => s).filter((s) => s !== st.winner).sort((a, b) => st.hands[a].length - st.hands[b].length || a - b);
    return [st.winner].concat(rest);
  }

  const API = { RED, YELLOW, GREEN, BLUE, WILD, SKIP, REV, DRAW2, WILDK, WILD4, HEX, WORD, PWORD, deck, shuffle, newGame, canPlay, playable, play, chooseColor, draw, pass, bot, doBot, bestColor, order, topCard };
  if (typeof module === 'object' && module.exports) module.exports = API; else root.UL = API;
})(this);
