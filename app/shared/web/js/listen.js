// 🎧 the TV's sound on this device. scrcpy captures it on the TV (Android 11+). Two ways, the last one used at once (the chip next to the button changes it):
//   only here: the TV's own speakers are silent (headphones on, the rest of the house asleep); everything the TV plays
//   both: the TV keeps playing (playback capture with audio_dup, Android 13+; older TVs answer "noshare" and go the first way).
//         This device lags the TV a little, and apps that refuse playback capture (some with DRM) are silent here.
// It is light next to the picture:
//   Opus, 64 kbit/s (8 KB/s): the computer's page, where the browser decodes Opus (WebCodecs needs a secure page, and
//         127.0.0.1 is one); the TV encodes it at a few percent of one core
//   PCM 24 kHz mono, 384 kbit/s (48 KB/s): the TV's own page (plain HTTP, no WebCodecs); the TV encodes nothing
// ✨ high quality: Opus at 160 kbit/s, PCM as captured (48 kHz stereo, 1.5 Mbit/s: fine on the home network).
// For comparison the 960p picture is up to 2 Mbit/s and most of a CPU core on the TV.
// Stream framing, as the picture's: 1 byte kind (0 format, 1 codec header, 2 sound, 3 notice word, 4 nothing new, 5 error word),
// 4 bytes length, data.
let listenShared = false;
const listenHq = () => { try { return localStorage.getItem('listenHq') === '1'; } catch { return false; } };
// While listening, a bar under the header: how it plays (menu) and HQ, as the phone app's chip and HQ under the picture (Listen.kt).
function showWay() {
  $('#listenway').textContent = (listenShared ? 'Cả hai' : 'Máy này') + ' ▾';
  $('#listenhq').classList.toggle('on', listenHq());
}
let listenOn = false, listenCtl = null, actx = null, adec = null, anext = 0, abytes = 0, listenGen = 0, catchUp = false;

async function opusHere() {
  if (CORE !== 'pc' || !window.AudioDecoder || !window.isSecureContext) return false;
  try { return !!(await AudioDecoder.isConfigSupported({ codec: 'opus', sampleRate: 48000, numberOfChannels: 2 })).supported; } catch { return false; }
}

// Plays one piece of sound right after the previous one. Kept close to live: a piece that would start more than 0.6 s from
// now is dropped (the network handed over a burst after a stall), and after a gap playback starts again a little ahead.
function playPcm(channels, rate) {
  if (!actx || !channels.length || !channels[0].length) return;
  const buf = actx.createBuffer(channels.length, channels[0].length, rate);
  channels.forEach((c, i) => buf.copyToChannel(c, i));
  const now = actx.currentTime;
  if (anext < now + 0.02) anext = now + 0.08;                         // ran dry (a stall): start again a little ahead
  // After a stall the network hands over the late sound in one burst; played in turn, it would keep the whole stall as delay
  // for good. Too far ahead: drop pieces until close to live again (one short skip instead of a lasting lag).
  const ahead = anext - now;
  if (VT) VT.audio.push([vtNow(), ahead * 1000, now, anext, actx.baseLatency, actx.outputLatency]);   // ?stats=1
  if (ahead > 0.25) catchUp = true; else if (ahead < 0.1) catchUp = false;
  if (catchUp) return;
  const src = actx.createBufferSource(); src.buffer = buf; src.connect(actx.destination); src.start(anext);
  anext += buf.duration;
}

function setListenUi(on, text) {
  const b = $('#listen'); setLabel(b, '🎧', on ? 'Đang nghe' : 'Nghe'); b.classList.toggle('on', on);
  $('#listenbar').hidden = !on; if (on) showWay();
  $('#listeninfo').hidden = !text; $('#listeninfo').textContent = text || '';
}

