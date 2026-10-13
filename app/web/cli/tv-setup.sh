#!/bin/bash
# app/web/cli/tv-setup.sh [apply|undo] [host:port]   (the work is in tv_setup.py, shared with Windows)
cd "$(dirname "$0")/../../.." && exec ./venv/bin/python app/web/cli/tv_setup.py "$@"
