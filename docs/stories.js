/* The intro page's stories as GSAP timelines: How it works (.st-h, four chapters) and the install tabs (.st-b browser,
   .st-a phone app, .st-c computer app, .st-p preparing the TV; one part per step). Their look is stories.css, their
   markup index.html. Each story is a list of "at second t, this changes", times in seconds from the loop's start; a
   part (a chapter or a step) is an equal slice of the loop, so `at(k, s)` is second s of part k. Edit the times here,
   there is nothing to generate.

   Everything is a track: one element's state held from one moment to the next. The helpers below (show, press, fill,
   type, toggle, focus, pointer, steps) only write tracks, and a track is plain tweens that each say where they start
   from, so jumping to any second (a chapter click, a screenshot) lands in the right state whatever played before. */
(function(){
  "use strict";
  if (!window.gsap) return;

  /* ---------- runtime: helpers and playback ---------- */

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
    if (state.filter === "brightness(1)") clear.push("filter");
    return clear.length ? { clearProps: clear.join(",") } : {};
  }

  function Story(el, T, parts){
    this.el = el; this.T = T; this.parts = parts; this.L = T / parts;
    this.tl = gsap.timeline({ paused: true, repeat: -1 });
    this.tl.set({}, {}, T);                                        // the loop is T long even if the last change is earlier
    this.held = false;                                             // a test pinned the time: no playing on scroll
  }
  Story.prototype = {
    q: function(sel){
      var l = [].slice.call(this.el.querySelectorAll(sel));
      if (!l.length) console.warn("story: nothing matches", sel);
      return l;
    },
    at: function(k, s){ return (k - 1) * this.L + s; },            // second s of part k (1-based)

    // pts: [t, state, duration, ease?] = from t, move to state over duration. Back to `init` just before the loop ends
    // unless `stay`, so every element starts each loop from the same state
    track: function(sel, init, pts, stay){
      var q = this.q(sel), tl = this.tl, cur = init, T = this.T;
      if (!q.length) return;
      function go(from, to, t, dur, ease){
        tl.fromTo(q, Object.assign({}, from), Object.assign({ duration: dur, ease: ease || EASE, immediateRender: false }, to, resting(to)), t);
      }
      tl.set(q, Object.assign({}, init, resting(init)), 0);          // GSAP writes into what it is given: always a copy
      pts.slice().sort(function(a, b){ return a[0] - b[0]; }).forEach(function(p){
        go(cur, p[1], p[0], p[2], p[3]); cur = p[1];
      });
      if (!stay && JSON.stringify(cur) !== JSON.stringify(init)) go(cur, init, T - .25, .25);
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
  function mir(x, y){ return P(4 + .92 * x, 34 + .92 * y); }       // a spot on the TV (cqw) as seen in the phone's picture (left 4, top 34, 92 wide)
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

  // where things are on the phone and on the TV
  var YT = [13.84, 32.9], MV = [19.9, 48.3];                       // the YouTube tile, the first "continue" film
  var OK = [50, 135], RIGHT = [66, 135], VOLUP = [86, 125], ROW = [50, 94];
  function key(d){ d = +d; return P(19 + 31 * ((d - 1) % 3), 88 + 19 * Math.floor((d - 1) / 3)); }   // the keypad's keys 1-9

  // the states a chip of "which way the phone talks" takes
  var CHIP = {
    good: { backgroundColor: "#173826", color: "#3ddc84" }, bad: { backgroundColor: "#3a1720", color: "#ff6b6b" },
    info: { backgroundColor: "#13243a", color: "#9fd0ff" }, idle: { backgroundColor: "#1f2738", color: "#9aa4b8" }
  };
  function chip(opacity, kind){ return Object.assign({ opacity: opacity }, CHIP[kind]); }
  function rise(pct){ return { on: { opacity: 1, yPercent: 0 }, off: { opacity: 0, yPercent: pct } }; }
  var CODE = { on: { color: "#fff", borderColor: "#5b8cff" }, off: { color: "rgba(255,255,255,0)", borderColor: "#2f3850" } };   // a code box getting its digit

  /* ---------- the stories ---------- */

  var STORIES = {
    "st-h": [40, 4, function(s){
      var A = function(k, t){ return s.at(k, t); };
      s.chapters();
      // the phone's three pages
      s.show(".hs1", [0, 1.9]);
      s.show(".hrm", [1.9, 30], [35, 40]);
      s.show(".hsf", [30, 35]);
      s.show(".hs1 .hr1", [1.0, 1.9], { f: .2 });
      s.press(".hs1 .hr1", 1.6);
      s.show(".hsb .nw", [0, 20], [30, 40], { f: .1 });
      s.show(".hsb .n4", [20, 30], { f: .1 });
      // chips on the remote: which way the phone is talking
      s.track(".ck.c1", chip(1, "good"), [
        [A(2, 1.2), chip(1, "bad"), .2], [A(2, 7.0), chip(1, "good"), .2],
        [A(3, 0), chip(.35, "good"), .2], [A(4, 0), chip(0, "good"), .2]]);
      s.show(".ck.c2", [A(2, 2.6), A(2, 6.8)], { on: chip(1, "info"), off: chip(.35, "idle") });
      s.show(".ck.c3", [A(3, 3.0), A(4, 0)], { on: chip(1, "info"), off: chip(0, "idle") });
      s.show(".ck.c4", [A(4, 5), 40], { on: chip(1, "info"), off: chip(0, "idle") });
      // what the TV and the phone's picture show
      s.show(".tyt", [A(1, 7.0), A(2, 0)]);
      s.show(".myt", [A(1, 7.4), A(2, 0)]);
      s.show(".moff", [A(2, 1.4), A(2, 7.4)]);
      s.show(".tmv", [A(3, 6.7), A(4, 0)]);
      s.show(".mmv", [A(3, 7.1), A(4, 0)]);
      s.show(".htv .z1", [A(1, 6.4), A(1, 7.0)], [A(2, 5.0), A(2, 5.3)], [A(4, 6.8), A(4, 8.0)], { f: .15 });
      s.show(".htv .z2", [A(2, 5.3), A(2, 6.6)], [A(4, 8.0), 39.6], { f: .15 });
      s.show(".htv .z3", [A(3, 6.2), A(3, 6.7)], { f: .15 });
      s.show(".htv .side", [A(2, .5), A(2, 2.3)], { on: { opacity: 1, xPercent: 0 }, off: { opacity: 0, xPercent: 30 } });
      s.toggle(".htv .side .tg", A(2, 1.2), false);
      s.show(".htv .vbar", [A(2, 3.5), A(2, 5.0)]);
      s.track(".htv .vbar i", { "--p": .4 }, [[A(2, 3.7), { "--p": .5 }, .15], [A(2, 4.3), { "--p": .6 }, .15]]);
      s.show(".htv .pair", [A(4, 2.0), A(4, 5.0)]);
      s.show(".htv .o1", [A(1, 2.1), A(1, 4.4)]);
      s.show(".htv .o2", [A(2, 6.6), A(2, 9.4)]);
      s.show(".htv .o3", [A(4, 5.2), A(4, 7.6)]);
      // the iPhone: address typed, then the code
      s.type(".hsf .u1", A(4, .3), A(4, 1.6), 17);
      s.show(".hsf .pcode", [A(4, 1.8), 40]);
      for (var i = 0; i < 6; i++) s.show(".hsf .bx i:nth-child(" + (i + 1) + ")", [A(4, 3.0 + .3 * i), 40], { f: .08, on: CODE.on, off: CODE.off });
      // the path between them
      s.show(".hpath .p1", [0, A(2, 2.6)], [A(2, 6.8), A(3, 0)]);
      s.show(".hpath .p2", [A(2, 2.6), A(2, 6.8)]);
      s.show(".hpath .p3", [A(3, 3.0), A(4, 0)]);
      s.show(".hpath .p4", [A(4, 0), 40]);
      s.show(".hpath .h1", [0, A(3, 0)], [A(4, 0), 40]);
      s.show(".hpath .h3", [A(3, 0), A(4, 0)]);
      s.show(".hpath .cloud", [A(3, .6), A(4, 0)]);
      s.show(".hpath .cloud .via", [A(3, .8), A(3, 2.6)]);
      // the cloud struck through and shaken: no middle server
      s.track(".hpath .cloud s", { textDecorationColor: "rgba(255,107,107,0)" }, [[A(3, 2.6), { textDecorationColor: "#ff6b6b" }, .2]]);
      s.track(".hpath .cloud>span", { x: 0 }, [[A(3, 2.6), { x: -4 }, .06], [A(3, 2.66), { x: 4 }, .06], [A(3, 2.72), { x: -3 }, .06], [A(3, 2.78), { x: 0 }, .06]]);
      s.show(".hpath .ln", [A(3, 3.0), A(4, 0)], {
        on: { boxShadow: "0px 0px 0px 3px rgba(61,220,132,.35), 0px 0px 18px 0px rgba(61,220,132,.5)" },
        off: { boxShadow: "0px 0px 0px 0px rgba(61,220,132,0), 0px 0px 0px 0px rgba(61,220,132,0)" }
      });
      // the dots flying along the path, up when the phone sends, down when the TV answers
      s.show(".lane.up", [A(1, 1.6), A(1, 2.0)], [A(1, 6.1), A(1, 6.7)], [A(2, 3.4), A(2, 5.6)], [A(3, 5.9), A(3, 6.5)],
             [A(4, 1.5), A(4, 1.9)], [A(4, 2.9), A(4, 4.8)], [A(4, 6.7), A(4, 8.4)], { f: .1 });
      s.show(".lane.dn", [A(1, 2.0), A(2, 1.3)], [A(2, 7.2), A(3, 0)], [A(3, 3.2), A(4, 0)], [A(4, 5.2), 40], { f: .1 });
      // the finger
      s.pointer(".hph .dfg", taps([
        tap(1.6, P(...ROW)), tap(A(1, 6.2), mir(...YT)),
        tap(A(2, 3.6), P(...VOLUP)), tap(A(2, 4.3), P(...VOLUP)), tap(A(2, 5.2), P(...RIGHT)),
        tap(A(3, 6.0), mir(...MV))
      ].concat("482913".split("").map(function(d, i){ return tap(A(4, 3.0 + .3 * i), key(d)); }),
               [tap(A(4, 6.8), P(...OK)), tap(A(4, 8.0), P(...RIGHT))])));
      // captions, in the order of .hcap's <p>s: [part, from, to]
      [[1, 0, 2], [1, 2, 6], [1, 6, 10], [2, 0, 2.6], [2, 2.6, 6.4], [2, 6.4, 10],
       [3, 0, 2.4], [3, 2.4, 6], [3, 6, 10], [4, 0, 2], [4, 2, 5], [4, 5, 10]].forEach(function(c, i){
        s.show(".hcap>:nth-child(" + (i + 1) + ")", [A(c[0], c[1]), A(c[0], c[2])], { f: .3, on: { opacity: 1, y: 0 }, off: { opacity: 0, y: 8 } });
      });
    }],

    "st-b": [20, 5, function(s){
      for (var k = 1; k <= 5; k++) s.steps(k);
      // the pages of the browser, one per step but the last two
      s.show(".b1", [0, 4]); s.show(".b2", [4, 8]); s.show(".b3", [8, 12]); s.show(".b4", [12, 20]);
      // 1: the Downloader search, "Chọn" on the remote
      s.type(".y1", .3, 1.2, 10);
      s.focus(".q1", [1.2, 1.5]); s.focus(".q2", [1.5, 4]);
      s.press(".q2", 1.6, 3.6);
      s.fill(".r1", 1.7, 3.0);
      s.show(".q2 .i1", [0, 3.1], { f: .05 }); s.show(".q2 .i2", [3.1, 4], { f: .05 });
      // 2: the address of the APK
      s.type(".y2", 4.3, 5.7, 7);
      s.focus(".q3", [5.8, 8]); s.press(".q3", 6.0);
      s.show(".dlr", [6.1, 8]); s.fill(".r2", 6.2, 7.6);
      // 3: install, with "unknown sources" switched on once
      s.show(".u3", [8.3, 9.6]); s.focus(".q5", [8.4, 9.6]); s.toggle(".g3", 9.0);
      s.focus(".q4", [9.6, 12]); s.press(".q4", 9.9);
      s.fill(".r3", 10.0, 11.2); s.show(".k3", [11.3, 12]);
      // 4: open it, allow the access, TV Setup says what is next
      s.focus(".q6", [12.3, 12.8]); s.press(".q6", 12.6);
      s.show(".u4", [12.8, 14.0]); s.focus(".q7", [12.9, 14]); s.toggle(".g4", 13.4);
      s.show(".b4 .st1", [12, 14.1], { f: .05 }); s.show(".b4 .st2", [14.1, 20], { f: .05 });
      s.show(".u5", [14.6, 20]);
      s.show(".o1", [18.6, 20]);
      // the TV's own remote, until the phone takes over
      s.press(".rc .rk", 1.6, 3.6, 6.0, 9.0, 9.9, 12.6, 13.4);
      s.press(".rc .rd", .5, .9, 4.4, 4.8, 5.2, 5.6, 9.4);
      s.show(".rc", [0, 16], rise(30));
      // 5: the phone scans the TV's QR, types the code
      s.show(".sph", [16.1, 20], rise(20));
      s.show(".e1", [16, 17.3]); s.show(".e2", [17.3, 18.5]); s.show(".e3", [18.5, 20]);
      for (var i = 0; i < 6; i++) s.show(".e2 .bx i:nth-child(" + (i + 1) + ")", [17.5 + .16 * i, 20], { f: .06, on: CODE.on, off: CODE.off });
      s.pointer(".sph .dfg", taps([tap(16.9, P(50, 124))].concat(
        "482913".split("").map(function(d, i){ return tap(17.5 + .16 * i, key(d)); }), [tap(19.4, P(...OK))])));
    }],

    "st-a": [16, 4, function(s){
      for (var k = 1; k <= 4; k++) s.steps(k);
      s.show(".a1", [0, 4]); s.show(".a2", [4, 8]); s.show(".a3", [8, 12]); s.show(".a4", [12, 16]);
      // 1: the store page, then its "Open"
      s.fill(".a1 .bar", .2, 1.4);
      s.show(".a1 .sheet", [1.8, 4], rise(40));
      s.fill(".a1 .sheet .bar", 2.5, 3.3); s.show(".a1 .sheet .ok", [3.4, 4]);
      // 2: looking for TVs
      s.press(".a2 .scan", 4.5);
      s.show(".a2 .rad", [4.6, 6.0]); s.show(".a2 .prow", [5.6, 8], { f: .2 });
      // 3: "Allow USB debugging?" on the TV
      s.show(".a3 .w1", [8, 10.5], { f: .1 }); s.show(".a3 .w2", [10.5, 12], { f: .1 });
      s.show(".stv .usb", [8.2, 10.6]);
      s.focus(".usb .q1", [8.8, 9.6]); s.focus(".usb .q2", [9.6, 10.6]);
      s.show(".usb .q1 em", [9.2, 16], { f: .05 });
      s.press(".usb .q2", 10.4);
      // 4: the TV's picture on the phone
      s.show(".stv .o1", [12.2, 14.2]);
      s.show(".stv .dfoc", [13.8, 14.2], { f: .12 });
      s.show(".stv .dyt", [14.2, 16]);
      s.show(".a4 .myt", [14.6, 16]);
      s.pointer(".sph .dfg", taps([tap(1.7, P(50, 62)), tap(2.4, P(72, 196)), tap(4.5, P(50, 39)), tap(6.8, P(...ROW)), tap(13.6, mir(...YT))]));
      s.press(".a1 .open", 1.7); s.press(".a1 .sheet .tb2", 2.4); s.press(".a2 .prow", 6.8);
    }],

    "st-c": [16, 4, function(s){
      for (var k = 1; k <= 4; k++) s.steps(k);
      s.show(".c1", [0, 4]); s.show(".c2", [4, 8]); s.show(".c3", [8, 12]); s.show(".c4", [12, 16]);
      // 1: the download, the file appearing
      s.fill(".c1 .bar", .3, 1.8); s.show(".c1 .ok", [1.9, 4]);
      s.show(".c1 .apic", [2.3, 4], { on: { opacity: 1, scale: 1 }, off: { opacity: 0, scale: .6 } });
      s.press(".c1 .apic", 3.0, 3.18);
      // 2: the app dragged into Applications, the folder's "Open" dialog
      s.track(".c2 .drag", { x: "0cqw", scale: 1, opacity: 1 }, [[4.4, { x: "45cqw", scale: .85, opacity: 1 }, 1.0], [5.4, { x: "45cqw", scale: .4, opacity: 0 }, .2]]);
      s.press(".c2 .fold", 5.4);
      s.show(".c2 .dlg", [5.8, 8]); s.press(".c2 .go", 7.2);
      // 3: the system's "unidentified developer" notice, "Open anyway"
      s.show(".c3 .more", [9.2, 12], { f: .15 }); s.press(".c3 .lnk", 9.0); s.press(".c3 .more", 10.4);
      // 4: the app looking for TVs, then "Allow USB debugging?" on the TV
      s.show(".c4 .sp1", [12, 12.5], { f: .1 }); s.show(".c4 .prow", [12.5, 16], { f: .2 }); s.press(".c4 .prow", 13.0);
      s.show(".c4 .wait", [13.1, 14.5], { f: .1 }); s.show(".c4 .mirw", [14.5, 16]);
      s.show(".stv .usb", [13.2, 14.4]); s.focus(".usb .q2", [13.6, 14.4]); s.show(".usb .q1 em", [0, 16]); s.press(".usb .q2", 14.2);
      s.show(".stv .o1", [14.5, 16]);
      // the cursor
      var ev = click(3.0, 50, 66).concat([[3.18, 50, 66, "press"], [3.3, 50, 66, "show"], [3.6, 60, 80, "hide"]]);
      ev = ev.concat(click(4.4, 25, 45), [[5.4, 70, 45, "move"], [5.6, 70, 45, "show"]], click(7.2, 64, 68, [70, 45]), [[7.7, 64, 68, "hide"]]);
      ev = ev.concat(click(9.0, 22, 58), click(10.4, 57, 86, [22, 58]), [[10.9, 57, 86, "hide"]]);
      ev = ev.concat(click(13.0, 50, 42), [[13.5, 50, 42, "hide"]]);
      s.pointer(".lap .cur", ev);
    }],

    "st-p": [12, 3, function(s){
      for (var k = 1; k <= 3; k++) s.steps(k);
      // 1: TV Setup opens Developer options by itself; ADB on
      s.press(".p2 .adb", 2.0); s.show(".p2 .dlg", [2.2, 3.2]); s.press(".p2 .dlg .go", 3.0); s.toggle(".p2 .adb .tg", 3.1);
      s.show(".o1", [3.3, 4]);
      // 2: no Developer options yet: Build pressed seven times
      s.show(".p1", [4, 8]);
      var build = [0, 1, 2, 3, 4, 5, 6].map(function(i){ return 4.5 + .3 * i; });
      s.press(".p1 .bld", ...build);
      [[build[3], build[4]], [build[4], build[5]], [build[5], build[6]]].forEach(function(w, i){   // "3 more taps", "2", "1"
        s.show(".p1 .left>:nth-child(" + (i + 1) + ")", [w[0] + .05, w[1]], { f: .05 });
      });
      s.show(".p1 .dev", [build[6] + .1, 8], rise(30));
      // 3: an operator's box asks for its own password at "Build": TV Setup opens Developer options by name instead
      s.show(".p3", [8, 10]);
      s.press(".p3 .bld", 8.4, 8.7, 9.0);
      s.show(".p3 .pw", [9.2, 10]);
      s.show(".p0", [0, 1.2], [10, 10.8]); s.show(".p2", [1.2, 4], [10.8, 12]);
      s.show(".o3", [10.9, 12]);
      s.press(".rc .rk", 2.0, 3.0, ...build, 8.4, 8.7, 9.0);
      s.press(".rc .rd", 1.6, 2.6, 4.2);
    }]
  };

  /* ---------- playback ---------- */

  var calm = matchMedia("(prefers-reduced-motion:reduce)");
  var made = [];                                                   // [Story]
  function storyOf(el){ return made.filter(function(s){ return s.el === el; })[0]; }

  // part k: from its start, playing; for reduced motion a still, 92% in unless data-still says where
  Story.prototype.go = function(k){
    var still = (this.el.dataset.still || "").split(",").filter(Boolean).map(Number)[k];
    this.held = false;
    if (calm.matches) this.tl.pause(k * this.L + (still != null ? still : this.L * .92));
    else this.tl.play(k * this.L);
  };

  document.querySelectorAll(".story").forEach(function(el){
    var name = Object.keys(STORIES).filter(function(n){ return el.classList.contains(n); })[0];
    if (!name) return;
    var def = STORIES[name], s = new Story(el, def[0], def[1]);
    def[2](s);
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

  // a tab shown again starts from the beginning
  document.querySelectorAll(".opt").forEach(function(o){
    o.addEventListener("click", function(){
      var s = storyOf(document.getElementById("p-" + o.dataset.tab));
      if (s) { s.held = false; s.tl.seek(0); if (calm.matches) s.go(0); }
    });
  });

  // for screenshots: stop one story at that second
  window.storySeek = function(el, sec){
    var s = storyOf(el); if (!s) return;
    s.held = true; s.tl.pause(sec);
  };
})();
