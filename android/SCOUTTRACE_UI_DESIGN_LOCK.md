# Acelynn's ScoutTrace™ — Approved UI Design Lock

Status: APPROVED visual reference by product owner, 2026-10-11.
Reference: ten-screen ScoutTrace mockup approved in project conversation on 2026-10-11.
Scope: Android validation branch only. No merge, deployment, or publication authorized.

## Design contract
Implement the mockup as the primary visual specification, retaining coherent navigation and accessibility:
1. Welcome — dark navy mountain/silhouette background, shield emblem, clear mission, Get Started.
2. Home Dashboard — light canvas, clear status summary, compact metric cards, prominent Run Investigation, quick tools, recent activity.
3. Investigation Results — dark navy canvas, evidence-ranked findings, summary/details/recommendations, high-contrast priority treatments.
4. Finding Details — light canvas, specific observed evidence, why it matters, confidence explained without invented numerical certainty, Take Action.
5. Remediation Guide — light canvas, numbered actions, Android settings links and safe step-by-step guidance.
6. Security Diff — dark canvas, changes since trusted baseline, filterable categories, explicitly distinguish requested versus granted permissions and visibility changes.
7. Investigation Timeline — light canvas, chronological observed events; never infer precise grant times from periodic snapshots.
8. Scam Second Opinion — dark canvas, text/link/QR/screenshot entry, source-based findings, no invented threat-feed hits or domain age.
9. Family Assistance — light canvas, explicit revocable consent, no covert monitoring, no invented remote monitoring.
10. Shareable Report — dark canvas, redacted summary by default, detailed sharing only after explicit opt-in.

## Shared visual language
- Navy/charcoal investigation surfaces, crisp white utility surfaces, cyan/blue primary actions, restrained red/amber/green status.
- Rounded phone-native cards, readable typography, generous tap targets, consistent icons, shield mark.
- Bottom navigation: Home, Investigations, Timeline, Tools, More.
- Responsive layouts for Samsung Galaxy Z Fold cover/unfolded screens and regular Android devices.
- Consistent header, branded footer, version information, accessible contrast, large text, and screen reader labels.
- All buttons and tabs must perform genuine actions. No mock security scores, fabricated telemetry, fake alerts, or misleading 'all clear' claims.
- Treat mockup sample app names, timestamps, percentage scores, and findings as illustrative placeholders ONLY.
- Preserve existing working scanner, APK inspector, privacy and security features.

## Implementation and release gates
A. Build reusable screen shells, design tokens, navigation and reusable finding components.
B. Connect Home and Investigation Results to real native scan output.
C. Connect Finding Details and Remediation to verifiable evidence and safe Settings intents.
D. Connect Security Diff and Timeline to encrypted observed baseline data.
E. Build Scam Second Opinion with local-first, honest coverage and optional opt-in reputation checks.
F. Build Family and Reports only with consent and redaction controls.
G. Test responsive layouts, back navigation, empty/loading/error states, and Android accessibility.
H. Security gate: replace unrestricted JavaScript interface with origin-restricted message handling before release.
I. Require successful CI, device QA, and explicit approval before merging or deploying.

## Change control
This design lock is the approved rollback/reference point for visual implementation. Deviations affecting overall layout, color system, navigation, or screen composition require explicit product-owner approval. Minor accessibility/responsiveness corrections are permitted. Do not overwrite working production UI merely to match an illustration.
