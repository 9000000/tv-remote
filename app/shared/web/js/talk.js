// Speaking into this computer where its window cannot turn speech into words (Windows' and Linux's web views have no speech
// service; the Mac's and Chrome's have, remote.js): the microphone goes to the TV's Assistant as a voice remote's would
// (/api/voice, Atv.voiceBegin), and the TV hears it. The computer app only: the phone app has its own screen, and the TV's
// page has no remote link to the TV.
const talkable = () => CORE === 'pc' && window.isSecureContext && !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia)
  && !(window.SpeechRecognition || window.webkitSpeechRecognition);
let talking = null;

async function talk() {
  if (talking) { talking.stop(); return; }
  const lit = (on) => ['#speak', '#voice'].forEach((x) => $(x).classList.toggle('on', on));   // both: the 🎤 by the box and Speak
  let mic;
  try { mic = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true } }); }
  catch { toast(T('remote.error_why', { why: T('remote.mic_not_allowed') })); return; }
  // 8 kHz, what the TV takes; 4096 samples a piece (8 KB, half a second): the TV pads smaller ones with silence
  const ac = new AudioContext({ sampleRate: 8000 }), src = ac.createMediaStreamSource(mic), node = ac.createScriptProcessor(4096, 1, 1);
  const t0 = performance.now();
  let id = null, sent = Promise.resolve(), held = [], heard = 0, quiet = 0, done = false;
  const send = (pcm, end) => {                                     // one after another: the TV plays them in the order sent
    sent = sent.then(() => fetch('/api/voice?do=' + (end ? 'end' : 'data') + '&id=' + id,
      { method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: pcm }).catch(() => {}));
  };
  const stop = () => {
    if (done) return;
    done = true; talking = null; lit(false);
    node.disconnect(); src.disconnect(); mic.getTracks().forEach((t) => t.stop()); ac.close();
    if (id !== null) { send(new Uint8Array(0), true); toast(T('remote.talk_sent')); }
  };
  talking = { stop }; lit(true);
  node.onaudioprocess = (e) => {
    if (done) return;
    const f = e.inputBuffer.getChannelData(0), pcm = new Int16Array(f.length);
    let sum = 0;
    for (let i = 0; i < f.length; i++) { const v = Math.max(-1, Math.min(1, f[i])); pcm[i] = v * 32767; sum += v * v; }
    if (id === null) held.push(pcm); else send(pcm);
    const ms = f.length / 8;
    if (Math.sqrt(sum / f.length) > 0.02) { heard += ms; quiet = 0; } else quiet += ms;
    const t = performance.now() - t0;                              // done speaking, never spoke, or too long
    if ((heard && quiet > 1200) || (!heard && t > 7000) || t > 15000) stop();
  };
  src.connect(node); node.connect(ac.destination);
  const r = await api('/api/voice?do=start', {});                  // the TV opens its Assistant, listening to us
  if (!r) { held = []; stop(); return; }
  id = r.id; held.forEach((p) => send(p)); held = [];
  if (done) { send(new Uint8Array(0), true); toast(T('remote.talk_sent')); } else toast(T('remote.talk_listening'));
}
if (talkable()) { put('#speak', 'hidden', false); $('#speak').addEventListener('click', talk); }