async function startListen(share) {
  const gen = ++listenGen;
  listenShared = !!share; try { localStorage.setItem('listenShare', listenShared ? '1' : '0'); } catch {}
  listenOn = true; setListenUi(true, 'Đang mở tiếng TV...');
  try {
    actx = actx || new (window.AudioContext || window.webkitAudioContext)({ latencyHint: 'interactive' });
    if (navigator.audioSession) navigator.audioSession.type = 'playback';   // iPhone: play even with the silent switch on
    await actx.resume();                                              // inside the tap: browsers start sound only then
  } catch { stopListen('Trình duyệt này không phát được âm thanh.'); return; }
  const opus = await opusHere(); if (gen !== listenGen) return;
  listenCtl = new AbortController(); anext = 0; abytes = 0;
  let fmt = { codec: 2, rate: 24000, ch: 1 }, ts = 0, why = 'TV đã dừng gửi tiếng.';
  try {
    const r = await fetch('/api/audio?codec=' + (opus ? 'opus' : 'pcm') + (listenShared ? '&share=1' : '') + (listenHq() ? '&hq=1' : ''), { signal: listenCtl.signal });
    if (!r.ok) { let e = r.statusText; try { e = (await r.json()).error || e; } catch {} throw new Error(r.status === 404 && CORE === 'pc' ? STALE : e); }
    toast(listenShared ? 'Đang nghe tiếng TV trên cả TV và máy này.' : 'Đang nghe tiếng TV trên máy này. Loa TV tạm im.');
    const rd = r.body.getReader(); let buf = new Uint8Array(0);
    for (;;) {
      const { done, value } = await rd.read(); if (done || gen !== listenGen) break;
      abytes += value.length;
      const nb = new Uint8Array(buf.length + value.length); nb.set(buf); nb.set(value, buf.length); buf = nb;
      while (buf.length >= 5) {
        const len = new DataView(buf.buffer, buf.byteOffset + 1, 4).getUint32(0); if (buf.length < 5 + len) break;
        const kind = buf[0], data = buf.slice(5, 5 + len); buf = buf.slice(5 + len);
        if (kind === 3 && new TextDecoder().decode(data) === 'noshare') { listenShared = false; toast('TV này dưới Android 13 nên chỉ nghe riêng được: loa TV tạm im.'); }
        else if (kind === 0) { const d = new DataView(data.buffer); fmt = { codec: data[0], rate: d.getUint32(1), ch: data[5] }; }
        else if (kind === 1 && fmt.codec === 1) {
          adec = new AudioDecoder({ output: (a) => {
            const chs = []; for (let i = 0; i < a.numberOfChannels; i++) { const c = new Float32Array(a.numberOfFrames); a.copyTo(c, { planeIndex: i, format: 'f32-planar' }); chs.push(c); }
            playPcm(chs, a.sampleRate); a.close();
          }, error: () => {} });
          adec.configure({ codec: 'opus', sampleRate: fmt.rate, numberOfChannels: fmt.ch, description: data });
        } else if (kind === 2) {
          if (VT) VT.arx.push([vtNow(), data.length]);
          if (fmt.codec === 1) { if (adec && adec.state === 'configured') adec.decode(new EncodedAudioChunk({ type: 'key', timestamp: ts += 20000, data })); }
          else {                                                       // 16-bit little-endian PCM, channels interleaved
            const v = new DataView(data.buffer), n = Math.floor(data.length / 2 / fmt.ch), chs = Array.from({ length: fmt.ch }, () => new Float32Array(n));
            for (let i = 0; i < n; i++) for (let c = 0; c < fmt.ch; c++) chs[c][i] = v.getInt16((i * fmt.ch + c) * 2, true) / 32768;
            playPcm(chs, fmt.rate);
          }
        } else if (kind === 5) { why = new TextDecoder().decode(data) === 'unsupported' ? 'TV này không gửi được tiếng (cần Android 11 trở lên).' : friendly(new TextDecoder().decode(data)); }
      }
    }
  } catch (e) { if (gen !== listenGen) return; why = e.name === 'AbortError' ? '' : 'Không lấy được tiếng TV: ' + friendly(e.message); }
  if (gen === listenGen && why && afterWake('listen')) { stopListen(); startListen(listenShared); return; }   // this device slept, not the TV
  if (gen === listenGen) stopListen(why);
}

function stopListen(msg) {
  listenGen++; listenOn = false;
  if (listenCtl) { listenCtl.abort(); listenCtl = null; }
  if (adec) { try { adec.close(); } catch {} adec = null; }
  setListenUi(false, ''); if (msg) toast(msg);
}

setInterval(() => {                                                    // what it costs, next to the button
  if (listenOn && abytes) setListenUi(true, '🎧 Đang nghe trên máy này · ' + (listenShared ? 'TV vẫn có tiếng' : 'loa TV tạm im') + (listenHq() ? ' · ✨ chất lượng cao' : '') + ' · ' + Math.max(1, Math.round(abytes / 1024)) + ' KB/s');
  abytes = 0;
}, 1000);
// 🎧: on or off at once, the way used last (picked with the chip under the header, once it plays); the session restarts either way
$('#listen').addEventListener('click', () => {
  if (listenOn) { stopListen(); return; }
  let last = '0'; try { last = localStorage.getItem('listenShare') || '0'; } catch {}
  startListen(last === '1');
});
const relisten = (share) => { if (listenOn) { stopListen(); startListen(share); } else showWay(); };
$('#listenway').addEventListener('click', () => {
  menuDlg('Cách nghe tiếng TV', listenShared ? 1 : 0, [
    ['@phone|Chỉ máy này', 'Loa TV tạm im. Hợp khi đeo tai nghe, cả nhà ngủ.', () => { if (listenShared) relisten(false); }],
    ['@tv|Cả TV và máy này', 'TV vẫn có tiếng (TV Android 13+). Máy này trễ hơn TV một chút; vài app như Netflix chặn thu tiếng kiểu này.', () => { if (!listenShared) relisten(true); }]]);
});
$('#listenhq').addEventListener('click', () => {                      // 160 kbit/s instead of 64; applies at once
  try { localStorage.setItem('listenHq', listenHq() ? '0' : '1'); } catch {}
  relisten(listenShared);
});
