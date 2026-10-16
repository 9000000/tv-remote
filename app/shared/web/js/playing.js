// Now playing: what the TV plays, from its media session (/api/playing: tvr-helper's Playing on the TV, NowPlaying.kt), above the
// remote while the Remote tab is open. The same bar is on the phone app's remote (RemoteScreen.kt). And the TV's clipboard
// (/api/clip), the "clip" entry of the Open on the TV menu.
const PL = { o: { state: 'none' }, at: 0, timer: 0, dragging: false };
const mmss = (ms) => { const s = Math.floor(ms / 1000), h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60, x = String(s % 60).padStart(2, '0'); return h ? h + ':' + String(m).padStart(2, '0') + ':' + x : m + ':' + x; };
const plPos = () => { const o = PL.o; let p = o.pos || 0; if (o.state === 'playing') p += (performance.now() - PL.at) * (o.speed || 1); return o.dur ? Math.min(p, o.dur) : p; };
function plDraw() {
  const o = PL.o, on = o.state && o.state !== 'none' && !!o.title;
  $('#playing').hidden = !on; if (!on) return;
  $('#pltitle').textContent = o.title; $('#pltitle').title = o.title;
  $('#plsub').textContent = [o.sub, appName(o.pkg)].filter(Boolean).join(' · ');
  const icon = '/api/app/icon?pkg=' + encodeURIComponent(o.pkg); if ($('#plicon').dataset.src !== icon) { $('#plicon').dataset.src = icon; $('#plicon').src = icon; }
  $('#plbar').hidden = !o.dur;
  if (o.dur && !PL.dragging) { $('#plseek').value = Math.round(plPos() / o.dur * 1000); $('#plpos').textContent = mmss(plPos()); $('#pldur').textContent = mmss(o.dur); }
  $('#plseek').disabled = !o.seek;
  $('#plplay use').setAttribute('href', o.state === 'paused' ? '#i-play' : '#i-pause');
}
async function plPoll() {
  clearTimeout(PL.timer);
  const looking = currentTab === 'remote' && !picking && !document.hidden && !stopped && ADB.state === 'ready';
  if (looking) { const o = await call('/api/playing', null, true); if (o) { PL.o = o; PL.at = performance.now(); } else PL.o = { state: 'none' }; }
  else PL.o = { state: 'none' };
  plDraw();
  PL.timer = setTimeout(plPoll, looking ? 1000 : 3000);
}
const plDo = (what, ms) => call('/api/playing', { do: what, ms }, true).then(() => setTimeout(plPoll, 500));
$('#plplay').addEventListener('click', () => { const p = PL.o.state === 'playing'; plDo(p ? 'pause' : 'play'); PL.o.pos = plPos(); PL.at = performance.now(); PL.o.state = p ? 'paused' : 'playing'; });
$('#plprev').addEventListener('click', () => plDo('prev'));
$('#plnext').addEventListener('click', () => plDo('next'));
$('#plseek').addEventListener('input', (e) => { PL.dragging = true; $('#plpos').textContent = mmss(e.target.value / 1000 * PL.o.dur); });
$('#plseek').addEventListener('change', (e) => {
  const ms = Math.round(e.target.value / 1000 * PL.o.dur); PL.dragging = false;
  PL.o.pos = ms; PL.at = performance.now(); plDo('seek', ms);
});
setInterval(() => { if (!$('#playing').hidden && PL.o.state === 'playing') plDraw(); }, 250);   // the time moves on between polls
document.addEventListener('visibilitychange', () => { if (!document.hidden) plPoll(); });
plPoll();

// the TV's clipboard: shown in a box anyone can edit, then copied here, set on the TV, or set and pasted where the TV's cursor is
// The screen off while the sound plays on (ScreenOff.kt): any key, on any remote, turns it back on.
async function screenOff() {
  if (needAdb()) return;
  if (await api('/api/screen/off', {})) toast('Đã tắt màn hình TV. Bấm phím bất kỳ để bật lại.');
}

async function clipDialog() {
  if (needAdb()) return;
  const got = await api('/api/clip'); if (!got) return;
  const box = el('textarea', { value: got.text || '', placeholder: tr('Clipboard của TV đang trống. Gõ hoặc dán chữ vào đây để gửi lên TV.'), spellcheck: false });
  const send = async (paste) => {
    if (!box.value) { toast('Chưa có chữ'); return; }
    if (await api('/api/clip', { text: box.value, paste })) { toast(paste ? 'Đã dán trên TV' : 'Đã chép lên TV'); d.close(); }
  };
  const copy = async () => {
    try { await navigator.clipboard.writeText(box.value); toast('Đã sao chép vào máy này'); d.close(); }
    catch { box.select(); toast('Không sao chép được: chọn chữ rồi sao chép tay'); }
  };
  const d = el('dialog', { className: 'ask' }, el('div', { className: 'card' },
    el('div', { className: 'card-head' }, el('h2', { textContent: tr('📋 Clipboard của TV') }), el('button', { className: 'small iconbtn', textContent: '✕', title: tr('Đóng'), 'aria-label': tr('Đóng'), onclick: () => d.close() })),
    box,
    el('div', { className: 'acts wrap' },
      el('button', { textContent: tr('Chép về máy'), onclick: copy }),
      el('button', { textContent: tr('Chép lên TV'), onclick: () => send(false) }),
      el('button', { className: 'primary', textContent: tr('Dán trên TV'), onclick: () => send(true) }))));
  d.addEventListener('close', () => d.remove());
  document.body.append(d); d.showModal(); box.focus(); box.select();
}
