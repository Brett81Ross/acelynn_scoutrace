#!/usr/bin/env python3
"""Source-level regression guard for release-critical ScoutTrace Android boundaries.

This is defense in depth, not a substitute for instrumentation or real-device QA.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/cactusbyte/scouttrace"
MAIN = (SOURCE / "MainActivity.kt").read_text(encoding="utf-8")
BRIDGE = (SOURCE / "ScoutTraceBridge.kt").read_text(encoding="utf-8")
INSPECTOR = (SOURCE / "ApkInspector.kt").read_text(encoding="utf-8")
STORE = (SOURCE / "SecureInvestigationStore.kt").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

checks = {
    "No legacy native JavaScript interface": "addJavascriptInterface(" not in MAIN,
    "Native bridge main-frame boundary": "if (!isMainFrame" in MAIN,
    "Origin-scoped WebKit message listener": "WebViewCompat.addWebMessageListener" in MAIN
        and 'setOf("https://acelynn-scoutrace.vercel.app")' in MAIN,
    "Native confirmation dialog": "fun confirmDestructiveAction(" in MAIN
        and 'setPositiveButton("Delete permanently")' in MAIN,
    "Trusted WebView HTTPS navigation": 'uri.scheme == "https" && uri.host == trustedHost' in MAIN,
    "External app navigation restricted to safe schemes": 'uri.scheme in setOf("http", "https", "mailto", "tel")' in MAIN,
    "Camera origin explicit port check": "req.origin.port == 443" in MAIN,
    "Backups disabled without restore policy": 'android:allowBackup="false"' in MANIFEST,
    "Bounded APK byte copy": "ApkStreamCopy.copy(input, output)" in INSPECTOR,
}


# Observation fingerprints are sensitive local investigation records. A successful
# checkpoint must depend on a checked, durable encrypted write.
protected_keys = re.search(r"private val keys\s*=\s*setOf\((.*?)\)", STORE, re.DOTALL)
checks["Observation fingerprint V5 uses encrypted storage"] = (
    protected_keys is not None
    and '"observationFingerprintV5"' in protected_keys.group(1)
    and "AES/GCM/NoPadding" in STORE
)
checks["Encrypted observation writes verify persistence"] = (
    'check(prefs.edit().putString(name, "gcm1:"' in STORE
    and 'secure.putString("observationFingerprintV5", fingerprint)' in BRIDGE
    and BRIDGE.index('secure.putString("observationFingerprintV5", fingerprint)')
        < BRIDGE.index('result.put("observationCheckpoint"')
)

for method, destructive_op in (
    ("clearSecurityHistory", 'secure.remove("timeline", "baseline"'),
    ("clearAppDispositions", "AppDispositionStore(context).clear()"),
):
    match = re.search(
        rf"fun {method}\(\): Boolean \{{(.*?)\n    \}}",
        BRIDGE,
        re.DOTALL,
    )
    checks[f"{method} requires native confirmation before deletion"] = (
        match is not None
        and "confirmDestructiveAction(" in match.group(1)
        and destructive_op in match.group(1)
        and match.group(1).index("confirmDestructiveAction(")
        < match.group(1).index(destructive_op)
    )

failed = [title for title, passes in checks.items() if not passes]
for title, passes in checks.items():
    print(("PASS: " if passes else "FAIL: ") + title)
if failed:
    print(f"{len(failed)} required security gate(s) failed.", file=sys.stderr)
    sys.exit(1)
print("Source-level security contract checks passed (physical device QA still required).")
