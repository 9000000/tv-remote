'use strict';
// Sound effects for every web game: tones and noise made on the spot (no audio files), one shared AudioContext.
// Kit games reach it as K.tone / K.noise / K.sfx; table, card and board games call Sfx.play('card') etc. ?mute silences it.
(function () {
  const mute = /[?&]mute\b/.test(location.search);
  let ac = null, live = 0, nbuf = null;
  const audio = () => {
    if (mute) return null;
    if (ac === null) { try { ac = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { ac = false; } }
    if (ac && ac.state === 'suspended') ac.resume().catch(() => { });
    return ac || null;
  };
  // at most a dozen voices at once: a weak TV stutters long before it runs out of nodes
  const tone = (f, d, type, v, slide, delay) => {
    const a = audio(); if (!a || live > 12) return;
    const t = a.currentTime + (delay || 0), o = a.createOscillator(), g = a.createGain();
    o.type = type || 'square'; o.frequency.setValueAtTime(f, t); if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(30, f * slide), t + d);
    g.gain.setValueAtTime(v == null ? 0.08 : v, t); g.gain.exponentialRampToValueAtTime(0.0001, t + d);
    o.connect(g); g.connect(a.destination); live++; o.onended = () => { live--; }; o.start(t); o.stop(t + d + 0.02);
  };
  const noise = (d, v, f0, f1) => {
    const a = audio(); if (!a || live > 12) return;
    if (!nbuf) { nbuf = a.createBuffer(1, a.sampleRate, a.sampleRate); const b = nbuf.getChannelData(0); for (let i = 0; i < b.length; i++) b[i] = Math.random() * 2 - 1; }
    const t = a.currentTime, s = a.createBufferSource(), fl = a.createBiquadFilter(), g = a.createGain();
    s.buffer = nbuf; fl.type = 'lowpass'; fl.frequency.setValueAtTime(f0 || 2000, t); fl.frequency.exponentialRampToValueAtTime(f1 || 200, t + d);
    g.gain.setValueAtTime(v || 0.2, t); g.gain.exponentialRampToValueAtTime(0.0001, t + d);
    s.connect(fl); fl.connect(g); g.connect(a.destination); live++; s.onended = () => { live--; }; s.start(t); s.stop(t + d + 0.02);
  };
  const S = {
    ui: () => tone(660, 0.07, 'triangle', 0.1),
    ok: () => { tone(520, 0.09, 'square', 0.07); tone(780, 0.14, 'square', 0.07, 0, 0.08); },
    tick: () => tone(880, 0.06, 'sine', 0.1),
    go: () => { tone(440, 0.12, 'sawtooth', 0.08); tone(880, 0.3, 'sawtooth', 0.08, 0, 0.1); },
    hit: (v) => tone(180 + (v || 0) * 140, 0.09, 'triangle', 0.18, 0.6),
    goal: () => { noise(0.5, 0.25, 1800, 120); [523, 659, 784, 1046].forEach((f, i) => tone(f, 0.18, 'square', 0.07, 0, i * 0.09)); },
    shoot: () => tone(520, 0.12, 'sawtooth', 0.07, 0.35),
    bounce: () => tone(300, 0.05, 'square', 0.05, 0.8),
    boom: () => { noise(0.55, 0.3, 2500, 90); tone(110, 0.4, 'sawtooth', 0.12, 0.3); },
    dash: () => noise(0.18, 0.12, 3500, 600),
    fall: () => tone(600, 0.6, 'sine', 0.12, 0.12),
    win: () => [523, 659, 784, 1046, 1318].forEach((f, i) => tone(f, 0.22, 'triangle', 0.1, 0, i * 0.11)),
    // board, card and quiz events
    move: () => tone(330, 0.06, 'triangle', 0.09, 1.3),                       // a pawn or cursor step, soft and short
    place: () => { tone(240, 0.07, 'triangle', 0.14, 0.7); noise(0.04, 0.05, 3000, 800); },   // a stone or piece set down
    card: () => noise(0.07, 0.1, 5000, 900),                                  // a card swished onto the table
    pickup: () => { tone(660, 0.06, 'sine', 0.09); tone(990, 0.09, 'sine', 0.09, 0, 0.05); },
    score: () => { tone(784, 0.08, 'triangle', 0.1); tone(1175, 0.14, 'triangle', 0.1, 0, 0.07); },
    right: () => { tone(587, 0.1, 'triangle', 0.1); tone(880, 0.2, 'triangle', 0.1, 0, 0.09); },
    bad: () => { tone(200, 0.16, 'sawtooth', 0.07, 0.6); tone(150, 0.2, 'sawtooth', 0.07, 0.6, 0.12); },
    dice: () => [0, 0.07, 0.14, 0.21].forEach((d, i) => tone(500 + i * 90, 0.04, 'square', 0.05, 0, d)),   // a quick rattle
    turn: () => tone(740, 0.08, 'sine', 0.07),                                // "your move": quieter than ui so it never nags
    lose: () => [440, 392, 330, 262].forEach((f, i) => tone(f, 0.2, 'triangle', 0.09, 0, i * 0.12))
  };
  // one name never fires twice within its gap: a frame with ten collisions makes one sound, not ten
  const gap = {}, GAP = { hit: 50, bounce: 50, move: 40, card: 40, place: 40, tick: 80 }, play = {};
  Object.keys(S).forEach((n) => {
    const min = GAP[n] || 30;
    play[n] = (v) => { const now = Date.now(); if (now - (gap[n] || 0) < min) return; gap[n] = now; S[n](v); };
  });
  window.Sfx = { tone: tone, noise: noise, wake: audio, s: play, play: (n, v) => { if (play[n]) play[n](v); } };
})();
