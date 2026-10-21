/* Demo only (loaded before pad.js, which is an unchanged copy of app/games/src/main/assets/pad/pad.js):
   - WebSocket is a stand-in that talks to the demo page (docs/demo/games.html) with postMessage instead of the TV's server;
   - a mouse press becomes the touch events pad.js listens to;
   - the language, the fullscreen and the layouts.json request are fixed so that nothing reaches for a TV. */
(function () {
  'use strict';
  var q = new URLSearchParams(location.search), slot = q.get('slot') || '1', lang = q.get('lang') || 'vi';
  try { Object.defineProperty(navigator, 'language', { get: function () { return lang; } }); } catch (e) {}
  try { Object.defineProperty(document.documentElement, 'requestFullscreen', { value: undefined }); document.documentElement.webkitRequestFullscreen = undefined; } catch (e) {}
  var realFetch = window.fetch;
  window.fetch = function (u) { return /layouts\.json/.test(String(u)) ? Promise.resolve(new Response('{}')) : realFetch.apply(this, arguments); };

  var live = null;
  function FakeWS() {
    live = this; this.readyState = 0;
    var me = this; setTimeout(function () { me.readyState = 1; if (me.onopen) me.onopen(); }, 150);
  }
  FakeWS.prototype.send = function (s) { parent.postMessage({ padSlot: slot, padMsg: String(s) }, '*'); };
  FakeWS.prototype.close = function () { this.readyState = 3; if (this.onclose) this.onclose(); };
  window.WebSocket = FakeWS;
  addEventListener('message', function (e) {
    var d = e.data; if (!d || typeof d.padIn !== 'string' || !live || !live.onmessage) return;
    live.onmessage({ data: d.padIn });
  });

  // mouse -> touch (not when a real touch just happened: the browser sends both)
  var lastTouch = 0, down = false;
  addEventListener('touchstart', function () { lastTouch = Date.now(); }, true);
  function fake(type, e) {
    var ev = new Event(type, { bubbles: true, cancelable: true });
    ev.changedTouches = [{ identifier: 1, clientX: e.clientX, clientY: e.clientY, target: e.target }];
    (e.target || document).dispatchEvent(ev);
  }
  document.addEventListener('mousedown', function (e) { if (Date.now() - lastTouch < 800 || e.button) return; if (e.target.closest && e.target.closest('input,textarea')) return; down = true; fake('touchstart', e); });
  document.addEventListener('mousemove', function (e) { if (down) fake('touchmove', e); });
  addEventListener('mouseup', function (e) { if (down) { down = false; fake('touchend', e); } });
  addEventListener('mouseleave', function (e) { if (down) { down = false; fake('touchend', e); } });
})();
