# Acelynn's ScoutTrace v2.1 — Atomic Build List Status

Validation branch only. No production deployment is authorized by this document.

## Implemented on branch
- App risk analyzer with explainable reasons.
- Permission matrix foundation.
- Android network and device-health observations.
- Evidence/context/confidence model.
- Correlation requirement for HIGH CAUTION.
- Deterministic baseline fingerprint.
- Baseline app/risk change comparison.
- Human-readable baseline narrative model.
- Security timeline.
- Trusted / Watch classification foundation.
- App settings, accessibility settings, security settings, and uninstall remediation entry points.
- Evidence-based remediation guidance model, now attached to findings.
- Prioritized investigation narrative attached to scan results and headline wired into Complete ScoutTrace UI.
- Native Privacy Center model.
- Text security report export.
- Native Android report sharing, wired to the Complete ScoutTrace Export Report action.
- Origin-restricted WebKit native messaging replaces legacy JavaScript interfaces; strict main-frame and method allowlists apply.
- Android native share-sheet text import feeds locally reviewed Scam Second Opinion without automatically opening links.
- Runtime-sensitive Permission Matrix distinguishes manifest requests from granted state and tracks grants in baseline comparisons.
- Baseline Comparator now reports named runtime permission grants and revocations, skips comparison for fields missing from legacy baselines, and has direct JVM regression tests.
- Install source observations distinguish recognized store ID, alternative installer ID, and unavailable Android installer metadata; source identity is not proof of sideloading.
- Strongest evidence correlation now requires Android-observed Accessibility and active Device Administrator states instead of unverified manifest declarations.
- Security patch date advisories use date parsing and a 180-day review threshold, without claiming an old patch date indicates malware.
- Native investigation UI now waits for asynchronously rendered web content, and received Android share text is queued until local Scam Review can receive it.
- CI now syntactically validates the injected JavaScript and rejects restored unrestricted WebView JavaScript interfaces.
- Native Android confirmation is now mandatory before clearing encrypted history or app classifications; web-only prompts cannot authorize deletion.
- Android-origin and safe external URL checks were tightened, including trusted HTTPS port validation before delayed camera grants.
- APK file streaming uses a 256 MB cap, bounded zero-read handling, SHA-256 verification, and a serialized worker; document-provider errors are redacted.
- Permission Matrix sections expand into observed per-app evidence and App Settings actions; Security Diff supports All/App inventory/Access/Risk filters.
- Settings, uninstall confirmation, and share chooser launch on the Android UI thread with bounded acknowledgement.
- CI additionally asserts native deletion confirmation, trusted-origin restrictions, the APK copy boundary, and disabled unencrypted backups.
- Encrypted record corruption and failed persistence cannot silently reset or overwrite data; deletion failures are surfaced.
- Static APK Inspector foundation: SHA-256, package, version, requested permissions.
- Play/internal distribution flavors.
- Play manifest removes unrestricted package visibility and declares limited launcher visibility.
- Scan coverage/visibility disclosures.
- Prioritized investigation narrative model.
- Play unit-test and lint gates in CI configuration.
- Play merged-manifest policy gate rejects unrestricted package visibility.
- Evidence-model and baseline-narrative regression tests.
- OEM-aware device profile foundation for Samsung, Pixel, Motorola, and generic Android fallback.
- Explicit disabled-state model for external threat reputation so the UI cannot imply a lookup occurred.

## Detection and correlation atomic batch (validation only)
- Assessment policy v2 prevents manifest-only and install-origin-only signals from becoming ELEVATED; HIGH CAUTION requires two Android-reported active privileges.
- Decisions expose the policy rule plus counts of active privileges, manifest declarations, and contextual indicators. This is deterministic triage, never a malware verdict.
- Versioned baseline comparisons withhold risk-score changes across policy upgrades while continuing to display observable permission and app metadata changes.
- Diff records when Accessibility or Device Administrator access is newly observed relative to a saved baseline; timestamps are not inferred activation times.
- Versioned observation-only fingerprint excludes derived scores and classifications, preventing scanner policy changes from creating synthetic timeline events. The first v2 scan establishes an observation checkpoint.
- Encrypted dispositions and timelines are checked before advancing an observation checkpoint, preserving fail-closed data integrity.
- Includes deterministic policy, fingerprint and migration regression tests. Physical device verification remains open.

