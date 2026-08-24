# Acelynn's ScoutTrace™ Android Device Security Engine — v2.0.0

Native Android wrapper plus JavaScript bridge for the ScoutTrace PWA.

The native Phone Security Sweep inspects Android-exposed installed package metadata, installer source indicators, enabled Accessibility services, active device-admin packages, overlay/install-package permission indicators, debuggable builds, Android security patch metadata, and active VPN state.

Results are risk indicators for review, not a claim that an app is malware. `QUERY_ALL_PACKAGES` is included because device-security/antivirus functionality is a core feature; Play Store distribution requires the applicable package-visibility declaration and policy compliance.

Build validation is performed by the repository Android CI workflow before distributing APK/AAB artifacts.

Acelynn's ScoutTrace™ • Cactus🌵Byte Studios™ • All Rights Reserved