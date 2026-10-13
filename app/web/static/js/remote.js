// remote keys, keyboard and mouse passthrough
// ---------- keys ----------
// Tap = one press. Hold (0.4 s) = the TV sees the key held down until you let go: fast scrolling, long-press menus.
document.querySelectorAll('[data-k]').forEach((b) => {
  const k = b.dataset.k; let t = null, long = false, down = false;
  const power = k === 'POWER';                                     // never a long press, and turning the TV off needs a yes
  const send = (dir) => {
    if (power && tvOn && !confirm('Đưa TV về chế độ chờ?\nNếu TV tắt cả mạng thì chỉ mở lại được bằng nút Đánh thức (cần một máy ở nhà đang bật) hoặc remote của TV.')) return;
    return api('/api/key', dir ? { key: k, dir } : { key: k });
  };
  const release = (tap) => {
    if (!down) return; down = false; clearTimeout(t);
    if (long) { long = false; b.classList.remove('held'); send('END_LONG'); } else if (tap) send();
    setTimeout(refresh, 350);
  };
  b.addEventListener('pointerdown', (e) => {
    if (e.button) return; down = true; long = false; try { b.setPointerCapture(e.pointerId); } catch {}
    if (!power) t = setTimeout(() => { long = true; b.classList.add('held'); send('START_LONG'); }, 400);
  });
  b.addEventListener('pointerup', () => { release(true); b.blur(); });           // keep focus off the button so typing goes to the TV
  ['pointercancel', 'lostpointercapture'].forEach((ev) => b.addEventListener(ev, () => release(false)));
  b.addEventListener('click', (e) => { if (e.detail === 0) send(); });                          // keyboard activation
  b.addEventListener('contextmenu', (e) => e.preventDefault());
});
$('#volr').addEventListener('input', () => { volHold = Date.now() + 4000; $('#volv').textContent = $('#volr').value; });
$('#volr').addEventListener('change', async () => {
  volHold = Date.now() + 20000;
  await api('/api/volume', { level: +$('#volr').value });
  volHold = Date.now() + 600; setTimeout(refresh, 700);
});
function normalizeLink(v) {
  v = v.trim(); if (!v) return '';
  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(v)) return v;
  return 'https://' + v;
}
async function openOnTv(link, label) {
  const r = await api('/api/app', { link });
  if (r) { toast(r.via === 'adb' ? 'Đã mở ' + label : 'Đã gửi lệnh, nhưng ADB đang đóng nên TV có thể bỏ qua'); setTimeout(refresh, 1500); }
}
$('#linkf').addEventListener('submit', (e) => { e.preventDefault(); const l = normalizeLink($('#link').value); if (l) openOnTv(l, 'link'); });
// ---------- keyboard: whatever is typed in the box is mirrored on the TV, keystroke by keystroke ----------
const txt = $('#text');
let typed = '', inputQ = Promise.resolve();
const queue = (path, data) => { inputQ = inputQ.then(() => api(path, data)); };   // one at a time, in order
function syncText() {                                                 // send only the difference: backspaces, then new characters
  const v = txt.value; let i = 0;
  while (i < typed.length && i < v.length && typed[i] === v[i]) i++;
  const del = [...typed.slice(i)].length, text = v.slice(i); typed = v;
  if (del || text) queue('/api/type', { del, text });
}
txt.addEventListener('input', (e) => { if (!e.isComposing) syncText(); });
txt.addEventListener('compositionend', syncText);                     // Vietnamese / IME input arrives when the syllable is done
$('#textf').addEventListener('submit', (e) => { e.preventDefault(); syncText(); queue('/api/type', { enter: true }); txt.value = typed = ''; });
$('#del').addEventListener('click', () => { if (txt.value) { txt.value = [...txt.value].slice(0, -1).join(''); syncText(); } else queue('/api/type', { del: 1 }); });

