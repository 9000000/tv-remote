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
//   });
//   t.go()                            // the first game; later ones by OK / START / {act:'again'}
//   t.turnTo(seat)                    // whose turn it is now (restarts the clock, wakes the bot, redraws, sends layouts)
//   t.changed()                       // redraw + layouts without a new turn
//   t.over(order)                     // the game ended: [seat, ...] best first; scores added, t.done = true
//   t.send(seat, layout)              // a layout outside view() (the Hint's picked cards), counted as sent
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
      name(s) { if (t.humans[s]) { try { return pad.name(s + 1) || W.p(s + 1); } catch (e) { return W.p(s + 1); } } return W.bot(s + 1); },
      say(s, w) { if (pad && t.humans[s]) pad.say(s + 1, w || ''); },
      buzz(s, ms) { if (pad && t.humans[s]) pad.vibrate(s + 1, ms); },
      go, turnTo, changed, over,
    };
    let botT = null;
    const players = () => (pad ? pad.players() : []);
    function seat() { const ps = players(); t.seats.forEach((s) => { t.humans[s] = ps.includes(s + 1); }); }
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
        if (!t.humans[s]) { sent[s] = null; return; }
        const l = g.view(t, s), j = l && JSON.stringify(l);
        if (j && j !== sent[s]) { sent[s] = j; pad.layout(s + 1, l); }
      });
    }
    t.send = (s, l) => { sent[s] = JSON.stringify(l); pad.layout(s + 1, l); };   // a one-off layout (a hint's selection)
    function changed() { if (g.draw) g.draw(t); push(); }
    function turnTo(s) { t.turn = s; t.left = g.turnSecs || 0; changed(); wake(); }
    function go() { clearTimeout(botT); seat(); t.done = false; t.order = []; t.games++; g.deal(t); if (!t.done) turnTo(t.turn); }
    function over(order) {
      clearTimeout(botT); t.done = true; t.order = order.slice(); t.lastWinner = order[0];
      const pts = SCORE[order.length] || SCORE[4];
      order.forEach((s, r) => { t.scores[s] += pts[r] || 0; t.buzz(s, r ? 120 : 700); });
      t.points = (r) => pts[r] || 0;
      changed();
    }
    // the clock: one tick a second, only on a phone's turn
    if (g.turnSecs) setInterval(() => {
      if (t.done || !t.humans[t.turn]) return;
      if (--t.left <= 0) { if (g.timeUp) g.timeUp(t, t.turn); } else if (g.tick) g.tick(t);
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
      if (typeof document === 'object') document.addEventListener('keydown', (e) => { if (e.key === 'Enter' && t.done) go(); });
    }
    return t;
  }
  Table.W = W; Table.VI = VI; Table.SCORE = SCORE; Table.COLORS = COLORS;
  if (typeof module === 'object' && module.exports) module.exports = Table; else root.Table = Table;
})(this);
