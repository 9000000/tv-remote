"""python3 -m unittest discover -s dev/sign/tests : --local validation of review_themes.py (the worker's rules, ported)."""
import json, os, sys, tempfile, unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import review_themes as rt   # noqa: E402

JPG = b"\xff\xd8\xff\xe0" + b"\0" * 100


def folder(conf=None, preview=JPG, wall=None, walls=None, **over):
    d = tempfile.mkdtemp(prefix="sub-")
    o = {"tvtheme": 1, "name": "Night", "by": "ann", "conf": {"wall": "dawn", "shade": 40} if conf is None else conf}
    o.update(over)
    json.dump(o, open(os.path.join(d, "theme.tvtheme.json"), "w"))
    if preview is not None:
        open(os.path.join(d, "preview.jpg"), "wb").write(preview)
    if wall is not None:
        open(os.path.join(d, "wall.jpg"), "wb").write(wall)
    for k, b in (walls or {}).items():
        os.makedirs(os.path.join(d, "walls"), exist_ok=True)
        open(os.path.join(d, "walls", k + ".jpg"), "wb").write(b)
    return d


class Local(unittest.TestCase):
    def test_good(self):
        t, e = rt.validate_folder(folder(wall=JPG))
        self.assertIsNone(e)
        self.assertEqual((t["name"], t["by"], t["lang"], t["flags"], t["has_wall"]), ("Night", "ann", "en", [], True))

    def test_theme_keys_match_kotlin(self):
        kt = open(os.path.join(rt.ROOT, "app/shared/src/home/kotlin/dev/tvremote/home/HomeConfig.kt")).read()
        for k in rt.THEME_KEYS:
            self.assertIn('"%s"' % k, kt)

    def test_refused(self):
        for d, why in [(folder(conf={"evil": 1}), "not theme settings"), (folder(preview=JPG + b"0" * 150_000), "150 KB"),
                       (folder(preview=b"GIF89a" + b"0" * 20), "not a jpeg"), (folder(wall=JPG + b"0" * 1_000_000), "1 MB"),
                       (folder(wall=b"RIFF....WEBPxxxx"), "not a jpeg"), (folder(by=""), "by"), (folder(tvtheme=2), "tvtheme"),
                       (folder(preview=None), "missing"), (folder(lang="de"), "lang")]:
            t, e = rt.validate_folder(d)
            self.assertIsNone(t)
            self.assertIn(why, e)

    def test_rotation_photos(self):
        t, e = rt.validate_folder(folder(conf={"wall": "photo2", "walls": ["photo", "photo2", "photo3"]}, walls={"photo": JPG, "photo2": JPG, "photo3": JPG}))
        self.assertIsNone(e)
        self.assertTrue(t["has_wall"])
        self.assertEqual(rt.photo_order({"wall": "photo2", "walls": ["photo", "photo2", "photo3"]}, t["walls"]), ["photo2", "photo", "photo3"])
        for ws, why in [({"pic": JPG}, "name it"), ({"photo": JPG + b"0" * 360_000}, "350 KB"), ({"photo": b"RIFF....WEBPxxxx"}, "not a jpeg")]:
            t, e = rt.validate_folder(folder(walls=ws))
            self.assertIsNone(t)
            self.assertIn(why, e)

    def test_flags(self):
        t, _ = rt.validate_folder(folder(conf={"wall": "web", "wurl": "https://evil.example/x.jpg"}))
        self.assertTrue(any("wurl" in f for f in t["flags"]) and any("URL in conf" in f for f in t["flags"]))
        t, _ = rt.validate_folder(folder(conf={"wall": "web", "wurl": "https://tvremote.pages.dev/home/loop.html?i=https%3A%2F%2Ftvremote.pages.dev%2Fa.jpg"}))
        self.assertEqual(t["flags"], [])


if __name__ == "__main__":
    unittest.main()
