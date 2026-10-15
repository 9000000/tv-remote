/* The intro page's animation runtime on GSAP: a Story is one looping timeline over one element (the hero demo, How it
   works, an install tab), written as "at second t, this changes" in demo.js and stories.js. Their look is demo.css and
   stories.css, their markup index.html. A story is cut into equal parts (a chapter, a step): `at(k, s)` is second s of
   part k.

   Everything is a track: one element's state held from one moment to the next. The helpers (show, press, fill, type,
   toggle, focus, pointer, steps) only write tracks, and a track is plain tweens that each say where they start from, so
   jumping to any second (a chapter click, a screenshot) lands in the right state whatever played before.
   Anim.define(name, T, parts, build) makes a Story of every `.name` on the page and plays it while it is on screen. */
(function(){
  "use strict";
  if (!window.gsap) return;

  /* ---------- helpers ---------- */

  // CSS's default `ease` (.25,.1,.25,1), used between every two moments so the motion looks as it always did
  function bezier(x1, y1, x2, y2){
    function at(t, a, b){ var u = 1 - t; return 3 * a * u * u * t + 3 * b * u * t * t + t * t * t; }
    return function(p){
      if (p <= 0 || p >= 1) return p;
      var lo = 0, hi = 1, t = p;
      for (var i = 0; i < 24; i++){ if (at(t, x1, x2) < p) lo = t; else hi = t; t = (lo + hi) / 2; }
      return at(t, y1, y2);
    };
  }
  var EASE = bezier(.25, .1, .25, 1);
  function steps(n){ return function(p){ return Math.floor(p * n) / n; }; }   // typing: one character at a time

  // GSAP writes `transform: translate(0,0)` and `filter: brightness(1)` for a resting element, which the browser draws
  // a hair differently from none (a layer of its own); a state that is only "at rest" asks for them to be cleared instead
  var IDENTITY = { x: 0, y: 0, xPercent: 0, yPercent: 0, scale: 1, scaleX: 1, scaleY: 1 };
  function resting(state){
    var tf = Object.keys(state).filter(function(k){ return k in IDENTITY; }), clear = [];
    if (tf.length && tf.every(function(k){ return parseFloat(state[k]) === IDENTITY[k]; })) clear.push("transform");
    if (state.filter === "brightness(1)" || state.filter === "grayscale(0)") clear.push("filter");
    return clear.length ? { clearProps: clear.join(",") } : {};
  }

  function Story(el, T, parts){
    this.el = el; this.T = T; this.parts = parts; this.L = T / parts;
    this.tl = gsap.timeline({ paused: true, repeat: -1 });
    this.tl.set({}, {}, T);                                        // the loop is T long even if the last change is earlier
    this.held = false;                                             // a test pinned the time: no playing on scroll
    this.back = { at: T - .25, dur: .25 };                         // when a track that ends elsewhere returns to its start
  }
  Story.prototype = {
    q: function(sel){
      var l = [].slice.call(this.el.querySelectorAll(sel));
      if (!l.length) console.warn("story: nothing matches", sel);
      return l;
    },
    at: function(k, s){ return (k - 1) * this.L + s; },            // second s of part k (1-based)

    // pts: [t, state, duration, ease?] = from t, move to state over duration. Back to `init` at this.back (just before
    // the loop ends) unless `stay`, so every element starts each loop from the same state. snap: {"--n": 1} rounds a
    // variable that a counter reads (an @property <integer> ignores 55.3)
    track: function(sel, init, pts, stay, snap){
      var q = this.q(sel), tl = this.tl, cur = init, back = this.back;
      if (!q.length) return;
      function go(from, to, t, dur, ease){
        tl.fromTo(q, Object.assign({}, from), Object.assign({ duration: dur, ease: ease || EASE, immediateRender: false }, snap ? { snap: snap } : {}, to, resting(to)), t);
      }
      tl.set(q, Object.assign({}, init, resting(init)), 0);          // GSAP writes into what it is given: always a copy
      pts.slice().sort(function(a, b){ return a[0] - b[0]; }).forEach(function(p){
        go(cur, p[1], p[0], p[2], p[3]); cur = p[1];
      });
      if (!stay && JSON.stringify(cur) !== JSON.stringify(init)) go(cur, init, back.at, back.dur);
    },

    // visible in each [a, b]; a <= 0 means already showing when the loop starts. Last argument may be {f, on, off}:
    // f the fade, on / off the two states (opacity by default)
    show: function(sel){
      var wins = [].slice.call(arguments, 1), o = Array.isArray(wins[wins.length - 1]) ? {} : wins.pop();
      var f = o.f == null ? .25 : o.f, on = o.on || { opacity: 1 }, off = o.off || { opacity: 0 }, init = off, pts = [], T = this.T;
      wins.forEach(function(w){
        if (w[0] <= 0) init = on; else pts.push([w[0] - f, on, f]);
        if (w[1] < T) pts.push([w[1], off, f]);
      });
      this.track(sel, init, pts);
    },
    press: function(sel){                                          // a tap's squeeze, at each second given
      var on = { scale: .88, filter: "brightness(1.4)" }, off = { scale: 1, filter: "brightness(1)" };
      this.track(sel, off, [].slice.call(arguments, 1).reduce(function(a, t){ return a.concat([[t, on, .08], [t + .08, off, .22]]); }, []));
    },
    fill: function(sel, a, b){                                     // a bar filling (its ::after reads --p)
      this.track(sel, { "--p": 0 }, [[a, { "--p": 1 }, b - a, "none"]]);
    },
    type: function(sel, a, b, n){ this.track(sel, { width: "0ch" }, [[a, { width: n + "ch" }, b - a, steps(n)]]); },
    toggle: function(sel, t, on){                                  // a switch flipping (its knob reads --k)
      var off = { backgroundColor: "#3a4258", "--k": 0 }, yes = { backgroundColor: "#3ddc84", "--k": 1 };
      on = on !== false;
      this.track(sel, on ? off : yes, [[t, on ? yes : off, .15]]);
    },
    focus: function(sel){                                          // the white focus ring of a TV's row or button
      var a = [].slice.call(arguments, 1);
      a.push({
        f: .12,
        on: { boxShadow: "0px 0px 0px 0.5cqw rgb(255,255,255)", backgroundColor: "#34405a" },
        off: { boxShadow: "0px 0px 0px 0cqw rgba(255,255,255,0)", backgroundColor: "rgba(255,255,255,.06)" }
      });
      this.show.apply(this, [sel].concat(a));
    },
    // a finger or a cursor, at each [t, x%, y%, hide|show|move|press]; it glides between them, from just below the
    // first and away to the last. Moved through --x / --y (stories.css): GSAP tweening `top` in % now and then started
    // from ten times the value and the finger shot off the phone
    pointer: function(sel, events){
      var ev = events.slice().sort(function(a, b){ return a[0] - b[0]; }), T = this.T;
      function st(x, y, kind){
        return { "--x": +x.toFixed(2), "--y": +y.toFixed(2), opacity: kind === "hide" ? 0 : 1, scale: kind === "press" ? .75 : 1 };
      }
      var f = ev[0], l = ev[ev.length - 1];
      var pts = [[0, f[1], f[2] + 12, "hide"]].concat(ev.map(function(e){ return [e[0], e[1], e[2], e[3]]; }), [[T, l[1], l[2], "hide"]]);
      var segs = [];
      for (var i = 1; i < pts.length; i++) segs.push([pts[i - 1][0], st(pts[i][1], pts[i][2], pts[i][3]), pts[i][0] - pts[i - 1][0]]);
      this.track(sel, st(pts[0][1], pts[0][2], "hide"), segs, true);
    },
    // the install step k lit while its part plays
    steps: function(k){
      this.show(".steps li:nth-child(" + k + ")", [(k - 1) * this.L, k * this.L], {
        f: .2,
        on: { backgroundColor: "rgba(91,140,255,.1)", boxShadow: "inset 3px 0 0 0 #5b8cff" },
        off: { backgroundColor: "rgba(91,140,255,0)", boxShadow: "inset 0px 0 0 0 rgba(91,140,255,0)" }
      });
    },
    // the chapter buttons under How it works: the part playing lit (cut over in .04 s), its bar filling
    chapters: function(){
      var on = { color: "#e9edf5" }, off = { color: "#6b7489" };
      for (var k = 1; k <= this.parts; k++){
        var a = (k - 1) * this.L, b = k * this.L, li = ".dchap li:nth-child(" + k + ") ", pts = [];
        if (k > 1) pts.push([a - .04, on, .04]);
        if (k < this.parts) pts.push([b - .04, off, .04]);
        this.track(li + "button", k > 1 ? off : on, pts, true);    // the last stays lit until the loop starts over
        this.track(li + "i", { "--p": 0 }, [[a, { "--p": 1 }, b - a - .04, "none"], [b - .04, { "--p": 0 }, .04]]);
      }
    }
  };

  var PY = 19.5 / 9;                                               // the phone's screen: height per width
  function P(x, y){ return [x, y / PY]; }                          // phone spot (cqw of its width) to % of its screen
  function tap(t, xy){ return [t, xy[0], xy[1]]; }

  // a finger pressing each [t, x, y] in turn: it glides from one to the next when they are close in time, and lifts
  // away and comes back when they are not (separate taps written apart would jump between their spots)
  function taps(pts, near){
    near = near || 1.4;
    var ev = [], prev = null;
    pts.slice().sort(function(a, b){ return a[0] - b[0]; }).forEach(function(p){
      var t = p[0], x = p[1], y = p[2];
      if (prev && t - prev[0] < near){
        var g = Math.min(.15, (t - prev[0]) / 3);
        ev.push([prev[0] + g, prev[1], prev[2], "show"], [t - g, x, y, "move"], [t, x, y, "press"]);
      } else {
        if (prev) ev.push([prev[0] + .15, prev[1], prev[2], "show"], [prev[0] + .5, prev[1] + 5, prev[2] + 6, "hide"]);
        ev.push([t - .6, x + 6, y + 8, "hide"], [t - .3, x, y, "show"], [t, x, y, "press"]);
      }
      prev = p;
    });
    ev.push([prev[0] + .15, prev[1], prev[2], "show"], [prev[0] + .5, prev[1] + 5, prev[2] + 6, "hide"]);
    return ev;
  }
  function click(t, x, y, from){                                   // a cursor gliding in and clicking
    var fx = from ? from[0] : x + 14, fy = from ? from[1] : y + 18;
    return [[t - .6, fx, fy, "show"], [t - .1, x, y, "move"], [t, x, y, "press"], [t + .12, x, y, "show"]];
  }

  /* ---------- playback ---------- */

  var calm = matchMedia("(prefers-reduced-motion:reduce)");
  var made = [];                                                   // [Story]
  function of(el){ return made.filter(function(s){ return s.el === el; })[0]; }

  // part k: from its start, playing; for reduced motion a still, 92% in unless data-still says where
  Story.prototype.go = function(k){
    var still = (this.el.dataset.still || "").split(",").filter(Boolean).map(Number)[k];
    this.held = false;
    if (calm.matches) this.tl.pause(k * this.L + (still != null ? still : this.L * .92));
    else this.tl.play(k * this.L);
  };

  function define(name, T, parts, build){
    document.querySelectorAll("." + name).forEach(function(el){
      var s = new Story(el, T, parts);
      build(s);
      made.push(s);
      s.tl.seek(0);
      if ("IntersectionObserver" in window){
        new IntersectionObserver(function(es){
          if (calm.matches || s.held) return;
          if (es[0].isIntersecting) s.tl.play(); else s.tl.pause();
        }, { threshold: .2 }).observe(el);
      } else if (!calm.matches) s.tl.play();
      el.querySelectorAll(".dchap button, .steps li").forEach(function(b, k){ b.addEventListener("click", function(){ s.go(k); }); });
      if (calm.matches) s.go(0);
    });
  }

  // for screenshots: stop one story at that second
  window.storySeek = function(el, sec){
    var s = of(el); if (!s) return;
    s.held = true; s.tl.pause(sec);
  };

  window.Anim = { define: define, of: of, calm: calm, P: P, PY: PY, tap: tap, taps: taps, click: click };
})();
