// ---------- the page on the TV itself (TV Setup → Quản lý trên TV, ManageActivity): driven by the TV's remote ----------
// No mouse and no touch: the D-pad moves the focus to the nearest control in that direction, OK presses it, Back (tvBack,
// called by the app) closes what is open first. Elsewhere this file does nothing.
if (window.ONTV) (() => {
  const SEL = 'button, a[href], input, select, textarea, summary, [tabindex]:not([tabindex="-1"])';
  const DIR = { ArrowUp: [0, -1], ArrowDown: [0, 1], ArrowLeft: [-1, 0], ArrowRight: [1, 0] };
  const seen = (e) => { if (e.disabled || e.closest('[hidden], [inert]')) return false; const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(e).visibility !== 'hidden'; };
  const scope = () => [...document.querySelectorAll('dialog[open]')].pop() || document;   // an open dialog keeps the focus in it
  const items = () => [...scope().querySelectorAll(SEL)].filter((e) => seen(e) && !(e.closest('details:not([open])') && e.tagName !== 'SUMMARY' && !e.closest('summary')));
  const start = () => { col = null; const s = scope(); return (s === document && document.querySelector('[data-tab][aria-selected="true"]')) || items()[0]; };
  // a text field the focus passes over keeps the TV's keyboard down (it would take the arrows); OK opens it (keydown below)
  const TEXT = 'textarea, input:not([type]), input[type=text], input[type=search], input[type=number], input[type=password], input[type=url], input[type=email], input[type=tel]';
  const quiet = (e) => { if (e.matches(TEXT) && !e.dataset.im) { e.dataset.im = e.inputMode || '-'; e.inputMode = 'none'; } };
  document.addEventListener('focusout', (ev) => { const e = ev.target; if (e.dataset && e.dataset.im) { e.inputMode = e.dataset.im === '-' ? '' : e.dataset.im; delete e.dataset.im; } }, true);
  const go = (e) => { if (!e) return false; quiet(e); e.focus({ preventScroll: true }); e.scrollIntoView({ block: 'nearest', inline: 'nearest' }); return true; };

  // Left / right: the nearest control on the same row, or none (the focus stays). Up / down: the next row that way, and on it
  // the control under the column the focus keeps since the last move sideways (a wide search box does not throw it off).
  let col = null;
  function next(from, [dx, dy]) {
    const a = from.getBoundingClientRect();
    const all = items().filter((e) => e !== from && !e.contains(from)).map((e) => [e, e.getBoundingClientRect()]);
    if (dx) {
      let best = null, score = Infinity;
      for (const [e, b] of all) {
        const edge = dx > 0 ? b.left - a.right : a.left - b.right, over = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        if (edge < -a.width / 2 || (b.left + b.right - a.left - a.right) * dx <= 0 || over < Math.min(a.height, b.height) * 0.3) continue;
        const s = Math.max(0, edge) + Math.abs(b.top + b.bottom - a.top - a.bottom) / 8;
        if (s < score) { score = s; best = e; }
      }
      if (best) { const b = best.getBoundingClientRect(); col = (b.left + b.right) / 2; }
      return best;
    }
    const ahead = all.filter(([, b]) => (dy > 0 ? b.top >= a.top + a.height / 2 : b.bottom <= a.bottom - a.height / 2))
      .sort((p, q) => dy > 0 ? p[1].top - q[1].top : q[1].bottom - p[1].bottom);
    if (!ahead.length) return null;
    const near = ahead[0][1], row = ahead.filter(([, b]) => dy > 0 ? b.top < near.bottom - 2 : b.bottom > near.top + 2);   // the row: what overlaps the nearest one
    const x = Math.min(Math.max(col ?? a.left + 1, a.left), a.right);
    let best = null, score = Infinity;
    for (const [e, b] of row) {
      const s = (b.left > x ? b.left - x : b.right < x ? x - b.right : 0) * 4 + Math.abs((dy > 0 ? b.top : b.bottom) - (dy > 0 ? near.top : near.bottom));
      if (s < score) { score = s; best = e; }
    }
    return best;
  }

  document.addEventListener('keydown', (ev) => {
    const d = DIR[ev.key], cur = document.activeElement;
    if (!d) {
      // OK on what Enter does not press by itself (a checkbox, a radio, a label)
      if (ev.key === 'Enter' && cur && cur.matches('input[type=checkbox], input[type=radio], label')) { ev.preventDefault(); cur.click(); }
      else if (ev.key === 'Enter' && cur && cur.matches(TEXT) && cur.inputMode === 'none') {   // OK on a field: now the keyboard
        ev.preventDefault(); cur.inputMode = cur.dataset.im === '-' ? '' : cur.dataset.im; delete cur.dataset.im; cur.blur(); cur.focus();
      }
      return;
    }
    const typing = cur && (cur.matches('textarea') || (cur.matches('input') && !cur.matches('[type=checkbox], [type=radio], [type=range], [type=button], [type=submit], [type=file]')));
    if (typing && d[0]) { const v = cur.value || '', at = cur.selectionStart; if (at != null && ((d[0] < 0 && at > 0) || (d[0] > 0 && at < v.length))) return; }   // the caret moves inside the words first
    if (cur && cur.matches('input[type=range]') && d[0]) return;    // a slider takes left / right
    ev.preventDefault();
    if (!cur || cur === document.body || !seen(cur) || !scope().contains(cur)) { go(start()); return; }
    if (!go(next(cur, d)) && d[1]) window.scrollBy({ top: d[1] * innerHeight * 0.4, behavior: 'smooth' });   // nothing that way: a long text still scrolls
  }, true);

  // Back: a dialog closes (as Esc would), then the focus goes back to the tabs; at the tabs the app leaves the page
  window.tvBack = () => {
    const d = [...document.querySelectorAll('dialog[open]')].pop();
    if (d) { if (d.dispatchEvent(new Event('cancel', { cancelable: true }))) d.close(); setTimeout(() => go(start()), 50); return true; }
    if (!document.activeElement?.closest('.tabs')) return go(start());
    return false;
  };
  // a dialog that opens takes the focus to its first control
  new MutationObserver(() => { const d = [...document.querySelectorAll('dialog[open]')].pop(); if (d && !d.contains(document.activeElement)) setTimeout(() => { if (d.open && !d.contains(document.activeElement)) go(items()[0]); }, 60); })
    .observe(document.body, { subtree: true, attributes: true, attributeFilter: ['open'], childList: true });
  addEventListener('load', () => setTimeout(() => go(start()), 300));
})();
