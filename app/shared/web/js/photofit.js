// A photo made the TV's shape: the crop (centred cover by default, moved by drag, widened by pinch or wheel), drawn small for a
// preview and at the TV's size for sending. ONE copy for the page's "Tải ảnh của bạn" (js/home.js) and for the page TV Home
// serves to a phone (app/home/.../PhotoServer.kt; its build copies this file, so the two never differ).
// A view is { z, cx, cy }: zoom (1 = the photo just covers the frame) and the crop's centre as fractions of the photo.

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
  const r = photoRect(img.width, img.height, W, H, v);
  ctx.drawImage(img, r.sx, r.sy, r.sw, r.sh, 0, 0, W, H);
}

/** The JPEG to send: [W] x [H] (the TV's own size), about 0.85 quality. */
function photoBlob(img, W, H, v, q) {
  const c = document.createElement('canvas'); c.width = W; c.height = H;
  photoDraw(c.getContext('2d'), img, W, H, v);
  return new Promise((ok, no) => c.toBlob((b) => (b ? ok(b) : no(new Error('jpeg'))), 'image/jpeg', q || 0.85));
}

/** Makes [canvas] a live preview of [img]: drag moves the crop, two fingers or the wheel zoom. Returns the view (read it when sending). */
function photoCrop(canvas, img) {
  const v = { z: 1, cx: 0.5, cy: 0.5 };
  const W = canvas.width, H = canvas.height;
  const draw = () => { const r = photoRect(img.width, img.height, W, H, v); v.cx = (r.sx + r.sw / 2) / img.width; v.cy = (r.sy + r.sh / 2) / img.height; photoDraw(canvas.getContext('2d'), img, W, H, v); };
  const pts = new Map(); let span = 0;
  const gap = () => { const p = [...pts.values()]; return Math.hypot(p[0].x - p[1].x, p[0].y - p[1].y); };
  canvas.addEventListener('pointerdown', (e) => { canvas.setPointerCapture(e.pointerId); pts.set(e.pointerId, { x: e.clientX, y: e.clientY }); if (pts.size === 2) span = gap(); });
  canvas.addEventListener('pointermove', (e) => {
    const p = pts.get(e.pointerId); if (!p) return;
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
  canvas.addEventListener('wheel', (e) => { e.preventDefault(); v.z = Math.min(8, Math.max(1, v.z * Math.exp(-e.deltaY / 400))); draw(); }, { passive: false });
  draw();
  return v;
}

if (typeof module !== 'undefined') module.exports = { photoRect };
