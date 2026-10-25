// "TVs it ran on": draws the census (GET /v1/tvs of the tvr-census Worker, dev/census) into every [data-tvs] element.
// data-limit="8": show only that many brands until the person searches or picks a kind (the home page); the rest is tvs.html.
(function(){
  var URL_ = "https://tvr-census.99-hoangtran.workers.dev/v1/tvs";
  var W = {                                                         // the words, both languages
    kinds: [["all", "Tất cả", "All"], ["tv", "Tivi", "TVs"], ["box", "TV Box", "Boxes, sticks"], ["projector", "Máy chiếu", "Projectors"]],
    badge: { tv: ["Tivi", "TV"], box: ["Box", "Box"], projector: ["Máy chiếu", "Projector"] },
    search: ["Tìm hãng, mẫu, mã máy…", "Search brand, model, device…"],
    sum: ["{d} thiết bị · {n} mẫu · {b} hãng", "{d} devices · {n} models · {b} brands"],
    units: ["{n} máy", "{n} TVs"], none: ["Không có mẫu nào khớp.", "No model matches."],
    empty: ["Chưa có dữ liệu. Danh sách sẽ hiện khi các app gửi báo cáo ẩn danh đầu tiên.", "No data yet. The list fills as the apps send their first anonymous reports."],
    off: ["Không tải được danh sách lúc này. Thử lại sau.", "Could not load the list right now. Try again later."],
    load: ["Đang tải…", "Loading…"], more: ["Xem thêm ({n})", "Show more ({n})"], less: ["Thu gọn", "Show less"], all: ["Xem tất cả {b} hãng →", "See all {b} brands →"]
  };
  var SHOW = 3;                                                     // rows per brand before "Show more"
  var lang = function(){ return document.documentElement.dataset.lang === "en" ? 1 : 0; };
  var t = function(k, v){ var s = W[k][lang()]; for (var x in v) s = s.replace("{" + x + "}", v[x]); return s; };
  function esc(s){ return String(s).replace(/[&<>"]/g, function(c){ return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); }
  function norm(s){ return String(s).normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/đ/g, "d").replace(/Đ/g, "D").toLowerCase(); }
  function android(a){ var v = (a || []).slice().sort(function(x, y){ return parseFloat(x) - parseFloat(y); }); return !v.length ? "" : v.length === 1 ? "Android " + v[0] : "Android " + v[0] + "–" + v[v.length - 1]; }

  function mount(el){
    var limit = +el.dataset.limit || 0, data = null, kind = "all", q = "", failed = false, open = {};
    var kindFor = function(m){ return m.kind === "box" || m.kind === "projector" ? m.kind : "tv"; };
    el.innerHTML = '<div class="tv-tools"><input type="search" class="tv-q" autocomplete="off" spellcheck="false" aria-label="Search"><div class="tv-chips" role="group"></div></div><p class="tv-sum"></p><div class="tv-list"></div>';
    var inp = el.querySelector(".tv-q"), chips = el.querySelector(".tv-chips"), sum = el.querySelector(".tv-sum"), list = el.querySelector(".tv-list");
    inp.oninput = function(){ q = norm(inp.value.trim()); draw(); };
    list.onclick = function(e){ var b = e.target.closest(".tv-fold"); if (b) { open[b.dataset.b] = !open[b.dataset.b]; draw(); } };
    chips.onclick = function(e){ var b = e.target.closest("button"); if (b) { kind = b.dataset.k; draw(); } };

    function draw(){
      inp.placeholder = t("search");
      var models = data ? data.models : [], counts = { all: models.length, tv: 0, box: 0, projector: 0 };
      models.forEach(function(m){ counts[kindFor(m)]++; });
      chips.innerHTML = W.kinds.map(function(k){
        return '<button type="button" data-k="' + k[0] + '" aria-pressed="' + (kind === k[0]) + '">' + esc(k[1 + lang()]) + ' <span class="n">' + counts[k[0]] + "</span></button>";
      }).join("");
      if (!data) { sum.textContent = ""; list.innerHTML = '<p class="tv-note">' + esc(failed ? t("off") : t("load")) + "</p>"; return; }
      if (!models.length) { sum.textContent = ""; list.innerHTML = '<p class="tv-note">' + esc(t("empty")) + "</p>"; return; }
      var shown = models.filter(function(m){
        return (kind === "all" || kindFor(m) === kind) && (!q || norm([m.brand, m.model].concat(m.devices || []).join(" ")).indexOf(q) >= 0);
      });
      var groups = {}, order = [];
      shown.forEach(function(m){ var g = groups[m.brand] || (groups[m.brand] = { brand: m.brand, n: 0, rows: [] }); if (!g.rows.length) order.push(g); g.n += m.count; g.rows.push(m); });
      order.sort(function(a, b){ return b.n - a.n || a.brand.localeCompare(b.brand); });
      sum.textContent = t("sum", { d: shown.reduce(function(a, m){ return a + (m.count || 0); }, 0), n: shown.length, b: order.length });
      var cut = limit && !q && kind === "all" && order.length > limit;
      var fold = !q && kind === "all";                                  // a search or a chip shows every match
      var html = (cut ? order.slice(0, limit) : order).map(function(g){
        return '<section class="tv-brand"><h3><span>' + esc(g.brand) + '</span><b>' + g.n + "</b></h3>" + g.rows.map(function(m, i){
          if (fold && g.rows.length > SHOW + 1 && !open[g.brand] && i >= SHOW) return "";
          var k = kindFor(m);
          return '<div class="tv-row"><span class="tv-m">' + esc(m.model) + '</span><span class="tv-a">' + esc(android(m.android)) + '</span><span class="tv-c" title="' + esc(t("units", { n: m.count })) + '">×' + m.count + '</span><i class="tv-b k-' + k + '">' + esc(W.badge[k][lang()]) + "</i></div>";
        }).join("") + (fold && g.rows.length > SHOW + 1 ? '<button type="button" class="tv-fold" data-b="' + esc(g.brand) + '" aria-expanded="' + !!open[g.brand] + '">' + esc(open[g.brand] ? t("less") : t("more", { n: g.rows.length - SHOW })) + "</button>" : "") + "</section>";
      }).join("");
      if (!order.length) html = '<p class="tv-note">' + esc(t("none")) + "</p>";
      if (cut) html += '<a class="btn btn-ghost tv-more" href="tvs">' + esc(t("all", { b: order.length })) + "</a>";
      list.innerHTML = html;
    }
    draw();
    new MutationObserver(draw).observe(document.documentElement, { attributes: true, attributeFilter: ["data-lang"] });
    fetch(URL_).then(function(r){ if (!r.ok) throw 0; return r.json(); }).then(function(d){ data = d && d.models ? d : { models: [] }; draw(); }).catch(function(){ failed = true; draw(); });
  }
  document.querySelectorAll("[data-tvs]").forEach(mount);
})();
