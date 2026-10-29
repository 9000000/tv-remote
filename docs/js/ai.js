/* "Copy the guide for AI" on the guide and the home page: a prompt that sends any chat AI to the guide made for it */
(function(){
  "use strict";
  // to guide/<lang>/index.md (one line per topic, written by dev/gen/guide_md.js) and on to only the topics it needs,
  // not the whole guide page (~25x the tokens); on a guide topic, that topic too
  var toast = document.getElementById("toast"), tt;
  function say(s){ toast.textContent = s; toast.classList.add("show"); clearTimeout(tt); tt = setTimeout(function(){ toast.classList.remove("show"); }, 2200); }
  function aiPrompt(){
    var vi = document.documentElement.dataset.lang === "vi", base = location.origin + "/guide/" + (vi ? "vi" : "en") + "/";
    var id = new URLSearchParams(location.search).get("t") || location.hash.slice(1), t = id && document.getElementById(id), here = t && t.classList.contains("topic") ? base + t.id : "";
    return vi
      ? "Tôi có câu hỏi về TV Remote (điều khiển Android TV / Google TV, LG, Samsung, Roku, VIDAA từ điện thoại hoặc máy tính).\n"
        + "Hướng dẫn cho AI: " + base + " (mỗi mục một dòng). Đọc trang đó trước, rồi chỉ mở trang của mục cần dùng.\n"
        + (here ? "Tôi đang xem mục: " + here + "\n" : "")
        + "Trả lời đúng việc tôi hỏi, theo hướng dẫn. Cần biết TV hay thiết bị của tôi thì hỏi. Có nhiều bước thì đưa từng bước, nói rõ làm trên app hay màn hình nào, chờ tôi báo xong mới sang bước tiếp.\n\n"
        + "Câu hỏi của tôi: "
      : "I have a question about TV Remote (control an Android TV / Google TV, LG, Samsung, Roku or VIDAA TV from a phone or computer).\n"
        + "The guide for AI: " + base + " (one line per topic). Read it first, then open only the topics you need.\n"
        + (here ? "I am reading: " + here + "\n" : "")
        + "Answer what I ask, from the guide. Ask about my TV or device only when it matters. For several steps, give one at a time, saying which app or screen it happens in, and wait until I say it worked.\n\n"
        + "My question: ";
  }
  [].forEach.call(document.querySelectorAll(".gai"), function(b){
    b.addEventListener("click", function(){
      var p = aiPrompt(), vi = document.documentElement.dataset.lang === "vi";
      (navigator.clipboard ? navigator.clipboard.writeText(p) : Promise.reject())
        .then(function(){ say(vi ? "Đã chép. Dán vào ChatGPT, Claude, Gemini…" : "Copied. Paste it into ChatGPT, Claude, Gemini…"); })
        .catch(function(){ window.prompt(vi ? "Chép đoạn này cho AI:" : "Copy this for your AI:", p); });
    });
  });
})();
