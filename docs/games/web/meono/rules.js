'use strict';
// Mèo nổ (Boom Cats): the rules and the bots, no screen and no clock (node tests load this file too: app/shared/tests/meono.test.js).
// Ported from gamehub24's ExplodingKittens.ts (the same author), base game only (no expansion cards): attack, skip, favor,
// shuffle, see the future, nope, defuse, five cats (pair = steal a random card, three = name a card and steal it).
// The game is a state machine, `G.phase`: play (the turn player plays cards or draws), target (picks a victim), name (three of a
// kind: names a card), nope (a window in which anyone may play Nope; a pause the screen times), give (a favor's victim picks
// a card), peek (sees the top 3: private), insert (puts a defused bomb back), over. The draw pile's top is the END of the array.
(function (root) {
  const ACTIONS = ['attack', 'skip', 'favor', 'shuffle', 'future'];
  const CATS = ['c1', 'c2', 'c3', 'c4', 'c5'];
  const NAMEABLE = ['defuse', 'nope', 'attack', 'skip', 'favor', 'future'];      // what a three of a kind may ask for (the pad has 6 buttons)
  const COUNT = { attack: 4, skip: 4, favor: 4, shuffle: 4, future: 5, nope: 5, c1: 4, c2: 4, c3: 4, c4: 4, c5: 4 };
  const NEEDS_TARGET = ['favor', 'pair', 'three'];
  const NOPE_CAP = 7;                                                             // a chain of Nopes ends here
  const isCat = (k) => CATS.includes(k);

  function shuffle(a, r) { r = r || Math.random; for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; }

  /** A new game for n players (2-4): everyone holds a Defuse and 6 cards; the pile has 2 more Defuses (1 for two players) and n-1 bombs. */
  function create(n, r) {
    r = r || Math.random;
    let no = 0; const mk = (k) => ({ id: k + '-' + (++no), k });
    const deck = []; Object.keys(COUNT).forEach((k) => { for (let i = 0; i < COUNT[k]; i++) deck.push(mk(k)); });
    shuffle(deck, r);
    const hands = Array.from({ length: n }, () => [mk('defuse'), ...deck.splice(0, 6)]);
    for (let i = 0; i < (n === 2 ? 1 : 2); i++) deck.push(mk('defuse'));
    for (let i = 0; i < n - 1; i++) deck.push(mk('bomb'));
    const G = {
      n, r, hands, draw: shuffle(deck, r), disc: [], alive: Array(n).fill(true), turn: Math.floor(r() * n), owe: 1, phase: 'play',
      pend: null, peek: null, bomb: null, outOrder: [], winner: -1, last: null, ev: null, evn: 0,
    };
    hands.forEach(sortHand);
    return G;
  }
  const ORDER = ['defuse', 'nope', 'attack', 'skip', 'favor', 'shuffle', 'future', ...CATS, 'bomb'];
  function sortHand(h) { h.sort((a, b) => ORDER.indexOf(a.k) - ORDER.indexOf(b.k)); }
  const aliveList = (G) => G.alive.map((a, i) => (a ? i : -1)).filter((i) => i >= 0);
  const nextAlive = (G, s) => { for (let k = 1; k <= G.n; k++) { const x = (s + k) % G.n; if (G.alive[x]) return x; } return s; };
  /** Who may be picked as a victim: the other living players holding a card. */
  const victims = (G, s) => aliveList(G).filter((x) => x !== s && G.hands[x].length > 0);
  const count = (hand, k) => hand.reduce((a, c) => a + (c.k === k ? 1 : 0), 0);
  const has = (G, s, k) => G.hands[s].some((c) => c.k === k);
  /** Whose move the game waits for (-1: nobody, the Nope window is open or the game is over). */
  const actor = (G) => (G.phase === 'give' ? G.pend.target : G.phase === 'nope' || G.phase === 'over' ? -1 : G.turn);
  const event = (G, type, seat, extra) => { G.ev = Object.assign({ type, seat }, extra); G.evn++; };

  function take(G, s, id) { const i = G.hands[s].findIndex((c) => c.id === id); return i < 0 ? null : G.hands[s].splice(i, 1)[0]; }

  /** The turn player plays [ids]: one action card, or 2-3 identical cards (a pair / three). '' = done, else why not. */
  function play(G, s, ids) {
    if (G.phase !== 'play' || s !== G.turn) return 'turn';
    const hand = G.hands[s]; ids = [...new Set(Array.isArray(ids) ? ids : [])];
    const cs = ids.map((i) => hand.find((c) => c.id === i));
    if (!cs.length || cs.some((c) => !c)) return 'card';
    const k = cs[0].k;
    if (cs.some((c) => c.k !== k)) return 'combo';
    let kind;
    if (cs.length === 1) { if (!ACTIONS.includes(k)) return 'single'; kind = k; }
    else if (cs.length <= 3 && k !== 'bomb' && k !== 'defuse' && k !== 'nope') kind = cs.length === 2 ? 'pair' : 'three';
    else return 'combo';
    cs.forEach((c) => { take(G, s, c.id); G.disc.push(c); });
    G.pend = { kind, by: s, base: k, n: cs.length, target: -1, name: null, nopes: [] };
    G.last = { by: s, kind, base: k, n: cs.length, target: -1, name: null, nopes: 0, noped: false };
    if (NEEDS_TARGET.includes(kind) && victims(G, s).length) G.phase = 'target'; else G.phase = 'nope';
    return '';
  }
  function target(G, s, t) {
    if (G.phase !== 'target' || s !== G.turn) return 'turn';
    if (!victims(G, s).includes(t)) return 'victim';
    G.pend.target = G.last.target = t; G.phase = G.pend.kind === 'three' ? 'name' : 'nope';
    return '';
  }
  function name(G, s, k) {
    if (G.phase !== 'name' || s !== G.turn) return 'turn';
    if (!NAMEABLE.includes(k)) return 'name';
    G.pend.name = G.last.name = k; G.phase = 'nope'; return '';
  }
  /** Anyone alive holding a Nope plays it in the window: each one flips whether the card goes through. */
  function nope(G, s) {
    if (G.phase !== 'nope' || !G.alive[s]) return 'turn';
    if (G.pend.nopes.length >= NOPE_CAP) return 'cap';
    const c = G.hands[s].find((x) => x.k === 'nope'); if (!c) return 'none';
    take(G, s, c.id); G.disc.push(c); G.pend.nopes.push(s); G.last.nopes = G.pend.nopes.length;
    return '';
  }
  function pass(G, to, owe) { G.turn = to; G.owe = owe; G.phase = 'play'; }
  /** The Nope window ends: the card works (an even number of Nopes) or fizzles. Returns the effect's words id for the screen. */
  function settle(G) {
    if (G.phase !== 'nope') return '';
    const p = G.pend, s = G.turn; G.pend = null; G.phase = 'play';
    G.last.noped = p.nopes.length % 2 === 1;
    if (G.last.noped) { event(G, 'noped', s); return 'noped'; }
    switch (p.kind) {
      case 'attack': pass(G, nextAlive(G, s), G.owe === 1 ? 2 : G.owe + 2); break;    // the next one takes the turns owed + 2
      case 'skip': if (--G.owe <= 0) pass(G, nextAlive(G, s), 1); break;
      case 'shuffle': shuffle(G.draw, G.r); break;
      case 'future': G.peek = G.draw.slice(-3).reverse(); G.phase = 'peek'; break;
      case 'favor': if (p.target >= 0 && G.hands[p.target].length) { G.pend = { target: p.target, kind: 'favor' }; G.phase = 'give'; } break;
      case 'pair': case 'three': {
        const h = G.hands[p.target]; if (p.target < 0 || !h || !h.length) break;
        const i = p.kind === 'pair' ? Math.floor(G.r() * h.length) : h.findIndex((c) => c.k === p.name);
        if (i < 0) { event(G, 'miss', s, { target: p.target, name: p.name }); break; }
        const c = h.splice(i, 1)[0]; G.hands[s].push(c); sortHand(G.hands[s]);
        event(G, 'steal', s, { target: p.target, card: p.kind === 'three' ? c.k : null });
        break;
      }
    }
    return p.kind;
  }
  function give(G, s, id) {
    if (G.phase !== 'give' || s !== G.pend.target) return 'turn';
    const c = take(G, s, id); if (!c) return 'card';
    G.hands[G.turn].push(c); sortHand(G.hands[G.turn]); event(G, 'favor', G.turn, { target: s });
    G.pend = null; G.phase = 'play'; return '';
  }
  function peekOk(G, s) { if (G.phase !== 'peek' || s !== G.turn) return 'turn'; G.peek = null; G.phase = 'play'; return ''; }

  function endDraw(G) { if (--G.owe <= 0) pass(G, nextAlive(G, G.turn), 1); else G.phase = 'play'; }
  /** The turn player draws (ends one of their turns): a bomb is defused if they can, else they are out. */
  function draw(G, s) {
    if (G.phase !== 'play' || s !== G.turn) return 'turn';
    const c = G.draw.pop();
    if (!c) { endDraw(G); return ''; }
    if (c.k !== 'bomb') { G.hands[s].push(c); sortHand(G.hands[s]); event(G, 'draw', s); endDraw(G); return ''; }
    const d = G.hands[s].find((x) => x.k === 'defuse');
    if (d) { take(G, s, d.id); G.disc.push(d); G.bomb = c; G.phase = 'insert'; event(G, 'defuse', s); return ''; }
    G.disc.push(c, ...G.hands[s].splice(0)); G.alive[s] = false; G.outOrder.push(s); event(G, 'boom', s);
    const left = aliveList(G);
    if (left.length <= 1) { G.winner = left[0]; G.phase = 'over'; } else pass(G, nextAlive(G, s), 1);
    return '';
  }
  /** Back into the pile [k] cards below the top (0 = on top, next to be drawn; -1 = anywhere; 99 = the bottom). */
  function insert(G, s, k) {
    if (G.phase !== 'insert' || s !== G.turn) return 'turn';
    if (typeof k !== 'number' || !(k >= -1)) return 'pos';
    const len = G.draw.length, d = k < 0 ? Math.floor(G.r() * (len + 1)) : Math.min(k, len);
    G.draw.splice(len - d, 0, G.bomb); G.bomb = null; G.phase = 'play'; endDraw(G); return '';
  }

  // ---- the bots (gamehub's: combos 40 %, a single action 30 %, else draw), with the Nope and the choices they did not make
  const pick = (G, a) => a[Math.floor(G.r() * a.length)];
  /** A bot's move in the play phase: {act:'draw'} or {act:'play', ids}. */
  function botMove(G, s) {
    const hand = G.hands[s], vs = victims(G, s);
    if (has(G, s, 'future') && G.r() < 0.35) return { act: 'play', ids: [hand.find((c) => c.k === 'future').id] };
    if (vs.length) {
      const kinds = CATS.filter((k) => count(hand, k) >= 2);
      if (kinds.length && G.r() < 0.4) {
        const k = pick(G, kinds), n = count(hand, k) >= 3 && G.r() < 0.5 ? 3 : 2;
        return { act: 'play', ids: hand.filter((c) => c.k === k).slice(0, n).map((c) => c.id) };
      }
    }
    const single = hand.filter((c) => ACTIONS.includes(c.k) && (c.k !== 'favor' || vs.length));
    if (single.length && G.r() < 0.3) return { act: 'play', ids: [pick(G, single).id] };
    return { act: 'draw' };
  }
  /** The victim a bot picks: the one with the most cards. */
  function botTarget(G, s) { const vs = victims(G, s); return vs.sort((a, b) => G.hands[b].length - G.hands[a].length)[0]; }
  function botName(G) { return pick(G, NAMEABLE); }
  /** Where a bot puts a defused bomb: near the top most of the time, to hurt whoever comes next. */
  function botInsert(G) { const x = G.r(); return x < 0.4 ? 0 : x < 0.6 ? 1 : x < 0.8 ? 2 : x < 0.9 ? -1 : 99; }
  /** The card a bot gives away: a cat it holds the least of, else anything but Defuse / Nope. */
  function botGive(G, s) {
    const h = G.hands[s], rank = (c) => (isCat(c.k) ? 0 : c.k === 'shuffle' ? 1 : c.k === 'defuse' || c.k === 'nope' ? 3 : 2) + G.r() * 0.5;
    return h.slice().sort((a, b) => rank(a) - rank(b))[0].id;
  }
  /** Does the bot in [s] play a Nope now? Against what hurts it, and to undo a Nope on its own card. */
  function botNope(G, s) {
    if (G.phase !== 'nope' || !G.alive[s] || !has(G, s, 'nope') || G.pend.nopes.length >= NOPE_CAP) return false;
    const p = G.pend, cancelled = p.nopes.length % 2 === 1;
    const victim = p.target === s || (p.kind === 'attack' && nextAlive(G, p.by) === s && p.by !== s);
    if (!cancelled && victim) return G.r() < 0.7;
    if (cancelled && p.by === s && p.nopes[p.nopes.length - 1] !== s) return G.r() < 0.5;
    return false;
  }
  /** One automatic step of whoever the game waits for (a bot, or a phone whose time ran out). Not for the Nope window. */
  function auto(G) {
    const s = actor(G);
    switch (G.phase) {
      case 'play': { const m = botMove(G, s); return m.act === 'draw' ? draw(G, s) : play(G, s, m.ids); }
      case 'target': return target(G, s, botTarget(G, s));
      case 'name': return name(G, s, botName(G));
      case 'give': return give(G, s, botGive(G, s));
      case 'peek': return peekOk(G, s);
      case 'insert': return insert(G, s, botInsert(G));
    }
    return 'phase';
  }
  /** The bots' round of Nopes, then the window closes (tests and bot-only games; the screen times its own window). */
  function autoNope(G) {
    for (let more = true; more && G.phase === 'nope';) {
      more = false;
      for (let s = 0; s < G.n; s++) if (G.phase === 'nope' && botNope(G, s)) { nope(G, s); more = true; }
    }
    return settle(G);
  }
  /** Finishing order for the scores: the survivor, then the others by who went out last. */
  function ranking(G) {
    const left = aliveList(G).sort((a, b) => G.hands[b].length - G.hands[a].length);
    return left.concat(G.outOrder.slice().reverse());
  }

  const api = {
    ACTIONS, CATS, NAMEABLE, COUNT, NOPE_CAP, isCat, shuffle, create, sortHand, aliveList, nextAlive, victims, count, has, actor,
    play, target, name, nope, settle, give, peekOk, draw, insert, botMove, botTarget, botName, botInsert, botGive, botNope, auto, autoNope, ranking,
  };
  if (typeof module === 'object' && module.exports) module.exports = api; else root.MK = api;
})(this);
