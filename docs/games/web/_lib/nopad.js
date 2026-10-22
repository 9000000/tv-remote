'use strict';
// A game page opened in a plain browser (a shared link, a search result): no window.tvpad, so no phones can join. A card says
// what the page is for, with a QR and a link to the site, where TV Games is installed. TV Games' WebView and the site's demo set
// tvpad before this runs (an old WebView as the page starts: hence the wait), so they never see it. An arcade page (Kit) can still be tried with the keyboard: the card can close.
// Needs ../_lib/qr.js first.   <script src="../_lib/qr.js"></script><script src="../_lib/nopad.js"></script>
(function () {
  const HOME = 'https://tvremote.pages.dev/#games';
  const VI = (navigator.language || '').toLowerCase().startsWith('vi');
  const W = VI ? { t: 'Game này chơi trên TV', s: 'Mở bằng app TV Games trên Android TV, điện thoại làm tay cầm. Quét mã hoặc mở link để cài:', go: 'Mở tvremote.pages.dev', kb: 'Chơi thử bằng bàn phím' }
    : { t: 'This game is played on a TV', s: 'Open it in the TV Games app on an Android TV, with phones as pads. Scan the code or open the link to get it:', go: 'Open tvremote.pages.dev', kb: 'Try it with the keyboard' };
  const show = () => {
    if (window.tvpad) return;
    const css = document.createElement('style');
    css.textContent = `
#nopad{position:fixed;inset:0;z-index:100;display:flex;align-items:center;justify-content:center;padding:16px;background:#0b0f1af2;
  font:500 16px/1.45 system-ui,-apple-system,sans-serif;color:#e2e8f0}
#nopad>div{display:flex;gap:28px;align-items:center;max-width:720px;padding:28px;border-radius:18px;background:#1b2028;box-shadow:0 16px 48px #000a}
#nopad svg{flex:none;width:200px;height:200px;border-radius:10px;background:#fff}
#nopad b{display:block;font-size:26px;line-height:1.2;margin-bottom:8px;color:#fff}
#nopad p{margin:0 0 16px;color:#9aa0a6}
#nopad a,#nopad button{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:0 18px;border-radius:10px;border:0;
  font:600 16px system-ui,sans-serif;cursor:pointer;text-decoration:none;margin:0 8px 8px 0}
#nopad a{background:#2f7df6;color:#fff}#nopad button{background:#2a3042;color:#e2e8f0}
@media (max-width:600px){#nopad>div{flex-direction:column;text-align:center;padding:22px}#nopad svg{width:180px;height:180px}}`;
    const box = document.createElement('div'); box.id = 'nopad';
    const card = document.createElement('div'), txt = document.createElement('div');
    const q = typeof qrSvg === 'function' ? qrSvg(HOME, 300) : null;
    if (q) card.append(q);
    const go = Object.assign(document.createElement('a'), { href: HOME, textContent: W.go });
    txt.append(Object.assign(document.createElement('b'), { textContent: W.t }), Object.assign(document.createElement('p'), { textContent: W.s }), go);
    if (window.Kit) txt.append(Object.assign(document.createElement('button'), { textContent: W.kb, onclick: () => box.remove() }));
    card.append(txt); box.append(card);
    document.head.append(css); document.body.append(box);
  };
  // a moment's grace: an old WebView puts tvpad in as the page starts, not before it
  const later = () => setTimeout(show, 800);
  if (document.readyState === 'loading') addEventListener('DOMContentLoaded', later); else later();
})();
