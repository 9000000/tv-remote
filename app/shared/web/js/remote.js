// remote keys, keyboard and mouse passthrough
// ---------- keys ----------
// A tap counts when the pointer lifts on the key (slide off it and nothing is sent). Hold (0.4 s) = the TV sees the key held down until you let go: fast scrolling, long-press menus.
document.querySelectorAll('[data-k]').forEach((b) => {
  const k = b.dataset.k; let t = null, long = false, down = false;
  const power = k === 'POWER';                                     // never a long press, and turning the TV off needs a yes
  const send = (dir) => {
    if (power && tvOn && !confirm('Đưa TV về chế độ chờ?\nNếu TV tắt cả mạng thì chỉ mở lại được bằng nút Đánh thức (cần một máy ở nhà đang bật) hoặc remote của TV.')) return;
    return api('/api/key', dir ? { key: k, dir } : { key: k }).then((r) => { if (r && r.noop) toast('TV đã nhận nút này, nhưng màn hình hiện tại không phản hồi'); return r; });
  };
  const release = (tap) => {
    if (!down) return; down = false; clearTimeout(t);
    b.classList.remove('held');
    if (long) { long = false; send('END_LONG'); } else if (tap) { flash(); send(); }
    setTimeout(refresh, 350);
  };
  const inside = (e) => { const r = b.getBoundingClientRect(); return e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom; };
  const flash = () => { b.classList.add('held'); setTimeout(() => { if (!long) b.classList.remove('held'); }, 160); };
  b.addEventListener('pointerdown', (e) => {
    if (e.button) return; down = true; long = false; try { b.setPointerCapture(e.pointerId); } catch {}
    if (!power) t = setTimeout(() => { long = true; b.classList.add('held'); send('START_LONG'); }, 400);
  });
  b.addEventListener('pointermove', (e) => { if (down && !inside(e)) release(false); });
  b.addEventListener('pointerup', (e) => { release(inside(e)); b.blur(); });           // keep focus off the button so typing goes to the TV
  ['pointercancel', 'lostpointercapture'].forEach((ev) => b.addEventListener(ev, () => release(false)));
  b.addEventListener('click', (e) => { if (e.detail === 0) send(); });                          // keyboard activation
  b.addEventListener('contextmenu', (e) => e.preventDefault());
});
function normalizeLink(v) {
  v = v.trim(); if (!v) return '';
  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(v)) return v;
  return 'https://' + v;
}
async function openOnTv(link, label) {
  const r = await api('/api/app', { link });
  if (r) { toast(r.via === 'adb' ? 'Đã mở ' + label : 'Đã gửi cho TV (không qua ADB: vài TV bỏ qua lệnh này)'); setTimeout(refresh, 1500); }
}
$('#linkf').addEventListener('submit', (e) => { e.preventDefault(); const l = normalizeLink($('#link').value); if (l) openOnTv(l, 'link'); });
// The Play Store on the TV, without its keyboard: an app id opens that app's page, anything else is searched for.
$('#storef').addEventListener('submit', (e) => {
  e.preventDefault(); const v = $('#store').value.trim(); if (!v) return;
  const id = /^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/i.test(v);
  // the TV's Play Store takes a search only as market://search (a play.google.com search link ends in a browser error)
  openOnTv(id ? 'https://play.google.com/store/apps/details?id=' + v : 'market://search?c=apps&q=' + encodeURIComponent(v), 'CH Play');
});
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
// Two ways to type, as in the phone app. Live (⚡ on): the TV follows every letter. Send: write first, then send it in one go.
let live = true;
function setLive(on) {
  live = on; try { localStorage.setItem('live', on ? '1' : '0'); } catch {}
  $('#live').classList.toggle('on', on);
  txt.placeholder = tr(on ? 'Gõ chữ lên TV' : 'Gõ xong bấm ↵ để gửi'); txt.value = typed = '';
}
$('#live').addEventListener('click', () => setLive(!live));
txt.addEventListener('input', (e) => { if (live && !e.isComposing) syncText(); });
txt.addEventListener('compositionend', () => live && syncText());   // Vietnamese / IME input arrives when the syllable is done
$('#textf').addEventListener('submit', (e) => {
  e.preventDefault();
  if (live) { syncText(); queue('/api/type', { enter: true }); } else if (txt.value) queue('/api/type', { text: txt.value });
  txt.value = typed = '';
});
$('#del').addEventListener('click', () => { if (txt.value) { txt.value = [...txt.value].slice(0, -1).join(''); if (live) syncText(); } else queue('/api/type', { del: 1 }); });
function setMore(on) { $('#more').hidden = !on; $('#morebtn').classList.toggle('on', on); try { localStorage.setItem('more', on ? '1' : ''); } catch {} }
try { setMore(!!localStorage.getItem('more')); } catch {}
$('#morebtn').addEventListener('click', () => setMore($('#more').hidden));
$('#shot').addEventListener('click', () => { const a = document.createElement('a'); a.href = '/api/screen'; a.download = 'tv-' + Math.floor(Date.now() / 1000) + (CORE === 'mac' ? '.jpg' : '.png'); a.click(); });
$('#digits').addEventListener('click', () => { const p = $('#numpad'); p.hidden = !p.hidden; try { localStorage.setItem('numpad', p.hidden ? '' : '1'); } catch {} });
try { setLive(localStorage.getItem('live') !== '0'); $('#numpad').hidden = !localStorage.getItem('numpad'); } catch { setLive(true); }

