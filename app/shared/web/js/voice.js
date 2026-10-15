// Spoken or typed sentences the remote carries out itself, before handing anything to the TV's Assistant: "mở YouTube",
// "tắt tiếng", "hẹn giờ tắt 30 phút", the name of a scene... Faster than the Assistant, and works on TVs without one.
// The phone app has the same rules (app/shared .../Voice.kt). Tests: app/shared/tests/voice.test.js.
(function (root) {
  // lower case, no accents, no punctuation: "Mở YouTube!" and "mo youtube" read the same
  const fold = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[đĐ]/g, 'd').toLowerCase()
    .replace(/[^\p{L}\p{N} ]/gu, ' ').replace(/\s+/g, ' ').trim();

  const KEYS = [
    [/^(tat tieng|bat tieng|im lang|mute|unmute)$/, 'MUTE'],
    [/^(tam dung|dung lai|dung|phat|phat tiep|tiep tuc|pause|play|resume|stop)$/, 'PLAY_PAUSE'],
    [/^(ve )?(man hinh chinh|trang chu|home|go home)$/, 'HOME'],
    [/^(quay lai|tro lai|back|go back)$/, 'BACK'],
    [/^(bai tiep|bai sau|tiep theo|next)$/, 'NEXT'],
    [/^(bai truoc|truoc do|previous)$/, 'PREV'],
    [/^(tat tv|tat ti ?vi|tat may|turn off( the)? tv|tv off)$/, 'SLEEP'],
    [/^(bat tv|bat ti ?vi|mo tv|mo ti ?vi|turn on( the)? tv|tv on)$/, 'WAKE'],
  ];

  // apps: [{pkg, name}]; scenes: [{id, name}]. Returns what to do, or null for "ask the Assistant".
  function voiceCommand(text, apps, scenes) {
    const s = fold(text);
    if (!s) return null;
    const scene = (scenes || []).find((x) => fold(x.name) === s || fold('canh ' + x.name) === s || fold('scene ' + x.name) === s);
    if (scene) return { kind: 'scene', scene };
    for (const [re, key] of KEYS) if (re.test(s)) return { kind: 'key', key, times: 1 };
    let m = /^(tang am luong|to len|to hon|volume up|louder|turn it up)( (\d+))?$/.exec(s);
    if (m) return { kind: 'key', key: 'VOL_UP', times: Math.min(+(m[3] || 3), 15) };
    m = /^(giam am luong|nho lai|nho hon|be lai|volume down|quieter|turn it down)( (\d+))?$/.exec(s);
    if (m) return { kind: 'key', key: 'VOL_DOWN', times: Math.min(+(m[3] || 3), 15) };
    m = /^(huy hen gio|bo hen gio|cancel( the)?( sleep)? timer)$/.exec(s);
    if (m) return { kind: 'timer', min: 0 };
    m = /^(hen gio tat( tv)?|hen gio|tat tv sau|tat sau|sleep in|turn off( the tv)? in|sleep timer) (\d+) ?(phut|p|minutes?|mins?|gio|tieng|hours?|h)?$/.exec(s);
    if (m) return { kind: 'timer', min: Math.min(+m[4] * (/^(gio|tieng|hours?|h)$/.test(m[5] || '') ? 60 : 1), 720) };
    m = /^(mo|bat|vao|chay|xem|open|launch|start|play)( ung dung| app)? (.+)$/.exec(s);
    if (m) {
      const want = m[3], named = (apps || []).map((a) => ({ a, n: fold(a.name) })).filter((x) => x.n);
      const hit = named.find((x) => x.n === want) || named.find((x) => x.n.startsWith(want) || want.startsWith(x.n + ' '))
        || named.find((x) => want.length >= 3 && x.n.includes(want)) || named.find((x) => x.n.length >= 3 && want.includes(x.n));
      if (hit) return { kind: 'app', pkg: hit.a.pkg, name: hit.a.name };
    }
    return null;
  }

  root.fold = fold;
  root.voiceCommand = voiceCommand;
  if (typeof module !== 'undefined') module.exports = { fold, voiceCommand };
})(typeof window !== 'undefined' ? window : globalThis);
