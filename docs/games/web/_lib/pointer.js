'use strict';
// Ptr: aiming for the arcade pages (balloons, slice, moles) on top of Kit. Each player has a crosshair in the 1280x720 canvas that the
// phone's touchpad moves by relative steps (window.tvpad 'pointer' events: {player, dx, dy} in pad pixels already speeded up, or
// {player, center} for its Recenter button); the Fire button is tvpad's 'A'. Without a phone: the mouse is player 1's crosshair and a
// click fires (desktop, tests); touch drags move player 1 too. A CPU seat steers its crosshair with Ptr.steer().
//   Ptr.use({ buttons:['A'] })   the games call it once: sets the phones' layout to the pointer pad while playing, the plain one on the menus
//                                (+ side: (slot index) => 'left' | 'right': that player's touchpad on that half of the phone)
//   Ptr.get(n)                   { x, y, vx, vy, sp (px/s, smoothed), px, py (last frame), fire (seconds since the last fire press) }
//   Ptr.onFire(fn(n))            a fire press (phone A, mouse click, Space / Z for player 1)
//   Ptr.update(dt)               call once per game step: speed, previous position
//   Ptr.draw(c, slots)           the crosshairs of the human seats, in their colour
//   Ptr.steer(p, tx, ty, dt, v)  move a crosshair toward a point at v px/s (a CPU)
//   Ptr.stick                    true (default): a stick / arrow keys / the TV remote's D-pad also move a human's crosshair (a Bluetooth pad, or the remote alone),
//                                and a pad's A / the remote's OK fires. A game with its own key handling (moles) or its own stick use (hockey) sets it false.
(function () {
  const K = window.Kit, pad = window.tvpad, W = 1280, H = 720, SENS = 2.4;
  const P = {}, fires = [];
  const Ptr = window.Ptr = { W, H };
  const get = Ptr.get = (n) => P[n] || (P[n] = { x: W / 2, y: H / 2, vx: 0, vy: 0, sp: 0, px: W / 2, py: H / 2, fire: 9, mv: 0 });
  const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
  const fire = (n) => {
    const now = Date.now(), p = get(n); if (now - (p.fat || 0) < 60) return; p.fat = now;     // one press can arrive twice (the button event and, on an older TV Games, its key): nobody taps faster than 60 ms
    p.fire = 0; fires.forEach((f) => { try { f(n); } catch (e) { console.error(e); } }); };
  Ptr.onFire = (f) => { fires.push(f); };
  Ptr.reset = () => { for (const k in P) { const p = P[k]; p.x = p.px = W / 2; p.y = p.py = H / 2; p.sp = 0; p.mv = 0; p.fire = 9; } };

  if (pad) {
    pad.on('pointer', (e) => {
      const p = get(e.player);
      if (e.center) { p.x = W / 2; p.y = H / 2; return; }
      p.x = clamp(p.x + e.dx * SENS, 0, W); p.y = clamp(p.y + e.dy * SENS, 0, H);
    });
    pad.on('button', (e) => { if (e.btn === 'A' && e.down && K.mode === 'play') fire(e.player); });
  }
  const cv = document.getElementById('c');
  const at = (ev) => { const r = cv.getBoundingClientRect(); return [clamp((ev.clientX - r.left) / r.width * W, 0, W), clamp((ev.clientY - r.top) / r.height * H, 0, H)]; };
  cv.addEventListener('mousemove', (e) => { const p = get(1), [x, y] = at(e); p.x = x; p.y = y; });
  cv.addEventListener('mousedown', (e) => { const p = get(1), [x, y] = at(e); p.x = x; p.y = y; if (K.mode === 'play') fire(1); });
  cv.addEventListener('touchmove', (e) => { const t = e.touches[0], p = get(1), [x, y] = at(t); p.x = x; p.y = y; e.preventDefault(); }, { passive: false });
  window.addEventListener('keydown', (e) => { if ((e.code === 'Space' || e.code === 'KeyZ' || (Ptr.stick && e.key === 'Enter')) && !e.repeat && K.mode === 'play') fire(1); });
  Ptr.stick = true;

  Ptr.use = (o) => {
    const buttons = (o && o.buttons) || ['A'];
    K.layout = () => {
      if (!pad || !K.G) return;
      try {
        const aspect = o && o.aspect;                                    // w/h of what the pointer steers: the touchpad takes that shape
        pad.layout('all', K.mode === 'play' ? { pointer: true, buttons, aspect } : { buttons: ['A', 'START'] });
        if (K.mode === 'play' && o && o.side) K.slots.forEach((s, i) => { if (s.human) pad.layout(s.pad, { pointer: true, buttons, aspect, side: o.side(i) }); });   // each player's touchpad on their side
      } catch (e) { }
    };
  };
  Ptr.update = (dt) => {
    if (Ptr.stick && K.mode === 'play') K.slots.forEach((s) => {
      if (!s.human) return;
      const q = K.pad(s.pad), p = get(s.pad);
      if (q.x || q.y) { p.x = clamp(p.x + q.x * 820 * dt, 0, W); p.y = clamp(p.y + q.y * 820 * dt, 0, H); }
      const g = !!(q.gp.A || q.gp.B); if (g && !p.gpd) fire(s.pad); p.gpd = g;                // a Bluetooth pad's A (phones fire through their own button event)
    });
    for (const k in P) {
      const p = P[k], d = Math.hypot(p.x - p.px, p.y - p.py) / Math.max(dt, 1e-3);
      p.sp += (d - p.sp) * Math.min(1, dt * 12); p.vx = (p.x - p.px) / Math.max(dt, 1e-3); p.vy = (p.y - p.py) / Math.max(dt, 1e-3);
      p.px = p.x; p.py = p.y; p.fire += dt;
    }
  };
  Ptr.steer = (p, tx, ty, dt, v) => {
    const dx = tx - p.x, dy = ty - p.y, d = Math.hypot(dx, dy), s = Math.min(d, v * dt);
    if (d > 0.5) { p.x += dx / d * s; p.y += dy / d * s; }
  };
  Ptr.draw = (c, slots) => {
    slots.forEach((s) => {
      if (!s.human) return;
      const p = get(s.pad), a = Math.max(0, 1 - p.fire * 5), r = 22 + a * 8;
      K.light(c, p.x, p.y, s.color, 44, 0.5 + a * 0.4);
      c.strokeStyle = '#fff'; c.lineWidth = 6; c.beginPath(); c.arc(p.x, p.y, r, 0, 6.283); c.stroke();
      c.strokeStyle = s.color; c.lineWidth = 3.5; c.beginPath(); c.arc(p.x, p.y, r, 0, 6.283); c.moveTo(p.x - r - 10, p.y); c.lineTo(p.x - 8, p.y); c.moveTo(p.x + 8, p.y); c.lineTo(p.x + r + 10, p.y);
      c.moveTo(p.x, p.y - r - 10); c.lineTo(p.x, p.y - 8); c.moveTo(p.x, p.y + 8); c.lineTo(p.x, p.y + r + 10); c.stroke();
    });
  };
})();
