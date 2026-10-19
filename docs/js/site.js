(function(){
  var root = document.documentElement;
  var titles = {
    vi: "TV Remote: điều khiển Android TV từ điện thoại, iPhone, máy tính",
    en: "TV Remote: control Android TV from your phone, iPhone or computer"
  };
  function setLang(l){
    root.dataset.lang = l; root.lang = l; document.title = titles[l];
    document.querySelectorAll("img[data-lang-src]").forEach(function(i){ i.src = i.dataset.langSrc.replace("LANG", l); });
    document.querySelectorAll(".lang button").forEach(function(b){ b.setAttribute("aria-pressed", b.dataset.set === l); });
    try { localStorage.setItem("lang", l); } catch(e){}
  }
  setLang(root.dataset.lang);
  document.querySelectorAll(".lang button").forEach(function(b){ b.onclick = function(){ setLang(b.dataset.set); }; });

  // install tabs
  document.querySelectorAll(".opt").forEach(function(o){
    o.onclick = function(){
      document.querySelectorAll(".opt").forEach(function(x){ x.setAttribute("aria-selected", x === o); });
      document.querySelectorAll(".panel").forEach(function(p){ p.classList.toggle("on", p.id === "p-" + o.dataset.tab); });
      var p = document.getElementById("p-" + o.dataset.tab);
      if (p.getBoundingClientRect().top > innerHeight - 120) p.scrollIntoView({behavior:"smooth", block:"start"});
    };
  });

  // QR codes for the steps done on a phone ("here" = this page)
  document.querySelectorAll("[data-qr]").forEach(function(d){
    var u = d.dataset.qr === "here" ? (/^https?:$/.test(location.protocol) ? location.origin + location.pathname : "") : d.dataset.qr;
    var s = u && typeof qrSvg === "function" ? qrSvg(u, 112) : null;
    if (!s) { d.remove(); return; }
    d.prepend(s);
    d.addEventListener("click", function(){                            // small on the page: shown big to scan
      var dlg = document.getElementById("qrbig"), box = dlg.querySelector(".big");
      box.innerHTML = ""; box.appendChild(qrSvg(u, 340)); box.appendChild(d.querySelector("div").cloneNode(true));
      dlg.showModal();
    });
  });
  document.getElementById("qrbig").addEventListener("click", function(e){ if (e.target === this) this.close(); });   // a click outside closes it

  // copy codes
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

  // scroll reveal
  if ("IntersectionObserver" in window){
    var io = new IntersectionObserver(function(es){
      es.forEach(function(e){ if (e.isIntersecting){ e.target.classList.add("in"); io.unobserve(e.target); } });
    }, {rootMargin:"0px 0px -8% 0px"});
    document.querySelectorAll(".reveal").forEach(function(el){ io.observe(el); });
  } else document.querySelectorAll(".reveal").forEach(function(el){ el.classList.add("in"); });

  // the header's link for the section being read: the last one whose top has passed a third of the way down the screen
  (function(){
    var links = [].slice.call(document.querySelectorAll(".navlinks a, .navsheet a")), secs = links.map(function(a){ var h = a.getAttribute("href"); return h.charAt(0) === "#" ? document.querySelector(h) : null; }), busy = false;
    function mark(){
      busy = false;
      var y = innerHeight / 3, cur = null;
      secs.forEach(function(s){ if (s && s.getBoundingClientRect().top <= y) cur = s; });   // links in page order (the bar's, then the sheet's)
      if (cur && cur.getBoundingClientRect().bottom < 0) cur = null;                     // past the last one (the closing part)
      links.forEach(function(a, i){ a.classList.toggle("on", !!cur && secs[i] === cur); });   // the bar's link and the sheet's both; none at the top (guide.html has no section here)
    }
    var prog = document.querySelector(".navprog");
    function bar(){                                                    // reading progress along the header's bottom edge
      var max = document.documentElement.scrollHeight - innerHeight;
      prog.style.setProperty("--p", max > 0 ? Math.min(1, Math.max(0, scrollY / max)).toFixed(4) : 0);
    }
    function tick(){ mark(); bar(); }
    addEventListener("scroll", function(){ if (!busy){ busy = true; requestAnimationFrame(tick); } }, {passive:true});
    addEventListener("resize", bar);
    tick();
  })();

  // the narrow screens' menu: the same links in a sheet under the header; closes on a tap on a link, outside, or Escape
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
    matchMedia("(min-width:981px)").addEventListener("change", function(m){ if (m.matches) set(false); });
  })();

  document.getElementById("yr").textContent = new Date().getFullYear();

  // the computer app: the file for this computer on the big button (one Mac app runs on both chips)
  (function(){
    var ua = navigator.userAgent, os = /Windows/.test(ua) ? "windows-x64" : /Mac/.test(ua) && !/iPhone|iPad/.test(ua) ? "mac" : /Linux/.test(ua) && !/Android/.test(ua) ? (/aarch64|arm64/.test(ua) ? "linux-arm64" : "linux-x64") : "";
    function pick(o){
      var a = document.querySelector('.dl-all a[data-os="' + o + '"]'); if (!a) return;
      document.querySelectorAll(".dl-all a").forEach(function(x){ x.classList.toggle("mine", x === a); });
      document.getElementById("dl-pc").href = a.href; document.getElementById("dl-os").innerHTML = ' <span class="vi">cho</span><span class="en">for</span> ' + a.innerHTML;
    }
    pick(os);
  })();

  // latest release tag in the hero pill; the page still reads fine if GitHub is unreachable
  fetch("https://api.github.com/repos/HoangTran0410/tv-remote/releases/latest")
    .then(function(r){ return r.ok ? r.json() : null; })
    .then(function(j){ if (j && j.tag_name) document.getElementById("ver").textContent = j.tag_name.replace(/^v?/, "v"); })
    .catch(function(){});
})();
