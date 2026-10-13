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