// ---------- mouse: click, drag and scroll on the picture act as a finger on the TV screen ----------
// The picture is an <img> (frames from the computer) or a <video> (H.264 straight from the TV): both take the same gestures.
let touching = false, lastMove = 0, lastWheel = 0, lastPos = { x: .5, y: .5 };
const touch = (a, p, extra) => queue('/api/touch', { a, x: p.x, y: p.y, ...extra });
[$('#screen'), $('#screenv')].forEach((scr) => {
  scr.draggable = false;
  const screenPos = (e) => {                                          // pointer -> 0..1 inside the picture (the box letterboxes it)
    const r = scr.getBoundingClientRect(), nw = scr.naturalWidth || scr.videoWidth || 16, nh = scr.naturalHeight || scr.videoHeight || 9;
    const k = Math.min(r.width / nw, r.height / nh), w = nw * k, h = nh * k;
    const x = (e.clientX - r.left - (r.width - w) / 2) / w, y = (e.clientY - r.top - (r.height - h) / 2) / h;
    return x < 0 || x > 1 || y < 0 || y > 1 ? null : { x, y };
  };
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
});
$('#wake').addEventListener('click', async () => {
  const b = $('#wake'); b.disabled = true; toast('Đang đánh thức TV...');
  const r = await api('/api/wake', {});
  if (r) { toast(r.ok ? 'TV đã sẵn sàng' : 'TV chưa lên. Kiểm tra “Khởi động từ xa” trên TV và máy ở nhà còn bật'); refresh(); }
  b.disabled = false;
});
// The computer keyboard is the TV's keyboard on the Remote tab, no need to click into the box first:
// letters go to the TV's text field, Backspace/Delete erase, arrows move, Enter confirms, Esc = Back.
const keymap = { ArrowUp:'UP', ArrowDown:'DOWN', ArrowLeft:'LEFT', ArrowRight:'RIGHT', Escape:'BACK', Home:'HOME' };
const typingHere = () => ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName);
document.addEventListener('keydown', (e) => {
  if (currentTab !== 'remote' || picking || typingHere() || e.metaKey || e.ctrlKey || e.altKey || e.isComposing || $('#preview').open || $('#about').open || $('#assist').open || $('#ts').open) return;
  const onButton = ['BUTTON', 'SUMMARY', 'A'].includes(document.activeElement.tagName);
  if (onButton && (e.key === 'Enter' || e.key === ' ' || e.key === 'Tab')) return;   // keyboard users still operate the page
  if (e.key === 'Escape' && document.fullscreenElement) return;       // let Esc leave fullscreen
  const adb = ADB.state === 'ready';
  if (keymap[e.key]) { e.preventDefault(); api('/api/key', { key: keymap[e.key] }); }
  else if (e.key === 'Enter') { e.preventDefault(); if (adb) queue('/api/type', { enter: true }); else api('/api/key', { key: 'OK' }); }
  else if (e.key === 'Backspace') { e.preventDefault(); if (adb) queue('/api/type', { del: 1 }); else api('/api/key', { key: 'DEL' }); }
  else if (!adb) return;                                              // letters need ADB
  else if (e.key === 'Delete') { e.preventDefault(); queue('/api/type', { fdel: 1 }); }
  else if (e.key.length === 1) { e.preventDefault(); queue('/api/type', { text: e.key }); }
});
document.addEventListener('paste', (e) => {                           // Cmd+V anywhere on the Remote tab pastes onto the TV
  if (currentTab !== 'remote' || typingHere() || ADB.state !== 'ready') return;
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
