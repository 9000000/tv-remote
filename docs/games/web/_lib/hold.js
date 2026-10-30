'use strict';
// A held button from a key source: Hold.make({ down, up, grace }) -> { down(), up(), held() }.
// Some remotes (and ADB / scrcpy / the TV remote protocol) send a down + up pair every ~50 ms while OK is held, or a down without
// `repeat`: read raw, each pair is "a new press" and the charge restarts. So a key-down while held is ignored, and a key-up counts
// only when no key-down follows within `grace` ms (a real release has none). Pure and timer-injectable: app/tvgames/tests/hold.test.js.
(function (root) {
  function make(o) {
    const grace = o.grace == null ? 180 : o.grace, set = o.setTimeout || setTimeout, clr = o.clearTimeout || clearTimeout;
    let held = false, t = null;
    return {
      down() { if (t != null) { clr(t); t = null; } if (held) return; held = true; o.down(); },
      up() { if (!held || t != null) return; t = set(() => { t = null; held = false; o.up(); }, grace); },
      held: () => held,
      reset() { if (t != null) clr(t); t = null; held = false; },
    };
  }
  const Hold = { make };
  if (typeof module === 'object' && module.exports) module.exports = Hold; else root.Hold = Hold;
})(this);
