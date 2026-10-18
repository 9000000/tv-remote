/* The guide's four animations: hand-written timelines in the way of stories.js, run by anim.js. The stage is drawn at
   560x340 and scaled to the box it sits in, so the same drawing reads on a phone and on a desktop. */
(function(){
  "use strict";
  if (!window.Anim) return;
  var T = 12, PARTS = 4;
  function chain(s){ for (var k = 1; k <= PARTS; k++) s.steps(k); }

  Anim.define("ga-pair", T, PARTS, function(s){
    chain(s);
    s.show(".tp1", [0, 5.3]);                       // the TV: address first, then the code
    s.show(".tp2", [5.3, T]);
    s.show(".pp1", [0, 9.3]);                       // the phone: the pairing page, then the remote
    s.show(".pp2", [9.3, T]);
    s.press(".bshow", 4.8);
    for (var i = 1; i <= 6; i++) s.show(".pd" + i, [6.2 + (i - 1) * .4, 9.3], { f: .1 });
    s.press(".bok", 8.9);
    s.show(".ga-ok", [9.6, 11.6]);
  });

  Anim.define("ga-conn", T, PARTS, function(s){
    chain(s);
    s.press(".bcon", 2.2);
    s.show(".pc1", [0, 3.2]);
    s.show(".pc2", [3.2, 9.5]);
    s.show(".pc3", [9.5, T]);
    s.show(".tc1", [0, 3.2], [8.9, T]);
    s.show(".tc2", [3.2, 8.9]);
    s.show(".ga-cb em", [6.2, 8.9], { f: .1 });
    s.show(".ga-ring", [7.2, 8.9], { f: .1 });
    s.press(".ga-okb", 8.5);
  });

  Anim.define("ga-pad", T, PARTS, function(s){
    chain(s);
    s.show(".ga-tst", [.3, 2.5]);
    s.press(".kr", 3.6); s.press(".kd", 5.7); s.press(".ka", 8.0); s.press(".kb", 9.7);
    s.show(".f1", [0, 3.5], { f: .1 });
    s.show(".f2", [3.5, 5.6], { f: .1 });
    s.show(".f5", [5.6, 8.2], [9.8, T], { f: .1 });
    s.show(".ga-opn", [8.2, 9.8]);
  });

  Anim.define("ga-ha", T, PARTS, function(s){
    chain(s);
    s.toggle(".ga-tg", 1.4);
    s.show(".ph1", [0, 6]);
    s.show(".ph2", [6, T]);
    s.press(".bcp", 4.2);
    s.show(".ga-cpt", [4.4, 5.9]);
    s.press(".brun", 8.0);
    s.show(".th1", [0, 8.4]);
    s.show(".th2", [8.4, T]);
  });

  // scale each stage to its box; replay buttons
  function fit(box){
    var st = box.querySelector(".ga-stage");
    function go(){ st.style.setProperty("--s", (box.clientWidth / 560).toFixed(4)); }
    go();
    if (window.ResizeObserver) new ResizeObserver(go).observe(box); else addEventListener("resize", go);
  }
  document.querySelectorAll(".ga").forEach(function(el){
    fit(el.querySelector(".ga-box"));
    var b = el.querySelector(".ga-replay");
    if (Anim.calm.matches) { b.hidden = true; return; }
    b.addEventListener("click", function(){
      var s = Anim.of(el); if (!s) return;
      s.held = false; s.tl.restart();
    });
  });
})();
