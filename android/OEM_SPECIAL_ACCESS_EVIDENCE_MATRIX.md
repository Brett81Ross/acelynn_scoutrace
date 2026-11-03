# ScoutTrace™ — OEM special-access evidence matrix (validation only)

This is a test protocol, not a claim that any real device was tested. Keep sensitive app names and screenshots out of public issues. No production connections or elevated permissions are required.

| Signal | Android source | What ScoutTrace may say | What it must not say | Common OEM/profile limitation |
|---|---|---|---|---|
| Accessibility | AccessibilityManager enabled-service list | Android-reported enabled service | The service recorded data | Service enumeration may be restricted, absent or disabled by profile |
| Device Administrator | DevicePolicyManager active admin component list | Android-reported active administrator | App is malware or device owner | Null list can mean no active admins; managed profiles may differ |
| Notification listener | Secure Setting for enabled listener components | Listed as enabled in Android settings | Connected/received notifications | OEM listener connectivity, filter, work-profile or background restrictions |
| Default keyboard | Secure Setting default input method | Selected input method | Logged keystrokes | No readable setting or switched work-profile context |
| Overlay, usage stats, install requests, all-files | APK manifest permissions | Declared only | AppOps currently allowed/used | AppOps and OEM controls are not a complete third-party query surface |
| VPN service | APK service binding declaration | Service declared | VPN is connected to that app | Active VPN transport is device level; service binding not connection |
| Runtime permissions | PackageManager.checkPermission | Android reports permission granted/denied | Permission actually used/data accessed | One-time permission, media subset, foreground-only, app-op restrictions |
| Package inventory | Restricted PackageManager enumeration | Visible packages examined | Complete installed app list | Android 11+ package visibility, OEM and work profiles |

## Physical testing worksheet

For each test device, record: manufacturer/model, Android/API/skin version, personal/work profile, Play or internal flavor, selected app (use disposable QA app), declared services/permissions, actual settings state as shown in Android, ScoutTrace state, mismatch and reproduction steps. Include date and tester; keep any device evidence private.

### Required matrix rows
- Samsung Galaxy Fold cover and unfolded, Samsung One UI: listener setting enabled/disabled, selected keyboard switch, Accessibility enabled/disabled, Device Administrator absent/present and Settings unavailability
- Google Pixel: same controls and an Android 14+ partial-photo permission case
- Motorola or another OEM where available: same settings and failure-path checks
- Emulator/API 26, 29, 30, 31, 33, 34, 35: declared permission version gating, missing service metadata, malformed/oversized settings, selected photos, and null admin list
- Managed/work profile: manually verify scope, account for invisible apps and policy-controlled listeners; no universal inventory claims

### CI verification and release gate
JVM tests prove parsing, model classification, comparator behavior and no automatic remediation. They cannot verify how Samsung or Pixel firmware exposes live states. A passing APK/AAB build is insufficient for device-level accuracy. A release requires physical test receipts and approved privacy/policy review.

Reference documentation:
- https://developer.android.com/reference/android/app/admin/DevicePolicyManager
- https://developer.android.com/reference/android/view/accessibility/AccessibilityManager
- https://developer.android.com/reference/android/service/notification/NotificationListenerService
- https://developer.android.com/reference/android/app/AppOpsManager
- https://developer.android.com/training/package-visibility
