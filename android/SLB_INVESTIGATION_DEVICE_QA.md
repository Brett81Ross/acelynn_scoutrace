# ScoutTrace — investigation UI and secure bridge device QA

Status: **validation branch only; no production deployment, merge, or Play publication**.

## Required devices and conditions
- Android 8 (API 26) or later with updated Android System WebView; Android 14/15 if available.
- Galaxy Fold narrow cover display and unfolded display; regular Android phone portrait and landscape.
- Offline and unreliable-network startup, navigation to an external site, back navigation, rotation, and activity recreation.
- Test with zero findings, one review finding, multiple findings, no saved baseline, saved baseline, and a baseline change.

## Mandatory regression checks
1. Run Complete ScoutTrace. Verify no synthetic malware verdict, confidence percentage, or invented scan coverage.
2. Findings: severity ordering, next-step priority, evidence details, and Android settings intents; WATCH changes order, IGNORE hides only non-HIGH CAUTION findings.
3. Security Diff: initial no-baseline state, saved baseline, version/permission change, package-visibility caveat, no false installation date. Change a runtime permission grant without changing the manifest and verify it appears as `grantedRuntimePermissions` in a baseline comparison and security-state timeline.
4. Timeline: observations sorted newest first, at most 50 retained, timestamp clearly described as *observation time*.
5. Scam Second Opinion: locally inspect an urgency message, a payment request, a message with a link, and benign text. Also share plain text from another Android app into ScoutTrace while cold and warm. Verify text appears in the editable checklist, no text upload or automatic link navigation, no declaration that a message is safe, and no unsupported attachment imports.
6. Family Assistance: consent guidance and redacted share sheet. Confirm recipient is selected by user and app identities/finding descriptions are not in shared text.
7. APK picker: cancel, non-APK, 256 MB+ file, malformed APK, normal APK, storage exhaustion, and content URI revoked during read; no remote upload.
8. WebView: invalid TLS certificate blocked; HTTP subresources blocked; JS popups disabled; external navigation cannot use privileged bridge.
9. Native bridge: forged iframe message, invalid origin, unknown method, invalid package name, invalid classification, oversized payload, excess args, duplicate ID, excessive queued requests, and shutdown race. No legacy JavascriptInterface.
10. Camera: permission denied, canceled, granted, backgrounded, and revoked mid-session. No camera access to external origins.
11. Keystore: baseline persistence across app restart, legacy plaintext migration, corrupted ciphertext (must surface error, never replace silently), simulated write failure (must not report saved), lock-state change, and local history clearing. Verify Trust/Watch/Ignore dispositions are encrypted, survive restart, migrate legacy plaintext with successful write-before-delete, and do not silently reappear after Reset.
12. Share reports: verify redaction in actual Android chooser preview, not merely the app UI.
13. Permission Matrix: verify declared versus currently granted permissions are reported separately and agree with Android App Info. Revoking camera, location, SMS or other runtime grants must not still appear as granted. Check different target SDKs, OEMs, and limited Play-flavor package visibility.
14. App trust evidence: ensure a fake installer identifier containing "google" or "samsung" is not treated as an official app store merely by substring; unknown or custom stores remain indicators for contextual review, not malware verdicts.
15. Encrypted deletions: simulate a failed write and verify Clear Local Security Data does not report success; simulate malformed encrypted Trust/Watch/Ignore JSON and verify no silent classification reset or overwrite.

## Release gate
CI compilation, unit tests, lint, APK/AAB and manifest checks are **necessary but not sufficient**. Device evidence (screenshots, Android version, test steps, results) and manual review of the ten-screen design contract are required. No device QA is claimed by this document.

## Known remaining work
- Native Android text/plain share-sheet ingestion is implemented on the validation branch. Verify fresh launch and warm singleTop delivery, no attachments, no unintended submission, local-only review, and 4,000-character limit on physical devices.
- Consent-based remote assistance is intentionally not offered; family assistance is guidance and user-initiated redacted sharing.
- iOS companion parity is outside this Android validation batch.
- Real-device rendering must confirm the approved ten-screen mockup; code-level UI additions alone do not establish pixel parity.
