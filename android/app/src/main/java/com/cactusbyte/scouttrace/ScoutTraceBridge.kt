package com.cactusbyte.scouttrace

import android.content.Context
import android.webkit.JavascriptInterface

class ScoutTraceBridge(private val context: Context) {
    @JavascriptInterface fun isNativeSecurityAvailable(): Boolean = true
    @JavascriptInterface fun runSecurityScan(): String = DeviceSecurityScanner(context).scan().toString()
    @JavascriptInterface fun getPlatformInfo(): String = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
}
