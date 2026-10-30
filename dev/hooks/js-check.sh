#!/bin/sh
# Claude Code PostToolUse hook (Edit / Write on a .js file): the file must parse, then the node tests the pre-push runs
# (~3 s). A failure goes back to the agent (exit 2) so it is fixed in the same change, not found at push or by a user.
f=$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("file_path",""))' 2>/dev/null)
case "$f" in *.js) ;; *) exit 0 ;; esac
[ -f "$f" ] || exit 0
cd "$(git -C "$(dirname "$f")" rev-parse --show-toplevel 2>/dev/null)" || exit 0
if ! out=$(node --check "$f" 2>&1); then echo "$out" | head -15 >&2; echo "js-check: $f does not parse" >&2; exit 2; fi
case "$f" in */app/shared/*|*/app/tvgames/*|*/docs/*) ;; *) exit 0 ;; esac
log=$(mktemp)
if node --test app/shared/tests/ app/tvgames/tests/ >"$log" 2>&1; then rm -f "$log"; exit 0; fi
grep -E "^not ok|SyntaxError|ReferenceError|^# fail|error:" "$log" | head -20 >&2
echo "js-check: node tests fail after editing $f (full log $log)" >&2
exit 2
