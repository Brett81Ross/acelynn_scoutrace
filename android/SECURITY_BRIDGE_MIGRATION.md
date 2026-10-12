# Native bridge migration gate (P0)

Status: **NOT COMPLETE — RELEASE BLOCKED**. The Android wrapper currently registers `addJavascriptInterface(ScoutTraceBridge(...), "ScoutTraceNative")` in MainActivity. Even with top-level host navigation checks, Android's legacy JS interface is visible to embedded frames. Do not merge or ship this build until it is removed.

## Completed immediate hardening
- Removed exposed `inspectApkUri(uri)` JavaScript entry point; APK inspection is restricted to the Android user-driven document picker.
- Corrected timeline reads to use the Keystore-backed encrypted store, matching writes.

## Required secure migration
1. Add AndroidX WebKit dependency and feature-gated `WebViewCompat.addWebMessageListener` with `allowedOriginRules = setOf("https://acelynn-scoutrace.vercel.app")`.
2. Reject every message unless `isMainFrame` and `sourceOrigin.scheme == "https"`, host exactly `acelynn-scoutrace.vercel.app`, and default HTTPS port.
3. Restrict message schema to `{id, method, args}`, cap payload sizes, and dispatch a hardcoded allowlist; **never** expose arbitrary URI inspection.
4. Replace all injected synchronous `window.ScoutTraceNative.method()` calls with an asynchronous Promise-based `postMessage`/reply protocol. Move scans off the UI thread and handle timeouts and errors.
5. Never install legacy `addJavascriptInterface` as a fallback. On unsupported WebView, fail closed and show an actionable error.
6. Keep native camera permissions origin-checked, block untrusted navigation, validate package names, and ensure share/export requires a user gesture.
7. Add instrumentation coverage for malicious iframes, alternate origins, malformed messages, denied methods, and scanner responsiveness.
8. Confirm full CI, on-device tests, and owner approval before merge or deployment.

No release claims should be made while the legacy interface remains registered.
