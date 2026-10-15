"""Several TVs at once (fleet.py): reading INFO's output against fleet_cases.json, which FleetTest.kt checks Fleet.kt against
too, and the actions refused before any TV is touched."""
import json, sys, unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import fleet
from adbtools import AdbError

CASES = json.loads((Path(__file__).resolve().parent / "fleet_cases.json").read_text())


class FleetTest(unittest.TestCase):
    def test_info_command(self):
        self.assertEqual(CASES["INFO"], fleet.INFO)

    def test_parse(self):
        for c in CASES["cases"]:
            self.assertEqual(c["info"], fleet.parse(c["out"]))

    def test_actions(self):
        self.assertTrue(callable(fleet.action("uninstall", "com.esaba.downloader")))
        self.assertTrue(callable(fleet.action("reboot", "")))
        for bad in ("", "com.x;reboot", "../x", "com.x\n"):
            self.assertRaises(ValueError, fleet.action, "uninstall", bad)
        self.assertRaises(AdbError, fleet.action, "uninstall", "dev.tvremote.setup")   # what the TV or this project needs
        self.assertRaises(ValueError, fleet.action, "format", "")


if __name__ == "__main__":
    unittest.main()
