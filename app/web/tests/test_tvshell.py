"""The shell recipes for the Apps tab and the TV card (tvshell.py) against tvshell_cases.json, which TvShellTest.kt checks the
apps' copy (TvShell.kt) against too. After changing a recipe on purpose: python3 app/web/tests/test_tvshell.py --write, then
make TvShell.kt give the same."""
import json, sys, unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import tvshell

CASES = Path(__file__).resolve().parent / "tvshell_cases.json"

CALLS = [
    ["backup", "com.esaba.downloader"], ["backup", "a.b"], ["backup", "com.x;reboot"], ["backup", "../x"], ["backup", ""],
    ["backup", "com.x\n"], ["backup", "cóm.x"],
    ["restore", "/data/local/tmp/tvr-rs-0123abcd.tar"], ["restore", "/sdcard/x.tar"], ["restore", "/data/local/tmp/tvr-rs-0123abcd.tar;x"],
    ["log", "com.google.android.youtube.tv", ""], ["log", "com.a.b", "10-15 04:30:07.960"], ["log", "com.a.b", "10-15 04:30:07"],
    ["log", "com.a.b", "10-15 04:30:07.960'; reboot; '"], ["log", "x", ""],
    ["tvset", "dns", ""], ["tvset", "dns", "dns.adguard-dns.com"], ["tvset", "dns", " Family.AdGuard-DNS.com "], ["tvset", "dns", "a;b.com"],
    ["tvset", "dns", "localhost"], ["tvset", "dns", "-x.com"], ["tvset", "dns", "x.c"],
    ["tvset", "sleep", "-1"], ["tvset", "sleep", 1800000], ["tvset", "sleep", "0"], ["tvset", "sleep", "1_000_000"], ["tvset", "sleep", "+60000"],
    ["tvset", "sleep", "99999999999"], ["tvset", "saver", "off"], ["tvset", "saver", "300000"], ["tvset", "saver", "5"],
    ["tvset", "density", "reset"], ["tvset", "density", "288"], ["tvset", "density", "2000"], ["tvset", "density", "28.8"],
    ["tvset", "home", "com.spocky.projengmenu/.ui.home.MainActivity"], ["tvset", "home", "a.b/.C$D"], ["tvset", "home", "a.b/c';reboot;'"],
    ["tvset", "home", "nohome"], ["tvset", "volume", "1"],
    ["app_info", "com.google.android.youtube.tv"], ["app_info", "a.b;reboot"],
    ["perm", "com.a.b", "android.permission.RECORD_AUDIO", True], ["perm", "com.a.b", "android.permission.RECORD_AUDIO", False],
    ["perm", "com.a.b", "android.permission.X;reboot", True], ["perm", "com.a.b", "", True], ["perm", "a;b", "android.permission.X", True],
]


def run(call):
    fn, *args = call
    try:
        return getattr(tvshell, fn)(*args)
    except ValueError:
        return None                                                   # refused


def table():
    return {"consts": {k: getattr(tvshell, k) for k in ("CLEAN", "REBOOT_PREP", "TVSET_READ", "BOOT", "SETTINGS_READ")},
            "calls": [{"call": c, "out": run(c)} for c in CALLS]}


class Recipes(unittest.TestCase):
    def test_same_as_the_table(self):
        want = json.loads(CASES.read_text())
        got = table()
        self.assertEqual(got["consts"], want["consts"])
        for g, w in zip(got["calls"], want["calls"]):
            self.assertEqual(g, w)
        self.assertEqual(len(got["calls"]), len(want["calls"]))

    def test_refuses_what_could_reach_the_shell(self):
        for c in CALLS:
            out = run(c)
            if out is not None:
                for bad in ("reboot", "\n"):
                    self.assertNotIn(bad, out.replace("REBOOT", ""), c)


class StorageRoot(unittest.TestCase):
    def test_roots(self):
        import adbtools
        for p in ("/sdcard", "/storage", "/storage/emulated", "/storage/emulated/0", "/storage/self/primary", "/storage/1A2B-3C4D"):
            self.assertTrue(adbtools.storage_root(p), p)
        for p in ("/sdcard/Download", "/storage/emulated/0/Movies", "/storage/1A2B-3C4D/x"):
            self.assertFalse(adbtools.storage_root(p), p)


if __name__ == "__main__":
    if "--write" in sys.argv:
        CASES.write_text(json.dumps(table(), ensure_ascii=False, indent=1) + "\n")
    else:
        unittest.main()
