#!/bin/sh
# Regenerate the SVGs from the .mmd sources. Needs @mermaid-js/mermaid-cli (mmdc) and a Chrome/Chromium.
# Set PUPPETEER_EXECUTABLE_PATH if mmdc cannot find a browser.
set -e
cd "$(dirname "$0")"
for f in *.mmd; do
  mmdc -q -c mermaid.json -p puppeteer.json -b transparent -i "$f" -o "${f%.mmd}.svg"
done
