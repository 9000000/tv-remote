"""Pair this computer with a TV for the Android TV Remote protocol (needed once per TV for the remote keys).

  pair.py <tv-ip>            pair if needed: the TV shows a 6-character code, type it here
  pair.py <tv-ip> --check    exit 0 if already paired, 3 if not

The same cert.pem/key.pem identify this computer to every TV it is paired with.
"""
import asyncio, os, sys
from pathlib import Path
from androidtvremote2 import AndroidTVRemote, CannotConnect, ConnectionClosed, InvalidAuth

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "app"))
from tvtools import CERT, KEY


async def main(host, check_only):
    remote = AndroidTVRemote("Claude Remote", CERT, KEY, host)
    if await remote.async_generate_cert_if_missing():
        os.chmod(KEY, 0o600)
    try:
        await remote.async_connect()
        remote.disconnect()
        print("already paired"); return 0
    except InvalidAuth:
        pass
    except (CannotConnect, ConnectionClosed, OSError) as e:
        print(f"cannot reach the TV's remote service on {host}:6466: {e!r}"); return 1
    if check_only:
        return 3
    await remote.async_start_pairing()
    while True:
        code = input("Nhập mã 6 ký tự đang hiện trên TV: ").strip()
        try:
            await remote.async_finish_pairing(code); break
        except InvalidAuth:
            print("Sai mã, thử lại.")
        except ConnectionClosed:
            print("TV đã đóng hộp thoại, mở lại..."); await remote.async_start_pairing()
    print("paired"); return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    sys.exit(asyncio.run(main(sys.argv[1], "--check" in sys.argv)))
