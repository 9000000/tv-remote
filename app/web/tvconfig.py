"""Saved TVs: tvs.json in the repo root (git-ignored, it holds addresses of your own devices).

  {"current": "100.64.0.10",
   "tvs": [{"name": "Living room", "host": "100.64.0.10", "lan": "192.168.1.88", "mac": "AA:BB:CC:DD:EE:FF",
            "relay_user": "...", "relay_host": "...", "relay_cmd": "..."}]}

`host` is what we connect to (Tailscale IP when the TV has one, so it works from anywhere). `mac` and the optional
relay_* fields are for Wake-on-LAN, which only works from a machine on the TV's own network (see cli/wake.py).
"""
import json, os
from pathlib import Path
from tvtools import ROOT

FILE = Path(os.environ.get("TVS_FILE") or ROOT / "tvs.json")


def load():
    try:
        cfg = json.loads(FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        cfg = {}
    cfg.setdefault("current", "")
    cfg.setdefault("tvs", [])
    return cfg


def save(cfg):
    FILE.write_text(json.dumps(cfg, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def find(cfg, host):
    return next((t for t in cfg["tvs"] if t.get("host") == host), None)


def upsert(cfg, entry, replace_host=None):
    """Add or update a TV. replace_host: the entry it used to be saved under (e.g. its LAN IP before it got a Tailscale IP)."""
    old = find(cfg, replace_host or entry["host"]) or find(cfg, entry["host"])
    if old:
        old.update({k: v for k, v in entry.items() if v not in ("", None)})
        entry = old
    else:
        cfg["tvs"].append(entry)
    cfg["tvs"] = [t for t in cfg["tvs"] if t is entry or t.get("host") != entry["host"]]
    cfg["current"] = entry["host"]
    return entry


def remove(cfg, host):
    cfg["tvs"] = [t for t in cfg["tvs"] if t.get("host") != host]
    if cfg["current"] == host:
        cfg["current"] = cfg["tvs"][0]["host"] if cfg["tvs"] else ""
