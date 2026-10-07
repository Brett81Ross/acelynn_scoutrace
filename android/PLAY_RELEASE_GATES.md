# ScoutTrace Android — Google Play Release Gates

Status: locked for v2.1 validation. This document is a release constraint, not a claim that Play approval is guaranteed.

## Package visibility
- The Play release must not depend on unrestricted installed-app inventory as its only investigation path.
- Broad package visibility is a release blocker until it is either removed from the Play variant or confirmed appropriate for ScoutTrace's declared core functionality and current Google Play policy.
- ScoutTrace must remain useful with limited package visibility.
- User-selected APK inspection is an explicit deep-inspection path.
- Any partial inventory must be labeled as the apps Android made visible to ScoutTrace, not "all apps on this phone."

## Product claims
- Findings are observable indicators for review, not proof of malware or spyware.
- No fake infection percentage, EMF claim, hidden-camera certainty, or iOS filesystem-antivirus claim.
- Confidence and evidence must remain distinguishable from severity.

## Privacy and permissions
- Keep analysis on-device by default.
- Do not embed reputation-service secrets in the APK or WebView.
- Exports and APK selection must be user initiated.
- Request only permissions required by an active feature.
- Data Safety and privacy disclosures must match actual release behavior.

## Architecture
- Treat the current WebView/native bridge as transitional.
- Native security components should move toward collectors -> correlator -> explainer -> remediator.
- Baselines must be deterministic; encrypted local storage is a v2.1 release target.
- Deep scans must degrade honestly when Android withholds visibility.

## Release gates
Before a Play candidate:
1. Compile-clean signed release AAB.
2. Physical-device QA on representative Android/OEM versions where available.
3. Package-visibility audit.
4. Permission and manifest audit.
5. Privacy/Data Safety audit.
6. Claim-language audit.
7. Release-signing and version-code verification.
8. Closed-test candidate approval before production.
