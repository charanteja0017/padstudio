#!/usr/bin/env bash
# Screenshot a local HTML file with Playwright's cached headless Chromium shell; it writes and exits
# (desktop Chrome --headless wrote the file but never exited on this Mac).
#
#   tools/lib/render-html.sh docs/redesign/kit.html /tmp/kit.png 1400x2000
#
# The size is the browser window in CSS px (default 1400x1000). Output is a PNG at device scale 1.
set -euo pipefail

HTML="${1:?usage: render-html.sh <file.html> <out.png> [WIDTHxHEIGHT]}"
OUT="${2:?usage: render-html.sh <file.html> <out.png> [WIDTHxHEIGHT]}"
SIZE="${3:-1400x1000}"
SHELL_BIN="$(ls -d "$HOME"/Library/Caches/ms-playwright/chromium_headless_shell-*/chrome-headless-shell-mac-arm64/chrome-headless-shell 2>/dev/null | tail -1)"
[[ -x "$SHELL_BIN" ]] || { echo "render-html: no Playwright chromium_headless_shell in ~/Library/Caches/ms-playwright" >&2; exit 1; }

ABS="$(cd "$(dirname "$HTML")" && pwd)/$(basename "$HTML")"
mkdir -p "$(dirname "$OUT")"
"$SHELL_BIN" --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --window-size="${SIZE/x/,}" --virtual-time-budget=4000 \
  --screenshot="$OUT" "file://$ABS" 2>/dev/null >/dev/null
[[ -s "$OUT" ]] || { echo "render-html: no screenshot written for $HTML" >&2; exit 1; }
echo "$OUT"
