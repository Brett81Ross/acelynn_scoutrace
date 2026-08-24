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
import android.view.accessibility.AccessibilityManager
import org.json.JSONArray
import org.json.JSONObject

class DeviceSecurityScanner(private val context: Context) {
    private val pm = context.packageManager
    private data class Finding(val level: String, val title: String, val detail: String, val packageName: String? = null)

    fun scan(): JSONObject {
        val findings = mutableListOf<Finding>()
        var sideloaded = 0
        val packages = installedPackages()
        val enabledAccessibility = enabledAccessibilityPackages()
        val admins = activeAdminPackages()
        val vpnActive = isVpnActive()

        for (pkg in packages) {
            val app = pkg.applicationInfo ?: continue
            val system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (system) continue
            val installer = installerOf(pkg.packageName)
            val requested = pkg.requestedPermissions?.toSet().orEmpty()
            val enabledA11y = pkg.packageName in enabledAccessibility
            val admin = pkg.packageName in admins
            val overlayGranted = pm.checkPermission("android.permission.SYSTEM_ALERT_WINDOW", pkg.packageName) == PackageManager.PERMISSION_GRANTED
            val canInstall = "android.permission.REQUEST_INSTALL_PACKAGES" in requested
            val debuggable = app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val trustedInstaller = installer == "com.android.vending" || installer?.contains("google", true) == true || installer?.contains("samsung", true) == true
            val isSideloaded = installer.isNullOrBlank() || !trustedInstaller
            if (isSideloaded) sideloaded++

            var score = 0
            val reasons = mutableListOf<String>()
            if (isSideloaded) { score += 2; reasons += "not installed by a recognized app store (${installer ?: "unknown source"})" }
            if (enabledA11y) { score += 4; reasons += "enabled Accessibility service" }
            if (admin) { score += 4; reasons += "active Device Administrator" }
            if (overlayGranted) { score += 2; reasons += "overlay permission" }
            if (canInstall) { score += 2; reasons += "can request package installs" }
            if (debuggable) { score += 1; reasons += "debuggable build" }
            if (enabledA11y && (overlayGranted || canInstall || isSideloaded)) score += 3

            if (score >= 7) findings += Finding("HIGH CAUTION", appLabel(pkg), reasons.joinToString(" • "), pkg.packageName)
            else if (score >= 4) findings += Finding("ELEVATED", appLabel(pkg), reasons.joinToString(" • "), pkg.packageName)
            else if (score >= 2) findings += Finding("REVIEW", appLabel(pkg), reasons.joinToString(" • "), pkg.packageName)
        }

        if (vpnActive) findings += Finding("REVIEW", "VPN is active", "An active VPN changes network routing. This may be expected; verify that you recognize the VPN provider.")
        if (Build.VERSION.SECURITY_PATCH.isNullOrBlank()) findings += Finding("REVIEW", "Security patch level unavailable", "Android did not report a security patch level.")

        val highest = when {
            findings.any { it.level == "HIGH CAUTION" } -> "HIGH CAUTION"
            findings.any { it.level == "ELEVATED" } -> "ELEVATED"
            findings.any { it.level == "REVIEW" } -> "REVIEW"
            else -> "CLEAR"
        }
        val out = JSONObject()
        out.put("level", highest)
        out.put("summary", "Inspected ${packages.size} installed packages and Android-exposed security signals. Findings are indicators for review, not proof of malware.")
        out.put("platform", "Android ${Build.VERSION.RELEASE}")
        out.put("securityPatch", Build.VERSION.SECURITY_PATCH ?: "unknown")
        out.put("counts", JSONObject().put("appsScanned", packages.size).put("findings", findings.size).put("sideloaded", sideloaded))
        out.put("findings", JSONArray().apply { findings.sortedBy { rank(it.level) }.forEach { put(JSONObject().put("level",it.level).put("title",it.title).put("detail",it.detail).put("packageName",it.packageName)) } })
        return out
    }

    private fun installedPackages(): List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())) else @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    private fun installerOf(pkg: String): String? = try { if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg) } catch (_: Exception) { null }
    private fun enabledAccessibilityPackages(): Set<String> = try { val am=context.getSystemService(AccessibilityManager::class.java); am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).mapNotNull { it.resolveInfo?.serviceInfo?.packageName }.toSet() } catch (_: Exception) { emptySet() }
    private fun activeAdminPackages(): Set<String> = try { context.getSystemService(DevicePolicyManager::class.java).activeAdmins?.map { it.packageName }?.toSet().orEmpty() } catch (_: Exception) { emptySet() }
    private fun isVpnActive(): Boolean = try { val cm=context.getSystemService(ConnectivityManager::class.java); val n=cm.activeNetwork ?: return false; cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true } catch (_: Exception) { false }
    private fun appLabel(pkg: PackageInfo): String = try { pm.getApplicationLabel(pkg.applicationInfo!!).toString() } catch (_: Exception) { pkg.packageName }
    private fun rank(level:String)=when(level){"HIGH CAUTION"->0;"ELEVATED"->1;"REVIEW"->2;else->3}
}
