// Draws the apps map and its tables from data/apps-map.json (the one source; the READMEs' mermaid comes from the same file via dev/gen/apps_map.js).
// Both languages are written out as .vi / .en pairs; site.css shows the one in <html data-lang>.
(function(){
  var NS = "http://www.w3.org/2000/svg";
  var GLYPH = {                                                     // 24x24 line icons for the nodes that have no logo
    globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"/>',
    terminal: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 9l3 3-3 3M13 15h4"/>',
    file: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/>',
    github: '<path d="M9 19c-4 1-4-2-6-2m12 4v-3a3 3 0 0 0-1-2c3 0 6-1.500 6-6a5 5 0 0 0-1-3 4 4 0 0 0 0-3s-1 0-3 1.500a10 10 0 0 0-6 0C8 4 7 4 7 4a4 4 0 0 0 0 3 5 5 0 0 0-1 3c0 4.500 3 6 6 6a3 3 0 0 0-1 2v3"/>',
    download: '<path d="M12 3v12m0 0l-5-5m5 5l5-5M4 21h16"/>',
    net: '<circle cx="6" cy="12" r="2.500"/><circle cx="18" cy="6" r="2.500"/><circle cx="18" cy="18" r="2.500"/><path d="M8.300 11l7.400-4M8.300 13l7.400 4"/>'
  };
  var COLOR = { ok: "var(--green)", partial: "var(--orange)", silent: "var(--red)" };
  var PILL = { ok: { vi: "Báo khi thiếu", en: "Tells you" }, partial: { vi: "Báo một phần", en: "Partly" }, silent: { vi: "Không báo", en: "Quiet" } };
  var HEAD = {
    link: { vi: "Đường nối", en: "Link" }, who: { vi: "Ai cần ai", en: "Who needs whom" }, what: { vi: "Để làm gì · đi đường nào", en: "What for · how" },
    miss: { vi: "Khi thiếu hoặc cũ", en: "If it is missing or old" }, app: { vi: "App", en: "App" }, code: { vi: "Mã Downloader", en: "Downloader code" },
    other: { vi: "Cách khác", en: "Other ways" }, none: { vi: "chưa có", en: "none yet" }
  };
  var ALT = { vi: "Sơ đồ các app kết nối với nhau", en: "Diagram of how the apps connect" };

  function esc(s){ return String(s).replace(/[&<>"]/g, function(c){ return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); }
  function both(o, tag, cls, attrs){                                  // <tag class="vi">..</tag><tag class="en">..</tag>
    return ["vi", "en"].map(function(l){ return "<" + tag + ' class="' + l + (cls ? " " + cls : "") + '"' + (attrs || "") + ">" + esc(o[l]) + "</" + tag + ">"; }).join("");
  }
  function logo(n, size){
    return n.logo ? '<img src="' + esc(n.logo) + '" width="' + size + '" height="' + size + '" alt="" loading="lazy">' : "";
  }

  function draw(map){
    var byId = {}; map.nodes.forEach(function(n){ byId[n.id] = n; });
    var grp = {}; map.groups.forEach(function(g){ grp[g.id] = g; });
    var s = '<svg viewBox="0 0 ' + map.view.w + " " + map.view.h + '" role="img" aria-label="' + esc(ALT.en) + '"><defs>';
    Object.keys(COLOR).forEach(function(k){
      s += '<marker id="am-' + k + '" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L10 5L0 10z" fill="' + COLOR[k] + '"/></marker>';
    });
    s += "</defs>";
    map.groups.forEach(function(g){
      s += '<rect class="grp" x="' + g.x + '" y="' + g.y + '" width="' + g.w + '" height="' + g.h + '" rx="14"/>';
      ["vi", "en"].forEach(function(l){ s += '<text class="grpl ' + l + '" x="' + g.lx + '" y="' + g.ly + '"' + (g.end ? ' text-anchor="end"' : "") + ">" + esc(g.name[l]) + "</text>"; });
    });
    map.edges.forEach(function(e){
      var p = e.p, b = e.bend || 0, d;
      if (e.dir === "v") d = "M" + p[0] + " " + p[1] + " C" + p[0] + " " + (p[1] + (p[3] - p[1]) / 2) + " " + p[2] + " " + (p[1] + (p[3] - p[1]) / 2) + " " + p[2] + " " + p[3];
      else d = "M" + p[0] + " " + p[1] + " C" + (p[0] + (p[2] - p[0]) / 2) + " " + (p[1] + b) + " " + (p[0] + (p[2] - p[0]) / 2) + " " + (p[3] + b) + " " + p[2] + " " + p[3];
      s += '<path class="e ' + e.told + '" d="' + d + '" marker-end="url(#am-' + e.told + ')"/>';
    });
    map.nodes.forEach(function(n){
      var h = n.h || 56, pad = n.logo || h > 50 ? 60 : 36, y0 = n.y + (h - 40) / 2;
      s += '<g class="node"><rect x="' + n.x + '" y="' + n.y + '" width="' + n.w + '" height="' + h + '" rx="10"/>';
      if (n.logo) s += '<image href="' + esc(n.logo) + '" x="' + (n.x + 10) + '" y="' + y0 + '" width="40" height="40"/>';
      else if (n.glyph) {
        var big = h > 50, sc = big ? 1.5 : 1, gy = n.y + (h - 24 * sc) / 2;
        s += '<g class="glyph" transform="translate(' + (n.x + (big ? 12 : 10)) + " " + gy + ") scale(" + sc + ')">' + GLYPH[n.glyph] + "</g>";
        if (big) pad = 56;
      }
      ["vi", "en"].forEach(function(l){
        s += '<text class="nm ' + l + '" x="' + (n.x + pad) + '" y="' + (n.sub ? n.y + h / 2 - 3 : n.y + h / 2 + 4) + '"' + (h <= 50 ? ' font-size="12"' : "") + ">" + esc(n.name[l]) + "</text>";
        if (n.sub) s += '<text class="sub ' + l + '" x="' + (n.x + pad) + '" y="' + (n.y + h / 2 + 14) + '">' + esc(n.sub[l]) + "</text>";
      });
      s += "</g>";
    });
    map.edges.forEach(function(e, i){
      var p = e.p, mx = (p[0] + p[2]) / 2, my = (p[1] + p[3]) / 2 + (e.dir === "v" ? 0 : 0.75 * (e.bend || 0));
      ["vi", "en"].forEach(function(l){
        var t = (i + 1) + " " + e.tag[l], w = Math.round(14 + 6.4 * t.length);
        s += '<g class="tag ' + l + '"><rect x="' + Math.round(mx - w / 2) + '" y="' + (my - 9) + '" width="' + w + '" height="18" rx="5"/><text x="' + mx + '" y="' + (my + 4) + '" text-anchor="middle" fill="' + COLOR[e.told] + '">' + esc(t) + "</text></g>";
      });
    });
    return s + "</svg>";
  }

  function who(map, id){
    var g = map.groups.filter(function(x){ return x.id === id; })[0];
    if (!g) return both(map.nodes.filter(function(n){ return n.id === id; })[0].name, "span");
    return ["vi", "en"].map(function(l){
      return '<span class="' + l + '">' + esc(map.nodes.filter(function(n){ return n.group === id; }).map(function(n){ return n.name[l]; }).join(", ")) + "</span>";
    }).join("");
  }

  function lbl(h){ return '<span class="lbl">' + both(h, "span") + "</span>"; }   // the column's name, shown only where the table is stacked (narrow screens)

  function edgeTable(map){
    var r = "<table><thead><tr><th>#</th><th>" + both(HEAD.who, "span") + "</th><th>" + both(HEAD.what, "span") + "</th><th>" + both(HEAD.miss, "span") + "</th><th></th></tr></thead><tbody>";
    map.edges.forEach(function(e, i){
      r += '<tr id="link-' + (i + 1) + '"><td class="num">' + (i + 1) + "</td><td>" + lbl(HEAD.who) + who(map, e.from) + " → " + who(map, e.to) + "</td><td>" + lbl(HEAD.what) +
        both(e.what, "span") + ' <span class="how">' + both(e.how, "span") + "</span></td><td>" + lbl(HEAD.miss) + both(e.missing, "span") +
        '</td><td><span class="pill ' + e.told + '">' + both(PILL[e.told], "span") + "</span></td></tr>";
    });
    return r + "</tbody></table>";
  }

  function installTable(map){
    var byId = {}; map.nodes.forEach(function(n){ byId[n.id] = n; });
    var r = "<table><thead><tr><th>" + both(HEAD.app, "span") + "</th><th>" + both(HEAD.code, "span") + "</th><th>" + both(HEAD.other, "span") + "</th></tr></thead><tbody>";
    map.install.forEach(function(a){
      var n = byId[a.app];
      r += '<tr><td class="appc">' + logo(n, 28) + both(n.name, "span") + '</td><td class="mono">' + lbl(HEAD.code) + (a.code ? esc(a.code) : both(HEAD.none, "span")) + "</td><td>" + lbl(HEAD.other) + both(a.other, "span") + "</td></tr>";
    });
    return r + "</tbody></table>";
  }

  fetch("data/apps-map.json").then(function(r){ return r.json(); }).then(function(map){
    document.getElementById("map").innerHTML = draw(map);
    document.getElementById("edges").innerHTML = edgeTable(map);
    document.getElementById("install").innerHTML = installTable(map);
  }).catch(function(){
    document.getElementById("map").textContent = "Could not load the map.";
  });
})();
