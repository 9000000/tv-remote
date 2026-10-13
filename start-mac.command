#!/bin/bash
# macOS: double-click this file. It starts the TV remote on this computer and opens it in the browser.
# Close the window (or press Ctrl+C) to stop. Linux: run ./start-linux.sh, which does the same.
cd "$(dirname "$0")" || exit 1
if [ ! -x venv/bin/python ]; then
  echo "First run: creating the Python environment..."
  python3 -m venv venv && ./venv/bin/pip install -q -r app/web/requirements.txt || { echo "Setup failed. Is Python 3.10+ installed?"; read -r; exit 1; }
fi
( sleep 2; open "http://127.0.0.1:8765" 2>/dev/null || xdg-open "http://127.0.0.1:8765" 2>/dev/null ) &
exec ./venv/bin/python app/web/server.py "$@"
