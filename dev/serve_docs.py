#!/usr/bin/env python3
"""The intro site (docs/) on http://localhost:8000, served as Cloudflare Pages does: /guide is guide.html.
python3 -m http.server answers 404 for those links. Usage: dev/serve_docs.py [port]"""
import functools, http.server, os, pathlib, sys

DOCS = pathlib.Path(__file__).resolve().parent.parent / "docs"


class Pages(http.server.SimpleHTTPRequestHandler):
    def translate_path(self, path):
        p = super().translate_path(path)
        if not os.path.exists(p) and os.path.exists(p + ".html"):          # pretty URLs: /guide -> guide.html
            return p + ".html"
        return p

    def end_headers(self):
        # no Cache-Control lets a browser keep a changed js/css by its own guess: an edit then looked like it did nothing
        self.send_header("Cache-Control", "no-cache")
        super().end_headers()


port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
print(f"http://localhost:{port}/")
http.server.ThreadingHTTPServer(("", port), functools.partial(Pages, directory=str(DOCS))).serve_forever()
