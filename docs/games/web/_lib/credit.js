'use strict';
// Credit: our table games are ported from gamehub24 (https://gamehub24.pages.dev, each player on their own phone, which it does
// better). A small tag in a bottom corner says so; pressing it, or Down on the TV remote, shows a QR that opens it on a phone.
// Any key or a tap closes the QR (and is not passed on), so does 30 s. Needs ../_lib/qr.js first.
// A game whose D-pad moves something sets Credit.key = () => <true only between games>, so Down still plays.
//   <script src="../_lib/qr.js"></script><script src="../_lib/credit.js"></script>
(function () {
  const URL_ = 'https://gamehub24.pages.dev/';
  const VI = (navigator.language || '').toLowerCase().startsWith('vi');
  const W = VI ? { tag: 'Phỏng theo gamehub24', key: '↓ mã QR', t: 'Chơi trên điện thoại', s: 'Quét mã để mở gamehub24: các game này, chơi trên điện thoại còn hay hơn.', x: 'Bấm phím bất kỳ để đóng' }
    : { tag: 'Based on gamehub24', key: '↓ QR code', t: 'Play on your phone', s: 'Scan to open gamehub24: these games, even better on a phone.', x: 'Press any key to close' };
  const C = { key: () => true };
  window.Credit = C;
  const css = document.createElement('style');
  css.textContent = `
#gh24{position:fixed;right:1.2vw;bottom:1vh;z-index:50;display:flex;gap:.6vw;align-items:center;padding:.5vh .9vw;border:0;border-radius:99px;
  background:#0b0f1acc;color:#cbd5e1;font:600 2vh/1.2 system-ui,sans-serif;opacity:.75;cursor:pointer}
#gh24 kbd{font:inherit;color:#94a3b8}
#gh24q{position:fixed;inset:0;z-index:60;display:flex;align-items:center;justify-content:center;background:#000a}
#gh24q[hidden]{display:none}
#gh24q div{display:flex;gap:3vw;align-items:center;max-width:70vw;padding:4vh 3vw;border-radius:2vh;background:#111827;color:#f1f5f9;
  font:500 2.4vh/1.35 system-ui,sans-serif;box-shadow:0 2vh 6vh #000a}
#gh24q svg{flex:none;width:30vh;height:30vh;border-radius:1vh}
#gh24q b{display:block;font-size:4vh;margin-bottom:1vh}
#gh24q u{display:block;margin:1.5vh 0;color:#93c5fd;text-decoration:none;font-weight:700}
#gh24q small{color:#94a3b8;font-size:1.8vh}`;
  const tag = document.createElement('button');
  tag.id = 'gh24'; tag.tabIndex = -1;
  tag.append(document.createTextNode(W.tag), Object.assign(document.createElement('kbd'), { textContent: W.key }));
  const box = document.createElement('div'); box.id = 'gh24q'; box.hidden = true;
  let timer = 0, eatUp = false;
  const close = () => { box.hidden = true; clearTimeout(timer); };
  const open = () => {
    if (!box.firstChild) {
      const card = document.createElement('div'), txt = document.createElement('p');
      const q = qrSvg(URL_, 300); if (q) card.append(q);
      txt.append(Object.assign(document.createElement('b'), { textContent: W.t }), W.s,
        Object.assign(document.createElement('u'), { textContent: 'gamehub24.pages.dev' }), Object.assign(document.createElement('small'), { textContent: W.x }));
      card.append(txt); box.append(card);
    }
    box.hidden = false; clearTimeout(timer); timer = setTimeout(close, 30000);
  };
  C.open = open; C.close = close;
  tag.addEventListener('click', (e) => { e.stopPropagation(); open(); });
  box.addEventListener('click', (e) => { e.stopPropagation(); close(); });
  // capture: an open QR eats the key that closes it, before the game sees it
  addEventListener('keydown', (e) => {
    if (!box.hidden) { e.preventDefault(); e.stopImmediatePropagation(); if (!e.repeat) { close(); eatUp = true; } return; }
    if (e.key === 'ArrowDown' && !e.repeat && C.key()) { e.preventDefault(); e.stopImmediatePropagation(); open(); }
  }, true);
  addEventListener('keyup', (e) => { if (eatUp || !box.hidden || (e.key === 'ArrowDown' && C.key())) { eatUp = false; e.stopImmediatePropagation(); } }, true);
  setInterval(() => { tag.lastChild.hidden = !C.key(); }, 500);      // the ↓ hint only while Down opens it
  const put = () => { document.head.append(css); document.body.append(tag, box); };
  if (document.body) put(); else addEventListener('DOMContentLoaded', put);
})();
