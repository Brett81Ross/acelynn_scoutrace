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
15. Encrypted deletions: simulate a failed write and verify Clear Local Security Data does not report success; simulate malformed encrypted Trust/Watch/Ignore JSON and verify no silent classification reset or overwrite. Verify the scan-failure screen offers separately confirmed history and classification erasure controls without erasing anything automatically.
16. Permission observation: confirm special-access rows show enabled Accessibility and active Device Administrator only when Android verifies them, while declared overlay and install-request capabilities are explicitly unverified. An APK declaration alone must never produce the strongest correlation finding.
17. Installer origins: unknown or null installer information must be categorized UNKNOWN, not counted as evidence of sideloading. An observed alternative installer must be described as an alternate identifier, not automatically malicious.
18. Security patch: verify no patch-date claim is made when the string is missing, malformed or future-dated; a valid patch date older than 180 days only recommends checking for updates.
19. Diff and timeline: changes to granted camera/microphone/location/contacts/SMS/phone permissions must show specifically which tracked grants were added/revoked. An older baseline lacking those fields must not fabricate changes. Scan-time timeline events must never claim to know when an app was installed or permission changed.
20. Web UI late mount: throttle the page so the .grid is created several seconds after onPageFinished; the native UI should still mount once. While cold launching from an Android text share intent, confirm its text survives until Scam Second Opinion attaches its event listener; no automatic web navigation or submission.
21. Injected JS gate: verify CI parses the complete embedded JavaScript with Node and rejects a reintroduced unrestricted addJavascriptInterface registration.
22. Native destructive-action confirmation: attempt history/classification deletion normally, cancel, press Back, background ScoutTrace before invoking, rotate during confirmation, and wait past 90 seconds. No action may delete data without tapping the Android-native "Delete permanently" dialog; cancellation must preserve the encrypted records. Verify a webpage script cannot skip that native dialog.
23. Document-stream safety: test empty and malformed selections, providers that return intermittent zero-byte reads, providers that never advance, files over 256 MB, and repeat inspections. Confirm byte-identical SHA-256 hashes, private cache cleanup, serialized background execution, and generic error messages that do not leak provider paths.
24. Expand each Permission Matrix category: show actual Android-visible package names, requested versus granted status, unknown AppOps state (overlay/install capabilities), 80-app list truncation, and functional App Settings buttons. Verify TalkBack focus and Fold cover/unfolded layouts.
25. Security Diff: filter displayed changes to All, App inventory, Access changes, and Risk changes. Check no data is fabricated, each filter updates the visible count, hidden elements are removed from the display, and native permissions remain the source of evidence.
26. Android Activity lifecycle: settings, uninstall, share-sheet, and picker actions must launch on the UI thread. Verify rapid taps, backgrounding during dialogs, unavailable handlers, and activity recreation. Invalid external file/content/intent/javascript links must not launch other apps; allowlist HTTPS, HTTP, mailto and tel only.
27. CI contract: confirm the injected JavaScript syntax test AND native security-boundary contract run before unit tests and policy gates; deliberately break each guard in a private test branch to verify it fails closed (do not weaken the production controls).

28. Assessment v2 truthfulness: manifest-only overlay/install-request declarations, alternative installer, and a debuggable flag must not produce ELEVATED or HIGH CAUTION; one active Accessibility or Device Administrator plus a distinct supporting signal may be ELEVATED; both active privileges together require review but do not establish malware.
29. Policy upgrade: install over an older baseline and confirm risk-level comparisons are suppressed when policy versions differ, while granted runtime permission changes, newly enabled privileges, and app-visibility differences remain visible. No false "new malware" timeline event may occur from a scoring-rule change alone.
30. Observation fingerprint: confirm that reordering package/permission arrays or reclassifying risk does not trigger a timeline change; changing an Android-reported permission, installer, app version, or active privilege does trigger one on a subsequent scan after the v2 checkpoint.
31. Integrity: corrupt encrypted classifications or timeline and verify failed scan does not advance the new fingerprint or erase the stored evidence.

