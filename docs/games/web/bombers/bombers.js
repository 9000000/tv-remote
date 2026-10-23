'use strict';
// Bombers: 2-4 players in a grid of pillars and crates drop bombs that burn in a cross; last one alive takes the round, first to 3 wins.
// The rules and the bots are in rules.js (no screen, tested in node); this file reads the pads, draws, and makes the sounds and sparks.
(function () {
  const K = window.Kit, B = window.BM, CS = 52, COLS = B.COLS, ROWS = B.ROWS, OX = (1280 - COLS * CS) / 2, OY = 100, WIN = 3, N = B.N;
  const NUM = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];                    // so the score row builds no strings per frame
  const SD_WORD = K.VI ? 'THU HẸP!' : 'SUDDEN DEATH';
  const W2 = 640;
  let w, floorCv, blocksCv, dirty, phase, ptimer, round, over, roundWin, sdShow, left = '', leftN = -1;

  const px = (x) => OX + x * CS, py = (y) => OY + y * CS;

  function paintFloor() {
    floorCv = floorCv || Object.assign(document.createElement('canvas'), { width: 1280, height: 720 });
    const c = floorCv.getContext('2d'); c.clearRect(0, 0, 1280, 720);
    c.fillStyle = 'rgba(9,16,40,.92)'; K.rr(c, OX - 10, OY - 10, COLS * CS + 20, ROWS * CS + 20, 18); c.fill();
    for (let r = 0; r < ROWS; r++) for (let q = 0; q < COLS; q++) { c.fillStyle = (r + q) % 2 ? '#14203f' : '#18264a'; c.fillRect(OX + q * CS, OY + r * CS, CS, CS); }
  }
  function paintBlocks() {                                                              // pillars and crates, repainted only when one changes
    blocksCv = blocksCv || Object.assign(document.createElement('canvas'), { width: 1280, height: 720 });
    const c = blocksCv.getContext('2d'); c.clearRect(0, 0, 1280, 720);
    for (let r = 0; r < ROWS; r++) for (let q = 0; q < COLS; q++) {
      const v = w.grid[r * COLS + q]; if (!v) continue; const x = OX + q * CS, y = OY + r * CS;
      if (v === 1) {
        c.fillStyle = '#1e2a5a'; K.rr(c, x + 1, y + 1, CS - 2, CS - 2, 8); c.fill();
        c.fillStyle = '#3550b8'; K.rr(c, x + 1, y + 1, CS - 2, CS - 9, 8); c.fill();
        c.strokeStyle = '#8fa8ff'; c.lineWidth = 2; K.rr(c, x + 6, y + 6, CS - 12, CS - 17, 5); c.stroke();
      } else {
        c.fillStyle = '#6b3f12'; K.rr(c, x + 2, y + 2, CS - 4, CS - 4, 6); c.fill();
        c.fillStyle = '#b8782c'; K.rr(c, x + 2, y + 2, CS - 4, CS - 9, 6); c.fill();
        c.strokeStyle = '#6b3f12'; c.lineWidth = 3; c.beginPath(); c.moveTo(x + 8, y + 8); c.lineTo(x + CS - 8, y + CS - 14); c.moveTo(x + CS - 8, y + 8); c.lineTo(x + 8, y + CS - 14); c.stroke();
        c.strokeStyle = '#e0a458'; c.lineWidth = 2; K.rr(c, x + 5, y + 5, CS - 10, CS - 15, 4); c.stroke();
      }
    }
    dirty = false;
  }

  // ---------------- what the world tells us: sounds and sparks only
  function hooks() {
    w.on.place = (p, b) => { K.tone(200, 0.09, 'square', 0.06, 0.7); };
    w.on.boom = (res) => {
      K.sfx.boom(); K.shake(4 + Math.min(res.nb, 4) * 2);
      for (let j = 0; j < res.nc; j += 2) K.burst(px(res.cells[j] % COLS + 0.5), py(((res.cells[j] / COLS) | 0) + 0.5), '#fb923c', 2, 150, 0.45, 5);
      if (res.nbr) dirty = true;
    };
    w.on.pick = (p) => { K.sfx.pickup(); K.burst(px(p.x), py(p.y), '#fff', 8, 160, 0.4, 4); };
    w.on.die = (p) => { K.sfx.hit(1); K.shake(8); K.burst(px(p.x), py(p.y), K.slots[p.i].color, 30, 360, 0.8, 7); K.burst(px(p.x), py(p.y), '#fff', 10, 260, 0.45, 5); };
    w.on.sd = (i) => { dirty = true; K.burst(px(i % COLS + 0.5), py(((i / COLS) | 0) + 0.5), '#94a3b8', 3, 120, 0.4, 4); if (i % 6 === 0) K.tone(140, 0.07, 'square', 0.05); };
  }
  function newRound() {
    round++; w = B.createWorld(K.slots.length, K.slots.map((s) => s.human)); hooks(); w.frozen = true; paintFloor(); paintBlocks();
    phase = 'ready'; ptimer = 1.6; sdShow = 0; leftN = -1;
  }

  function readPads() {                                                                 // the sticks and A of the human seats
    for (const s of K.slots) if (s.human) {
      const p = K.pad(s.pad), q = w.players[s.i];
      B.axisDir(q, p.x, p.y);
      if (p.pulse) { q.bomb = true; p.pulse = false; }
    }
  }

  G = {
    id: 'bombers', title: { vi: 'Đặt bom', en: 'Bombers' },
    tag: { vi: 'Đặt bom phá thùng, né lửa — còn sống cuối cùng thắng ván, 3 ván thắng trận', en: 'Blow up crates, dodge the fire — last one alive takes the round, first to 3 wins' },
    help: { vi: ['Cần analog / D-pad: di chuyển', 'Nút A: đặt bom (nổ sau 2,5 giây, nổ dây chuyền)', 'Thùng vỡ có thể rơi: thêm bom, tầm nổ, tốc độ'], en: ['Stick / D-pad: move', 'A: drop a bomb (blows after 2.5 s, sets off others)', 'Crates may drop: more bombs, longer flame, speed'] },
    total: [2, 4], buttons: ['A'],
    start() { round = 0; over = false; newRound(); },
    update(dt) {
      K.updateParts(dt);
      if (over) { B.update(w, dt); return; }
      if (phase === 'ready') { ptimer -= dt; if (ptimer <= 0) { phase = 'play'; w.frozen = false; K.sfx.go(); } return; }
      if (phase === 'play') readPads();
      B.update(w, dt);
      if (phase === 'play') {
        if (w.T >= B.SD_AT && !sdShow) sdShow = 1.8;
        if (sdShow > 0) sdShow -= dt;
        if (B.alive(w) <= 1) {
          phase = 'end'; ptimer = 2.2; w.frozen = true; roundWin = -1;
          for (const p of w.players) if (p.alive) roundWin = p.i;
          if (roundWin >= 0) { K.slots[roundWin].score++; K.sfx.ok(); }
        }
      } else if (phase === 'end') {
        ptimer -= dt;
        if (ptimer <= 0) {
          const top = K.slots.reduce((m, s) => (s.score > m.score ? s : m), K.slots[0]);
          if (top.score >= WIN) { over = true; K.over(top.i, K.slots.map((s) => s.name + ': ' + s.score)); } else newRound();
        }
      }
    },
    draw(c) {
      if (dirty) paintBlocks();
      K.shakeApply(c); c.drawImage(floorCv, 0, 0);
      const sd = w.T >= B.SD_AT && phase !== 'ready';
      if (sd) { c.strokeStyle = 'rgba(239,68,68,' + (0.5 + 0.4 * Math.sin(K.t * 8)).toFixed(2) + ')'; c.lineWidth = 4; K.rr(c, OX - 10, OY - 10, COLS * CS + 20, ROWS * CS + 20, 18); c.stroke(); }
      else { c.strokeStyle = '#4f6bd8'; c.lineWidth = 4; K.rr(c, OX - 10, OY - 10, COLS * CS + 20, ROWS * CS + 20, 18); c.stroke(); }
      // power-ups
      for (let i = 0; i < N; i++) if (w.power[i]) drawPower(c, OX + (i % COLS) * CS + CS / 2, OY + ((i / COLS) | 0) * CS + CS / 2, w.power[i]);
      c.drawImage(blocksCv, 0, 0);
      // fire
      for (let i = 0; i < N; i++) if (w.flame[i]) {
        const x = OX + (i % COLS) * CS, y = OY + ((i / COLS) | 0) * CS, a = Math.min(1, w.flameT[i] / 0.22);
        c.globalAlpha = a; c.fillStyle = '#ea580c'; K.rr(c, x + 2, y + 2, CS - 4, CS - 4, 12); c.fill();
        c.fillStyle = '#fbbf24'; K.rr(c, x + 9, y + 9, CS - 18, CS - 18, 9); c.fill();
        c.fillStyle = '#fff7d6'; K.rr(c, x + 18, y + 18, CS - 36, CS - 36, 5); c.fill(); c.globalAlpha = 1;
      }
      // bombs
      for (const b of w.bombs) if (b.on) {
        const x = OX + (b.c + 0.5) * CS, y = OY + (b.r + 0.5) * CS, hot = b.t < 0.7 && ((b.t * 14) | 0) % 2 === 0, s = 1 + 0.07 * Math.sin(b.t * 11);
        c.fillStyle = 'rgba(0,0,0,.35)'; c.beginPath(); c.ellipse(x, y + 13, 15, 6, 0, 0, 6.283); c.fill();
        c.fillStyle = hot ? '#7f1d1d' : '#0b1020'; c.beginPath(); c.arc(x, y + 2, 16 * s, 0, 6.283); c.fill();
        c.strokeStyle = K.slots[b.owner].color; c.lineWidth = 3; c.beginPath(); c.arc(x, y + 2, 16 * s, 0, 6.283); c.stroke();
        c.fillStyle = 'rgba(255,255,255,.4)'; c.beginPath(); c.arc(x - 5, y - 4, 4, 0, 6.283); c.fill();
        c.fillStyle = '#fbbf24'; c.fillRect(x + 6, y - 17, 4, 7); c.fillStyle = hot ? '#fff' : '#f97316'; c.beginPath(); c.arc(x + 8, y - 19, 4 + (((b.t * 20) | 0) & 1), 0, 6.283); c.fill();
      }
      // players
      for (const p of w.players) if (p.alive) drawPlayer(c, p, K.slots[p.i].color);
      K.drawParts(c);
      K.hud(c, K.slots, (s) => (w.players[s.i] && !w.players[s.i].alive ? 'out' : ''));
      drawStats(c);
      // time to the shrink, and the shrink
      if (sd) K.text(c, SD_WORD, W2, 703, 15, '#f87171', 'center', 800);
      else if (phase !== 'ready') { const n = Math.max(0, Math.ceil(B.SD_AT - w.T)); if (n !== leftN) { leftN = n; left = (K.VI ? 'Thu hẹp sau ' : 'Shrinks in ') + n + 's'; } K.text(c, left, W2, 703, 15, '#64748b', 'center', 600); }
      if (phase === 'ready') K.banner(c, (K.VI ? 'Ván ' : 'Round ') + NUM[round % 10], null, '#fff');
      else if (phase === 'end' && !over) K.banner(c, roundWin >= 0 ? K.slots[roundWin].name : (K.VI ? 'Hòa' : 'Draw'), roundWin >= 0 ? (K.VI ? 'thắng ván này' : 'takes the round') : null, roundWin >= 0 ? K.slots[roundWin].color : '#fff');
      else if (sdShow > 0) K.banner(c, SD_WORD, null, '#f87171');
    },
  };
  var G;

  function drawPlayer(c, p, col) {
    const x = px(p.x), y = py(p.y) + Math.sin(p.walk * 9) * 1.5, f = p.face;
    c.fillStyle = 'rgba(0,0,0,.35)'; c.beginPath(); c.ellipse(x, y + 17, 15, 6, 0, 0, 6.283); c.fill();
    c.fillStyle = col; c.beginPath(); c.arc(x, y, 18, 0, 6.283); c.fill();
    c.fillStyle = 'rgba(0,0,0,.22)'; c.beginPath(); c.arc(x, y + 5, 18, 0.1, 3.04); c.fill();
    c.strokeStyle = 'rgba(255,255,255,.75)'; c.lineWidth = 2.5; c.beginPath(); c.arc(x, y, 18, 0, 6.283); c.stroke();
    const ex = f === 2 ? -4 : f === 3 ? 4 : 0, ey = f === 0 ? -5 : f === 1 ? 2 : -1, gx = f === 2 ? -2 : f === 3 ? 2 : 0, gy = f === 0 ? -2 : f === 1 ? 2 : 0;
    c.fillStyle = '#fff'; c.beginPath(); c.arc(x - 6 + ex, y + ey, 5.5, 0, 6.283); c.arc(x + 6 + ex, y + ey, 5.5, 0, 6.283); c.fill();
    c.fillStyle = '#0b1020'; c.beginPath(); c.arc(x - 6 + ex + gx, y + ey + gy, 2.6, 0, 6.283); c.arc(x + 6 + ex + gx, y + ey + gy, 2.6, 0, 6.283); c.fill();
  }
  function drawPower(c, x, y, t) {
    c.fillStyle = 'rgba(255,255,255,.92)'; K.rr(c, x - 17, y - 17, 34, 34, 8); c.fill();
    if (t === 1) {                                                                       // one more bomb
      c.fillStyle = '#0b1020'; c.beginPath(); c.arc(x - 1, y + 3, 9, 0, 6.283); c.fill(); c.fillStyle = '#f97316'; c.fillRect(x + 4, y - 9, 3, 6);
    } else if (t === 2) {                                                                // longer flame
      c.fillStyle = '#ea580c'; c.fillRect(x - 3, y - 12, 6, 24); c.fillRect(x - 12, y - 3, 24, 6); c.fillStyle = '#fbbf24'; c.fillRect(x - 1.5, y - 8, 3, 16); c.fillRect(x - 8, y - 1.5, 16, 3);
    } else {                                                                             // faster
      c.strokeStyle = '#0891b2'; c.lineWidth = 4; c.lineCap = 'round'; c.beginPath(); c.moveTo(x - 9, y - 8); c.lineTo(x - 1, y); c.lineTo(x - 9, y + 8); c.moveTo(x + 1, y - 8); c.lineTo(x + 9, y); c.lineTo(x + 1, y + 8); c.stroke(); c.lineCap = 'butt';
    }
  }
  function drawStats(c) {                                                               // under each score card: bombs, flame, speed
    const n = K.slots.length, cw = Math.min(290, (1280 - 60) / n - 14), gx = 640 - (n * cw + (n - 1) * 14) / 2;
    K.slots.forEach((s, i) => {
      const p = w.players[i], x = gx + i * (cw + 14) + 14, y = 86; c.globalAlpha = p.alive ? 1 : 0.4;
      c.fillStyle = '#e8eefc'; c.beginPath(); c.arc(x + 5, y, 5.5, 0, 6.283); c.fill(); K.text(c, NUM[p.maxB], x + 18, y, 16, '#e8eefc', 'left', 700);
      c.fillStyle = '#fb923c'; c.fillRect(x + 54, y - 1.5, 12, 3); c.fillRect(x + 58.5, y - 6, 3, 12); K.text(c, NUM[p.range], x + 72, y, 16, '#e8eefc', 'left', 700);
      c.strokeStyle = '#22d3ee'; c.lineWidth = 2.5; c.beginPath(); c.moveTo(x + 106, y - 5); c.lineTo(x + 112, y); c.lineTo(x + 106, y + 5); c.moveTo(x + 113, y - 5); c.lineTo(x + 119, y); c.lineTo(x + 113, y + 5); c.stroke();
      K.text(c, NUM[Math.round((p.speed - 3.4) / 0.5)], x + 126, y, 16, '#e8eefc', 'left', 700); c.globalAlpha = 1;
    });
  }
  K.run(G);
})();
Credit.key = () => Kit.mode !== 'play';   // Down plays while a game is on
