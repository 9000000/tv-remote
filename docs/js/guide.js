(function(){
  "use strict";
  var root = document.documentElement;
  var TITLES = { vi: "Hướng dẫn dùng TV Remote", en: "TV Remote guide: step by step" };
  var PREV = { vi: "Trước", en: "Previous" }, NEXT = { vi: "Tiếp", en: "Next" };
  var FOR = { vi: "Mục này viết cho bản ", en: "Written for version " };
  var HOLD = { vi: "Tìm trong hướng dẫn…", en: "Search the guide…" };
  var NONE = { vi: "Không thấy mục nào khớp. Thử từ khác, ví dụ “adb” hay “hẹn giờ”.", en: "Nothing matches. Try another word, such as “adb” or “sleep timer”." };
  var q = document.getElementById("gq"), res = document.getElementById("gres"), dlg = document.getElementById("gdlg");
  var side = document.getElementById("gside"), back = document.querySelector(".gback"), menuBtn = document.querySelector(".gmenu");
  var topics = [].slice.call(document.querySelectorAll(".topic")), groups = [].slice.call(document.querySelectorAll(".ggroup"));
  var index = [], sel = -1, shown = [];

  /* ---------- "since which version": data/since.json (topic id -> since, and setup / phone / pc minimums), dates by version ---------- */
  var SINCE = null;
  var S_WORDS = { vi: { since: "Có từ ", next: "Bản tới", setup: "TV Setup ≥ ", phone: "App điện thoại ≥ ", pc: "App máy tính ≥ " },
                  en: { since: "Since ", next: "Next release", setup: "TV Setup ≥ ", phone: "Phone app ≥ ", pc: "Computer app ≥ " } };
  var MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  function fmtDate(iso, lang){
    var m = /^(\d{4})-(\d\d)-(\d\d)$/.exec(iso || ""); if (!m) return "";
    return lang === "vi" ? m[3] + "/" + m[2] + "/" + m[1] : MONTHS[+m[2] - 1] + " " + +m[3] + ", " + m[1];
  }
  function sinceHtml(id, lang){
    var e = SINCE && SINCE.topics[id]; if (!e) return "";
    var w = S_WORDS[lang], v = e.since, d = fmtDate(SINCE.dates[v], lang);
    var first = v === "next" ? '<span>' + w.next + '</span>'
      : w.since + '<a href="https://github.com/HoangTran0410/tv-remote/releases/tag/v' + esc(v) + '">' + esc(v) + '</a>' + (d ? ' <span class="gsd">(' + d + ')</span>' : "");
    var parts = [first];
    ["setup", "phone", "pc"].forEach(function(k){ if (e[k]) parts.push(w[k] + esc(e[k])); });
    return parts.map(function(x){ return "<span>" + x + "</span>"; }).join("");
  }
  function paintSince(t, lang){
    var el = t.querySelector(".gsince");
    if (!el){ el = document.createElement("div"); el.className = "gsince"; t.querySelector("h3").after(el); }
    el.innerHTML = sinceHtml(t.id, lang); el.hidden = !!SINCE && !el.innerHTML; /* until since.json arrives the line keeps its room (no layout shift) */
  }
  fetch("data/since.json").then(function(r){ return r.json(); }).then(function(j){ SINCE = j; if (cur) show(cur); }).catch(function(){});

  /* A table of three or more columns stacked on a phone loses its head row: each cell carries its column's name to show in
     its place (CSS ::before, so search does not read it twice). A grid of ticks (empty corner) shows the ticked names as chips. */
  document.querySelectorAll(".gtab .cmp").forEach(function(t){
    var th = t.querySelectorAll("thead th");
    if (th.length < 3) return;
    t.classList.add("wide");
    if (!th[0].textContent.trim()) t.classList.add("ticks");
    t.querySelectorAll("tbody tr").forEach(function(tr){
      [].forEach.call(tr.children, function(td, i){
        var h = th[i]; if (!i || !h) return;
        ["vi", "en"].forEach(function(l){ var s = h.querySelector("." + l); td.setAttribute("data-" + l, (s || h).textContent.trim()); });
      });
    });
  });

  /* ---------- language ---------- */
  function setLang(l){
    root.dataset.lang = l; root.lang = l; document.title = TITLES[l];
    q.placeholder = HOLD[l];
    document.querySelectorAll(".lang button").forEach(function(b){ b.setAttribute("aria-pressed", b.dataset.set === l); });
    try { localStorage.setItem("lang", l); } catch(e){}
    build(); if (q.value.trim()) find(); else hide(); if (cur) show(cur);
  }
  document.querySelectorAll(".lang button").forEach(function(b){ b.onclick = function(){ setLang(b.dataset.set); }; });

  /* ---------- search: a plain index of the page in the language shown, accents and case ignored ---------- */
  // one character in, one character out, so a position in the folded text is the same position in the original
  function fold(s){
    var o = "";
    for (var i = 0; i < s.length; i++){
      var c = s.charAt(i), f = (c === "đ" || c === "Đ") ? "d" : c.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
      o += f.length === 1 ? f : (f.charAt(0) || c);
    }
    return o;
  }
  function esc(s){ return s.replace(/[&<>"]/g, function(c){ return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); }
  function textOf(node, lang, noTitle){                                     // visible text in this language only
    var w = document.createTreeWalker(node, NodeFilter.SHOW_TEXT), out = [], n, last = null;
    while ((n = w.nextNode())){
      var e = n.parentElement, other = e && e.closest(lang === "vi" ? ".en" : ".vi");
      if (other || (e && e.closest(".ga-box,.gst-box,.gsince,.gtry")) || (noTitle && e && e.closest("h3")) || !n.nodeValue.trim()) continue;
      var block = e && e.closest("td,th,li,p,h4,dt,dd,figcaption");   // table cells and steps would run into each other
      if (out.length && block !== last && !/[.:;!?…]\s*$/.test(out[out.length - 1])) out.push("·");
      out.push(n.nodeValue); last = block;
    }
    return out.join(" ").replace(/\s+/g, " ").replace(/\s+([.,;:!?)”])/g, "$1").trim();
  }
  /* "Try it": the topics the live demo can open on (demo/go.js), a button beside the title */
  var PLAY = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 4.5v15l12-7.5z"/></svg>';
  topics.forEach(function(t){
    if (!window.DEMO_GO || !window.DEMO_GO[t.id]) return;
    var a = document.createElement("a");
    a.className = "gtry"; a.href = "demo/?go=" + t.id; a.target = "tvr-demo";
    a.innerHTML = PLAY + '<span class="vi">Thử ngay</span><span class="en">Try it</span>';
    t.querySelector("h3").append(a);
  });
  function build(){
    var lang = root.dataset.lang;
    index = topics.map(function(a){
      var h = a.querySelector("h3"), title = textOf(h, lang).replace(/^\S+\s/, "");   // no cloneNode: a cloned lazy <img> is fetched at once
      var text = textOf(a, lang, true), kw = a.dataset.kw || "";
      var g = a.closest(".ggroup").querySelector(".gh");
      return { id: a.id, title: title, text: text, group: textOf(g, lang), ft: fold(title), fx: fold(text), fk: fold(kw) };
    });
  }
  // where a word beginning with t starts in fs (-1: none), so "ho" does not light up inside "không"
  function at(fs, t, from){
    var i = from - 1;
    while ((i = fs.indexOf(t, i + 1)) >= 0){ if (i === 0 || !/[a-z0-9]/.test(fs.charAt(i - 1))) return i; }
    return -1;
  }
  function marks(s, fs, toks){                                      // s with every token's hits in <mark>
    var on = new Array(s.length + 1).fill(0);
    toks.forEach(function(t){ var i = -1; while ((i = at(fs, t, i + 1)) >= 0){ for (var k = i; k < i + t.length; k++) on[k] = 1; } });
    var o = "", open = false;
    for (var i = 0; i < s.length; i++){
      if (on[i] && !open){ o += "<mark>"; open = true; } else if (!on[i] && open){ o += "</mark>"; open = false; }
      o += esc(s.charAt(i));
    }
    return o + (open ? "</mark>" : "");
  }
  function snippet(it, toks){
    var pos = -1;
    toks.forEach(function(t){ var i = at(it.fx, t, 0); if (i >= 0 && (pos < 0 || i < pos)) pos = i; });
    var a = Math.max(0, pos - 45), b = Math.min(it.text.length, a + 150);
    if (a > 0){ var sp = it.text.indexOf(" ", a); if (sp >= 0 && sp < pos) a = sp + 1; }
    var s = it.text.slice(a, b), f = it.fx.slice(a, b);
    return (a > 0 ? "… " : "") + marks(s, f, toks) + (b < it.text.length ? " …" : "");
  }
  function find(){
    var toks = fold(q.value.trim()).split(/\s+/).filter(Boolean);
    if (!toks.length){ hide(); return; }
    var hits = [];
    index.forEach(function(it, order){
      var score = 0;
      for (var i = 0; i < toks.length; i++){
        var t = toks[i], s = it.ft.indexOf(t) >= 0 ? 3 : it.fx.indexOf(t) >= 0 ? 1 : it.fk.indexOf(t) >= 0 ? .5 : 0;
        if (!s){ score = 0; break; }                                // every word must be somewhere
        score += s;
      }
      var phrase = toks.join(" ");                                   // the words together, in order: the best hit
      var exact = score && toks.length > 1 && (at(it.ft, phrase, 0) >= 0 || at(it.fx, phrase, 0) >= 0);
      if (exact) score += 6;
      if (score) hits.push({ it: it, score: score, order: order, exact: exact });
    });
    hits.sort(function(a, b){ return b.score - a.score || a.order - b.order; });
    if (hits.length && hits[0].exact) hits = hits.filter(function(h){ return h.exact; });   // the words together beat the words apart
    shown = hits.slice(0, 8);
    var lang = root.dataset.lang;
    res.innerHTML = shown.length ? shown.map(function(h, i){
      return '<li role="option" id="r' + i + '" data-id="' + h.it.id + '"><a href="#' + h.it.id + '"><small>' + esc(h.it.group) + '</small><b>' + marks(h.it.title, h.it.ft, toks) + '</b>' + snippet(h.it, toks) + '</a></li>';
    }).join("") : '<li class="none">' + NONE[lang] + '</li>';
    pick(shown.length ? 0 : -1);
  }
  function hide(){ res.innerHTML = ""; shown = []; sel = -1; q.removeAttribute("aria-activedescendant"); }
  function pick(i){
    sel = i;
    [].forEach.call(res.querySelectorAll("li[role=option]"), function(li, k){ li.setAttribute("aria-selected", k === i); });
    if (i >= 0){ q.setAttribute("aria-activedescendant", "r" + i); var li = document.getElementById("r" + i); if (li) li.scrollIntoView({ block: "nearest" }); }
    else q.removeAttribute("aria-activedescendant");
  }
  function jump(id){ closeFind(); closeDrawer(); go(id); }
  q.addEventListener("input", find);
  q.addEventListener("focus", function(){ if (q.value.trim()) find(); });
  q.addEventListener("keydown", function(e){
    if (e.key === "ArrowDown" && shown.length){ e.preventDefault(); pick((sel + 1) % shown.length); }
    else if (e.key === "ArrowUp" && shown.length){ e.preventDefault(); pick((sel - 1 + shown.length) % shown.length); }
    else if (e.key === "Enter" && shown.length){ e.preventDefault(); jump(shown[Math.max(sel, 0)].it.id); }
    else if (e.key === "Escape"){ e.stopPropagation(); closeFind(true); }
  });
  res.addEventListener("click", function(e){
    var li = e.target.closest("li[data-id]"); if (!li) return;
    e.preventDefault(); jump(li.dataset.id);
  });
  // the search is a dialog: opened from the menu's button, "/" or Ctrl/⌘ K; Esc or a click outside closes it
  var opener = null;
  function openFind(){
    opener = document.activeElement; closeDrawer();
    dlg.hidden = false; root.style.overflow = "hidden"; q.focus(); q.select(); if (q.value.trim()) find();
  }
  function closeFind(refocus){
    if (dlg.hidden) return;
    dlg.hidden = true; root.style.overflow = "";
    if (refocus && opener && opener.focus) opener.focus();
  }
  [].forEach.call(document.querySelectorAll(".gfind"), function(b){ b.addEventListener("click", openFind); });
  dlg.querySelector(".gdlg-back").addEventListener("click", function(){ closeFind(true); });
  document.addEventListener("keydown", function(e){
    var typing = /^(INPUT|TEXTAREA|SELECT)$/.test((e.target.tagName || "")) || e.target.isContentEditable;
    if ((e.key === "/" && !typing && !e.ctrlKey && !e.metaKey) || ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k")){
      e.preventDefault(); openFind();
    }
  });

  /* ---------- menu: a column on wide screens, a drawer on narrow ones ---------- */
  function openDrawer(){ side.classList.add("open"); back.hidden = false; menuBtn.setAttribute("aria-expanded", "true"); root.style.overflow = "hidden"; side.querySelector(".gclose").focus({ preventScroll: true }); }
  function closeDrawer(back2){
    if (!side.classList.contains("open")) return;
    side.classList.remove("open"); back.hidden = true; menuBtn.setAttribute("aria-expanded", "false"); root.style.overflow = "";
    if (back2) menuBtn.focus();
  }
  menuBtn.addEventListener("click", function(){ side.classList.contains("open") ? closeDrawer(true) : openDrawer(); });
  back.addEventListener("click", function(){ closeDrawer(true); });
  side.querySelector(".gclose").addEventListener("click", function(){ closeDrawer(true); });
  side.addEventListener("click", function(e){
    var a = e.target.closest("a[data-t]"); if (!a || e.ctrlKey || e.metaKey || e.shiftKey) return;
    e.preventDefault(); closeDrawer(); go(a.dataset.t);
  });
  document.addEventListener("keydown", function(e){ if (e.key === "Escape"){ if (!dlg.hidden) closeFind(true); else closeDrawer(true); } });
  matchMedia("(min-width:981px)").addEventListener("change", function(m){ if (m.matches) closeDrawer(); });

  /* ---------- one topic at a time, chosen by the address (#id): the page stays short and its animation plays at once ---------- */
  var links = {}, cur = null, pager = document.querySelector(".gpager");
  [].forEach.call(side.querySelectorAll("a[data-t]"), function(a){ links[a.dataset.t] = a; });
  function find1(id){                                               // a topic, or the first topic of a group (#g-start), or the first
    var el = id && document.getElementById(id);
    if (el && el.classList.contains("ggroup")) el = el.querySelector(".topic");
    return el && el.classList.contains("topic") ? el : topics[0];
  }
  // the topic goes in ?t=id, not #id: a chat app's link preview never sees a # (_worker.js); a #id written by hand still opens it
  function here(){ return location.hash.slice(1) || new URLSearchParams(location.search).get("t") || ""; }
  function addr(id){ return location.pathname + "?t=" + encodeURIComponent(id); }
  function go(id){
    var t = find1(id);
    if (location.hash || here() !== t.id) history.pushState(null, "", addr(t.id));
    show(t.id); scrollTo(0, 0);
  }
  function show(id){
    var t = find1(id), lang = root.dataset.lang, k = topics.indexOf(t);
    topics.forEach(function(x){ x.classList.toggle("cur", x === t); });
    groups.forEach(function(g){ g.classList.toggle("cur", g.contains(t)); });
    if (cur && links[cur]){ links[cur].classList.remove("on"); links[cur].removeAttribute("aria-current"); }
    cur = t.id; var a = links[cur]; if (a){ a.classList.add("on"); a.setAttribute("aria-current", "page"); }
    if (side.scrollHeight > side.clientHeight){                  // keep it in view inside the menu without moving the page
      if (a.offsetTop < side.scrollTop + 30) side.scrollTop = a.offsetTop - 60;
      else if (a.offsetTop + a.offsetHeight > side.scrollTop + side.clientHeight - 30) side.scrollTop = a.offsetTop - side.clientHeight + a.offsetHeight + 60;
    }
    var name = function(x){ return esc(textOf(x.querySelector("h3"), lang).replace(/^\S+\s/, "")); };
    var p = topics[k - 1], n = topics[k + 1];
    pager.innerHTML = (p ? '<a class="pv" href="#' + p.id + '" data-t="' + p.id + '"><small>← ' + PREV[lang] + '</small>' + name(p) + '</a>' : '')
      + (n ? '<a class="nx" href="#' + n.id + '" data-t="' + n.id + '"><small>' + NEXT[lang] + ' →</small>' + name(n) + '</a>' : '');
    paintSince(t, lang);
    var v = t.dataset.v, ver = document.querySelector(".gver");    // "next": checked against main, stamped by release.sh
    ver.hidden = !v || v === "next"; ver.textContent = ver.hidden ? "" : FOR[lang] + v;
    document.title = textOf(t.querySelector("h3"), lang).replace(/^\S+\s/, "") + " · " + TITLES[lang];
  }
  pager.addEventListener("click", function(e){
    var a = e.target.closest("a[data-t]"); if (!a || e.ctrlKey || e.metaKey || e.shiftKey) return;
    e.preventDefault(); go(a.dataset.t);
  });
  addEventListener("popstate", function(){ show(here()); });
  addEventListener("hashchange", function(){ history.replaceState(null, "", addr(find1(here()).id)); show(here()); scrollTo(0, 0); });   // a link written by hand: ?t= follows it

  /* ---------- a screenshot opens full size on a click; a click or Esc closes it ---------- */
  (function(){
    var box = null;
    function close(){ if (box) { box.remove(); box = null; } }
    document.addEventListener("click", function(e){
      var im = e.target.closest(".gshot img");
      if (!im) { close(); return; }
      close(); box = document.createElement("div"); box.className = "gzoom";
      var big = document.createElement("img"); big.src = im.currentSrc || im.src; big.alt = im.alt;
      box.appendChild(big); document.body.appendChild(box);
    });
    document.addEventListener("keydown", function(e){ if (e.key === "Escape") close(); });
  })();

  document.getElementById("yr").textContent = new Date().getFullYear();
  if (here()) history.replaceState(null, "", addr(find1(here()).id));   // an old #id link copied from here previews its topic too
  show(here()); root.classList.add("gr");
  if (location.hash) addEventListener("load", function(){ setTimeout(function(){ if (scrollY < 200) scrollTo(0, 0); }, 50); });   // the browser jumps to #topic, under the bar
  setLang(root.dataset.lang);
})();
