/* Shared by the demos of TV Home / TV Channels / TV Games: language, scaling of the 1280x720 screen, and one key vocabulary
   (up down left right ok back home menu) fed by the on-screen remote, the keyboard and, in the hub, its forwarded keys.
   "menu" is what a long press of OK does on the real TVs: a long press of OK on the remote / Enter, or the Menu button / M. */
(function () {
  var lang = 'vi';
  try { var m = /[?&]lang=(vi|en)/.exec(location.search); lang = m ? m[1] : localStorage.getItem('lang') || (/^vi/i.test(navigator.language) ? 'vi' : 'en'); if (lang !== 'vi' && lang !== 'en') lang = 'vi'; } catch (e) {}
  document.documentElement.dataset.lang = lang; document.documentElement.lang = lang;
  var VI = lang === 'vi';
  var TVKit = window.TVKit = { VI: VI, handler: null, T: function (vi, en) { return VI ? vi : en; } };

  var tv, scr;
  function fit() { if (!tv || !scr) return; var s = tv.clientWidth / 1280; scr.style.transform = 'scale(' + s + ')'; }
  TVKit.mount = function (tvEl, scrEl) {
    tv = tvEl; scr = scrEl; fit();
    if (window.ResizeObserver) new ResizeObserver(fit).observe(tv); else addEventListener('resize', fit);
  };

  function emit(k) { if (TVKit.handler) TVKit.handler(k); }
  var HOLD = 520, okTimer = null, okDown = false;
  // OK is the one key with two meanings: a quick press is OK, a long press (real TVs: hold OK) is the menu
  function okPress(down) {
    if (down) { if (okDown) return; okDown = true; okTimer = setTimeout(function () { okTimer = null; emit('menu'); }, HOLD); }
    else { if (!okDown) return; okDown = false; if (okTimer) { clearTimeout(okTimer); okTimer = null; emit('ok'); } }
  }
  TVKit.press = function (k, down) { if (k === 'ok') okPress(down); else if (down) emit(k); };

  var KEYS = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', Enter: 'ok', ' ': 'ok', Escape: 'back', Backspace: 'back', Home: 'home', ContextMenu: 'menu', m: 'menu', M: 'menu', h: 'home' };
  function typing(e) { var t = e.target; return t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable); }
  addEventListener('keydown', function (e) {
    if (e.ctrlKey || e.metaKey || e.altKey || typing(e)) return;
    var k = KEYS[e.key]; if (!k) return;
    e.preventDefault(); if (e.repeat) return;
    TVKit.press(k, true); mark(k, true);
  });
  addEventListener('keyup', function (e) { var k = KEYS[e.key]; if (!k || typing(e)) return; if (k === 'ok') okPress(false); mark(k, false); });
  function mark(k, on) { var b = document.querySelector('.rc [data-k="' + k + '"]'); if (b) b.classList.toggle('down', on); }

  function bindRemote() {
    var r = document.querySelector('.rc'); if (r && !r.children.length) r.innerHTML = TVKit.rcHtml();
    document.querySelectorAll('.rc [data-k]').forEach(function (b) {
      var k = b.dataset.k;
      b.addEventListener('pointerdown', function (e) { e.preventDefault(); TVKit.press(k, true); });
      b.addEventListener('pointerup', function () { if (k === 'ok') okPress(false); });
      b.addEventListener('pointerleave', function () { if (k === 'ok' && okDown && okTimer) { clearTimeout(okTimer); okTimer = null; okDown = false; } });
      b.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') e.stopPropagation(); });   // a focused button: the page's own key map would double it
    });
  }
  // the hub forwards its keys when the page in the frame has no focus; it can also ask for a reset or a language
  addEventListener('message', function (e) {
    var d = e.data; if (!d || typeof d !== 'object') return;
    if (d.demoKey) { if ('down' in d) TVKit.press(d.demoKey, !!d.down); else { TVKit.press(d.demoKey, true); if (d.demoKey === 'ok') okPress(false); } }
    if (d.demoReset && TVKit.reset) TVKit.reset();
  });
  // a tab changes in the hub, or a link out of the TV (e.g. OK on the TV Channels tile)
  TVKit.go = function (tab) { if (parent !== window) parent.postMessage({ demoTab: tab }, '*'); else location.href = tab + '.html'; };

  TVKit.rcHtml = function () {
    var ic = function (d) { return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + d + '</svg>'; };
    return '<div class="pad"><button class="u" data-k="up" aria-label="Up">&#9650;</button><button class="l" data-k="left" aria-label="Left">&#9664;</button>' +
      '<button class="ok" data-k="ok" aria-label="OK">OK</button><button class="r" data-k="right" aria-label="Right">&#9654;</button><button class="d" data-k="down" aria-label="Down">&#9660;</button></div>' +
      '<div class="row"><button data-k="back" aria-label="Back">' + ic('<path d="M9 14L4 9l5-5"/><path d="M4 9h10a6 6 0 0 1 0 12h-3"/>') + TVKit.T('Quay lại', 'Back') + '</button>' +
      '<button data-k="home" aria-label="Home">' + ic('<path d="M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1Z"/>') + 'Home</button></div>' +
      '<div class="row"><button data-k="menu" aria-label="Menu">' + ic('<path d="M4 6h16M4 12h16M4 18h16"/>') + TVKit.T('Menu (giữ OK)', 'Menu (hold OK)') + '</button></div>' +
      '<p class="hint kb">' + TVKit.T('Bàn phím: ', 'Keyboard: ') + '<kbd>&larr;&uarr;&darr;&rarr;</kbd> <kbd>Enter</kbd> <kbd>Esc</kbd> <kbd>M</kbd></p>';
  };
  document.addEventListener('DOMContentLoaded', bindRemote);
  // in the hub the frame is as tall as its page (the hub scrolls, not the frame)
  if (parent !== window) {
    var lastH = 0, tell = function () { var h = document.body.offsetHeight; if (h !== lastH) { lastH = h; parent.postMessage({ demoH: h, from: location.pathname }, '*'); } };
    addEventListener('load', function () { tell(); if (window.ResizeObserver) new ResizeObserver(tell).observe(document.body); });
    addEventListener('resize', tell);
  }
})();
