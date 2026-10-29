"""HomeConfig.THEME_KEYS, read from the Kotlin (the one list), for sign_themes.py and review_themes.py."""
import os, re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
KT = os.path.join(ROOT, "app", "shared", "src", "home", "kotlin", "dev", "tvremote", "home", "HomeConfig.kt")
LEGACY = ["wstyle"]   # read once by HomeConfig (into "wopt"): themes saved before the widget options have it


def theme_keys():
    m = re.search(r"val THEME_KEYS = listOf\((.*?)\)", open(KT, encoding="utf-8").read(), re.S)
    return re.findall(r'"(\w+)"', m.group(1)) + LEGACY
