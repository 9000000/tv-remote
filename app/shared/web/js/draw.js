// ✏️ Drawing on the TV's screen over the picture here: TV Setup draws the same strokes over whatever is on the TV, for the
// person watching (DrawLayer.kt there, Draw.kt for the way strokes go). Points are sent in 0..10000 of the picture's size.
// What is drawn here fades soon after the finger lifts: by then the TV's picture shows it, from the TV itself.
const DRAW_COLORS = ['#ff3b30', '#ffd60a', '#32d74b', '#40c8ff', '#ffffff'];
let drawing = false, drawColor = DRAW_COLORS[0], drawWidth = 60, drawId = 0, drawQ = [], drawSending = false, drawPen = null;
const drawLocal = [];                                                  // {pts, color, width, upAt}: drawn here until they fade

function drawRect() {                                                  // where the picture is inside the box (it letterboxes)
  const box = $('#screenbox').getBoundingClientRect(), m = !$('#screenv').hidden ? $('#screenv') : $('#screen');
  const nw = m.naturalWidth || m.videoWidth || 16, nh = m.naturalHeight || m.videoHeight || 9;
  const k = Math.min(box.width / nw, box.height / nh), w = nw * k, h = nh * k;
  return { box, x: (box.width - w) / 2, y: (box.height - h) / 2, w, h };
}

function drawSize() {
  const c = $('#drawcv'), r = $('#screenbox').getBoundingClientRect(), d = window.devicePixelRatio || 1;
  if (c.width !== Math.round(r.width * d) || c.height !== Math.round(r.height * d)) { c.width = Math.round(r.width * d); c.height = Math.round(r.height * d); }
}

function drawPaint() {
  const c = $('#drawcv'), g = c.getContext('2d'), d = window.devicePixelRatio || 1, r = drawRect(), now = Date.now();
  drawSize();
  g.setTransform(d, 0, 0, d, 0, 0); g.clearRect(0, 0, c.width, c.height);
  g.lineCap = 'round'; g.lineJoin = 'round';
  for (let i = drawLocal.length - 1; i >= 0; i--) if (drawLocal[i].upAt && now - drawLocal[i].upAt > 1600) drawLocal.splice(i, 1);
  for (const s of drawLocal) {
    if (!s.pts.length) continue;
    g.globalAlpha = s.upAt ? Math.max(0, 1 - (now - s.upAt) / 1600) : 1;
    g.strokeStyle = s.color; g.lineWidth = Math.max(2, s.width / 10000 * r.w);
    g.beginPath();
    s.pts.forEach(([x, y], i) => { const px = r.x + x / 10000 * r.w, py = r.y + y / 10000 * r.h; if (i) g.lineTo(px, py); else { g.moveTo(px, py); g.lineTo(px + .1, py); } });
    g.stroke();
  }
  g.globalAlpha = 1;
  if (drawLocal.some((s) => s.upAt)) requestAnimationFrame(drawPaint);
}

// One request at a time; what comes meanwhile goes in the next, so a slow link never falls behind the finger.
function drawSend(op) {
  const last = drawQ[drawQ.length - 1];
  if (op.o === 'a' && last && last.o !== 'u' && last.o !== 'x' && last.id === op.id) last.p.push(...op.p); else drawQ.push(op);
  if (!drawSending) drawFlush();
}
async function drawFlush() {
  drawSending = true;
  while (drawQ.length && drawing) {
    const ops = drawQ; drawQ = [];
    const r = await fetch('/api/draw', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ops }) }).catch(() => null);
    if (!r || !r.ok) {
      const why = r ? await r.text().catch(() => '') : '';
      drawStop();
      toast(/draw:old/.test(why) ? 'TV Setup trên TV cần bản mới hơn để vẽ: cập nhật TV Setup rồi thử lại.'
        : /draw:missing/.test(why) ? 'Cần cài TV Setup lên TV để vẽ lên màn hình.'
        : /draw:nooverlay/.test(why) ? 'TV này không cho app vẽ đè lên màn hình.'
        : 'Không vẽ được lên TV: ' + friendly(why || 'mất kết nối'));
      break;
    }
  }
  drawSending = false;
}

function drawStart() {
  if (!screenOn) startScreen();                                        // the strokes go where the picture shows
  drawing = true; drawQ = [];
  $('#drawcv').hidden = false; $('#drawbar').hidden = false; $('#drawbtn').classList.add('primary');
  drawSize();
}
function drawStop() {
  drawing = false; drawPen = null; drawLocal.length = 0;
  $('#drawcv').hidden = true; $('#drawbar').hidden = true; $('#drawbtn').classList.remove('primary');
}
$('#drawbtn').addEventListener('click', () => { if (drawing) drawStop(); else drawStart(); });
$('#drawdone').addEventListener('click', drawStop);
$('#drawundo').addEventListener('click', () => drawSend({ o: 'u' }));
$('#drawclear').addEventListener('click', () => { drawLocal.length = 0; drawPaint(); drawSend({ o: 'x' }); });
document.querySelectorAll('#drawbar [data-color]').forEach((b) => b.addEventListener('click', () => {
  drawColor = b.dataset.color;
  document.querySelectorAll('#drawbar [data-color]').forEach((x) => x.classList.toggle('on', x === b));
}));
$('#drawthick').addEventListener('click', () => { drawWidth = drawWidth === 60 ? 140 : 60; $('#drawthick').classList.toggle('on', drawWidth > 60); });

const drawAt = (e) => {
  const r = drawRect(), x = (e.clientX - r.box.left - r.x) / r.w, y = (e.clientY - r.box.top - r.y) / r.h;
  return x < 0 || x > 1 || y < 0 || y > 1 ? null : [Math.round(x * 10000), Math.round(y * 10000)];
};
$('#drawcv').addEventListener('pointerdown', (e) => {
  const p = drawAt(e); if (e.button || !p) return;
  e.preventDefault(); try { $('#drawcv').setPointerCapture(e.pointerId); } catch {}
  drawPen = { id: ++drawId, pts: [p], color: drawColor, width: drawWidth, upAt: 0 };
  drawLocal.push(drawPen); drawPaint();
  drawSend({ o: 's', id: drawPen.id, c: drawColor, w: drawWidth, p: [...p] });
});
$('#drawcv').addEventListener('pointermove', (e) => {
  if (!drawPen) return;
  const p = drawAt(e); if (!p) return;
  const [lx, ly] = drawPen.pts[drawPen.pts.length - 1];
  if (Math.abs(p[0] - lx) + Math.abs(p[1] - ly) < 25) return;            // a few pixels on the TV: not worth a point
  drawPen.pts.push(p); drawPaint();
  drawSend({ o: 'a', id: drawPen.id, p: [...p] });
});
['pointerup', 'pointercancel'].forEach((ev) => $('#drawcv').addEventListener(ev, () => { if (drawPen) { drawPen.upAt = Date.now(); drawPen = null; drawPaint(); } }));
window.addEventListener('resize', () => { if (drawing) drawPaint(); });
