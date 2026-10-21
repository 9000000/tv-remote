// window.tvpad + a Gamepad API for the phones: injected by WebGameActivity into the game page only (see WEBGAMES.md).
(function () {
  'use strict';
  const N = window.TVPadNative;
  if (!N || window.tvpad) return;
  const BTN = { A: 0, B: 1, X: 2, Y: 3, L: 4, R: 5, L2: 6, R2: 7, C: 17, D: 18, SELECT: 8, START: 9, UP: 12, DOWN: 13, LEFT: 14, RIGHT: 15 };
  const on = {}, pads = [null, null, null, null];
  let list = [];
  const pings = {}, names = {};
  const emit = (t, d) => (on[t] || []).slice().forEach((f) => { try { f(d); } catch (e) { console.error(e); } });
  const mk = (p) => ({
    id: 'TV Games phone ' + p + ' (STANDARD GAMEPAD)', index: p - 1, connected: true, mapping: 'standard', timestamp: performance.now(),
    axes: [0, 0, 0, 0], buttons: Array.from({ length: 19 }, () => ({ pressed: false, touched: false, value: 0 })),
    vibrationActuator: { type: 'dual-rumble', playEffect: (_t, o) => { N.vibrate(p, Math.round((o && o.duration) || 100)); return Promise.resolve('complete'); }, reset: () => Promise.resolve('complete') },
  });
  const fire = (t, g) => { const e = new Event(t); e.gamepad = g; window.dispatchEvent(e); };
  navigator.getGamepads = () => pads.slice();
  try { list = JSON.parse(N.players()); } catch (e) { list = []; }
  list.forEach((p) => { pads[p - 1] = mk(p); names[p] = N.name(p); });
  const call = (p) => (p === 'all' || p == null ? 0 : p | 0);
  window.tvpad = {
    on(t, f) { (on[t] = on[t] || []).push(f); if (t === 'join') setTimeout(() => list.forEach((p) => f({ player: p, name: this.name(p) })), 0); return this; },
    off(t, f) { on[t] = (on[t] || []).filter((x) => x !== f); return this; },
    players() { return list.slice(); },
    ping(p) { return pings[p] == null ? N.ping(p) : pings[p]; },       // a phone's round trip to the TV in ms (median of its last 5), -1 = not known
    name(p) { return names[p] != null ? names[p] : N.name(p) || ''; },   // what the player typed on the phone; '' = the default ("Player N": the page words it)
    joinUrl() { return N.joinUrl(); },                                 // the link the TV's QR holds
    qr(px) { return N.qr(px | 0 || 256); },                            // that QR as a PNG data: URL, px wide
    layout(p, spec) { N.layout(call(p), spec ? JSON.stringify(spec) : 'null'); },
    vibrate(p, ms) { N.vibrate(call(p), ms | 0); },
    say(p, text) { N.say(call(p), String(text)); },
  };
  window.addEventListener('load', () => pads.forEach((g) => g && fire('gamepadconnected', g)));
  // events from the TV app
  window.__tvpadIn = (kind, p, a, b, c) => {
    if (kind === 'join') { if (list.indexOf(p) < 0) list.push(p); list.sort(); pads[p - 1] = mk(p); names[p] = a || ''; emit('join', { player: p, name: window.tvpad.name(p) }); fire('gamepadconnected', pads[p - 1]); }
    else if (kind === 'leave') { const g = pads[p - 1]; list = list.filter((x) => x !== p); pads[p - 1] = null; emit('leave', { player: p }); if (g) { g.connected = false; fire('gamepaddisconnected', g); } }
    else if (kind === 'rename') { names[p] = a || ''; emit('rename', { player: p, name: window.tvpad.name(p) }); }
    else if (kind === 'ping') { pings[p] = a; emit('ping', { player: p, ms: a }); }
    else if (kind === 'button') {
      const g = pads[p - 1], i = BTN[a];
      if (g && i != null) { const x = g.buttons[i]; x.pressed = x.touched = !!b; x.value = b ? 1 : 0; g.timestamp = performance.now();
        if (!g.stick && i >= 12 && i <= 15) { g.axes[0] = (g.buttons[15].pressed ? 1 : 0) - (g.buttons[14].pressed ? 1 : 0); g.axes[1] = (g.buttons[13].pressed ? 1 : 0) - (g.buttons[12].pressed ? 1 : 0); }
      }
      emit('button', { player: p, btn: a, down: !!b, lat: c >= 0 ? c : null, at: performance.now() });
    } else if (kind === 'axis') { const g = pads[p - 1]; if (g) { g.axes[0] = a; g.axes[1] = b; g.stick = a !== 0 || b !== 0; g.timestamp = performance.now(); }
      emit('axis', { player: p, x: a, y: b });
    } else if (kind === 'text') emit('text', { player: p, text: a });
  };
})();
