"""python3 -m unittest discover app/web/tests   (no packages needed beyond the server's own)"""
import array, asyncio, base64, json, sys, unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import scenes as scn
from adbtools import AdbError


class FakeAdb:
    """Answers shell commands from a table of (substring, answer); records what was asked."""
    def __init__(self, answers=()):
        self.answers, self.cmds = list(answers), []

    async def shell(self, cmd, timeout=30):
        self.cmds.append(cmd)
        return next((a for k, a in self.answers if k in cmd), "")


def run(c):
    return asyncio.run(c)


class Clean(unittest.TestCase):
    def test_keeps_good_steps_and_drops_bad_ones(self):
        out = scn.clean([{"id": "s1", "name": " Xem phim ", "icon": "🎬", "steps": [
            {"do": "wake"}, {"do": "app", "pkg": "com.google.android.youtube.tv"}, {"do": "app", "pkg": "not a package; rm -rf /"},
            {"do": "key", "key": "mute"}, {"do": "key", "key": "NOPE"}, {"do": "volume", "level": 250}, {"do": "wait", "s": "2.5"},
            {"do": "link", "url": "javascript"}, {"do": "link", "url": "https://youtu.be/x"}, {"do": "timer", "min": -5}, {"do": "explode"}]}])
        self.assertEqual(out, [{"id": "s1", "name": "Xem phim", "icon": "🎬", "steps": [
            {"do": "wake"}, {"do": "app", "pkg": "com.google.android.youtube.tv"}, {"do": "key", "key": "MUTE"}, {"do": "volume", "level": 100},
            {"do": "wait", "s": 2.5}, {"do": "link", "url": "https://youtu.be/x"}, {"do": "timer", "min": 0}]}])

    def test_drops_scenes_without_a_name_or_steps_and_limits_counts(self):
        self.assertEqual(scn.clean([{"name": "", "steps": [{"do": "wake"}]}, {"name": "x", "steps": []}, "junk", None]), [])
        self.assertEqual(scn.clean("not a list"), [])
        many = scn.clean([{"name": f"s{i}", "steps": [{"do": "wake"}] * 40} for i in range(30)])
        self.assertEqual(len(many), 24)
        self.assertEqual(len(many[0]["steps"]), 30)

    def test_bad_ids_are_replaced(self):
        out = scn.clean([{"id": "../../etc", "name": "a", "steps": [{"do": "sleep"}]}])
        self.assertRegex(out[0]["id"], r"^s[0-9a-f]+$")


class Backup(unittest.TestCase):
    def test_a_backup_reads_back_through_clean(self):
        scenes = scn.clean([{"id": "s1", "name": "Đi ngủ", "icon": "🌙", "steps": [{"do": "timer", "min": 30}]}])
        file = json.loads(json.dumps(scn.backup(scenes), ensure_ascii=False))
        self.assertEqual(file["kind"], "scenes")
        self.assertEqual(scn.clean(file["scenes"]), scenes)


class Run(unittest.TestCase):
    def test_runs_steps_in_order(self):
        adb = FakeAdb([("resolve-activity", "priority=0\ncom.google.android.youtube.tv/.Main\n"), ("media_session", "")])
        failed = run(scn.run(adb, [{"do": "wake"}, {"do": "app", "pkg": "com.google.android.youtube.tv"}, {"do": "volume", "level": 12}, {"do": "key", "key": "MUTE"}]))
        self.assertIsNone(failed)
        self.assertEqual(adb.cmds[0], "input keyevent 224")
        self.assertIn("am start -n com.google.android.youtube.tv/.Main", adb.cmds[2])
        self.assertIn("cmd media_session volume --stream 3 --set 12", adb.cmds[3])
        self.assertEqual(adb.cmds[-1], "input keyevent 164")

    def test_a_failing_step_is_named_and_the_rest_still_runs(self):
        adb = FakeAdb([("resolve-activity", "No activity found")])
        failed = run(scn.run(adb, [{"do": "app", "pkg": "com.missing.app"}, {"do": "sleep"}]))
        self.assertIn("1:", failed)
        self.assertEqual(adb.cmds[-1], "input keyevent 223")

    def test_volume_falls_back_to_the_old_command(self):
        adb = FakeAdb([("media_session", "Unknown command"), ("media volume", "")])
        run(scn.volume(adb, 7))
        self.assertIn("media volume --stream 3 --set 7", adb.cmds[1])

    def test_volume_presses_keys_when_the_tv_keeps_its_level(self):
        adb = FakeAdb([("--get", "[V] volume is 3 in range [0..15]")])
        run(scn.volume(adb, 5))
        self.assertEqual(adb.cmds[-1], "input keyevent 24; input keyevent 24")
        adb = FakeAdb([("--get", "volume is 9 in range")])
        run(scn.volume(adb, 9))
        self.assertNotIn("keyevent", adb.cmds[-1])


class Rules(unittest.TestCase):
    def test_reads_the_broadcast_answer(self):
        state = {"ok": True, "sleep_at": 0, "running": True}
        adb = FakeAdb([("RulesReceiver", 'Broadcast completed: result=-1, data="%s"' % base64.b64encode(json.dumps(state).encode()).decode())])
        self.assertEqual(run(scn.rules(adb, {"do": "sleep", "minutes": 30})), state)
        sent = adb.cmds[0].split("--es set ")[1].split(" ")[0]
        self.assertEqual(json.loads(base64.b64decode(sent)), {"do": "sleep", "minutes": 30})

    def test_starts_the_service_when_the_receiver_could_not(self):
        state = {"ok": True, "wanted": True, "running": False}
        adb = FakeAdb([("RulesReceiver", 'data="%s"' % base64.b64encode(json.dumps(state).encode()).decode())])
        run(scn.rules(adb, None))
        self.assertTrue(any("start-foreground-service" in c for c in adb.cmds))

    def test_without_tv_setup(self):
        self.assertEqual(run(scn.rules(FakeAdb([("RulesReceiver", "Broadcast completed: result=0")]))), {"setup": "missing"})
        self.assertEqual(run(scn.rules(FakeAdb([("RulesReceiver", "result=0"), ("pm path", "package:/data/app/x.apk")]))), {"setup": "old"})


class Downmix(unittest.TestCase):
    def test_four_samples_become_one(self):
        pcm = array.array("h", [100, 300, 500, 700, -1000, -1000, -3000, -3000]).tobytes()
        out, carry = scn.downmix(pcm)
        self.assertEqual(array.array("h", out).tolist(), [400, -2000])
        self.assertEqual(carry, b"")

    def test_leftover_bytes_carry_to_the_next_piece(self):
        pcm = array.array("h", [8, 8, 8, 8, 4, 4]).tobytes()
        out, carry = scn.downmix(pcm)
        self.assertEqual(array.array("h", out).tolist(), [8])
        self.assertEqual(len(carry), 4)
        out2, carry2 = scn.downmix(array.array("h", [4, 4]).tobytes(), carry)
        self.assertEqual(array.array("h", out2).tolist(), [4])
        self.assertEqual(carry2, b"")

    def test_packet_framing(self):
        self.assertEqual(scn.packet(2, b"ab"), b"\x02\x00\x00\x00\x02ab")


if __name__ == "__main__":
    unittest.main()
