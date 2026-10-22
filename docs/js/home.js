/* The home page: language, copy a code, the download for this device, the release number, a gentle fade-in on scroll. */
(function(){
  var root = document.documentElement;
  var titles = {
    vi: "TV Remote: điều khiển Android TV từ điện thoại, iPhone, máy tính",
    en: "TV Remote: control Android TV from your phone, iPhone or computer"
  };
  function setLang(l){
    root.dataset.lang = l; root.lang = l; document.title = titles[l];
    document.querySelectorAll("img[data-lang-src]").forEach(function(i){ LI(i); });
    document.querySelectorAll(".lang button").forEach(function(b){ b.setAttribute("aria-pressed", b.dataset.set === l); });
    try { localStorage.setItem("lang", l); } catch(e){}
  }
  setLang(root.dataset.lang);
  document.querySelectorAll(".lang button").forEach(function(b){ b.onclick = function(){ setLang(b.dataset.set); }; });

  // the narrow screens' menu, as on the guide: closes on a tap on a link, outside, or Escape
  (function(){
    var btn = document.querySelector(".menubtn"), sheet = document.getElementById("navsheet");
    function set(open, back){
      sheet.hidden = !open; btn.setAttribute("aria-expanded", open);
      if (!open && back) btn.focus();
    }
    btn.onclick = function(){ set(sheet.hidden); if (!sheet.hidden) sheet.querySelector("a").focus({preventScroll:true}); };
    sheet.addEventListener("click", function(e){ if (e.target.closest("a")) set(false); });
    document.addEventListener("click", function(e){ if (!sheet.hidden && !e.target.closest("nav")) set(false); });
    document.addEventListener("keydown", function(e){ if (e.key === "Escape" && !sheet.hidden) set(false, true); });
    matchMedia("(min-width:561px)").addEventListener("change", function(m){ if (m.matches) set(false); });
  })();

  // copy the Downloader code
  var toast = document.getElementById("toast"), tt;
  function say(s){ toast.textContent = s; toast.classList.add("show"); clearTimeout(tt); tt = setTimeout(function(){ toast.classList.remove("show"); }, 1800); }
  document.querySelectorAll("[data-copy]").forEach(function(b){
    b.onclick = function(){
      var v = b.dataset.copy, en = root.dataset.lang === "en";
      (navigator.clipboard ? navigator.clipboard.writeText(v) : Promise.reject())
        .then(function(){ say((en ? "Copied " : "Đã chép ") + v); })
        .catch(function(){ say((en ? "Code: " : "Mã: ") + v); });
    };
  });

  // fade in on scroll (nothing moves for people who asked for less motion: site.css)
  var rv = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window){
    var io = new IntersectionObserver(function(es){
      es.forEach(function(e){ if (e.isIntersecting){ e.target.classList.add("in"); io.unobserve(e.target); } });
    }, {rootMargin:"0px 0px -8% 0px"});
    rv.forEach(function(el){ io.observe(el); });
  } else rv.forEach(function(el){ el.classList.add("in"); });

  // the row: buttons, arrow keys and the "3 / 8" read the same scroll position; nothing moves by itself
  (function(){
    var t = document.getElementById("car"); if (!t) return;
    var cards = t.querySelectorAll(".fc"), n = document.getElementById("car-n"), pv = document.getElementById("car-prev"), nx = document.getElementById("car-next");
    var calm = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
    function step(){ return cards.length > 1 ? cards[1].offsetLeft - cards[0].offsetLeft : t.clientWidth; }
    function upd(){
      var i = Math.min(cards.length - 1, Math.round(t.scrollLeft / step()));
      n.textContent = (i + 1) + " / " + cards.length;
      pv.disabled = t.scrollLeft < 4; nx.disabled = t.scrollLeft > t.scrollWidth - t.clientWidth - 4;
    }
    // our own glide, not scrollBy smooth: that one fought the mandatory snap (a stutter at the end) and a second press started
    // from wherever the first had got to, landing between cards; reduced motion still slides, only shorter (a jump lost people)
    var glide = null;
    function max(){ return t.scrollWidth - t.clientWidth; }
    function to(i){
      var s = step(), last = Math.ceil(max() / s - 0.01); i = Math.max(0, Math.min(i, last));
      var from = t.scrollLeft, end = Math.min(i * s, max()), ms = calm ? 220 : 420, t0 = performance.now();
      if (glide) cancelAnimationFrame(glide.raf);
      glide = {i: i}; t.classList.add("glide");
      (function f(now){
        var k = Math.min(1, (now - t0) / ms), e = 1 - Math.pow(1 - k, 3);   // ease-out cubic
        t.scrollLeft = from + (end - from) * e;
        if (k < 1) glide.raf = requestAnimationFrame(f); else { glide = null; t.classList.remove("glide"); }
      })(t0);
    }
    function go(d){ to((glide ? glide.i : Math.round(t.scrollLeft / step())) + d); }
    pv.addEventListener("click", function(){ go(-1); }); nx.addEventListener("click", function(){ go(1); });
    t.addEventListener("keydown", function(e){
      if (e.target !== t) return;
      if (e.key === "ArrowRight"){ e.preventDefault(); go(1); } else if (e.key === "ArrowLeft"){ e.preventDefault(); go(-1); }
    });
    // a mouse drags the row like a finger (touch already swipes it): snap and smooth off while held, a flick carries on to the
    // card it points at; a drag is never also a click on the card under it
    var drag = null, moved = false;
    t.addEventListener("pointerdown", function(e){
      if (e.pointerType !== "mouse" || e.button !== 0) return;
      if (glide){ cancelAnimationFrame(glide.raf); glide = null; t.classList.remove("glide"); }
      drag = {x: e.clientX, left: t.scrollLeft, id: e.pointerId, lx: e.clientX, lt: e.timeStamp, v: 0}; moved = false;
    });
    t.addEventListener("pointermove", function(e){
      if (!drag || e.pointerId !== drag.id) return;
      var dx = e.clientX - drag.x;
      if (!moved){ if (Math.abs(dx) < 6) return; moved = true; t.classList.add("drag"); t.setPointerCapture(drag.id); }
      t.scrollLeft = drag.left - dx;
      var dt = e.timeStamp - drag.lt; if (dt > 0) drag.v = (e.clientX - drag.lx) / dt;   // px per ms, for the flick
      drag.lx = e.clientX; drag.lt = e.timeStamp;
    });
    function let_go(e){
      if (!drag) return;
      var v = e.timeStamp - drag.lt > 120 ? 0 : drag.v; drag = null;   // held still before letting go: no flick
      if (!moved) return;
      t.classList.remove("drag");
      var s = step(), at = t.scrollLeft / s;
      var i = Math.abs(v) > 0.3 ? (v < 0 ? Math.ceil(at) : Math.floor(at)) : Math.round(at);   // a flick goes on to the next card in its way, never two
      to(i);
    }
    t.addEventListener("pointerup", let_go); t.addEventListener("pointercancel", let_go);
    t.addEventListener("click", function(e){ if (moved){ e.preventDefault(); e.stopPropagation(); moved = false; } }, true);
    t.addEventListener("dragstart", function(e){ e.preventDefault(); });   // the pictures and links would start a native drag instead
    t.addEventListener("scroll", function(){ requestAnimationFrame(upd); }, {passive:true});
    window.addEventListener("resize", upd); upd();
  })();

  document.getElementById("yr").textContent = new Date().getFullYear();

  // the big button gets the file for this device; where there is none (iPhone, iPad) it goes to the steps
  (function(){
    var ua = navigator.userAgent, os = /Android/.test(ua) ? "android" : /Windows/.test(ua) ? "windows-x64" : /Mac/.test(ua) && !/iPhone|iPad/.test(ua) && !(navigator.maxTouchPoints > 1) ? "mac" : /Linux/.test(ua) ? (/aarch64|arm64/.test(ua) ? "linux-arm64" : "linux-x64") : "";
    var a = os && document.querySelector('#dl-all a[data-os="' + os + '"]'); if (!a) return;
    document.querySelectorAll("#dl-all a").forEach(function(x){ x.classList.toggle("mine", x === a); });
    document.getElementById("dl-main").href = a.href;
    document.getElementById("dl-main-os").innerHTML = ' <span class="vi">cho</span><span class="en">for</span> ' + a.textContent.trim();
  })();

  // latest release tag in the pill; the page reads fine if GitHub is unreachable
  fetch("https://api.github.com/repos/HoangTran0410/tv-remote/releases/latest")
    .then(function(r){ return r.ok ? r.json() : null; })
    .then(function(j){ if (j && j.tag_name) document.getElementById("ver").textContent = j.tag_name.replace(/^v?/, "v"); })
    .catch(function(){});
})();
