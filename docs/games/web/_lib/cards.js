'use strict';
// Cards: the motion every card game on TV Games shares (Tiến lên, Mèo nổ, Colors; the remote panel in table.js). FLIP with the Web
// Animations API: the card is drawn where it ends up, measured, and animated from where it came from. transform + opacity only (a weak
// TV box keeps these on the compositor), will-change only while a card moves, nothing at all under prefers-reduced-motion.
//
//   Cards.fly(el, from, {delay, dur, rot, back})   el flies from [from] (element, selector or {x,y,w,h}) to where it is now;
//                                                   back: it also starts face down and turns over on arrival (a drawn card)
//   Cards.flyIn(els, froms, {gap})                  several, one after another; froms[i % froms.length]
//   Cards.ghost(like, from, to, {delay, fade})      a clone of [like] (a card back) flies between two rects and goes: for cards
//                                                   that end up on a seat's few small backs, not in a place of their own
//   Cards.slide(map)                                {el: oldRect}: each el slides from its old place to its new one (a hand closing up)
//   Cards.at(pos)                                   the rect of seat 's' 'e' 'n' 'w' (its backs, else the seat, else the remote panel)
//   Cards.hold(rects) / Cards.held()                the remote panel hands the rects of the cards it just played to the game's draw()
//   Cards.deal(posList, from, like)                 a few backs fly from [from] to each seat, one after another (a new game)
(function (root) {
  const EASE = 'cubic-bezier(.2,.8,.25,1)', DUR = 340;
  const reduced = () => { try { return !!(root.matchMedia && root.matchMedia('(prefers-reduced-motion: reduce)').matches); } catch (e) { return false; } };
  const can = () => typeof document === 'object' && document.body && Element.prototype.animate && !reduced();

  /** x as {x, y, w, h}: an element, a selector or already a rect; null when it is not on screen. */
  function rect(x) {
    const e = typeof x === 'string' ? document.querySelector(x) : x;
    if (!e) return null;
    const r = e.getBoundingClientRect ? e.getBoundingClientRect() : null;
    const o = r ? { x: r.left, y: r.top, w: r.width, h: r.height } : { x: e.x, y: e.y, w: e.w, h: e.h };
    return o.w > 0 && o.h > 0 ? o : null;
  }
  const done = (a, f) => { a.onfinish = a.oncancel = f; };

  function fly(el, from, o) {
    o = o || {};
    const f = rect(from);
    if (!f || !can()) return null;
    const to = rect(el);
    if (!to) return null;
    const dx = f.x + f.w / 2 - (to.x + to.w / 2), dy = f.y + f.h / 2 - (to.y + to.h / 2), s = Math.max(.25, Math.min(2, f.w / to.w));
    const end = getComputedStyle(el).transform, e = end === 'none' ? '' : ' ' + end;     // the CSS pose it lands in (a focused card is lifted)
    const start = `translate(${dx}px,${dy}px) rotate(${o.rot || 0}deg) scale(${s})` + e, rest = e.trim() || 'none';
    const dur = o.dur || DUR, opt = { duration: o.back ? dur * 1.4 : dur, delay: o.delay || 0, fill: 'backwards' };
    let kf, flip = null;
    if (o.back) {
      flip = document.createElement('cflip'); el.append(flip);
      kf = [{ transform: start, opacity: .6, offset: 0, easing: EASE }, { transform: rest, opacity: 1, offset: .62 },
        { transform: (e + ' scaleX(.04)').trim(), offset: .8, easing: 'ease-out' }, { transform: rest, offset: 1 }];
      const a = flip.animate([{ opacity: 1, offset: 0 }, { opacity: 1, offset: .79 }, { opacity: 0, offset: .8 }, { opacity: 0, offset: 1 }], { duration: opt.duration, delay: opt.delay, fill: 'both' });
      done(a, () => flip.remove());
    } else {
      kf = [{ transform: start, opacity: .5 }, { transform: rest, opacity: 1 }];
      opt.easing = EASE;
    }
    el.style.willChange = 'transform, opacity';
    const an = el.animate(kf, opt);
    done(an, () => { el.style.willChange = ''; if (flip) flip.remove(); });
    return an;
  }

  function flyIn(els, froms, o) {
    o = o || {};
    const list = (Array.isArray(froms) ? froms : [froms]).filter(Boolean);
    if (!list.length) return;
    els.forEach((el, i) => fly(el, list[i % list.length], Object.assign({}, o, { delay: (o.delay || 0) + i * (o.gap != null ? o.gap : 45), rot: (o.rot != null ? o.rot : (i % 2 ? 1 : -1) * (4 + (i * 3) % 7)) })));
  }

  function ghost(like, from, to, o) {
    o = o || {};
    const f = rect(from), d = rect(to);
    if (!f || !d || !like || !can()) return null;
    const g = like.cloneNode(false), st = g.style, cs = getComputedStyle(like);   // outside its parent the clone loses the parent's rules: copy the look
    g.className = 'cghost'; g.removeAttribute('id');
    st.background = cs.background; st.border = Math.min(parseFloat(cs.borderTopWidth) || 0, f.w * .06) + 'px solid ' + cs.borderTopColor; st.borderRadius = Math.min(parseFloat(cs.borderTopLeftRadius) || 0, f.w * .12) + 'px'; st.boxShadow = cs.boxShadow; st.boxSizing = 'border-box';
    const set = { position: 'fixed', inset: 'auto', margin: '0', left: f.x + 'px', top: f.y + 'px', width: f.w + 'px', height: f.h + 'px', 'transform-origin': '0 0', 'z-index': '30', 'pointer-events': 'none', 'will-change': 'transform, opacity' };
    Object.keys(set).forEach((k) => st.setProperty(k, set[k]));
    document.body.append(g);
    const a = g.animate([{ transform: 'none', opacity: 1, easing: EASE },
      { transform: `translate(${d.x - f.x}px,${d.y - f.y}px) rotate(${o.rot || 0}deg) scale(${d.w / f.w},${d.h / f.h})`, opacity: o.fade ? 0 : 1 }],
    { duration: o.dur || DUR + 60, delay: o.delay || 0, fill: 'both' });
    done(a, () => g.remove());
    setTimeout(() => g.remove(), (o.dur || DUR + 60) + (o.delay || 0) + 400);
    return a;
  }

  function slide(map) {
    if (!can()) return;
    map.forEach((old, el) => {
      const n = rect(el);
      if (!n || !old || (Math.abs(n.x - old.x) < 1 && Math.abs(n.y - old.y) < 1)) return;
      const end = getComputedStyle(el).transform, e = end === 'none' ? '' : ' ' + end;
      el.animate([{ transform: `translate(${old.x - n.x}px,${old.y - n.y}px)` + e }, { transform: e.trim() || 'none' }], { duration: 220, easing: EASE });
    });
  }

  function at(pos) {
    return rect('.seat.' + pos + ' .backs') || rect('.seat.' + pos) || rect('#rmt .hand') ||
      { x: innerWidth / 2 - 20, y: innerHeight - 70, w: 40, h: 56 };
  }

  const small = (d) => ({ x: d.x + d.w / 2 - 7, y: d.y + d.h / 2 - 10, w: 14, h: 20 });   // where a card ends on a seat's row of backs
  let H = null;
  function hold(rects) { H = rects && rects.length ? { r: rects, t: Date.now() } : null; }
  function held() { const h = H; H = null; return h && Date.now() - h.t < 600 ? h.r : null; }

  function deal(list, from, like, per) {
    if (!can() || !like) return;
    const f = rect(from);
    if (!f) return;
    const n = per || 4;
    const seats = list.filter((p) => !(p === 's' && document.body.classList.contains('rmt-on')));   // the remote panel deals its own hand
    for (let k = 0; k < n; k++) seats.forEach((p, j) => {
      const d = at(p);
      ghost(like, { x: f.x + f.w / 2 - 12, y: f.y + f.h / 2 - 17, w: 24, h: 34 }, small(d), { delay: (k * seats.length + j) * 40, dur: 300, fade: true, rot: 20 });
    });
  }

  // Cards that changed hands since the last call, from the games' draw(): [o.lens] the hand sizes by seat, [o.dropN] cards that left the
  // deck, [o.deck] / [o.like] its selector and a card back to copy, [o.pos] the seat letters, [o.fresh] a new game (deal instead).
  // A draw flies from the deck to the seat's backs (a steal from the seat that lost a card); a seat the remote panel plays does it itself.
  let was = [];
  function flow(o) {
    const now = o.lens, before = was, like = document.querySelector(o.like); was = now;
    if (!can() || !like) return;
    if (o.fresh) { deal(o.pos, o.deck, like); return; }
    if (before.length !== now.length) return;
    const rm = document.body.classList.contains('rmt-on'), dk = rect(o.deck), loss = now.map((n, s) => (n < before[s] ? s : -1)).filter((s) => s >= 0);
    let fromDeck = o.dropN > 0 && o.dropN < 20 ? o.dropN : 0, j = 0;
    now.forEach((n, s) => {
      if (s === 0 && rm) return;
      for (let k = before[s]; k < n && k - before[s] < 3; k++) {
        const src = fromDeck > 0 ? (fromDeck--, dk) : loss.length ? small(at(o.pos[loss[0]])) : dk;
        if (src) ghost(like, src, small(at(o.pos[s])), { delay: j++ * 90, fade: true, rot: 12 });
      }
    });
  }

  if (typeof document === 'object' && document.head) {
    const css = document.createElement('style');
    css.textContent = `cflip{display:block;position:absolute;inset:0;border-radius:inherit;box-sizing:border-box;border:.3vh solid #dfe4f1;background:repeating-linear-gradient(45deg,var(--back-a,#3348a8) 0 .8vh,var(--back-b,#2a3b8c) .8vh 1.6vh);pointer-events:none}
.cghost{box-shadow:0 .3vh .8vh #0008}`;
    document.head.append(css);
  }
  root.Cards = { fly, flyIn, ghost, slide, flow, at, small, hold, held, deal, rect, reduced };
})(typeof window === 'object' ? window : globalThis);
