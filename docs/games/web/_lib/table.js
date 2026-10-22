'use strict';
// Table: what every turn-based table game on TV Games shares (Tiến lên, Uno, Ludo, ...), as gamehub24's BaseGame is for its
// games: a game gives its rules through a few hooks and gets seats, bots, phones joining and leaving, a turn clock, scores
// and the phones' layouts. Four seats: player N sits in seat N-1; a bot holds every seat without a phone and hands it over
// (hand and all) when a phone joins, and takes it back when the phone drops. The TV remote's OK / a phone's START or "again"
// deals the next game. Words: Table.W (vi / en by the TV's language) plus the game's own.
//
//   const t = Table({
//     seats: 4,                       // 2-4
//     turnSecs: 40,                   // a phone's turn; 0 = no clock
//     botMs: [900, 1500],             // a bot's think time
//     cards: true,                    // uses the pad's {cards} layout: an older TV Games is told to update
//     deal(t) {},                     // a new game: set t.turn, the game's state
//     view(t, seat) {},               // the {cards:{..}} (or any layout) for the phone in [seat], called after every change
//     act(t, seat, msg) {},           // a phone's button: msg = {act, cards}; return words to say on that phone (a refusal) or nothing
//     bot(t, seat) {},                // a bot's move in [seat] (call t.next(...) or whatever the game does)
//     timeUp(t, seat) {},             // the clock ran out for a phone: play for it (pass, the smallest move)
//     draw(t) {},                     // the TV, after every change
//     remote: true,                   // seat 1 with no phone is played with the TV remote (see "the remote seat" below); or {mount:'#stage', quick:'play'}
//   });
//   t.go()                            // the first game; later ones by OK / START / {act:'again'}
//   t.turnTo(seat)                    // whose turn it is now (restarts the clock, wakes the bot, redraws, sends layouts)
//   t.changed()                       // redraw + layouts without a new turn
//   t.over(order)                     // the game ended: [seat, ...] best first; scores added, t.done = true
//   t.send(seat, layout)              // a layout outside view() (the Hint's picked cards), counted as sent
// The remote seat: with no phone in seat 1, the TV remote plays it. The panel (#rmt) draws what view(t, 0) would send that phone
// (hand, acts, note): Left / Right move along a row, Up / Down switch between the hand and the acts, OK picks a card (or, with
// quick:'play', plays it) / presses an act. Presses go to the same act(t, 0, {act, cards}) a phone's would, so the rules stay
// the game's. The game styles it with body.rmt-on (shown, not between games) and #rmt; t.remote is true while it is in use.
//   t.name(seat), t.human(seat), t.left (seconds on the clock), t.scores[seat], t.games, t.lastWinner, t.say(seat, words), t.buzz(seat, ms)
(function (root) {
  const VI = (typeof navigator === 'object' && (navigator.language || '').toLowerCase().startsWith('vi'));
  const W = VI ? {
    p: (n) => `Người chơi ${n}`, bot: (n) => `Máy ${n}`, wait: (n) => `Chờ ${n}…`, turn: 'Lượt của bạn', again: 'Ván mới',
    newg: 'Ván mới: nhấn Ván mới trên điện thoại hoặc OK', win: (n) => `${n} thắng ván!`, pts: 'điểm', total: 'Tổng',
    place: ['Nhất', 'Nhì', 'Ba', 'Bét'], old: 'Cần TV Games mới hơn để chơi game này', oldh: 'App điện thoại hoặc máy tính → TV Games → Cập nhật',
    nopad: 'Mở trang này trong TV Games trên TV', notyet: 'Chưa tới lượt bạn',
  } : {
    p: (n) => `Player ${n}`, bot: (n) => `Bot ${n}`, wait: (n) => `Waiting for ${n}…`, turn: 'Your turn', again: 'New game',
    newg: 'New game: tap New game on a phone or press OK', win: (n) => `${n} wins!`, pts: 'pts', total: 'Total',
    place: ['1st', '2nd', '3rd', 'Last'], old: 'This game needs a newer TV Games', oldh: 'Phone or computer app → TV Games → Update',
    nopad: 'Open this page in TV Games on the TV', notyet: 'Not your turn yet',
  };
  const SCORE = { 2: [1, -1], 3: [2, 0, -2], 4: [3, 1, -1, -3] };
  const COLORS = ['#3b82f6', '#ef4444', '#22c55e', '#eab308'];

  function Table(g) {
    const pad = root.tvpad, n = g.seats || 4;
    const t = {
      W, VI, COLORS, pad, n, seats: [...Array(n).keys()], ok: !!pad && (!g.cards || !!(pad.has && pad.has('cards'))),
      humans: Array(n).fill(false), scores: Array(n).fill(0), turn: 0, left: 0, done: true, games: 0, lastWinner: -1, order: [],
      human: (s) => t.humans[s],
      name(s) { if (phone(s)) { try { return pad.name(s + 1) || W.p(s + 1); } catch (e) { return W.p(s + 1); } } return s === 0 && t.remote ? W.p(1) : W.bot(s + 1); },
      say(s, w) { if (pad && phone(s)) pad.say(s + 1, w || ''); },
      buzz(s, ms) { if (pad && phone(s)) pad.vibrate(s + 1, ms); },
      go, turnTo, changed, over,
    };
    let botT = null;
    const players = () => (pad ? pad.players() : []);
    const phone = (s) => t.humans[s] && !(s === 0 && t.remote);                   // a real phone sits there (seat 1 may be the TV remote's)
    function seat() {
      const ps = players(); t.seats.forEach((s) => { t.humans[s] = ps.includes(s + 1); });
      t.remote = t.ok && !!g.remote && !t.humans[0]; if (t.remote) t.humans[0] = true;   // no phone in seat 1: the remote plays it
    }
    function wake() {
      clearTimeout(botT);
      if (t.done || t.humans[t.turn] || !g.bot) return;
      const ms = (g.botMs || [900, 1500]);
      botT = setTimeout(() => { if (!t.done && !t.humans[t.turn]) g.bot(t, t.turn); }, ms[0] + Math.random() * (ms[1] - ms[0]));
    }
    // the TV is the one authority, the phones show only their own seat: after each change every phone gets its layout, and
    // only when it differs from the last one sent (a bot's move redraws the TV, not the phones of those it does not concern).
    // A phone that reconnects gets its last layout from TV Games itself (GameServer keeps it), so nothing here re-sends.
    const sent = [];
    function push() {
      if (!t.ok) return;
      t.seats.forEach((s) => {
        if (!phone(s)) { sent[s] = null; return; }
        const l = g.view(t, s), j = l && JSON.stringify(l);
        if (j && j !== sent[s]) { sent[s] = j; pad.layout(s + 1, l); }
      });
    }
    t.send = (s, l) => {                                          // a one-off layout (a hint's selection)
      if (s === 0 && t.remote) { if (l.cards && l.cards.sel) R.sel = new Set(l.cards.sel.map(String)); rdraw(); return; }
      sent[s] = JSON.stringify(l); pad.layout(s + 1, l);
    };
    function changed() { if (g.draw) g.draw(t); push(); rdraw(); }
    function turnTo(s) { t.turn = s; t.left = g.turnSecs || 0; changed(); wake(); }
    function go() { clearTimeout(botT); seat(); t.done = false; t.order = []; t.games++; g.deal(t); if (!t.done) turnTo(t.turn); }
    function over(order) {
      clearTimeout(botT); t.done = true; t.order = order.slice(); t.lastWinner = order[0];
      const pts = SCORE[order.length] || SCORE[4];
      order.forEach((s, r) => { t.scores[s] += pts[r] || 0; t.buzz(s, r ? 120 : 700); });
      t.points = (r) => pts[r] || 0;
      changed();
    }
    // ---- the remote seat (see the header)
    const R = { sel: new Set(), zone: 'hand', i: 0, hand: [], acts: [], turn: false, flash: '', fk: 0, fT: 0, el: null, key: '' };
    const RM = typeof g.remote === 'object' ? g.remote : {};
    const mk = (tag, c, x) => { const e = document.createElement(tag); if (c) e.className = c; if (x != null) e.textContent = x; return e; };
    const ICON = { play: '🃏', pass: '⏭️', skip: '⏭️', hint: '💡', draw: '📥', roll: '🎲', buy: '🛒', build: '🏠', sell: '💸', mort: '🏦', end: '🏁', ok: '✅', nope: '🚫', give: '🎁', jailpay: '💰', jailcard: '🎫' };   // as the pad's
    const SU = { s: '♠', c: '♣', d: '♦', h: '♥' };
    function rcard(c) {
      const id = String(c.id), e = mk('div', 'cd' + (R.sel.has(id) ? ' sel' : '') + (c.off ? ' off' : ''));
      if (c.t == null) {                                           // a plain playing card: rank + suit letter ('10h')
        const su = id.slice(-1), rk = id.slice(0, -1);
        if (su === 'h' || su === 'd') e.classList.add('red');
        e.append(mk('b', '', rk), mk('i', '', SU[su] || ''), mk('u', '', SU[su] || ''));
      } else if (c.look === 'deed') {                              // a property: colour band, name, what it earns, houses
        e.classList.add('deed'); const band = mk('s', '', c.i || ''); if (/^#[0-9a-f]{3,8}$/i.test(c.bg || '')) band.style.background = c.bg;
        e.append(band, mk('span', '', String(c.t).slice(0, 16)), mk('em', '', String(c.sub || '')));
      } else {                                                     // a card of the game's own: colour, big sign, name
        e.classList.add('own'); if (/^#[0-9a-f]{3,8}$/i.test(c.bg || '')) e.style.setProperty('--bg', c.bg);
        const k = String(c.k != null ? c.k : c.i != null && c.look !== 'deed' ? c.i : '').slice(0, 4);
        e.append(mk('b', '', k), mk('span', '', String(c.t).slice(0, 16)), mk('i', 'co', k));   // co: the corner a fanned card shows
      }
      return e;
    }
    function rdraw() {
      if (typeof document !== 'object' || !document.body) return;
      const l = t.remote && !t.done ? g.view(t, 0) : null, c = l && l.cards;
      document.body.classList.toggle('rmt-on', !!c);
      if (!c) { if (R.el) R.el.hidden = true; R.turn = false; return; }
      if (!R.el) {
        R.el = mk('div'); R.el.id = 'rmt'; (RM.mount ? document.querySelector(RM.mount) : document.body).append(R.el);
        const css = mk('style');
        css.textContent = `#rmt{--u:1vh;position:fixed;left:0;right:0;bottom:0;z-index:20;display:flex;flex-direction:column;gap:calc(var(--u)*1.2);padding:calc(var(--u)*1.2) 2vw calc(var(--u)*1.4);
  background:#0b0f1af2;border-top:calc(var(--u)*.3) solid var(--pc);font:700 calc(var(--u)*3.2)/1.2 system-ui,sans-serif;color:#f1f4fb;cursor:none}
#rmt[hidden]{display:none}
#rmt .top{display:flex;align-items:center;gap:calc(var(--u)*1.6);min-height:calc(var(--u)*4.4)}
#rmt .dot{width:calc(var(--u)*2.4);height:calc(var(--u)*2.4);border-radius:50%;background:var(--pc);flex:none}
#rmt .nm{flex:none;font-size:calc(var(--u)*3.4)}#rmt .tt{flex:none;color:#9aa5bd;font-size:calc(var(--u)*2.8)}
#rmt .note{flex:1;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;color:#9aa5bd}#rmt .note.turn{color:#fff}#rmt .note.bad{color:#fdba74}
#rmt .tm{flex:none;width:12vw;height:calc(var(--u)*.9);border-radius:99px;background:#fff2;overflow:hidden}#rmt .tm i{display:block;height:100%;background:var(--pc);transition:width 1s linear}
#rmt .hand{display:flex;justify-content:center;padding-top:calc(var(--u)*4.4);min-height:calc(var(--u)*19)}
#rmt .hand:empty{display:none}
#rmt .cd{position:relative;flex:0 0 calc(var(--u)*12.4);height:calc(var(--u)*17.4);border-radius:calc(var(--u)*1.2);background:#f7f7f2;color:#1b1e27;overflow:hidden;
  box-shadow:0 .3vh .8vh #0008;transition:transform .12s,filter .12s}
#rmt .cd.red{color:#d7263d}
#rmt .cd b{position:absolute;left:calc(var(--u)*1);top:calc(var(--u)*.6);font:800 calc(var(--u)*5)/1 system-ui}
#rmt .cd i{position:absolute;left:calc(var(--u)*1.2);top:calc(var(--u)*5.8);font:calc(var(--u)*3.8)/1 system-ui;font-style:normal}
#rmt .cd u{position:absolute;right:calc(var(--u)*.8);bottom:calc(var(--u)*.6);font:calc(var(--u)*8)/1 system-ui;text-decoration:none}
#rmt .cd.own{--bg:#334155;background:linear-gradient(150deg,#ffffff30,#fff0 45%,#0004),var(--bg);color:#fff;flex-basis:calc(var(--u)*13.6);border:calc(var(--u)*.5) solid #f7f7f2;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:calc(var(--u)*.8)}
#rmt .cd.own b{position:static;font-size:calc(var(--u)*6.4);text-shadow:0 .2vh .6vh #000a}
#rmt .cd.own span{font-size:calc(var(--u)*2.6);white-space:nowrap;padding:0 calc(var(--u)*.6);text-shadow:0 .2vh .5vh #000a}
#rmt .cd.deed{display:block;border:0;background:#f7f7f2;color:#1b1e27;text-align:center}
#rmt .cd.deed s{display:flex;align-items:center;justify-content:center;height:calc(var(--u)*4.6);font-size:calc(var(--u)*2.4);text-decoration:none;color:#fff;background:#64748b}
#rmt .cd.deed span{display:block;margin-top:calc(var(--u)*1.2);font-size:calc(var(--u)*2.5);line-height:1.1;white-space:normal;text-shadow:none}
#rmt .cd.deed em{display:block;margin-top:calc(var(--u)*.8);font-style:normal;font-size:calc(var(--u)*2.2);color:#475569}
#rmt .cd+.cd{margin-left:calc(var(--u)*var(--ov,.8))}
#rmt .cd.own .co{position:absolute;left:calc(var(--u)*.8);top:calc(var(--u)*.5);font:800 calc(var(--u)*3.6)/1 system-ui;text-shadow:0 .2vh .5vh #000a}
#rmt .cd.off{filter:brightness(.45) saturate(.6)}   /* darker, not see-through: overlapped cards must not show through */
#rmt .cd.sel{transform:translateY(calc(var(--u)*-2.4));box-shadow:0 0 0 calc(var(--u)*.5) #38bdf8,0 .3vh .8vh #0008}
#rmt .cd.focus{z-index:3;outline:calc(var(--u)*.7) solid #fff;outline-offset:calc(var(--u)*.2);transform:translateY(calc(var(--u)*-3.6)) scale(1.08)}
#rmt .cd.sel.focus{transform:translateY(calc(var(--u)*-4.6)) scale(1.08)}
#rmt .acts{display:flex;justify-content:center;gap:calc(var(--u)*1.6);flex-wrap:nowrap}
#rmt .ac{flex:0 1 auto;min-width:0;height:calc(var(--u)*7.4);padding:0 calc(var(--u)*3);border-radius:calc(var(--u)*1.2);background:#243044;color:#e2e8f0;display:flex;align-items:center;white-space:nowrap;
  border:calc(var(--u)*.4) solid transparent;transition:transform .12s}
#rmt .ac.main{background:#2563eb;color:#fff}
#rmt .ac.off{opacity:.4}
#rmt .ac.focus{border-color:#fff;box-shadow:0 0 calc(var(--u)*2.4) #fff9;transform:scale(1.07)}`;
        document.head.append(css);
      }
      const cs = (Array.isArray(c.hand) ? c.hand : []).slice(0, 40).map((x) => (x && typeof x === 'object' ? x : { id: String(x) }));
      const ids = cs.map((x) => String(x.id)), acts = Array.isArray(c.acts) ? c.acts : [], isTurn = !!c.turn;
      R.sel = new Set(Array.isArray(c.sel) ? c.sel.map(String) : [...R.sel].filter((x) => ids.includes(x)));
      const key = ids.join() + '|' + acts.map((a) => a.id).join();
      if ((isTurn && !R.turn) || key !== R.key) {                  // my turn began or the choices changed: the cursor starts where a move is likely
        R.zone = cs.length ? 'hand' : 'acts';
        R.i = R.zone === 'acts' ? Math.max(0, acts.findIndex((a) => a.main && !a.off)) : 0;
      }
      R.turn = isTurn; R.key = key; R.hand = cs; R.acts = acts;
      if (R.zone === 'hand' && !cs.length) R.zone = 'acts';
      if (R.zone === 'acts' && !acts.length && cs.length) R.zone = 'hand';
      R.i = Math.max(0, Math.min(R.i, (R.zone === 'hand' ? cs : acts).length - 1));
      R.el.hidden = false; R.el.style.setProperty('--pc', t.COLORS[0]);
      const foc = R.zone === 'hand' ? cs[R.i] : null;
      const top = mk('div', 'top'), tm = mk('div', 'tm'), note = mk('div', 'note' + (R.flash ? ' bad' : isTurn ? ' turn' : ''), R.flash || (foc && foc.t != null && foc.look !== 'deed' ? foc.t + (foc.sub ? ' · ' + foc.sub : '') : foc && foc.sub) || c.note || '');
      tm.append(mk('i')); tm.hidden = !isTurn || !g.turnSecs;
      top.append(mk('span', 'dot'), mk('b', 'nm', t.name(0)), mk('span', 'tt', W.total + ' ' + (t.scores[0] > 0 ? '+' : '') + t.scores[0]), note, tm);
      const hand = mk('div', 'hand'), bar = mk('div', 'acts');
      if (cs.length && cs[0].look !== 'deed') {                    // a fanned hand: each card shows about half of itself (more when few), never wider than the screen
        const w = cs[0].t == null ? 12.4 : 13.6, n = cs.length, full = n <= 4 ? .85 : n <= 7 ? .65 : .5, room = (RM.width || 165) - w;
        hand.style.setProperty('--ov', String(-(w - Math.max(w * .25, Math.min(w * full, n > 1 ? room / (n - 1) : w)))));
      }
      cs.forEach((x, k) => { const e = rcard(x); if (R.zone === 'hand' && k === R.i) e.classList.add('focus'); hand.append(e); });
      acts.forEach((a, k) => {
        const w = String(a.s || a.t), ic = a.i != null ? a.i : /^[^\x00-\x7f]/.test(w) ? '' : ICON[a.id] || '';   // a short word (s) and an icon: the panel's buttons are small
        const e = mk('div', 'ac' + (a.main ? ' main' : '') + (a.off ? ' off' : '') + (R.zone === 'acts' && k === R.i ? ' focus' : ''), (ic ? ic + ' ' : '') + w);
        bar.append(e);
      });
      R.el.replaceChildren(top, hand, bar); rtimer();
    }
    function rtimer() { const k = R.el && R.el.querySelector('.tm i'); if (k && g.turnSecs) k.style.width = Math.max(0, t.left / g.turnSecs * 100) + '%'; }
    function rflash(w) { R.flash = w; clearTimeout(R.fT); R.fT = setTimeout(() => { R.flash = ''; rdraw(); }, 2500); }
    function rpress(a, only) {
      const cards = only ? [only] : R.hand.map((x) => String(x.id)).filter((x) => R.sel.has(x));
      const why = g.act(t, 0, { act: a, cards });
      if (why) rflash(why); else { R.flash = ''; R.sel = new Set(); }
      rdraw();
    }
    function rkey(e) {
      R.pre = t.done;                                   // was the game already over before this key? (the win's own OK must not also start the next one)
      if (!t.remote || t.done || !R.el || R.el.hidden) return;
      const k = e.key, row = R.zone === 'hand' ? R.hand : R.acts;
      if (k === 'ArrowLeft' || k === 'ArrowRight') {
        const d = k === 'ArrowLeft' ? -1 : 1; let j = R.i;
        for (let n = 0; n < row.length; n++) { j += d; if (j < 0 || j >= row.length) { j = R.i; break; } if (R.zone === 'hand' || !row[j].off) break; }
        R.i = j;
      } else if (k === 'ArrowUp' || k === 'ArrowDown') {
        const to = k === 'ArrowUp' ? 'hand' : 'acts';
        if (to !== R.zone && (to === 'hand' ? R.hand : R.acts).length) {
          R.zone = to; const r = to === 'hand' ? R.hand : R.acts;
          R.i = to === 'acts' ? Math.max(0, R.acts.findIndex((a) => a.main && !a.off)) : Math.min(R.i, r.length - 1);
        }
      } else if (k === 'Enter') {
        if (e.repeat) { e.preventDefault(); return; }
        if (R.zone === 'hand' && R.hand[R.i]) {
          const id = String(R.hand[R.i].id);
          if (RM.quick) { rpress(RM.quick === true ? 'play' : RM.quick, id); return; }
          if (R.sel.has(id)) R.sel.delete(id); else R.sel.add(id);
        } else if (R.acts[R.i]) {
          if (!R.acts[R.i].off) { e.preventDefault(); rpress(R.acts[R.i].id); return; }
        }
      } else return;
      e.preventDefault(); rdraw();
    }
    if (g.remote && typeof document === 'object') {
      if (root.Credit) root.Credit.key = () => t.done || !t.remote;   // Down plays while the remote does; between games it still opens the QR
      document.addEventListener('keydown', rkey);
    }
    t.rdraw = rdraw;

    // the clock: one tick a second, only on a phone's turn
    if (g.turnSecs) setInterval(() => {
      if (t.done || !t.humans[t.turn]) return;
      if (--t.left <= 0) { if (g.timeUp) g.timeUp(t, t.turn); } else { if (g.tick) g.tick(t); rtimer(); }
    }, 1000);
    if (t.ok) {
      pad.on('text', ({ player, text }) => {
        const s = player - 1; let m;
        try { m = JSON.parse(text); } catch (e) { return; }
        if (!m || typeof m !== 'object' || s < 0 || s >= n) return;
        if (m.act === 'again') { if (t.done) go(); return; }
        if (!t.humans[s] || t.done) return;
        const why = g.act(t, s, m);
        if (why) { pad.say(player, why); pad.vibrate(player, 250); }
      });
      const moved = () => { seat(); changed(); wake(); };   // a phone took a bot's seat or gave it back
      pad.on('join', moved); pad.on('leave', moved);
      pad.on('button', ({ btn, down }) => { if (down && btn === 'START' && t.done) go(); });
      if (typeof document === 'object') document.addEventListener('keydown', (e) => { if (e.key === 'Enter' && t.done && R.pre !== false && !e.repeat) go(); });
    }
    return t;
  }
  Table.W = W; Table.VI = VI; Table.SCORE = SCORE; Table.COLORS = COLORS;
  if (typeof module === 'object' && module.exports) module.exports = Table; else root.Table = Table;
})(this);
