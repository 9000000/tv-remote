// A photo made the TV's shape: the crop (centred cover by default, moved by drag, widened by pinch or wheel), drawn small for a
// preview and at the TV's size for sending. ONE copy for the page's "Tải ảnh của bạn" (js/home.js) and for the page TV Home
// serves to a phone (app/home/.../PhotoServer.kt; its build copies this file, so the two never differ).
// A view is { z, cx, cy, whole }: zoom (1 = the photo just covers the frame) and the crop's centre as fractions of the photo; whole: no
// crop, the photo sent as it is (HomeConfig "wfit" contain: TV Home shows it whole over itself blurred, a portrait photo's way).

/** The picked file as something a canvas draws, tried three ways: phones' browsers fail some photos one way and not the next (a
 * 50-200 MP photo runs out of memory whole but opens shrunk; an <img> opens some that createImageBitmap does not). Rejects when none can
 * (HEIC in Chrome). */
function photoOpen(f) {
  const tries = [];
  if (window.createImageBitmap) tries.push(() => createImageBitmap(f), () => createImageBitmap(f, { resizeWidth: 4096, resizeQuality: 'high' }));
  tries.push(() => new Promise((ok, no) => {
    const u = URL.createObjectURL(f), i = new Image();
    i.onload = () => ok(i); i.onerror = () => { URL.revokeObjectURL(u); no(new Error('image')); }; i.src = u;
  }));
  return tries.reduce((p, t) => p.catch(t), Promise.reject(new Error('image')));
}

/** The part of an [iw] x [ih] photo that fills a [W] x [H] frame at view [v], in the photo's own pixels. */
function photoRect(iw, ih, W, H, v) {
  const z = Math.min(8, Math.max(1, (v && v.z) || 1));
  const k = Math.max(W / iw, H / ih) * z;                              // frame pixels per photo pixel
  const sw = W / k, sh = H / k;
  // the centre is kept where the crop still lies inside the photo, so a drag stops at the edge instead of showing a bar
  const sx = Math.min(iw - sw, Math.max(0, ((v && v.cx != null ? v.cx : 0.5) * iw) - sw / 2));
  const sy = Math.min(ih - sh, Math.max(0, ((v && v.cy != null ? v.cy : 0.5) * ih) - sh / 2));
  return { sx, sy, sw, sh };
}

function photoDraw(ctx, img, W, H, v) {
  if (v && v.whole) { photoWhole(ctx, img, W, H); return; }
  const r = photoRect(img.width, img.height, W, H, v);
  ctx.drawImage(img, r.sx, r.sy, r.sw, r.sh, 0, 0, W, H);
}

/** As TV Home draws a whole photo (Backdrop.photo): itself cropped, blurred and dimmed behind, the photo whole in the middle. */
function photoWhole(ctx, img, W, H) {
  const k = Math.max(W / img.width, H / img.height), sw = W / k, sh = H / k;
  ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';
  if ('filter' in ctx) {                                              // a real blur, a little past the edges (a blur fades them)
    const r = Math.round(W / 40), m = r * 2;
    ctx.filter = 'blur(' + r + 'px)';
    ctx.drawImage(img, (img.width - sw) / 2, (img.height - sh) / 2, sw, sh, -m, -m, W + 2 * m, H + 2 * m);
    ctx.filter = 'none';
  } else {                                                            // Safari before 18: a tiny copy scaled up
    const sm = document.createElement('canvas'); sm.width = Math.max(1, Math.round(W / 16)); sm.height = Math.max(1, Math.round(H / 16));
    sm.getContext('2d').drawImage(img, (img.width - sw) / 2, (img.height - sh) / 2, sw, sh, 0, 0, sm.width, sm.height);
    ctx.drawImage(sm, 0, 0, W, H);
  }
  ctx.fillStyle = 'rgba(0,0,0,.3)'; ctx.fillRect(0, 0, W, H);
  const kc = Math.min(W / img.width, H / img.height), dw = img.width * kc, dh = img.height * kc;
  ctx.drawImage(img, (W - dw) / 2, (H - dh) / 2, dw, dh);
}

/** The JPEG to send: [W] x [H] (the TV's own size), about 0.85 quality; a whole one at its own shape, just covering [W] x [H]. */
function photoBlob(img, W, H, v, q) {
  const c = document.createElement('canvas'); c.width = W; c.height = H;
  if (v && v.whole) {                                                 // the TV crops or fits it itself (wfit): enough pixels to fill the screen either way
    const k = Math.min(1, Math.max(W / img.width, H / img.height));
    c.width = Math.round(img.width * k); c.height = Math.round(img.height * k);
    c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
  } else photoDraw(c.getContext('2d'), img, W, H, v);
  return new Promise((ok, no) => c.toBlob((b) => (b ? ok(b) : no(new Error('jpeg'))), 'image/jpeg', q || 0.85));
}

/** Makes [canvas] a live preview of [img]: drag moves the crop, two fingers or the wheel zoom. Returns the view (read it when sending). */
function photoCrop(canvas, img) {
  const v = { z: 1, cx: 0.5, cy: 0.5 };
  const W = canvas.width, H = canvas.height;
  const draw = () => { if (v.whole) { photoDraw(canvas.getContext('2d'), img, W, H, v); return; } const r = photoRect(img.width, img.height, W, H, v); v.cx = (r.sx + r.sw / 2) / img.width; v.cy = (r.sy + r.sh / 2) / img.height; photoDraw(canvas.getContext('2d'), img, W, H, v); };
  const pts = new Map(); let span = 0;
  const gap = () => { const p = [...pts.values()]; return Math.hypot(p[0].x - p[1].x, p[0].y - p[1].y); };
  canvas.addEventListener('pointerdown', (e) => { canvas.setPointerCapture(e.pointerId); pts.set(e.pointerId, { x: e.clientX, y: e.clientY }); if (pts.size === 2) span = gap(); });
  canvas.addEventListener('pointermove', (e) => {
    const p = pts.get(e.pointerId); if (!p || v.whole) return;      // whole: nothing to move
    const dx = e.clientX - p.x, dy = e.clientY - p.y; p.x = e.clientX; p.y = e.clientY;
    if (pts.size === 2) { const s = gap(); if (span > 0) v.z = Math.min(8, Math.max(1, v.z * s / span)); span = s; }
    else {                                                              // the photo follows the finger: the crop moves the other way
      const r = photoRect(img.width, img.height, W, H, v), per = r.sw / (canvas.clientWidth || W);
      v.cx -= dx * per / img.width; v.cy -= dy * per / img.height;
    }
    draw();
  });
  const up = (e) => { pts.delete(e.pointerId); span = 0; };
  canvas.addEventListener('pointerup', up); canvas.addEventListener('pointercancel', up);
  canvas.addEventListener('wheel', (e) => { if (v.whole) return; e.preventDefault(); v.z = Math.min(8, Math.max(1, v.z * Math.exp(-e.deltaY / 400))); draw(); }, { passive: false });
  draw();
  v.redraw = draw;
  return v;
}

if (typeof module !== 'undefined') module.exports = { photoRect };
