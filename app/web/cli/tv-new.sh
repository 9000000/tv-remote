#!/bin/bash
# app/web/cli/tv-new.sh <tv-ip>   (the work is in tv_new.py, shared with Windows)
cd "$(dirname "$0")/../../.." && exec ./venv/bin/python app/web/cli/tv_new.py "$@"
