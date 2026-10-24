'use strict';
// Lobby: the one "who is playing, how do I join" screen of the web games that draw their own (the Kit arcade pages, quiz, caro; the
// table games start at once and have none). Same look as TV Games' own lobby: the join QR is the hero on the left 40% (drawn
// once, integer pixels per module, white quiet zone, readable from 3 m), "Scan to play" + the short link under it; on the right the
// game's title, the players as chips that pop in as phones join, ONE primary pill ("OK: Start" when enough players are in,
// else "Needs N more") and small hints at the bottom. A pure DOM overlay: nothing animates but a chip's pop, so the page can idle at ~0 fps.
// Inside TV Games the native lobby (LobbyView.kt: QR card, chips, OK pill) has just ended, so the page does NOT repeat the QR there
// (window.TVPadNative is the bridge TV Games injects): the QR column shows only where nothing else offers the join link (a plain
// host or a test; ?lobbyqr forces it), and the layout then centres on the right side. Words match the native lobby.
// Needs ../_lib/qr.js first (the QR is drawn from its modules); ../_lib/sfx.js is optional (a tick when a phone joins).
//
//   Lobby.set({ title, tag?, chips: [{ id, name, color, label?, cpu? }], min?: 0, go?: false | 'text', extra?: Node, extraKey?: string, hints?: [string] })
//   Lobby.hide()        Lobby.shown (bool)
// Call set() as often as you like: it redraws only when what it shows changed (compare by value; extra only by extraKey).
(function () {
  const VI = (navigator.language || '').toLowerCase().startsWith('vi');
  const W = VI ? { scan: 'Quét để chơi', go: 'OK Bắt đầu', need: (n) => `Cần thêm ${n} người`, cpu: 'Máy' }
    : { scan: 'Scan to play', go: 'OK Start', need: (n) => `Needs ${n} more`, cpu: 'CPU' };
  const L = window.Lobby = { shown: false, W };
  const css = document.createElement('style');
  css.textContent = `
#lobby{position:fixed;inset:0;z-index:40;display:flex;background:radial-gradient(120% 90% at 25% 40%,#1a2340 0,#0b0d12 70%);color:#f1f4fb;
  font:600 3vh/1.25 system-ui,Roboto,"Helvetica Neue",Arial,sans-serif;cursor:none;overflow:hidden}
#lobby[hidden]{display:none}
#lobby .lq[hidden]{display:none}
#lobby .lq{flex:0 0 40vw;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2.2vh;padding-left:2vw}
#lobby canvas,#lobby img.qrf{display:block;image-rendering:pixelated;border-radius:1.4vh;box-shadow:0 1vh 4vh #000a;background:#fff}
#lobby .scan{font-size:4.4vh;font-weight:800;text-align:center}
#lobby .url{max-width:36vw;font-size:2.4vh;color:#93c5fd;text-align:center;word-break:break-all}
#lobby .lr{flex:1;min-width:0;display:flex;flex-direction:column;padding:0 4vw 0 2vw}
#lobby .lq[hidden]+.lr{padding:0 10vw}
#lobby .main{flex:1;min-height:0;display:flex;flex-direction:column;justify-content:center;gap:3vh}
#lobby h1{text-align:left;margin:0;font-size:9vh;line-height:1.05;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
#lobby .cats{justify-content:flex-start;margin:0;font-size:3vh}   /* quiz's own topic picker, restyled to the lobby's scale */
#lobby .tag{font-size:3vh;color:#9aa5bd;margin-top:-1vh}
#lobby .chips{display:flex;flex-wrap:wrap;gap:1.4vh 1.2vw;min-height:7vh}
#lobby .chip{display:flex;align-items:center;gap:.9vw;max-width:21vw;padding:.9vh 1.6vw .9vh .9vh;border-radius:99px;background:#161b27;font-size:3vh;animation:lbin .28s ease-out}
#lobby .chip.old{animation:none}
#lobby .chip b{flex:none;min-width:5vh;height:5vh;padding:0 .6vh;border-radius:99px;display:grid;place-items:center;background:var(--pc);color:#0b0d12;font-size:2.6vh;font-weight:900}
#lobby .chip span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
#lobby .chip.cpu{background:transparent;border:.3vh dashed #3a4258;color:#9aa5bd}
#lobby .chip.cpu b{background:transparent;border:.3vh solid var(--pc);color:var(--pc)}
#lobby .go{align-self:flex-start;padding:1.5vh 3vw;border-radius:99px;font-size:4.4vh;font-weight:800;background:#2563eb;color:#fff;box-shadow:0 0 4vh #2563eb88}
#lobby .go.wait{background:#243044;color:#fbbf24;box-shadow:none;font-size:3.4vh}
#lobby .hints{flex:none;margin:0 0 3vh;padding-right:var(--lbr,0);font-size:2.1vh;font-weight:500;color:#94a3b8}
@keyframes lbin{from{transform:scale(.6);opacity:0}to{transform:scale(1);opacity:1}}
@media (prefers-reduced-motion:reduce){#lobby .chip{animation:none}}`;
  let root, lq, lr, last = '', ids = null, qrKey = '';
  const el = (t, c, x) => { const e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; };
  const pad = () => window.tvpad;
  const qrOn = () => !window.TVPadNative || /[?&]lobbyqr/.test(location.search);
  const joinUrl = () => { try { return (pad() && pad().joinUrl && pad().joinUrl()) || ''; } catch (e) { return ''; } };

  // the QR: from the link's own modules at a whole number of pixels each (never smoothed, never blurred), 4 modules of white round it.
  // Drawn once per link and size: the lobby needs no redraw while nothing joins.
  function drawQr() {
    const url = qrOn() ? joinUrl() : ''; lq.replaceChildren(); lq.hidden = !url; qrKey = url + '|' + innerWidth + 'x' + innerHeight; if (!url) return;
    const m = typeof qrModules === 'function' ? qrModules(url) : null, n = m ? m.length + 8 : 0;
    const target = Math.min(innerWidth * 0.31, innerHeight * 0.64);
    let box;
    if (m) {
      const k = Math.max(2, Math.floor(target / n)), c = el('canvas'), x = c.getContext('2d'); c.width = c.height = n * k;
      x.fillStyle = '#fff'; x.fillRect(0, 0, n * k, n * k); x.fillStyle = '#000';
      m.forEach((row, y) => row.forEach((on, i) => { if (on) x.fillRect((i + 4) * k, (y + 4) * k, k, k); }));
      c.style.width = c.style.height = n * k + 'px'; box = c;                         // CSS px = canvas px: no resampling at 1x
    } else {                                                                          // a link longer than qr.js takes: the TV's own PNG
      try { box = el('img', 'qrf'); box.src = pad().qr(Math.round(target)); box.style.width = box.style.height = Math.round(target) + 'px'; } catch (e) { return; }
    }
    lq.append(box, el('div', 'scan', W.scan), el('div', 'url', url.replace(/^https?:\/\//, '')));
  }
  function build() {
    root = el('div'); root.id = 'lobby'; root.hidden = true; lq = el('div', 'lq'); lr = el('div', 'lr'); root.append(lq, lr);
    document.head.append(css); document.body.append(root);
    addEventListener('resize', () => { if (L.shown) drawQr(); });
  }
  L.set = (o) => {
    if (!document.body) { addEventListener('DOMContentLoaded', () => L.set(o)); return; }
    if (!root) build();
    const chips = o.chips || [], have = chips.filter((c) => !c.cpu).length, min = o.min || 0;
    const key = JSON.stringify([o.title, o.tag, chips, min, o.go, o.hints, o.extraKey, innerWidth, innerHeight]);
    if (!L.shown) { root.hidden = false; L.shown = true; last = ''; ids = null; }
    if (key === last) return;
    last = key;
    if (!qrKey || qrKey.split('|')[0] !== (qrOn() ? joinUrl() : '') || (qrOn() && !lq.firstChild)) drawQr();
    const old = ids || {}, first = ids === null; ids = {};
    const row = el('div', 'chips'); let joined = false;
    chips.forEach((c) => {
      ids[c.id] = 1; const e = el('div', 'chip' + (c.cpu ? ' cpu' : '') + (old[c.id] || first ? ' old' : ''));
      const b = el('b', '', c.label != null ? c.label : String(c.id).replace(/\D/g, '') || '·'); e.style.setProperty('--pc', c.color || '#3b82f6');
      e.append(b, el('span', '', c.name)); row.append(e); if (!old[c.id] && !first && !c.cpu) joined = true;
    });
    if (joined && window.Sfx && Sfx.play) Sfx.play('ui');                            // a phone joined: a tick, and its chip pops in
    const main = el('div', 'main'), kids = [el('h1', '', o.title || '')];
    if (o.tag) kids.push(el('div', 'tag', o.tag));
    kids.push(row);
    if (o.go !== false) kids.push(have >= min ? el('div', 'go', typeof o.go === 'string' ? o.go : W.go) : el('div', 'go wait', W.need(min - have)));
    if (o.extra) kids.push(o.extra);
    main.append(...kids);
    const hints = el('div', 'hints', (o.hints || []).filter(Boolean).join('  ·  '));
    lr.style.setProperty('--lbr', window.Credit ? '24vw' : '0');                      // the credit tag sits bottom right: hints stop before it
    lr.replaceChildren(main, hints);
  };
  L.hide = () => { if (root && L.shown) { root.hidden = true; } L.shown = false; };
})();
