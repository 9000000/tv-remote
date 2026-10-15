/* The intro page's hero demo (.demo at the top of #features) as one GSAP timeline: four chapters of 10 s, the phone and
   the TV on the same clock. Its look is demo.css, its markup index.html (the captions' text too: .dcap's <p>s, in the
   order of CAPS below); the runtime is anim.js. Times are seconds from the loop's start, `A(k, s)` is second s of
   chapter k. Edit the times here, there is nothing to generate. */
(function(){
  "use strict";
  if (!window.Anim) return;
  var tap1 = function(t, x, y){                                    // a finger tapping: in from below, squeezed, away
    return [[t - .6, x + 6, y + 8, "hide"], [t - .3, x, y, "show"], [t, x, y, "press"], [t + .2, x, y, "show"], [t + .55, x + 5, y + 6, "hide"]];
  };

  Anim.define("demo", 40, 4, function(s){
    var A = function(k, t){ return s.at(k, t); };
    var ON = { opacity: 1 }, OFF = { opacity: 0 };
    var SNAP = function(v){ var o = {}; o[v] = 1; return o; };
    s.back = { at: 38.8, dur: .4 };                                // what ends elsewhere is back to its start by 39.2
    s.chapters();

    /* ---------- the stage: which TV, which phone screen ---------- */
    s.track(".dtv", ON, [[29.84, OFF, .4], [39.6, ON, .4]]);       // gives way to the four TVs in chapter 4
    s.track(".dq1", ON, [[9.6, OFF, .32], [39.68, ON, .32]]);      // the phone's four screens, one per chapter
    s.track(".dq2", OFF, [[9.68, ON, .32], [19.6, OFF, .32]]);
    s.track(".dq3", OFF, [[19.68, ON, .32], [29.6, OFF, .32]]);
    s.track(".dq4", OFF, [[29.68, ON, .32], [39.6, OFF, .32]]);
    s.track(".dst", ON, [[A(3, 1), OFF, .3], [29.92, ON, .08]]);   // the phone's status bar, not on its lock screen
    s.track(".dwall", { opacity: 0, scale: .95 }, [[30, { opacity: 1, scale: 1 }, .4], [39.44, { opacity: 0, scale: 1 }, .32]], true);

    /* ---------- ① Optimize TV ---------- */
    // the stock home screen (an ad, suggestions, a spinner) and the light one swapped in; the clock reads 21:30 after chapter 3's skip
    s.track(".dblt", { opacity: 1, scale: 1 }, [[A(1, 5.5), { opacity: 0, scale: 1.05 }, .7]]);
    s.track(".dblt .ad", { opacity: 1, filter: "grayscale(0)" }, [[A(1, 2.9), { opacity: .2, filter: "grayscale(1)" }, .5]]);
    s.track(".dlag", ON, [[A(1, 2.6), OFF, .3]]);                  // the spinner of a slow TV
    for (var i = 0; i < 6; i++)                                    // the suggested tiles drop out one by one
      s.track(".dblt .sg i:nth-child(" + (i + 1) + ")", { opacity: 1, scale: 1, filter: "grayscale(0)" },
              [[A(1, 2.9 + i * .25), { opacity: 0, scale: .6, filter: "grayscale(1)" }, .35]]);
    s.track(".dcln .ck .t1", ON, [[A(3, 6), OFF, .1]]);
    s.track(".dcln .ck .t2", OFF, [[A(3, 6), ON, .1]]);

    // the phone: free RAM climbing, the button, the checklist, the undo line
    s.track(".dp1 .ram b", { "--dm": 312, color: "#ff6b6b" }, [[A(1, 2), { "--dm": 1148, color: "#3ddc84" }, 4]], false, SNAP("--dm"));
    s.track(".dp1 .ram em", { "--p": .16, color: "#ff6b6b" }, [[A(1, 2), { "--p": .58, color: "#3ddc84" }, 4]]);   // its bar draws in currentColor
    s.track(".dp1 .go", { scale: 1 }, [[1.4, { scale: .95 }, .15], [1.55, { scale: 1 }, .25]]);
    s.track(".dp1 .go", { backgroundColor: "#5b8cff" }, [[1.4, { backgroundColor: "#8fb0ff" }, .15], [1.55, { backgroundColor: "#232b3d" }, .25], [5.8, { backgroundColor: "#173826" }, .2]]);
    s.track(".dp1 .go", { color: "#06112b" }, [[1.4, { color: "#c9d1e0" }, .4], [5.8, { color: "#3ddc84" }, .2]]);
    s.track(".dp1 .go .g1", ON, [[1.7, OFF, .05]]);                // "Optimize now" -> "Optimizing…" -> "Done"
    s.track(".dp1 .go .g2", OFF, [[1.7, ON, .05], [5.9, OFF, .05]]);
    s.track(".dp1 .go .g3", OFF, [[5.9, ON, .05]]);
    [[1.8, 2.8], [2.8, 4.5], [4.5, 5.8]].forEach(function(r, i){   // each row: appears, spins, ticks
      var row = ".dp1 .c" + (i + 1);
      s.track(row, { opacity: 0, "--ty": 3 }, [[r[0], { opacity: 1, "--ty": 0 }, .3]]);
      s.track(row + " u", ON, [[r[1] - .05, OFF, .05]]);
      s.track(row + " i", { "--o": 0, "--sc": .4 }, [[r[1], { "--o": 1, "--sc": 1.2 }, .2], [r[1] + .2, { "--o": 1, "--sc": 1 }, .2]]);
    });
    s.track(".dp1 .cl .dn", { "--dn": 0 }, [[2.9, { "--dn": 23 }, 1.5, "none"]], false, SNAP("--dn"));   // "23 suggested apps off"
    s.track(".dp1 .un", { opacity: 0, "--ty": 3 }, [[6.1, { opacity: 1, "--ty": 0 }, .4]]);

    /* ---------- ② help grandma from far away: draw, she presses ---------- */
    s.track(".dyt", { opacity: 0, scale: .94 }, [[A(2, 6.5), { opacity: 1, scale: 1 }, .4], [A(3, 5.1), { opacity: 0, scale: .94 }, .4]]);
    s.track(".dyt .pl", { "--p": .04 }, [[A(2, 6.6), { "--p": .55 }, 8.6, "none"]]);     // a film playing, until the limit
    var F = [{ opacity: 0, scale: 1 }, { opacity: 1, scale: 1.1 }, { opacity: 1, scale: 1 }, { opacity: 0, scale: 1.1 }];
    s.track(".dfoc", F[0], [[A(2, 5.4), F[1], .3], [A(2, 6.05), F[2], .15], [A(2, 6.2), F[1], .15], [A(2, 6.35), F[3], .45]]);   // her focus lands on YouTube, twice
    function draw(sel, t, dur){                                    // a stroke: appears, then draws (dash offset 100 -> 0), then goes
      var a = { "--o": 100, opacity: 1 }, b = { "--o": 0, opacity: 1 }, c = { "--o": 0, opacity: 0 };
      s.track(sel, { "--o": 100, opacity: 0 }, [[t, a, .02], [t + .02, b, dur, "none"], [A(2, 6.2), c, .4]]);
    }
    draw(".mir .ddraw .c", A(2, 2.4), 1.78); draw(".mir .ddraw .a", A(2, 4.4), .58);    // on the phone's picture
    draw(".dtv .ddraw .c", A(2, 2.65), 1.78); draw(".dtv .ddraw .a", A(2, 4.65), .58);  // a beat later on grandma's TV
    s.track(".dp2 .pen", { scale: 1 }, [[A(2, 1.1), { scale: .9 }, .15], [A(2, 1.25), { scale: 1 }, .15]]);
    s.track(".dp2 .pen", { backgroundColor: "#1f2738", color: "#c9d1e0" },
            [[A(2, 1.1), { backgroundColor: "#ffd23f", color: "#1a1400" }, .3], [A(2, 6.4), { backgroundColor: "#1f2738", color: "#c9d1e0" }, .4]]);

    /* ---------- ③ the TV minds the kids: limit, notice, bedtime ---------- */
    s.track(".dlim", { opacity: 0, "--ty": -2 }, [[A(3, 1.1), { opacity: 1, "--ty": 0 }, .4], [A(3, 5.1), { opacity: 0, "--ty": 0 }, .3]]);
    s.track(".dlim .cn", { "--ds": 52, opacity: 1 }, [[A(3, 1.5), { "--ds": 59, opacity: 1 }, 1.5, "none"], [A(3, 3), { "--ds": 59, opacity: 0 }, .05]], false, SNAP("--ds"));
    s.show(".dlim .full", [A(3, 3.05), 40], { f: .05 });           // 59:52 .. 59:59, then "60:00"
    function warn(sel, a, b, out, bar){                            // the notice slides up, its bar counts down
      s.track(sel, { opacity: 0, yPercent: 130 }, [[a, { opacity: 1, yPercent: 0 }, .4], [b, { opacity: 0, yPercent: 0 }, out]]);
      s.track(sel + " i", { "--p": 1 }, [[a + .4, { "--p": 0 }, bar, "none"]]);
    }
    warn(".dwarn.w3", A(3, 3.2), A(3, 5.1), .3, 1.5);
    warn(".dwarn.w4", A(3, 6.9), A(3, 8.6), .2, 1.3);
    s.show(".dff", [A(3, 6), A(3, 6.8)], { f: .3 });               // skipped ahead to 21:30
    s.track(".doff", OFF, [[A(3, 8.8), ON, .5], [30, OFF, .2]]);   // standby
    s.track(".dp3 .lock", OFF, [[A(3, .9), ON, .4]]);              // Mom's phone goes dark

    /* ---------- ④ four TVs at once ---------- */
    var DONE = [4.6, 5.6, 4.0, 5.2], READY = [9.0, 9.5, 8.7, 9.2];  // seconds into chapter 4: installed, back from restart
    var CB0 = { backgroundColor: "rgba(91,140,255,0)", borderColor: "#4a5470", color: "rgba(6,17,43,0)" };
    var CB1 = { backgroundColor: "#5b8cff", borderColor: "#5b8cff", color: "#06112b" };
    DONE.forEach(function(d, i){
      var n = i + 1, m = ".mtv:nth-child(" + n + ") ", f = ".dp4 .f" + n + " ", ins = A(4, 2.3), inst = A(4, d), r = A(4, READY[i]);
      var bar = m + ".pg, " + f + "em";                            // the install bar, on the TV and on the phone's row
      s.track(bar, OFF, [[ins, ON, .2], [inst, OFF, .2]]);
      s.track(bar, { "--p": 0 }, [[ins + .2, { "--p": 1 }, inst - ins - .2, "none"]]);
      s.track(m + ".ok", { opacity: 0, scale: .3 }, [[inst, { opacity: 1, scale: 1.15 }, .2], [inst + .2, { opacity: 1, scale: 1 }, .2], [inst + 1.3, { opacity: 0, scale: 1 }, .3]]);
      s.track(m + ".t i:last-child", { opacity: 0, scale: .3 }, [[inst + .2, { opacity: 1, scale: 1 }, .3]], true);   // the new tile stays
      s.track(m + ".bo", OFF, [[A(4, 7.5), ON, .2], [r, OFF, .3]]);  // the restart: a black screen with a spinner
      s.track(f + ".cb", CB0, [[A(4, 1.05 + i * .1), CB1, .15], [39.6, CB0, .4]]);   // the row's box ticked by "Select all"
      [["sa", 30, A(4, 2.3)], ["sb", A(4, 2.3), inst], ["sc", inst, A(4, 7.5)], ["se", A(4, 7.5), r], ["sf", r, 40]].forEach(function(w){
        s.track(f + "." + w[0], OFF, [[w[1], ON, .05], [w[2] - .05, OFF, .05]]);   // the row's status text, one at a time
      });
    });
    s.track(".dp4 .dhd .all", { scale: 1 }, [[A(4, .9), { scale: .88 }, .15], [A(4, 1.05), { scale: 1 }, .2]]);
    function button(sel, a, b){                                    // pressed, held lit, then let go
      var base = { scale: 1, backgroundColor: "#1f2738", color: "#c9d1e0" };
      s.track(sel, base, [[a, { scale: .92, backgroundColor: "#5b8cff", color: "#06112b" }, .15], [a + .15, { scale: 1, backgroundColor: "#5b8cff", color: "#06112b" }, .2], [b, base, .3]]);
    }
    button(".dp4 .act .in", A(4, 1.85), A(4, 5.7));
    button(".dp4 .act .rb", A(4, 7.05), A(4, 9.5));

    /* ---------- between the phone and the TV: its touches flying across ---------- */
    s.track(".dbeam", OFF, [[1.5, ON, .15], [5.8, OFF, .15], [11.3, ON, .15], [15.1, OFF, .15], [32, ON, .15], [36, OFF, .15], [37.2, ON, .15], [39.3, OFF, .15]]);

    /* ---------- the finger on each chapter's phone screen ---------- */
    var PY = Anim.PY;
    function ph(x, y){ return [4 + .92 * x, (32 + .92 * y) / PY]; }   // a spot on the TV (0-100 wide) in the phone's picture
    function bez(p0, p1, p2, p3, n){
      var pts = [];
      for (var j = 1; j <= n; j++){
        var t = j / n, u = 1 - t;
        pts.push([u*u*u*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t*t*t*p3[0], u*u*u*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t*t*t*p3[1]]);
      }
      return pts;
    }
    var C = [[[21.5, 25.6], [15, 23.6], [4.5, 24.6], [3.2, 31]], [[3.2, 31], [2, 37], [8, 40.6], [14.5, 40.4]],
             [[14.5, 40.4], [22, 40.2], [25.6, 36.4], [25, 31.6]], [[25, 31.6], [24.6, 28.6], [22.6, 26.6], [19.4, 25.9]]];
    var circle = [C[0][0]].concat(...C.map(function(c){ return bez(c[0], c[1], c[2], c[3], 4); }));
    var arrow = [[40, 14.5], [33.5, 17.5], [26.6, 23.6]];
    s.pointer(".dp1 .dfg", tap1(1.5, 50, 30.7));
    var ev = tap1(A(2, 1.25), 14, 42.7), p0 = ph(circle[0][0], circle[0][1]), q0 = ph(arrow[0][0], arrow[0][1]);
    ev.push([A(2, 1.9), p0[0], p0[1], "hide"], [A(2, 2.25), p0[0], p0[1], "show"]);
    circle.forEach(function(p, j){ var x = ph(p[0], p[1]); ev.push([A(2, 2.4) + 1.8 * j / (circle.length - 1), x[0], x[1], "press"]); });
    ev.push([A(2, 4.25), q0[0], q0[1], "show"]);
    arrow.forEach(function(p, j){ var x = ph(p[0], p[1]); ev.push([A(2, 4.4) + .6 * j / (arrow.length - 1), x[0], x[1], "press"]); });
    var end = ph(30, 22);
    ev.push([A(2, 5.25), end[0], end[1], "hide"]);
    s.pointer(".dp2 .dfg", ev);                                    // taps the pen, circles YouTube, draws the arrow
    s.pointer(".dp4 .dfg", tap1(31.05, 86, 7.6).concat(tap1(32, 26.75, 48), tap1(37.2, 73.25, 48)));   // "All", "Install APK", "Restart"

    /* ---------- the caption under the TV: [chapter, from, to], in the order of .dcap's <p>s ---------- */
    var CAPS = [[1, 0, 2], [1, 2, 6], [1, 6, 10], [2, 0, 2.2], [2, 2.2, 5.6], [2, 5.6, 10],
                [3, 0, 1.3], [3, 1.3, 5.6], [3, 5.6, 10], [4, 0, 2], [4, 2, 7.1], [4, 7.1, 10]];
    var IN = { opacity: 1, y: 0 }, UP = { opacity: 0, y: -6 };
    CAPS.forEach(function(c, i){
      var a = A(c[0], c[1]), b = A(c[0], c[2]), sel = ".dcap>:nth-child(" + (i + 1) + ")";
      if (i === 0) s.track(sel, IN, [[b - .3, UP, .3], [39.76, IN, .24]], true);   // the loop's first one is also showing as it wraps
      else s.track(sel, { opacity: 0, y: 8 }, [[a, IN, .3], [b - .3, UP, .3]], true);
    });
  });
})();