// ---------- mouse: click, drag and scroll on the picture act as a finger on the TV screen ----------
const scr = $('#screen'); scr.draggable = false;
function screenPos(e) {                                               // pointer -> 0..1 inside the picture (the img letterboxes it)
  const r = scr.getBoundingClientRect(), nw = scr.naturalWidth || 16, nh = scr.naturalHeight || 9;
  const k = Math.min(r.width / nw, r.height / nh), w = nw * k, h = nh * k;
  const x = (e.clientX - r.left - (r.width - w) / 2) / w, y = (e.clientY - r.top - (r.height - h) / 2) / h;
  return x < 0 || x > 1 || y < 0 || y > 1 ? null : { x, y };
}
let touching = false, lastMove = 0, lastWheel = 0, lastPos = { x: .5, y: .5 };
const touch = (a, p, extra) => queue('/api/touch', { a, x: p.x, y: p.y, ...extra });
scr.addEventListener('pointerdown', (e) => {
  const p = screenPos(e); if (e.button || !p) return;
  e.preventDefault(); touching = true; lastPos = p; try { scr.setPointerCapture(e.pointerId); } catch {} touch('down', p);
});
scr.addEventListener('pointermove', (e) => {
  const p = screenPos(e); if (!touching || !p || Date.now() - lastMove < 40) return;
  lastMove = Date.now(); lastPos = p; touch('move', p);
});
['pointerup', 'pointercancel'].forEach((ev) => scr.addEventListener(ev, (e) => { if (touching) { touching = false; touch('up', screenPos(e) || lastPos); } }));
scr.addEventListener('wheel', (e) => {
  const p = screenPos(e); if (!p) return;
  e.preventDefault(); if (Date.now() - lastWheel < 90) return;
  lastWheel = Date.now(); touch('scroll', p, { dy: e.deltaY > 0 ? -1 : 1 });
}, { passive: false });
scr.addEventListener('contextmenu', (e) => { e.preventDefault(); api('/api/key', { key: 'BACK' }); });   // right click = Back
$('#wake').addEventListener('click', async () => {
  const b = $('#wake'); b.disabled = true; toast('Đang đánh thức TV...');
  const r = await api('/api/wake', {});
  if (r) { toast(r.ok ? 'TV đã sẵn sàng' : 'TV chưa lên. Kiểm tra “Khởi động từ xa” trên TV và laptop ở nhà còn bật'); refresh(); }
  b.disabled = false;
});
// The computer keyboard is the TV's keyboard on the Remote tab, no need to click into the box first:
// letters go to the TV's text field, Backspace/Delete erase, arrows move, Enter confirms, Esc = Back.
const keymap = { ArrowUp:'UP', ArrowDown:'DOWN', ArrowLeft:'LEFT', ArrowRight:'RIGHT', Escape:'BACK', Home:'HOME' };
const typingHere = () => ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName);
document.addEventListener('keydown', (e) => {
  if (currentTab !== 'remote' || typingHere() || e.metaKey || e.ctrlKey || e.altKey || e.isComposing || $('#preview').open || $('#about').open) return;
  const onButton = ['BUTTON', 'SUMMARY', 'A'].includes(document.activeElement.tagName);
  if (onButton && (e.key === 'Enter' || e.key === ' ' || e.key === 'Tab')) return;   // keyboard users still operate the page
  if (e.key === 'Escape' && document.fullscreenElement) return;       // let Esc leave fullscreen
  const adb = ADB.state === 'ready';
  if (keymap[e.key]) { e.preventDefault(); api('/api/key', { key: keymap[e.key] }); }
  else if (e.key === 'Enter') { e.preventDefault(); if (adb) queue('/api/type', { enter: true }); else api('/api/key', { key: 'OK' }); }
  else if (e.key === 'Backspace') { e.preventDefault(); if (adb) queue('/api/type', { del: 1 }); else api('/api/key', { key: 'DEL' }); }
  else if (e.key === 'Delete') { e.preventDefault(); queue('/api/type', { fdel: 1 }); }
  else if (e.key.length === 1) { e.preventDefault(); queue('/api/type', { text: e.key }); }
});
document.addEventListener('paste', (e) => {                           // Cmd+V anywhere on the Remote tab pastes onto the TV
  if (currentTab !== 'remote' || typingHere()) return;
  const t = (e.clipboardData || window.clipboardData).getData('text'); if (t) { e.preventDefault(); queue('/api/type', { text: t.slice(0, 500) }); }
});


// ---------- ask the TV: typed or spoken here, handed to the TV's own search / Assistant ----------
async function askTv(q) { q = q.trim(); if (q && await api('/api/search', { query: q })) { toast('Đã gửi cho TV: ' + q); setTimeout(refresh, 2500); } }
$('#askf').addEventListener('submit', (e) => { e.preventDefault(); askTv($('#ask').value); $('#ask').value = ''; });
// speech is turned into text by the browser (Chrome), not by the TV
const Speech = window.SpeechRecognition || window.webkitSpeechRecognition;
if (Speech) {
  const b = $('#speak'); let rec = null; b.hidden = false;
  b.addEventListener('click', () => {
    if (rec) { rec.stop(); return; }
    rec = new Speech(); rec.lang = LANG === 'vi' ? 'vi-VN' : 'en-US'; rec.interimResults = true;
    rec.onresult = (e) => { const r = e.results[e.results.length - 1]; $('#ask').value = r[0].transcript; if (r.isFinal) { askTv(r[0].transcript); $('#ask').value = ''; } };
    rec.onerror = (e) => toast('Lỗi: ' + (e.error === 'not-allowed' ? 'trình duyệt chưa được phép dùng micro' : e.error));
    rec.onend = () => { rec = null; b.classList.remove('on'); };
    b.classList.add('on'); rec.start();
  });
}