32. Expanded runtime permissions: Android 8/12/13/14/15 checks for camera, mic, precise/approximate/background location, contacts, SMS, call logs, calendar, body sensors, activity recognition, notifications, Bluetooth, nearby Wi-Fi, media and selected photos. Compare against Android App Info. A grant does not prove access/use.
33. Android 14 partial media: grant Selected Photos only. Verify ScoutTrace displays the selection-limited scope and never reports unrestricted photo library access. Deny and re-grant permissions, including one-time permissions.
34. Special access declarations: verify AppOps-controlled overlay, usage access, settings modification, all-files access, install requests and exact alarms are declared-only, NOT verified active, even if app services request bind permissions. Test Accessibility, notification listener and VPN service declarations separately from uses-permission tags.
35. Privilege query failure: simulate inability to query Accessibility and Device Administrator lists; scanner must show UNKNOWN/unavailable, not false inactive or alleged active access; no unverified state may raise correlation severity.
36. Baseline upgrade: an older six-permission baseline must not cause false runtime grant changes when ScoutTrace moves to schema v2. A new baseline saved on the same schema must detect actual permission grants and special declarations. The v3 fingerprint must not emit an upgrade-only timeline entry.
37. Play policy: confirm GET_SERVICES is only a query flag (no new Android permission), Play flavor still lacks QUERY_ALL_PACKAGES and only labels visible apps.

38. Saved-baseline correlation: grant microphone/camera/location on a visible test app after saving a baseline; verify newly observed grants appear as baseline-relative review (not use evidence or spyware proof). Revocation alone must not become a new-access alert.
39. Special declarations: update a QA APK to declare a notification listener, overlay, or VPN binding without enabling it. Verify context-only correlation and explicit App Info review guidance, never an active-access claim.
40. Priority ordering: HIGH CAUTION still precedes ELEVATED and REVIEW; within equal severity newly observed privileged access sorts ahead of unverified declaration context. WATCH cannot move a lower-severity finding ahead of HIGH CAUTION; IGNORE cannot hide HIGH CAUTION.
41. Remediation: settings guidance and user approval only. When suspected abuse or stalking makes changes risky, review safety-planning language. No silent uninstall, revocation or background activity.
42. Missing baseline or Android-hidden package: show no invented correlated change. Observation timestamps are scanning timestamps, not estimated when an Android permission changed.

43. Dashboard: confirm top next step comes from native narrative priority, and Review priority finding opens its matching app's evidence, not an independently sorted first card.
44. Baseline changes: grant a camera or mic runtime permission to a CLEAR app after saving a baseline; verify the access-change panel shows the grant without calling it spyware and opens matching Android App Info.
45. For an enabled Accessibility service versus declaration-only VPN/notification service, confirm separate status labels and accurate remediation. No automatic uninstall or permission revocation.
46. IGNORE suppresses lower-severity correlated changes, HIGH CAUTION remains, and no-baseline scan displays honest setup guidance.
47. Check dark/light contrast, Galaxy Fold cover/unfolded screens, scrolling to the exact finding, detail expansion and TalkBack announcements.

## Release gate
CI compilation, unit tests, lint, APK/AAB and manifest checks are **necessary but not sufficient**. Device evidence (screenshots, Android version, test steps, results) and manual review of the ten-screen design contract are required. No device QA is claimed by this document.

## Known remaining work
- Native Android text/plain share-sheet ingestion is implemented on the validation branch. Verify fresh launch and warm singleTop delivery, no attachments, no unintended submission, local-only review, and 4,000-character limit on physical devices.
- Consent-based remote assistance is intentionally not offered; family assistance is guidance and user-initiated redacted sharing.
- iOS companion parity is outside this Android validation batch.
- Real-device rendering must confirm the approved ten-screen mockup; code-level UI additions alone do not establish pixel parity.
