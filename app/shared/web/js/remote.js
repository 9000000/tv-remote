// remote keys, keyboard and mouse passthrough
// ---------- the rows of keys the page draws from remote.json (the same file the phone app's remote reads) ----------
// A key is bound below like the hand-made ones (data-k); an action ("do") runs its REMOTE_DO handler (remote_spec.js).
document.querySelectorAll('[data-rows]').forEach((box) => {
  for (const row of REMOTE.rows[box.dataset.rows] || []) for (const raw of row.filter(remoteHere)) {
    const e = remoteEntry(raw);
    const b = el('button', { className: 'rb lab' + (e.style ? ' ' + e.style : ''), id: 'r-' + e.id, innerHTML: '<svg class="ic" aria-hidden="true"><use href="#i-' + e.icon + '"/></svg><small></small>' });
    b.querySelector('small').textContent = remoteWords(e);
    if (e.key) b.dataset.k = e.key; else b.addEventListener('click', () => REMOTE_DO[e.do](e.arg));
    const needs = [...e.needs || [], ...remoteRole(e)];
    if (needs.length) b.dataset.needs = needs.join(' ');              // greyed on a TV that cannot, or for a guest (applyKind in core.js)
    box.append(b);
  }
});
// ---------- keys ----------
// A tap counts when the pointer lifts on the key (slide off it and nothing is sent). Hold (0.4 s) = the TV sees the key held down until you let go: fast scrolling, long-press menus.
document.querySelectorAll('[data-k]').forEach((b) => {
  const k = b.dataset.k; let t = null, long = false, down = false;
  const power = k === 'POWER';                                     // never a long press, and turning the TV off needs a yes
  const send = async (dir) => {
    if (power && tvOn && !await ask('Đưa TV về chế độ chờ?\nNếu TV tắt cả mạng thì chỉ mở lại được bằng nút Đánh thức (cần một máy ở nhà đang bật) hoặc remote của TV.', { ok: 'Tắt TV' })) return;
    return api('/api/key', dir ? { key: k, dir } : { key: k }).then((r) => { if (r && r.noop) toast('TV đã nhận nút này, nhưng màn hình hiện tại không phản hồi'); return r; });
  };
  const release = (tap) => {
    if (!down) return; down = false; clearTimeout(t);
    b.classList.remove('held');
    if (long) { long = false; send('END_LONG'); } else if (tap) { buzz(); flash(); send(); }
    setTimeout(refresh, 350);
  };
  const inside = (e) => { const r = b.getBoundingClientRect(); return e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom; };
  const flash = () => { b.classList.add('held'); setTimeout(() => { if (!long) b.classList.remove('held'); }, 160); };
  b.addEventListener('pointerdown', (e) => {
    if (e.button || b.disabled) return; down = true; long = false; try { b.setPointerCapture(e.pointerId); } catch {}   // disabled: a key this TV does not have (applyKind)
    if (!power) t = setTimeout(() => { long = true; buzz(); b.classList.add('held'); send('START_LONG'); }, 400);
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
  if (r) { toast(r.via === 'adb' || NATIVE() ? 'Đã mở ' + label : 'Đã gửi cho TV (không qua ADB: vài TV bỏ qua lệnh này)'); setTimeout(refresh, 1500); }
}
// ---------- "Thêm phím", third row: Mở trên TV, Hẹn giờ tắt, Cảnh, Vẽ lên TV; their menus' entries are in remote.json ----------
// The same menus as the phone app's remote (RemoteScreen.kt, PhoneRemote.kt); the entries live in remote.json, what they do here in REMOTE_DO.
const needAdb = () => { if (ADB.state === 'ready') return false; toast(NATIVE() ? kindWhy() : 'Việc này cần ADB. Bật ADB cho TV này rồi thử lại.'); return true; };
const storePage = (id) => openOnTv('https://play.google.com/store/apps/details?id=' + id, 'CH Play');
// the TV's Play Store takes a search only as market://search (a play.google.com search link ends in a browser error)
const storeSearch = (q) => openOnTv('market://search?c=apps&q=' + encodeURIComponent(q), 'CH Play');
const castPick = (accept) => { if (needAdb()) return; const f = $('#castfile'); f.accept = accept; f.click(); };   // the picker must open within the tap
async function askStoreSearch() { const v = await ask('Tìm app trên CH Play', { value: '', ok: 'Tìm' }); if (v && v.trim()) storeSearch(v.trim()); }
async function askStorePage() { const v = await ask('App id (tên gói)', { value: 'org.videolan.vlc', ok: 'Mở' }); if (v && v.trim()) storePage(v.trim()); }
async function askLink() { const v = await ask('Đường dẫn', { value: 'https://youtu.be/', ok: 'Mở' }); const l = v && normalizeLink(v); if (l) openOnTv(l, 'link'); }
function screenshot() { const a = document.createElement('a'); a.href = '/api/screen'; a.download = 'tv-' + Math.floor(Date.now() / 1000) + '.png'; a.click(); }
async function timerPlus() { const x = await loadRules({ do: 'sleep_add', minutes: 15 }); if (x) toast('TV sẽ tự tắt lúc ' + clock(x.sleep_at)); }
async function timerCustom() {
  const v = await ask('Tắt TV sau bao lâu?', { value: '', ok: 'Hẹn' }); if (v === null) return;
  const m = sleepMinutes(v); if (m) setSleep(m); else toast('Nhập số phút (vd 40) hoặc giờ tắt (vd 23:30)');
}
const quickOpen = () => menuDlg('Mở trên TV', -1, remoteMenu('open', { expand: { tvapps: () => quickApps.slice().sort((a, b) => appName(a).localeCompare(appName(b))).map((p) => ['@play|' + appName(p), '', () => openOnTv(p, appName(p))]) } }));   // LG / Samsung: their apps first
async function timerMenu() {
  if (NATIVE()) { menuDlg('Màn hình TV', -1, remoteMenu('timer')); return; }   // LG: the screen off only; the timer lives in TV Setup
  if (needAdb() || !await setupFor('rules')) return;                  // TV Setup runs the timer on the TV (SetupNeed "rules")
  const r = await loadRules(null, true); if (!r) return;
  const at = r.sleep_at || 0, hm = (m) => m < 60 ? m + ' phút' : Math.floor(m / 60) + ' giờ' + (m % 60 ? ' 30' : '');
  const items = remoteMenu('timer', { when: { timerOn: !!at }, expand: { lengths: () => [15, 30, 45, 60, 90, 120].map((m) => ['Tắt sau ' + hm(m), '', () => setSleep(m)]) } });
  menuDlg(at ? 'TV tự tắt lúc ' + clock(at) : 'Hẹn giờ tắt TV', at ? 0 : -1, items);
}
// "Ngõ vào": the TV's real inputs by name ("HDMI 1 · PlayStation") and a tap to switch. A TV with none to list (a box, the emulator,
// Samsung) or no ADB: one short line, and the TV's own picker opens, as the key always did.
const pressInput = () => api('/api/key', { key: 'INPUT' });
async function inputsMenu() {
  if (!can('inputs')) return pressInput();
  const r = await api('/api/inputs', null, true), list = (r && r.inputs) || [];
  if (!list.length) { toast(r ? 'TV này không có ngõ vào để chọn' : 'Chưa đọc được ngõ vào của TV'); return pressInput(); }
  menuDlg('Ngõ vào', -1, [...list.map((i) => ['@input|' + i.label, i.connected === false ? 'Chưa cắm' : '', () => switchTo(i)]), ['@menu|Bảng chọn của TV', '', pressInput]]);
}
async function switchTo(i) {
  const r = await api('/api/input', { id: i.id });
  if (r) { toast('Đã chuyển sang ' + i.label); setTimeout(refresh, 1500); }
}
function scenesMenu() {
  if (!NATIVE() && needAdb()) return;                                 // LG / Samsung run scenes by their remote link
  menuDlg('Cảnh', -1, remoteMenu('scenes', { expand: { scenes: () => SCENES.map((sc) => [((sc.icon || '') + '  ' + sc.name).trim(), '', () => runScene(sc)]) } }));
}
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
function setMore(on, now) { if (now) $('#more').hidden = !on; else slide($('#more'), on); $('#morebtn').classList.toggle('on', on); try { localStorage.setItem('more', on ? '1' : ''); } catch {} }
try { setMore(!!localStorage.getItem('more'), true); } catch {}
$('#morebtn').addEventListener('click', () => setMore(!$('#morebtn').classList.contains('on')));
$('#digits').addEventListener('click', () => { const p = $('#numpad'), on = p.hidden || p.classList.contains('shutting'); slide(p, on); try { localStorage.setItem('numpad', on ? '1' : ''); } catch {} });
try { setLive(localStorage.getItem('live') !== '0'); $('#numpad').hidden = !localStorage.getItem('numpad'); } catch { setLive(true); }

// ---------- mouse: click, drag and scroll on the picture act as a finger on the TV screen ----------
// The picture is an <img> (frames from the computer) or a <video> (H.264 straight from the TV): both take the same gestures.
let touching = false, lastMove = 0, lastWheel = 0, lastPos = { x: .5, y: .5 }, tapDot = null;
// Where the finger landed: a dot that follows a drag and fades out in a ring when let go, so a click on a still picture shows.
function tapMark(e, what) {
  const box = $('#screenbox').getBoundingClientRect(), at = (m) => { m.style.left = (e.clientX - box.left) + 'px'; m.style.top = (e.clientY - box.top) + 'px'; };
  if (what === 'move') { if (tapDot) at(tapDot); return; }
  if (what === 'down') { if (tapDot) tapDot.remove(); tapDot = el('div', { className: 'tap' }); at(tapDot); $('#screenbox').append(tapDot); return; }
  const m = tapDot; if (!m) return; tapDot = null;
  m.classList.add('gone'); m.addEventListener('animationend', () => m.remove());
}
// Moves are only worth their latest position: a slow link keeps one waiting move and updates it, so the TV never replays a drag
// seconds after the finger lifted. Scrolls past a few waiting ones are dropped for the same reason.
let moveNext = null, waiting = 0;
function touch(a, p, extra) {
  const m = { a, x: p.x, y: p.y, ...extra };
  if (a === 'move') { const had = moveNext; moveNext = m; if (!had) inputQ = inputQ.then(() => { const n = moveNext; moveNext = null; return api('/api/touch', n); }); return; }
  if (a === 'scroll' && waiting > 2) return;
  waiting++; inputQ = inputQ.then(() => api('/api/touch', m)).finally(() => { waiting--; });
}
[$('#screen'), $('#screenv'), $('#screenc')].forEach((scr) => {
  scr.draggable = false;
  const screenPos = (e) => {                                          // pointer -> 0..1 inside the picture (the box letterboxes it)
    const r = scr.getBoundingClientRect(), [nw, nh] = picSize(scr).map((v, i) => v || [16, 9][i]);
    const k = Math.min(r.width / nw, r.height / nh), w = nw * k, h = nh * k;
    const x = (e.clientX - r.left - (r.width - w) / 2) / w, y = (e.clientY - r.top - (r.height - h) / 2) / h;
    return x < 0 || x > 1 || y < 0 || y > 1 ? null : { x, y };
  };
  scr.addEventListener('pointerdown', (e) => {
    const p = screenPos(e); if (e.button || !p || !can('control')) return;   // a guest who may only watch: the picture is not a touchpad
    e.preventDefault(); touching = true; lastPos = p; try { scr.setPointerCapture(e.pointerId); } catch {} touch('down', p); tapMark(e, 'down');
  });
  scr.addEventListener('pointermove', (e) => {
    if (touching && screenPos(e)) tapMark(e, 'move');
    const p = screenPos(e); if (!touching || !p || Date.now() - lastMove < 40) return;
    lastMove = Date.now(); lastPos = p; touch('move', p);
  });
  ['pointerup', 'pointercancel'].forEach((ev) => scr.addEventListener(ev, (e) => { if (touching) { touching = false; touch('up', screenPos(e) || lastPos); tapMark(e, 'up'); } }));
  scr.addEventListener('wheel', (e) => {
    const p = screenPos(e); if (!p || !can('control')) return;
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
  if (window.ONTV || currentTab !== 'remote' || picking || typingHere() || e.metaKey || e.ctrlKey || e.altKey || e.isComposing || document.querySelector('dialog[open]')) return;   // any dialog open: its keys are its own
  const onButton = ['BUTTON', 'SUMMARY', 'A'].includes(document.activeElement.tagName);
  if (document.activeElement.closest('nav.tabs') && /^(Arrow(Left|Right)|Home|End)$/.test(e.key)) return;   // the tab bar's own arrows
  if (onButton && (e.key === 'Enter' || e.key === ' ' || e.key === 'Tab')) return;   // keyboard users still operate the page
  if (e.key === 'Escape' && document.fullscreenElement) return;       // let Esc leave fullscreen
  const adb = ADB.state === 'ready' || NATIVE() && can('text');      // letters: ADB, or LG's / Samsung's keyboard link
  if (keymap[e.key]) { e.preventDefault(); api('/api/key', { key: keymap[e.key] }); }
  else if (e.key === 'Enter') { e.preventDefault(); if (adb) queue('/api/type', { enter: true }); else api('/api/key', { key: 'OK' }); }
  else if (e.key === 'Backspace') { e.preventDefault(); if (adb) queue('/api/type', { del: 1 }); else api('/api/key', { key: 'DEL' }); }
  else if (!adb) return;                                              // letters need ADB
  else if (e.key === 'Delete') { e.preventDefault(); queue('/api/type', { fdel: 1 }); }
  else if (e.key.length === 1) { e.preventDefault(); queue('/api/type', { text: e.key }); }
});
document.addEventListener('paste', (e) => {                           // Cmd+V anywhere on the Remote tab pastes onto the TV
  if (window.ONTV || currentTab !== 'remote' || typingHere() || document.querySelector('dialog[open]') || !(ADB.state === 'ready' || NATIVE() && can('text'))) return;
  const t = (e.clipboardData || window.clipboardData).getData('text'); if (t) { e.preventDefault(); queue('/api/type', { text: t.slice(0, 500) }); }
});


// ---------- ask the TV: typed or spoken here; what is not a command is looked up on YouTube (else the TV's search) ----------
async function askTv(q) {
  q = q.trim(); if (!q) return;
  const c = voiceCommand(q, voiceApps(), SCENES);                     // "mở YouTube", "tắt tiếng", a scene's name...: done here, at once (voice.js)
  if (c) { doCommand(c); return; }
  const r = await api('/api/search', { query: q });
  if (r) { toast((r.to === 'youtube' ? 'Đang tìm trên YouTube: ' : 'Đã gửi cho TV: ') + q); setTimeout(refresh, 2500); }
}
$('#askf').addEventListener('submit', (e) => { e.preventDefault(); askTv($('#ask').value); $('#ask').value = ''; });
// speech is turned into text by the browser (Chrome, Safari), not by the TV. Browsers allow the microphone only on secure pages:
// the TV's own page is plain HTTP, so there the phone keyboard's own microphone key does it.
const Speech = window.SpeechRecognition || window.webkitSpeechRecognition;
if (!(Speech && window.isSecureContext) && matchMedia('(pointer:coarse)').matches) $('#ask').placeholder = tr('Hỏi TV: mở YouTube · nói bằng nút 🎤 của bàn phím');
if (Speech && window.isSecureContext) {
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
// Speak, as on the phone app: this device turns speech into text (the browser), else the "Hỏi TV" box takes it typed (on a
// phone its keyboard's 🎤); held, or with no box (no ADB), the TV's own Assistant.
{
  const v = $('#voice'); let held = 0;
  const assist = () => { api('/api/key', { key: 'ASSIST' }); toast('Đã mở Trợ lý trên TV'); };
  v.addEventListener('pointerdown', () => { held = setTimeout(() => { held = -1; assist(); }, 600); });
  ['pointerup', 'pointerleave', 'pointercancel'].forEach((e) => v.addEventListener(e, () => { if (held > 0) clearTimeout(held); }));
  v.addEventListener('contextmenu', (e) => e.preventDefault());
  v.addEventListener('click', () => {
    if (held === -1) { held = 0; return; }
    if (!$('#speak').hidden && !$('#kbd').disabled) $('#speak').click();
    else if (!$('#kbd').disabled) { $('#ask').scrollIntoView({ block: 'center', behavior: 'smooth' }); $('#ask').focus(); }
    else assist();
  });
}
