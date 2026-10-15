"""Guards the computer's server relies on: paths inside shared storage, safe file names, the saved-TV list, and reading the
TV's "Allow USB debugging?" screen. python3 -m unittest discover app/web/tests"""
import json, os, sys, tempfile, unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import adbtools
from adbtools import AdbError, FP_RE, PKG_RE, _nodes, _part, safe_name, safe_path


class Paths(unittest.TestCase):
    def test_inside_shared_storage(self):
        self.assertEqual(safe_path(""), "/sdcard/Download")
        self.assertEqual(safe_path("/sdcard//Movies/./a b/"), "/sdcard/Movies/a b")
        self.assertEqual(safe_path("/storage/ABCD-1234"), "/storage/ABCD-1234")

    def test_refuses_escapes(self):
        for bad in ("/sdcard/../data", "/data/local/tmp", "/", "/sdcardx/file"):
            with self.assertRaises(AdbError, msg=bad):
                safe_path(bad)

    def test_names(self):
        self.assertEqual(safe_name("../../etc/passwd"), "passwd")
        self.assertEqual(safe_name("a;b.txt"), "a_b.txt")
        self.assertEqual(safe_name("phim Việt (1).mp4"), "phim Việt (1).mp4")
        self.assertEqual(safe_name("..."), "file")
        self.assertEqual(len(safe_name("x" * 300)), 120)

    def test_package_names(self):
        self.assertTrue(PKG_RE.match("com.google.android.youtube.tv"))
        for bad in ("youtube", "com.x; reboot", "1com.x", "com..x"):
            self.assertFalse(PKG_RE.match(bad), bad)


class Memory(unittest.TestCase):
    def test_sums_per_app_and_skips_the_second_table(self):
        sample = """Total PSS by process:
    119,516K: com.google.android.youtube.tv (pid 4709 / activities)
     73,623K: com.google.android.katniss:search (pid 4380)
     40,000K: com.google.android.katniss:interactor (pid 24727)
      3,000K: com.tiny.app (pid 1)

Total PSS by OOM adjustment:
    119,516K: com.google.android.youtube.tv (pid 4709 / activities)
"""
        self.assertEqual(adbtools.memory_by_app(sample), [("com.google.android.youtube.tv", 116), ("com.google.android.katniss", 110)])
        self.assertEqual(adbtools.memory_by_app("nothing"), [])

    def test_protected_apps(self):
        for p in ("com.google.android.katniss", "com.android.systemui", "dev.tvremote.setup", "android"):
            self.assertTrue(adbtools.PROTECTED.fullmatch(p), p)
        for p in ("com.sony.dtv.demomode", "com.netflix.ninja", "android.autoinstalls.config.sony.bravia"):
            self.assertFalse(adbtools.PROTECTED.fullmatch(p), p)


class AllowScreen(unittest.TestCase):
    XML = ('<hierarchy><node text="The computer\'s RSA key fingerprint is:&#10;AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89" bounds="[0,0][100,40]" />'
           '<node resource-id="com.android.systemui:id/alwaysUse" class="android.widget.CheckBox" checked="false" focused="true" bounds="[10,50][210,90]" />'
           '<node resource-id="android:id/button2" class="android.widget.Button" bounds="[300,100][400,140]" />'
           '<node resource-id="android:id/button1" class="android.widget.Button" bounds="[500,100][600,140]" /></hierarchy>')

    def test_reads_the_parts(self):
        nodes = _nodes(self.XML)
        self.assertEqual(FP_RE.search(nodes[0]["text"]).group(0), "AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89")
        self.assertEqual((_part(nodes, "always")["x"], _part(nodes, "always")["y"]), (110, 70))
        self.assertTrue(_part(nodes, "always")["focused"])
        self.assertEqual(_part(nodes, "allow")["x"], 550)
        self.assertEqual(_part(nodes, "cancel")["x"], 350)
        self.assertIsNone(_part([], "allow"))


