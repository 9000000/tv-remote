// Runs INSIDE the game page, before any of its scripts (run.js injects it after `window.__botCfg = {...}` and the real
// app/games/src/main/assets/tvpad.js). Two parts:
//  1. a fake TVPadNative, so the REAL tvpad.js builds window.tvpad on top of it (the page sees exactly the API it sees on the TV);
//     it records every layout / say / vibrate the game sends.
//  2. the brains: per-game players that press buttons, move the stick / pointer, answer choices and play cards at human speed
//     (random + simple heuristics, some use the game's own AI to pick a sensible move so the match can end).
(() => {
  'use strict';
  const cfg = window.__botCfg, rnd = (a, b) => a + Math.random() * (b - a), pick = (a) => a[(Math.random() * a.length) | 0];
  const B = window.__bot = { cfg, raf: 0, sent: 0, seq: 0, lay: {}, says: [], vibes: 0, layoutCount: 0, per: {}, running: false };

  // ---- 1. fake native bridge
  const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
  const rec = (p, json) => {
    let spec = null; try { spec = JSON.parse(json); } catch (e) { }
    B.layoutCount++; const r = { seq: ++B.seq, spec };
    if (p === 0) { B.lay.all = r; for (let i = 1; i <= 4; i++) delete B.lay[i]; } else B.lay[p] = r;
  };
  window.TVPadNative = {
    players: () => JSON.stringify(cfg.players), name: (p) => (cfg.names && cfg.names[p]) || '', ping: () => 12,
    joinUrl: () => 'http://192.168.1.50:8780/?c=abc123',
    qr: (px) => { try { const m = qrModules(TVPadNative.joinUrl()), k = Math.max(1, Math.floor((px || 256) / (m.length + 8))), c = document.createElement('canvas'), x = c.getContext('2d'); c.width = c.height = k * (m.length + 8); x.fillStyle = '#fff'; x.fillRect(0, 0, c.width, c.height); x.fillStyle = '#000'; m.forEach((r, y) => r.forEach((on, i) => on && x.fillRect((i + 4) * k, (y + 4) * k, k, k))); return c.toDataURL(); } catch (e) { return PNG; } },
    layout: rec, vibrate: () => { B.vibes++; }, say: (p, t) => { B.says.push([p, String(t)]); if (B.says.length > 50) B.says.shift(); }, ownKeys: () => { B.ownKeys = true; },
  };
  const raf0 = window.requestAnimationFrame.bind(window);
  window.requestAnimationFrame = (cb) => raf0((t) => { B.raf++; cb(t); });
  B.layoutOf = (p) => { const a = B.lay[p], b = B.lay.all; const r = !a ? b : !b ? a : a.seq > b.seq ? a : b; return r && r.spec; };

  // ---- the pad side: what a phone sends
  const tin = (...a) => { B.sent++; window.__tvpadIn(...a); };
  const names = ['Bot An', 'Bot Binh', 'Bot Chi', 'Bot Dung'];
  B.join = (p) => tin('join', p, names[p - 1]);
  B.leave = (p) => { B.per[p] = null; tin('leave', p); };
  B.btn = (p, b, down) => tin('button', p, b, down ? 1 : 0, 18);
  B.tap = (p, b, ms) => { B.btn(p, b, true); setTimeout(() => B.btn(p, b, false), ms || 70); };
  B.axis = (p, x, y) => tin('axis', p, x, y);
  B.ptr = (p, dx, dy) => tin('pointer', p, dx, dy);
  B.text = (p, o) => tin('text', p, JSON.stringify(o));
  const live = () => window.tvpad.players();
  const mem = (p) => B.per[p] || (B.per[p] = { next: 0, fire: 0, ax: 0, ay: 0, tx: 640, ty: 360 });

  // a cheap signature of what the player can see: canvases (48x27 samples) + page text + layouts sent
  const tmp = document.createElement('canvas'); tmp.width = 48; tmp.height = 27;
  B.sig = () => {
    let h = 7;
    try {
      const c = tmp.getContext('2d', { willReadFrequently: true });
      document.querySelectorAll('canvas').forEach((cv) => {
        if (cv === tmp || !cv.width) return;
        c.clearRect(0, 0, 48, 27); c.drawImage(cv, 0, 0, 48, 27);
        const d = c.getImageData(0, 0, 48, 27).data; for (let i = 0; i < d.length; i += 4) h = (h * 31 + (d[i] >> 3) + (d[i + 1] >> 3) * 3 + (d[i + 2] >> 3) * 7) | 0;
      });
    } catch (e) { }
    const t = document.body ? document.body.innerText : ''; for (let i = 0; i < t.length; i++) h = (h * 31 + t.charCodeAt(i)) | 0;
    return h + ':' + B.seq;
  };

  // ---- 2. brains. Each: state() -> 'lobby' | 'play' | 'over'; start(); rematch(); step(now) every 50 ms while running
  const K = () => window.Kit;
  const kitState = () => (!K() ? 'lobby' : K().mode === 'title' ? 'lobby' : K().mode === 'over' ? 'over' : 'play');
  // Kit arcade pages. input: 'stick' (axis, held directions), 'ptr' (pointer events toward random targets + fire)
  const kit = {
    state: kitState,
    start: () => B.tap(1, 'A', 80),
    rematch: () => B.tap(live()[0] || 1, 'A', 80),                            // the lit choice is "Rematch" (sel 0)
    step(now) {
      if (kitState() !== 'play') return;
      for (const p of live()) {
        const m = mem(p);
        if (cfg.input === 'ptr') {
          const c = window.Ptr && window.Ptr.get(p);
          if (c) {
            if (now > m.next || Math.hypot(m.tx - c.x, m.ty - c.y) < 25) { m.tx = rnd(60, 1220); m.ty = rnd(60, 660); m.next = now + rnd(500, 1600); }
            const dx = (m.tx - c.x) / 2.4, dy = (m.ty - c.y) / 2.4, d = Math.hypot(dx, dy) || 1, s = Math.min(d, 30) / d;
            if (now - (m.pt || 0) > 33) { m.pt = now; B.ptr(p, dx * s, dy * s); }
          }
          if (cfg.fire !== false && now > m.fire) { m.fire = now + rnd(180, 700); B.tap(p, 'A', 60); }
        } else {
          if (now > m.next) {
            m.next = now + rnd(250, 800); const a = pick([0, 0.785, 1.57, 2.36, 3.14, 3.93, 4.71, 5.5, null]);
            m.ax = a == null ? 0 : Math.cos(a); m.ay = a == null ? 0 : Math.sin(a); B.axis(p, +m.ax.toFixed(2), +m.ay.toFixed(2));
          }
          if (now > m.fire) { m.fire = now + rnd(250, 1200); B.tap(p, 'A', 70); }
        }
      }
    },
  };

  // Table games: read the layout the game sent to this phone ({cards:{hand, acts, turn, sel, note}}) and play it.
  const tbl = {
    state: () => (typeof table === 'undefined' ? 'lobby' : table.done ? (table.games > 0 ? 'over' : 'lobby') : 'play'),
    start: () => { },                                                          // the table deals itself on load
    rematch: () => B.text(live()[0] || 1, { act: 'again' }),
    step(now) {
      if (typeof table === 'undefined' || table.done) return;
      for (const p of live()) {
        const L = B.layoutOf(p), c = L && L.cards, m = mem(p);
        if (!c || !c.turn) { m.key = null; continue; }
        const key = JSON.stringify(c);
        if (key !== m.key) { m.key = key; m.tries = 0; m.since = now; m.wait = rnd(600, 1500); m.last = 0; }
        if (now - m.since < m.wait || now - m.last < 1400) continue;
        m.last = now;
        const acts = (c.acts || []).filter((a) => !a.off && a.id !== 'again');
        const hand = (c.hand || []).filter((x) => !(x && x.off)).map((x) => (x && typeof x === 'object' ? String(x.id) : String(x)));
        // first try: the main button; after a refusal (nothing changed): anything, with a random pick of cards
        const a = !acts.length ? null : m.tries === 0 ? (acts.find((x) => x.main) || pick(acts)) : pick(acts);
        m.tries++;
        if (!a) { if (hand.length) B.text(p, { act: 'play', cards: [pick(hand)] }); continue; }
        let cards = Array.isArray(c.sel) && c.sel.length ? c.sel.map(String) : [];
        if (!cards.length && hand.length && /^(play|give|build|sell|mort)$/.test(a.id)) { cards = [pick(hand)]; for (let i = 0; i < m.tries - 1 && i < 2; i++) { const x = pick(hand); if (!cards.includes(x)) cards.push(x); } }
        B.text(p, { act: a.id, cards });
      }
    },
  };

  // Caro: walk the cursor to a cell the game's own AI likes (85%) or a random empty one, then A
  const caro = {
    state: () => (typeof g === 'undefined' ? 'lobby' : g.over ? 'over' : 'play'),
    start: () => { },
    rematch: () => B.tap(live()[0] || 1, 'START', 80),
    step(now) {
      if (typeof g === 'undefined' || g.over || g.wait) return;
      for (let s = 0; s < 2; s++) {
        const p = g.seat[s]; if (!p || g.ts !== s || !live().includes(p)) continue;
        const m = mem(p), c = g.cur[s], N = Caro.N;
        if (m.tgt == null || g.b[m.tgt] || m.ts !== g.ts + ':' + g.last) {
          const empty = []; for (let i = 0; i < g.b.length; i++) if (!g.b[i]) empty.push(i);
          m.tgt = Math.random() < 0.85 ? Caro.bestMove(g.b, markOf(g.ts)) : pick(empty); m.ts = g.ts + ':' + g.last; m.arrive = 0;
        }
        if (now < (m.next || 0)) continue;
        const tx = m.tgt % N, ty = (m.tgt / N) | 0;
        if (c.x !== tx) { B.tap(p, c.x < tx ? 'RIGHT' : 'LEFT', 40); m.next = now + rnd(90, 160); }
        else if (c.y !== ty) { B.tap(p, c.y < ty ? 'DOWN' : 'UP', 40); m.next = now + rnd(90, 160); }
        else { if (!m.arrive) m.arrive = now + rnd(300, 800); if (now >= m.arrive) { B.tap(p, 'A', 70); m.next = now + 900; m.arrive = 0; } }
      }
    },
  };

  // Bida: on my turn aim with the pointer (toward the game's own suggested shot, +- noise), hold A to charge, let go
  const wrap = (a) => { while (a > Math.PI) a -= 2 * Math.PI; while (a < -Math.PI) a += 2 * Math.PI; return a; };
  const bida = {
    state: () => (typeof table === 'undefined' ? 'lobby' : table.done ? (table.games > 0 ? 'over' : 'lobby') : 'play'),
    start: () => { },
    rematch: () => B.text(live()[0] || 1, { act: 'again' }),
    step(now) {
      const A = window.__bida; if (!A || table.done) return;
      const S = A.S, p = table.turn + 1; if (!live().includes(p) || (S.phase !== 'aim' && S.phase !== 'place')) { return; }
      const m = mem(p);
      if (m.shot !== S.shotId + ':' + S.phase) { m.shot = S.shotId + ':' + S.phase; m.mv = null; m.t0 = now; m.holdUntil = 0; }
      if (now - m.t0 < 800) return;                                            // looks at the table first
      if (!m.mv) { try { m.mv = A.BD.botMove(S.g, 1); } catch (e) { m.mv = { ang: rnd(0, 6.28), pw: rnd(0.3, 0.9), x: S.px, y: S.py }; } m.ang = m.mv.ang + rnd(-0.12, 0.12); }
      if (now - (m.pt || 0) < 33) return; m.pt = now;
      if (S.phase === 'place') {
        const dx = (m.mv.x - S.px) / 1.85, dy = (m.mv.y - S.py) / 1.85, d = Math.hypot(dx, dy);
        if (d > 3) B.ptr(p, dx / d * Math.min(d, 25), dy / d * Math.min(d, 25));
        else if (now - (m.a || 0) > 1200) { m.a = now; B.tap(p, 'A', 90); m.mv = null; m.t0 = now - 500; }
        return;
      }
      const diff = wrap(m.ang - S.ang);
      if (!S.charging && Math.abs(diff) > 0.015) { const dx = Math.max(-40, Math.min(40, diff / 0.003)); B.ptr(p, dx, 0); }
      else if (!S.charging && !m.holdUntil) { B.btn(p, 'A', true); m.holdUntil = now + Math.max(120, (m.mv.pw || 0.6) * 1100); }
      else if (S.charging && now >= m.holdUntil) { B.btn(p, 'A', false); }
    },
  };

  // Quiz: answer each question after 1.5-5 s, right half of the time
  const quiz = {
    state: () => (typeof st === 'undefined' ? 'lobby' : st.mode === 'lobby' ? 'lobby' : st.mode === 'end' ? 'over' : 'play'),
    start: () => B.tap(1, 'START', 80),
    rematch: () => B.tap(live()[0] || 1, 'START', 80),
    step(now) {
      if (typeof st === 'undefined' || st.mode !== 'ask') return;
      for (const p of live()) {
        const m = mem(p), id = st.i + ':' + p;
        if (m.q !== id) { m.q = id; m.at = now + rnd(1500, 5000); }
        if (now >= m.at && st.ans[p] == null) { const q = st.qs[st.i], right = q ? KEYS[q.c] : 'A'; B.tap(p, Math.random() < 0.5 ? right : pick(KEYS), 70); m.at = now + 99999; }
      }
    },
  };

  const brains = { kit, tbl, caro, bida, quiz };
  const brain = () => brains[cfg.kind];
  B.state = () => { try { return brain().state(); } catch (e) { return 'lobby'; } };
  B.start = () => brain().start();
  B.rematch = () => brain().rematch();
  B.run = (on) => {
    B.running = on; clearInterval(B.iv);
    if (on) B.iv = setInterval(() => { try { brain().step(performance.now()); } catch (e) { console.error('bot step', e); } }, 50);
  };
  B.info = () => ({ raf: B.raf, sent: B.sent, vibes: B.vibes, layouts: B.layoutCount, players: live(), says: B.says.slice(-3), fps: window.__fps || null });
})();
