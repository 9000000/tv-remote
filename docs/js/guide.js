(function(){
  "use strict";
  var root = document.documentElement;
  var TITLES = { vi: "Hướng dẫn dùng TV Remote", en: "TV Remote guide: step by step" };
  var HOLD = { vi: "Tìm trong hướng dẫn…", en: "Search the guide…" };
  var NONE = { vi: "Không thấy mục nào khớp. Thử từ khác, ví dụ “adb” hay “hẹn giờ”.", en: "Nothing matches. Try another word, such as “adb” or “sleep timer”." };
  var q = document.getElementById("gq"), res = document.getElementById("gres");
  var side = document.getElementById("gside"), back = document.querySelector(".gback"), menuBtn = document.querySelector(".gmenu");
  var topics = [].slice.call(document.querySelectorAll(".topic"));
  var index = [], sel = -1, shown = [];

  /* ---------- language ---------- */
  function setLang(l){
    root.dataset.lang = l; root.lang = l; document.title = TITLES[l];
    q.placeholder = HOLD[l];
    document.querySelectorAll(".lang button").forEach(function(b){ b.setAttribute("aria-pressed", b.dataset.set === l); });
    try { localStorage.setItem("lang", l); } catch(e){}
    build(); if (q.value.trim()) find(); else hide();
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
  function textOf(node, lang){                                      // visible text in this language only
    var w = document.createTreeWalker(node, NodeFilter.SHOW_TEXT), out = [], n, last = null;
    while ((n = w.nextNode())){
      var e = n.parentElement, other = e && e.closest(lang === "vi" ? ".en" : ".vi");
      if (other || (e && e.closest(".ga-box")) || !n.nodeValue.trim()) continue;
      var block = e && e.closest("td,th,li,p,h4,dt,dd,figcaption");   // table cells and steps would run into each other
      if (out.length && block !== last && !/[.:;!?…]\s*$/.test(out[out.length - 1])) out.push("·");
      out.push(n.nodeValue); last = block;
    }
    return out.join(" ").replace(/\s+/g, " ").replace(/\s+([.,;:!?)”])/g, "$1").trim();
  }
  function build(){
    var lang = root.dataset.lang;
    index = topics.map(function(a){
      var h = a.querySelector("h3"), title = textOf(h, lang).replace(/^\S+\s/, ""), clone = a.cloneNode(true);
      clone.querySelector("h3").remove();
      var text = textOf(clone, lang), kw = a.dataset.kw || "";
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
    res.hidden = false; pick(shown.length ? 0 : -1);
  }
  function hide(){ res.hidden = true; res.innerHTML = ""; shown = []; sel = -1; q.removeAttribute("aria-activedescendant"); }
  function pick(i){
    sel = i;
    [].forEach.call(res.querySelectorAll("li[role=option]"), function(li, k){ li.setAttribute("aria-selected", k === i); });
    if (i >= 0){ q.setAttribute("aria-activedescendant", "r" + i); var li = document.getElementById("r" + i); if (li) li.scrollIntoView({ block: "nearest" }); }
    else q.removeAttribute("aria-activedescendant");
  }
  function jump(id){
    var el = document.getElementById(id); if (!el) return;
    hide(); closeDrawer();
    history.replaceState(null, "", "#" + id);
    el.scrollIntoView({ behavior: "smooth", block: "start" });
    flash(el);
  }
  function flash(el){ el.classList.remove("hit"); void el.offsetWidth; el.classList.add("hit"); }
  q.addEventListener("input", find);
  q.addEventListener("focus", function(){ if (q.value.trim()) find(); });
  q.addEventListener("keydown", function(e){
    if (e.key === "ArrowDown" && shown.length){ e.preventDefault(); pick((sel + 1) % shown.length); }
    else if (e.key === "ArrowUp" && shown.length){ e.preventDefault(); pick((sel - 1 + shown.length) % shown.length); }
    else if (e.key === "Enter" && shown.length){ e.preventDefault(); jump(shown[Math.max(sel, 0)].it.id); }
    else if (e.key === "Escape"){ if (!res.hidden) hide(); else { q.value = ""; q.blur(); } }
  });
  res.addEventListener("click", function(e){
    var li = e.target.closest("li[data-id]"); if (!li) return;
    e.preventDefault(); jump(li.dataset.id);
  });
  document.addEventListener("click", function(e){ if (!e.target.closest(".gsearch")) hide(); });
  document.addEventListener("keydown", function(e){
    var typing = /^(INPUT|TEXTAREA|SELECT)$/.test((e.target.tagName || "")) || e.target.isContentEditable;
    if ((e.key === "/" && !typing && !e.ctrlKey && !e.metaKey) || ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k")){
      e.preventDefault(); closeDrawer(); q.focus(); q.select();
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
  side.addEventListener("click", function(e){ if (e.target.closest("a")) closeDrawer(); });
  document.addEventListener("keydown", function(e){ if (e.key === "Escape") closeDrawer(true); });
  matchMedia("(min-width:981px)").addEventListener("change", function(m){ if (m.matches) closeDrawer(); });

  // the topic being read lights in the menu
  var links = {}, cur = null, busy = false;
  [].forEach.call(side.querySelectorAll("a[data-t]"), function(a){ links[a.dataset.t] = a; });
  function spy(){
    busy = false;
    var y = 190, now = topics[0];
    topics.forEach(function(t){ if (t.getBoundingClientRect().top <= y) now = t; });
    if (innerHeight + scrollY >= document.documentElement.scrollHeight - 4) now = topics[topics.length - 1];
    if (now.id === cur) return;
    if (cur) links[cur].classList.remove("on");
    cur = now.id; var a = links[cur]; a.classList.add("on"); a.setAttribute("aria-current", "true");
    if (side.scrollHeight > side.clientHeight){                  // keep it in view inside the menu without moving the page
      if (a.offsetTop < side.scrollTop + 30) side.scrollTop = a.offsetTop - 60;
      else if (a.offsetTop + a.offsetHeight > side.scrollTop + side.clientHeight - 30) side.scrollTop = a.offsetTop - side.clientHeight + a.offsetHeight + 60;
    }
  }
  var prog = document.querySelector(".navprog");
  function bar(){ var max = root.scrollHeight - innerHeight; prog.style.setProperty("--p", max > 0 ? Math.min(1, Math.max(0, scrollY / max)).toFixed(4) : 0); }
  addEventListener("scroll", function(){ if (!busy){ busy = true; requestAnimationFrame(function(){ spy(); bar(); }); } }, { passive: true });
  addEventListener("resize", bar);
  addEventListener("hashchange", function(){ var el = document.getElementById(location.hash.slice(1)); if (el && el.classList.contains("topic")) flash(el); });

  /* ---------- the header's narrow-screen menu (same as the home page) ---------- */
  (function(){
    var btn = document.querySelector(".menubtn"), sheet = document.getElementById("navsheet");
    function set(open, b){ sheet.hidden = !open; btn.setAttribute("aria-expanded", open); if (!open && b) btn.focus(); }
    btn.onclick = function(){ set(sheet.hidden); };
    sheet.addEventListener("click", function(e){ if (e.target.closest("a")) set(false); });
    document.addEventListener("click", function(e){ if (!sheet.hidden && !e.target.closest("nav")) set(false); });
    document.addEventListener("keydown", function(e){ if (e.key === "Escape" && !sheet.hidden) set(false, true); });
    matchMedia("(min-width:981px)").addEventListener("change", function(m){ if (m.matches) set(false); });
  })();

  document.getElementById("yr").textContent = new Date().getFullYear();
  setLang(root.dataset.lang);
  spy(); bar();
  if (location.hash){ var t = document.getElementById(location.hash.slice(1)); if (t && t.classList.contains("topic")) setTimeout(function(){ t.scrollIntoView({ block: "start" }); flash(t); }, 60); }
})();
