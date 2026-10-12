#!/bin/bash
# Double-click (or run) to start the TV remote and open it in the browser. Ctrl+C / close the window to stop.
cd "$(dirname "$0")" || exit 1
if [ ! -x venv/bin/python ]; then
  echo "First run: creating the Python environment..."
  python3 -m venv venv && ./venv/bin/pip install -q -r requirements.txt || { echo "Setup failed. Is Python 3.10+ installed?"; read -r; exit 1; }
fi
( sleep 2; open "http://127.0.0.1:8765" 2>/dev/null || xdg-open "http://127.0.0.1:8765" 2>/dev/null ) &
exec ./venv/bin/python app/server.py "$@"
