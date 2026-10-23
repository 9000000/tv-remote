// The page's last line of defence: an error nobody caught (a script bug, a rejected promise nobody awaited) shows a box with the
// message and stack and a Copy button, so what went wrong is on the screen to read or send, not only in a console nobody has open.
// Loaded first (before i18n.js), so it also sees a failure of the scripts after it; the words are asked when the box is drawn.
// The Android apps have the same thing for the native side (app/shared/src/guard); tests/errbox.test.js runs this file.
(() => {
  const word = (id) => (typeof T === 'function' ? T(id) : id);
  const LINES = 6;                                                    // of the stack on screen; Copy has all of it
  let box = null, repeats = 0, shown = '', title, body, more, copyBtn, full = '';

  /** What Copy puts on the clipboard: when, where and the whole stack. */
  const report = (msg, stack) => [new Date().toISOString(), (typeof location !== 'undefined' ? location.href : ''),
    (typeof navigator !== 'undefined' ? navigator.userAgent : ''), '', msg, stack || ''].join('\n');

  /** Resource errors (img/script onerror), the cross-origin "Script error." (no message, no line: nothing to read) and the
   *  ResizeObserver notice (harmless, every browser sends it) are not worth a box. */
  const ignored = (e) => {
    if (e.target && e.target !== window && e.target.nodeType) return true;       // an element failed to load: its own onerror handles it
    const m = String(e.message || '');
    if (m === 'Script error.' && !e.error && !e.lineno) return true;
    return /^ResizeObserver loop/.test(m);
  };

  const copy = () => {
    const done = () => { copyBtn.textContent = word('err.copied'); };
    try { navigator.clipboard.writeText(full).then(done, fallback); } catch { fallback(); }
    function fallback() {                                              // http on the LAN has no navigator.clipboard
      try { const t = document.createElement('textarea'); t.value = full; document.body.append(t); t.select(); document.execCommand('copy'); t.remove(); done(); } catch {}
    }
  };

  const close = () => { if (box) box.remove(); box = null; repeats = 0; shown = ''; };

  function show(msg, stack) {
    msg = String(msg || '').slice(0, 500);
    full = report(msg, stack);
    const key = msg + '\n' + String(stack || '').split('\n')[0];
    if (box && key === shown) {                                       // the same error again: one box, counted
      repeats++;
      more.textContent = '×' + (repeats + 1);
      return;
    }
    if (!box) {
      box = document.createElement('div');
      box.className = 'errbox';
      box.setAttribute('role', 'alert');
      title = document.createElement('b');
      more = document.createElement('span');
      body = document.createElement('pre');
      const row = document.createElement('div');
      copyBtn = document.createElement('button');
      const closeBtn = document.createElement('button');
      copyBtn.addEventListener('click', copy);
      closeBtn.addEventListener('click', close);
      closeBtn.textContent = word('err.close');
      row.className = 'errbox-row';
      row.append(copyBtn, closeBtn);
      box.append(title, more, body, row);
      document.body.append(box);
      try { if (/[?&]ontv=1/.test(location.search)) closeBtn.focus(); } catch {}   // the TV's D-pad needs a focus to start from
    }
    repeats = 0; shown = key;
    title.textContent = word('err.title');
    more.textContent = '';
    copyBtn.textContent = word('err.copy');
    body.textContent = [msg].concat(String(stack || '').split('\n').filter((l) => l.trim()).slice(0, LINES)).join('\n');
  }

  window.addEventListener('error', (e) => {
    if (ignored(e)) return;
    show(e.message || (e.error && e.error.message) || 'Error', e.error && e.error.stack);
  });
  window.addEventListener('unhandledrejection', (e) => {
    const r = e.reason;
    show((r && r.message) || String(r), r && r.stack);
  });
  window.ErrBox = { show, close, ignored };
})();
