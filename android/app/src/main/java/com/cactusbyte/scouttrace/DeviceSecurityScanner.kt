package com.cactusbyte.scouttrace

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import org.json.JSONArray
import org.json.JSONObject

class DeviceSecurityScanner(private val context: Context) {
    private val pm = context.packageManager
    private data class Finding(val level: String, val title: String, val detail: String, val packageName: String? = null)

    fun scan(): JSONObject {
        val findings = mutableListOf<Finding>()
        val appRisks = JSONArray()
        val permissionMatrix = JSONObject()
        val permissionBuckets = linkedMapOf(
            "camera" to "android.permission.CAMERA",
            "microphone" to "android.permission.RECORD_AUDIO",
            "location" to "android.permission.ACCESS_FINE_LOCATION",
            "contacts" to "android.permission.READ_CONTACTS",
            "sms" to "android.permission.READ_SMS",
            "phone" to "android.permission.READ_PHONE_STATE"
        )
        permissionBuckets.keys.forEach { permissionMatrix.put(it, JSONArray()) }

        var sideloaded = 0
        val packages = installedPackages()
        val enabledAccessibility = enabledAccessibilityPackages()
        val admins = activeAdminPackages()
        val vpnActive = isVpnActive()

        for (pkg in packages) {
            val app = pkg.applicationInfo ?: continue
            if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            val installer = installerOf(pkg.packageName)
            val requested = pkg.requestedPermissions?.toSet().orEmpty()
            val enabledA11y = pkg.packageName in enabledAccessibility
            val admin = pkg.packageName in admins
            val overlayGranted = Settings.canDrawOverlays(context) && pm.checkPermission("android.permission.SYSTEM_ALERT_WINDOW", pkg.packageName) == PackageManager.PERMISSION_GRANTED
            val canInstall = "android.permission.REQUEST_INSTALL_PACKAGES" in requested
            val debuggable = app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val trustedInstaller = installer == "com.android.vending" || installer?.contains("google", true) == true || installer?.contains("samsung", true) == true
            val isSideloaded = installer.isNullOrBlank() || !trustedInstaller
            if (isSideloaded) sideloaded++

            permissionBuckets.forEach { (label, permission) ->
                if (permission in requested) permissionMatrix.getJSONArray(label).put(JSONObject().put("name", appLabel(pkg)).put("packageName", pkg.packageName))
            }

            var score = 0
            val reasons = mutableListOf<String>()
            if (isSideloaded) { score += 2; reasons += "unrecognized install source (${installer ?: "unknown"})" }
            if (enabledA11y) { score += 4; reasons += "enabled Accessibility service" }
            if (admin) { score += 4; reasons += "active Device Administrator" }
            if (overlayGranted) { score += 2; reasons += "overlay capability" }
            if (canInstall) { score += 2; reasons += "can request package installs" }
            if (debuggable) { score += 1; reasons += "debuggable build" }
            if (enabledA11y && (overlayGranted || canInstall || isSideloaded)) score += 3

            val level = when { score >= 7 -> "HIGH CAUTION"; score >= 4 -> "ELEVATED"; score >= 2 -> "REVIEW"; else -> "CLEAR" }
            appRisks.put(JSONObject().put("name", appLabel(pkg)).put("packageName", pkg.packageName).put("level", level).put("score", score).put("installer", installer ?: "unknown").put("reasons", JSONArray(reasons)))
            if (level != "CLEAR") findings += Finding(level, appLabel(pkg), reasons.joinToString(" • "), pkg.packageName)
        }

        if (vpnActive) findings += Finding("REVIEW", "VPN is active", "Network traffic is being routed through a VPN. Verify that you recognize the provider.")
        if (Build.VERSION.SECURITY_PATCH.isNullOrBlank()) findings += Finding("REVIEW", "Security patch level unavailable", "Android did not report a security patch level.")

        val highest = when {
            findings.any { it.level == "HIGH CAUTION" } -> "HIGH CAUTION"
            findings.any { it.level == "ELEVATED" } -> "ELEVATED"
            findings.any { it.level == "REVIEW" } -> "REVIEW"
            else -> "CLEAR"
        }
        return JSONObject()
            .put("level", highest)
            .put("summary", "Inspected ${packages.size} installed packages and Android-exposed security signals. Findings are indicators for review, not proof of malware.")
            .put("platform", "Android ${Build.VERSION.RELEASE}")
            .put("securityPatch", Build.VERSION.SECURITY_PATCH ?: "unknown")
            .put("vpnActive", vpnActive)
            .put("counts", JSONObject().put("appsScanned", packages.size).put("findings", findings.size).put("sideloaded", sideloaded))
            .put("findings", JSONArray().apply { findings.sortedBy { rank(it.level) }.forEach { put(JSONObject().put("level", it.level).put("title", it.title).put("detail", it.detail).put("packageName", it.packageName)) } })
            .put("appRisks", appRisks)
            .put("permissionMatrix", permissionMatrix)
    }

    private fun installedPackages(): List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())) else @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    private fun installerOf(pkg: String): String? = try { if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg) } catch (_: Exception) { null }
    private fun enabledAccessibilityPackages(): Set<String> = try { context.getSystemService(AccessibilityManager::class.java).getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).mapNotNull { it.resolveInfo?.serviceInfo?.packageName }.toSet() } catch (_: Exception) { emptySet() }
    private fun activeAdminPackages(): Set<String> = try { context.getSystemService(DevicePolicyManager::class.java).activeAdmins?.map { it.packageName }?.toSet().orEmpty() } catch (_: Exception) { emptySet() }
    private fun isVpnActive(): Boolean = try { val cm = context.getSystemService(ConnectivityManager::class.java); val n = cm.activeNetwork ?: return false; cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true } catch (_: Exception) { false }
    private fun appLabel(pkg: PackageInfo): String = try { pm.getApplicationLabel(pkg.applicationInfo!!).toString() } catch (_: Exception) { pkg.packageName }
    private fun rank(level: String) = when (level) { "HIGH CAUTION" -> 0; "ELEVATED" -> 1; "REVIEW" -> 2; else -> 3 }
}
