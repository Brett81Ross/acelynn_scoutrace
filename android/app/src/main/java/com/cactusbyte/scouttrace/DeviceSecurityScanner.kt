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
    private data class Finding(val level:String,val title:String,val detail:String,val packageName:String?=null)

    fun scan(): JSONObject {
        val findings=mutableListOf<Finding>()
        val appRisks=JSONArray()
        val permissionMatrix=JSONObject()
        val buckets=linkedMapOf(
            "camera" to "android.permission.CAMERA",
            "microphone" to "android.permission.RECORD_AUDIO",
            "location" to "android.permission.ACCESS_FINE_LOCATION",
            "contacts" to "android.permission.READ_CONTACTS",
            "sms" to "android.permission.READ_SMS",
            "phone" to "android.permission.READ_PHONE_STATE"
        )
        (buckets.keys + listOf("accessibilityEnabled", "deviceAdminActive", "overlayDeclared", "installPackagesDeclared"))
            .forEach { permissionMatrix.put(it, JSONArray()) }
        val packages=installedPackages()
        val accessibility=enabledAccessibilityPackages()
        val admins=activeAdminPackages()
        val network=networkState()
        var otherInstallers=0
        var unknownInstallers=0

        for(pkg in packages) {
            val app=pkg.applicationInfo ?: continue
            if(app.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            val installer=installerOf(pkg.packageName)
            val requested=pkg.requestedPermissions?.toSet().orEmpty()
            val enabledA11y=pkg.packageName in accessibility
            val admin=pkg.packageName in admins
            // This checks the APK declaration, not active AppOps overlay access.
            val overlay="android.permission.SYSTEM_ALERT_WINDOW" in requested
            val canInstall="android.permission.REQUEST_INSTALL_PACKAGES" in requested
            val debuggable=app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val source=InstallSourceTrust.classify(installer)
            val side=source == InstallSourceTrust.Source.OTHER_INSTALLER
            if(side) otherInstallers++
            if(source == InstallSourceTrust.Source.UNKNOWN) unknownInstallers++

            buckets.forEach { (label,permission) ->
                if(permission in requested) permissionMatrix.getJSONArray(label).put(JSONObject()
                    .put("name",appLabel(pkg)).put("packageName",pkg.packageName)
                    .put("requested", true)
                    .put("granted", pm.checkPermission(permission, pkg.packageName) == PackageManager.PERMISSION_GRANTED))
            }

            fun special(bucket: String, state: String, verified: Boolean?) {
                val item = JSONObject().put("name", appLabel(pkg)).put("packageName", pkg.packageName).put("state", state)
                item.put("granted", verified ?: JSONObject.NULL)
                permissionMatrix.getJSONArray(bucket).put(item)
            }
            if(enabledA11y) special("accessibilityEnabled", "ENABLED", true)
            if(admin) special("deviceAdminActive", "ACTIVE", true)
            if(overlay) special("overlayDeclared", "DECLARED_ACCESS_UNVERIFIED", null)
            if(canInstall) special("installPackagesDeclared", "DECLARED_ACCESS_UNVERIFIED", null)

            var score=0
            val reasons=mutableListOf<String>()
            if(side){score+=2;reasons+="installer differs from recognized store identifiers; not proof of sideloading"}
            if(enabledA11y){score+=4;reasons+="enabled Accessibility service"}
            if(admin){score+=4;reasons+="active Device Administrator"}
            if(overlay){score+=2;reasons+="declares overlay permission; active access not verified"}
            if(canInstall){score+=2;reasons+="declares package-install request permission; active ability not verified"}
            if(debuggable){score+=1;reasons+="debuggable build"}
            if(enabledA11y && (overlay || canInstall || side)) score+=3
            val evidence=EvidenceModel.assess(side,enabledA11y,admin,overlay,canInstall,debuggable)
            val correlated=evidence.optBoolean("correlated")
            val indicatorCount=evidence.optInt("indicatorCount")
            val level=when{
                correlated && score>=7->"HIGH CAUTION"
                indicatorCount>=2 && score>=4->"ELEVATED"
                score>=1->"REVIEW"
                else->"CLEAR"
            }
            appRisks.put(JSONObject().put("name",appLabel(pkg)).put("packageName",pkg.packageName).put("level",level).put("score",score).put("installer",installer?:"unknown").put("installSourceStatus",source.name).put("versionCode",if(Build.VERSION.SDK_INT>=28)pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong()).put("requestedPermissions",JSONArray(requested.sorted()))
                .put("grantedRuntimePermissions", JSONArray(buckets.values.filter {
                    it in requested && pm.checkPermission(it, pkg.packageName) == PackageManager.PERMISSION_GRANTED
                }.sorted()))
                .put("overlayDeclared", overlay)
                .put("accessibilityEnabled",enabledA11y).put("deviceAdminActive",admin).put("reasons",JSONArray(reasons)).put("evidence",evidence))
            if(level!="CLEAR") findings+=Finding(level,appLabel(pkg),reasons.joinToString(" • "),pkg.packageName)
        }

        if(network.optBoolean("vpnActive")) findings+=Finding("REVIEW","VPN is active","Network traffic is routed through a VPN. Verify that you recognize the provider.")
        if(network.optBoolean("captivePortal")) findings+=Finding("REVIEW","Captive portal detected","The current network requires a sign-in or interception page. Avoid sensitive activity until the network is validated.")
        val patch=Build.VERSION.SECURITY_PATCH
        if(patch.isBlank()) findings+=Finding("REVIEW","Security patch unavailable","Android did not report a security patch level.")
        val health=deviceHealth()
        if(health.optBoolean("developerOptionsEnabled")) findings+=Finding("REVIEW","Developer options enabled","Developer options are enabled. This may be intentional; review the setting if you did not enable it.")

        val highest=when{findings.any{it.level=="HIGH CAUTION"}->"HIGH CAUTION";findings.any{it.level=="ELEVATED"}->"ELEVATED";findings.any{it.level=="REVIEW"}->"REVIEW";else->"CLEAR"}
        return JSONObject()
            .put("level",highest)
            .put("summary","Analyzed ${appRisks.length()} non-system apps from ${packages.size} Android-visible packages. Findings are indicators for review, not proof of malware.")
            .put("visibility",VisibilityStatus(context).describe(packages.size))
            .put("coverage",ScanCoverage.packageCoverage(packages.size))
            .put("reputation",ReputationStatus.current())
            .put("platform","Android ${Build.VERSION.RELEASE}")
            .put("securityPatch",patch.ifBlank{"unknown"})
            .put("network",network)
            .put("deviceHealth",health)
            .put("deviceProfile",DeviceProfile.current())
            .put("counts",JSONObject().put("appsScanned",appRisks.length()).put("visiblePackages",packages.size)
                .put("findings",findings.size).put("sideloaded",otherInstallers)
                .put("otherInstallers",otherInstallers).put("unknownInstallers",unknownInstallers))
            .put("findings",JSONArray().apply{findings.sortedBy{rank(it.level)}.forEach{put(JSONObject().put("level",it.level).put("title",it.title).put("detail",it.detail).put("packageName",it.packageName).put("remediation",RemediationGuide.forFinding(it.level,it.packageName)))}})
            .put("appRisks",appRisks)
            .put("permissionMatrix",permissionMatrix)
    }

    private fun networkState():JSONObject=try{
        val cm=context.getSystemService(ConnectivityManager::class.java)
        val network=cm.activeNetwork
        val caps=network?.let{cm.getNetworkCapabilities(it)}
        JSONObject().put("connected",network!=null)
            .put("vpnActive",caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true)
            .put("wifi",caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true)
            .put("cellular",caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)==true)
            .put("validated",caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true)
            .put("captivePortal",caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)==true)
    } catch(_:Exception){JSONObject().put("unknown",true)}

    private fun deviceHealth():JSONObject=try{
        JSONObject()
            .put("developerOptionsEnabled",Settings.Global.getInt(context.contentResolver,Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,0)==1)
            .put("securityPatch",Build.VERSION.SECURITY_PATCH.ifBlank{"unknown"})
            .put("sdk",Build.VERSION.SDK_INT)
    } catch(_:Exception){JSONObject().put("unknown",true)}

    private fun installedPackages():List<PackageInfo> = if(Build.VERSION.SDK_INT>=33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())) else @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    private fun installerOf(pkg:String):String?=try{if(Build.VERSION.SDK_INT>=30)pm.getInstallSourceInfo(pkg).installingPackageName else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)}catch(_:Exception){null}
    private fun enabledAccessibilityPackages():Set<String> = try{context.getSystemService(AccessibilityManager::class.java).getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).mapNotNull{it.resolveInfo?.serviceInfo?.packageName}.toSet()}catch(_:Exception){emptySet()}
    private fun activeAdminPackages():Set<String> = try{context.getSystemService(DevicePolicyManager::class.java).activeAdmins?.map{it.packageName}?.toSet().orEmpty()}catch(_:Exception){emptySet()}
    private fun appLabel(pkg:PackageInfo):String=try{pm.getApplicationLabel(pkg.applicationInfo!!).toString()}catch(_:Exception){pkg.packageName}
    private fun rank(level:String)=when(level){"HIGH CAUTION"->0;"ELEVATED"->1;"REVIEW"->2;else->3}
}