class SavedTvs(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        os.environ["TVS_FILE"] = str(Path(self.tmp.name) / "tvs.json")
        import importlib, tvconfig
        self.tv = importlib.reload(tvconfig)

    def tearDown(self):
        os.environ.pop("TVS_FILE", None)
        self.tmp.cleanup()

    def test_moving_a_tv_to_its_tailscale_address_keeps_one_entry(self):
        cfg = self.tv.load()
        self.assertEqual(cfg, {"current": "", "tvs": []})
        self.tv.upsert(cfg, {"name": "Phòng khách", "host": "192.168.1.88", "lan": "192.168.1.88", "mac": "AA:BB:CC:DD:EE:FF"})
        self.tv.upsert(cfg, {"name": "Phòng khách", "host": "100.64.0.10", "lan": "192.168.1.88", "mac": ""}, replace_host="192.168.1.88")
        self.assertEqual(len(cfg["tvs"]), 1)
        self.assertEqual(cfg["tvs"][0]["host"], "100.64.0.10")
        self.assertEqual(cfg["tvs"][0]["mac"], "AA:BB:CC:DD:EE:FF")          # an empty value never wipes a known one
        self.assertEqual(cfg["current"], "100.64.0.10")
        self.tv.save(cfg)
        self.assertEqual(json.loads(Path(os.environ["TVS_FILE"]).read_text())["tvs"][0]["name"], "Phòng khách")

    def test_removing_the_current_tv_picks_another(self):
        cfg = self.tv.load()
        self.tv.upsert(cfg, {"name": "A", "host": "10.0.0.2"})
        self.tv.upsert(cfg, {"name": "B", "host": "10.0.0.3"})
        self.tv.remove(cfg, "10.0.0.3")
        self.assertEqual(cfg["current"], "10.0.0.2")
        self.tv.remove(cfg, "10.0.0.2")
        self.assertEqual(cfg, {"current": "", "tvs": []})


class ApkPackage(unittest.TestCase):
    """The package name read from an APK's binary manifest, to check after an install that the TV kept the app."""
    def test_reads_package(self):
        m = (Path(__file__).resolve().parents[2] / "shared/src/test/resources/setup-manifest.bin").read_bytes()
        self.assertEqual(adbtools.apk_package(m), "dev.tvremote.setup")

    def test_garbage_is_none(self):
        self.assertIsNone(adbtools.apk_package(b""))
        self.assertIsNone(adbtools.apk_package(b"not a manifest at all"))

    def test_info_and_fit(self):
        """Version and minimum Android, and the check before installing; ApkPackageTest in ManagePathsTest.kt checks the same."""
        m = (Path(__file__).resolve().parents[2] / "shared/src/test/resources/setup-manifest.bin").read_bytes()
        i = adbtools.apk_info(m)
        self.assertEqual((i["package"], i["minSdkVersion"]), ("dev.tvremote.setup", "21"))
        self.assertTrue(i["versionName"] and i["versionCode"].isdigit())
        self.assertEqual(adbtools.apk_abis(["lib/arm64-v8a/a.so", "lib/x86/a.so", "lib/x86/b.so", "lib/readme.txt", "assets/lib/x/c.so"]), ["arm64-v8a", "x86"])
        self.assertIsNone(adbtools.apk_fits(i, [], 28, ["armeabi-v7a"]))
        self.assertEqual(adbtools.apk_fits({"minSdkVersion": "29"}, [], 28, []), "apk needs sdk 29, the tv has sdk 28")
        self.assertEqual(adbtools.apk_fits({}, ["arm64-v8a"], 28, ["armeabi-v7a", "armeabi"]), "apk is built for arm64-v8a, the tv runs armeabi-v7a,armeabi")
        self.assertIsNone(adbtools.apk_fits({}, ["arm64-v8a", "armeabi-v7a"], 28, ["armeabi-v7a"]))


if __name__ == "__main__":
    unittest.main()
