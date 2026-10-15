"""Answering the TV's "Allow USB debugging?" (adbtools.Adb.prompt_allow) against a pretend TV that queues the questions of
several keys, as real TVs do: Allow is only ever pressed on the question about the key asked for, never on a stranger's that
takes its place, whether the TV takes taps or only the arrow keys. AdbPromptTest.kt plays the same scenes against AdbPrompt.kt."""
import asyncio, re, sys, unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import adbtools

OURS = "AA:" * 15 + "AA"
STRANGER = "BB:" * 15 + "BB"
PARTS = {"always": (110, 70), "cancel": (350, 120), "allow": (550, 120)}   # where the box draws them, as on the emulator
ORDER = ["always", "cancel", "allow"]                                 # the arrow keys move through them in this order


class FakeTv(adbtools.Adb):
    """The question box of a TV. [queue]: the keys asking, first on screen. [taps]: False for a TV that ignores taps.
    [after]: called with (part, tv) after each press, to make the screen change under the code (a question timing out...)."""

    def __init__(self, queue, taps=True, after=None, focus="always"):
        super().__init__("fake:5555")
        self.queue = [{"fp": fp, "always": False} for fp in queue]
        self.taps, self.after, self.focus, self.answers = taps, after, focus, []

    async def shell(self, cmd, timeout=None, **kw):
        if cmd.startswith("dumpsys window"):
            return "mCurrentFocus=Window{1 u0 com.android.systemui/com.android.systemui.usb.UsbDebuggingActivity}" if self.queue \
                else "mCurrentFocus=Window{2 u0 com.google.android.tvlauncher/.MainActivity}"
        if cmd.startswith("uiautomator dump"):
            if not self.queue:
                return "<hierarchy></hierarchy>"
            q = self.queue[0]
            node = lambda part, rid, cls, extra="": (f'<node resource-id="{rid}" class="{cls}" focused="{str(self.focus == part).lower()}" {extra}'
                                                    f'bounds="[{PARTS[part][0] - 10},{PARTS[part][1] - 10}][{PARTS[part][0] + 10},{PARTS[part][1] + 10}]" />')
            return ("<hierarchy>" + f'<node text="The computer\'s RSA key fingerprint is:&#10;{q["fp"]}" bounds="[0,0][100,40]" />'
                    + node("always", "com.android.systemui:id/alwaysUse", "android.widget.CheckBox", f'checked="{str(q["always"]).lower()}" ')
                    + node("cancel", "android:id/button2", "android.widget.Button") + node("allow", "android:id/button1", "android.widget.Button")
                    + "</hierarchy>")
        m = re.match(r"input tap (\d+) (\d+)", cmd)
        if m:
            if self.taps:
                hit = next((p for p, (x, y) in PARTS.items() if abs(x - int(m[1])) <= 10 and abs(y - int(m[2])) <= 10), None)
                if hit:
                    self.press(hit)
            return ""
        m = re.match(r"input keyevent (\d+)", cmd)
        if m and self.queue:
            k, i = int(m[1]), ORDER.index(self.focus)
            if k == 23:
                self.press(self.focus)
            elif k in (20, 22):
                self.focus = ORDER[min(i + 1, 2)]
            elif k in (19, 21):
                self.focus = ORDER[max(i - 1, 0)]
        return ""

    def press(self, part):
        if not self.queue:
            return
        q = self.queue[0]
        if part == "always":
            q["always"] = not q["always"]
        else:
            self.answers.append((q["fp"], "allowed" if part == "allow" else "declined"))
            self.queue.pop(0)
            self.focus = "always"
        if self.after:
            self.after(part, self)


def run(tv, want=OURS, others=False):
    async def nap(*a, **k):                                           # the waits between steps: none needed here
        pass
    with mock.patch.object(adbtools.asyncio, "sleep", nap):
        return asyncio.run(tv.prompt_allow(want, 0, others))


def vanish(part, tv):
    """The question about our key goes away (it timed out, or someone pressed Cancel) right after "Always" is ticked; the
    TV then shows the next key's question in its place."""
    if part == "always" and tv.queue and tv.queue[0]["fp"] == OURS:
        tv.queue.pop(0)


class Allow(unittest.TestCase):
    def test_ours(self):
        tv = FakeTv([OURS])
        self.assertEqual(run(tv), "allowed")
        self.assertEqual(tv.answers, [(OURS, "allowed")])

    def test_ours_with_the_arrow_keys(self):
        tv = FakeTv([OURS], taps=False)
        self.assertEqual(run(tv), "allowed")
        self.assertEqual(tv.answers, [(OURS, "allowed")])

    def test_a_stranger_first_is_left_alone(self):
        tv = FakeTv([STRANGER, OURS])
        self.assertEqual(run(tv), "other:" + STRANGER)
        self.assertEqual(tv.answers, [])

    def test_a_stranger_first_is_declined_when_asked(self):
        tv = FakeTv([STRANGER, OURS])
        self.assertEqual(run(tv, others=True), "allowed")
        self.assertEqual(tv.answers, [(STRANGER, "declined"), (OURS, "allowed")])

    def test_a_stranger_taking_our_place_is_never_allowed(self):
        for taps in (True, False):
            for others in (False, True):
                tv = FakeTv([OURS, STRANGER], taps=taps, after=vanish)
                run(tv, others=others)
                self.assertNotIn((STRANGER, "allowed"), tv.answers, f"taps={taps} others={others}")

    def test_a_stranger_appearing_while_the_keys_move_is_never_allowed(self):
        tv = FakeTv([OURS, STRANGER], taps=False)
        real = tv.shell

        async def shell(cmd, timeout=None, **kw):                      # ours goes away after the first arrow key toward Allow
            out = await real(cmd, timeout)
            if re.match(r"input keyevent (20|22)$", cmd) and tv.queue and tv.queue[0]["fp"] == OURS and tv.queue[0]["always"]:
                tv.queue.pop(0)
                tv.focus = "allow"                                    # and the stranger's box opens with Allow in focus
            return out
        tv.shell = shell
        run(tv)
        self.assertNotIn((STRANGER, "allowed"), tv.answers)


if __name__ == "__main__":
    unittest.main()
