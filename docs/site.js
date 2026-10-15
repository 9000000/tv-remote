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

  // the demo (CSS keyframes on one --T clock; the stories are GSAP, in stories.js): it runs only while on screen, a
  // chapter button plays from there. Reduced motion: no playing, each chapter shows its still (seconds into the chapter)
  (function(){
    var calm = matchMedia("(prefers-reduced-motion:reduce)"), stills = [8.5, 4.8, 4, 6.5];
    function go(el, k, n){                                             // chapter k of n
      var q = parseFloat(getComputedStyle(el).getPropertyValue("--T")) / n;
      if (calm.matches) { el.style.setProperty("--d", -(k * q + stills[k]) + "s"); return; }
      el.style.setProperty("--d", -(k * q) + "s"); el.classList.add("rs"); void el.offsetWidth; el.classList.remove("rs");
    }
    document.querySelectorAll(".demo").forEach(function(el){
      if ("IntersectionObserver" in window) new IntersectionObserver(function(es){ el.classList.toggle("run", es[0].isIntersecting); }, {threshold:.2}).observe(el);
      else el.classList.add("run");
      var parts = el.querySelectorAll(".dchap button"), n = parts.length;
      parts.forEach(function(b, k){ b.addEventListener("click", function(){ go(el, k, n); }); });
      if (calm.matches && n) go(el, 0, n);
    });
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