## Expanded permission and special-access detection (validation only)
- Sensitive runtime permission catalog expanded to 25 distinct categories with API-level applicability and explicit granted, denied or unavailable states. Observed grant does not establish actual use.
- Android 13/14 notifications, media files, and selected-photo access distinguish platform and partial-library scope.
- Special-access declaration catalog detects overlays, install requests, usage access, settings changes, all-files access, exact alarms and Accessibility/notification-listener/VPN service binding declarations.
- Only Accessibility enabled services and active Device Administrators can be marked Android-reported active; other apps' special AppOps access is not verified.
- Active privilege query failures remain UNKNOWN, not observed inactive.
- Permission evidence schema versioning protects saved baselines from false grant alerts when ScoutTrace gains new detection categories.
- Observation fingerprint v3 adds new permission/special-access evidence without fabricating a policy-upgrade timeline event; v2 checkpoint retained until explicit history deletion.
- Play flavor retains restricted package visibility. No new permissions are requested by ScoutTrace.
- Unit regression tests cover Android API gates, partial-media semantics, service declarations and baseline/fingerprint migration. Device QA remains required.

## Saved-baseline evidence correlation (validation only)
- New BaselineChangeCorrelation correlates Android-reported newly enabled access, sensitive runtime grants, and special-access declarations from an existing trusted baseline. No baseline means no inferred change.
- Priorities retain app severity first, then baseline change significance, then explicit WATCH preferences.
- Declared services and AppOps capabilities remain context-only; runtime grants do not imply permissions were used.
- Contextual remediation links users to Android settings and includes personal-safety considerations before removing potentially monitoring-related apps.
- Correlation is one event per visible app, has no fabricated activation time, and does not independently change the underlying malware assessment.
- Pure JVM regression tests cover correlation precedence, missing observations, declaration-only alerts, revocations, and non-destructive remediation.
- Physical-device verification remains open; no production release authorized.

## Partially implemented / needs completion
- Permission Matrix special-access categories.
- Trusted / Watch / Ignore states are stored; Ignore/Reset are exposed in UI. Ignored lower-priority findings are hidden from display and priority callout, while HIGH CAUTION remains visible. Watch findings are prioritized within the same severity tier, with HIGH CAUTION always ahead of lower severity; device QA remains open.
- APK Inspector native document picker.
- APK signing-certificate and SDK metadata.
- Protected threat-reputation backend.
- Keystore AES-GCM baseline/history storage and encrypted Trust/Watch/Ignore records have been implemented on the validation branch; device migration and error-path QA remain open.
- Complete ScoutTrace UI rendering is substantially wired for narrative headline, remediation next steps, baseline narrative, visible-package wording, and coverage disclosure; final device QA remains.
- Branded QR sharing.
- Background baseline checks.
- iOS posture/environment expansion.
- OEM-specific remediation fallbacks.

## Validation gates still open
- Play-flavor compilation, lint, unit tests and artifact build have succeeded on earlier validation commits; the final candidate head must independently pass CI again.
- Compile, unit test, lint, and release bundle must pass at the exact final release candidate commit.
- Physical-device QA remains required.
- Google Play manifest/package-visibility policy audit remains required.
- Privacy/Data Safety and claim-language audits remain required.
- Release signing and closed-test candidate approval remain required.

## Release boundary
Do not merge to main, deploy production, or upload to Google Play until the open implementation items that define the chosen release scope are resolved and the validation gates pass.
