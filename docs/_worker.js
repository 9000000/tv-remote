// Cloudflare Pages: every request is the static files', except two.
// /dl/<file>: the latest release's file on GitHub, behind a link short enough to read out, type and fit a small QR (the apps
// show these; their own updates still go to GitHub, checked by SHA256SUMS). Only the release's names: no open redirect.
// A guide link with ?t=<topic>: a chat app's link preview (Messenger, Zalo) never sees the #topic, so it showed the whole
// guide's title; here that topic's own name and first lines go into the page's title and og: tags. guide.js writes ?t= into
// the address, so a copied link carries it.
const FILES = new Set(["tv-remote.apk", "tv-setup.apk", "tv-home.apk", "tv-channels.apk", "tv-games.apk",
  "TV-Remote-mac.zip", "TV-Remote-windows-x64.zip", "TV-Remote-linux-x64.tar.gz", "TV-Remote-linux-arm64.tar.gz"]);

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    const dl = /^\/dl\/([A-Za-z0-9.-]+)$/.exec(url.pathname);
    if (dl && FILES.has(dl[1])) return Response.redirect("https://github.com/HoangTran0410/tv-remote/releases/latest/download/" + dl[1], 302);
    const id = url.searchParams.get("t");
    const res = await env.ASSETS.fetch(req);
    if (!id || !/^\/guide(\.html)?$/.test(url.pathname) || !/^[a-z0-9-]{1,60}$/.test(id) || !res.ok) return res;
    const topic = await find(await env.ASSETS.fetch(new URL("/guide", url)), id);
    if (!topic.vi) return res;
    const title = topic.vi + " / " + topic.en;
    const desc = (topic.pvi + " " + topic.pen).trim().slice(0, 300);
    const set = (v) => ({ element(e) { e.setAttribute("content", v); } });
    return new HTMLRewriter()
      .on("title", { element(e) { e.setInnerContent(topic.vi + " · TV Remote"); } })
      .on('meta[property="og:title"]', set(title))
      .on('meta[name="twitter:title"]', set(title))
      .on('meta[property="og:url"]', set(url.origin + "/guide?t=" + id))
      .on('meta[property="og:description"]', desc ? set(desc) : {})
      .on('meta[name="description"]', desc ? set(desc) : {})
      .transform(res);
  },
};

// The topic's heading (vi, en) and its first paragraph (vi, en), read from the page itself: nothing to keep in step
async function find(res, id) {
  const t = { vi: "", en: "", pvi: "", pen: "" };
  let inTopic = false, paras = 0;
  const grab = (key, only) => ({ text(x) { if (inTopic && (!only || only())) t[key] += x.text; } });
  await new HTMLRewriter()
    .on("article.topic", { element(e) {
      inTopic = e.getAttribute("id") === id; paras = 0;
    } })
    .on("article.topic > p", { element() { if (inTopic) paras++; } })
    .on("article.topic h3 span.vi", grab("vi"))
    .on("article.topic h3 span.en", grab("en"))
    .on("article.topic > p span.vi", grab("pvi", () => paras === 1))
    .on("article.topic > p span.en", grab("pen", () => paras === 1))
    .transform(res).arrayBuffer();
  for (const k in t) t[k] = t[k].replace(/\s+/g, " ").trim();
  return t;
}
