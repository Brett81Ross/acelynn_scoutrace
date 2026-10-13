#!/usr/bin/env python3
"""Fail CI if the embedded ScoutTrace WebView investigation script is malformed."""
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/cactusbyte/scouttrace/MainActivity.kt"

def main() -> int:
    source = MAIN.read_text(encoding="utf-8")
    match = re.search(r'val js\s*=\s*"""(.*?)"""\.trimIndent\(\)', source, re.DOTALL)
    if match is None:
        print("ERROR: ScoutTrace injected JavaScript block not found.", file=sys.stderr)
        return 1
    if "addJavascriptInterface(" in source:
        print("ERROR: Legacy unrestricted WebView bridge detected.", file=sys.stderr)
        return 1
    if "WebViewCompat.addWebMessageListener" not in source:
        print("ERROR: Origin-restricted WebView messaging is missing.", file=sys.stderr)
        return 1
    node = shutil.which("node")
    if node is None:
        print("ERROR: Node.js is required to validate the embedded JavaScript.", file=sys.stderr)
        return 1
    with tempfile.TemporaryDirectory(prefix="scouttrace-js-") as tmp:
        js = Path(tmp) / "native-investigation.js"
        js.write_text(match.group(1), encoding="utf-8")
        result = subprocess.run([node, "--check", str(js)], check=False)
        if result.returncode != 0:
            print("ERROR: Embedded native investigation JavaScript failed syntax validation.", file=sys.stderr)
            return result.returncode
    print("PASS: Embedded ScoutTrace JavaScript parses and the restricted bridge is present.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
