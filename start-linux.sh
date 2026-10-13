#!/bin/bash
# Linux: run this file. It starts the TV remote on this computer and opens it in the browser. Ctrl+C to stop.
exec "$(dirname "$0")/start-mac.command" "$@"
