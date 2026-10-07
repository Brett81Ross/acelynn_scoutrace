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
        buckets.keys.forEach { permissionMatrix.put(it,JSONArray()) }
        val packages=installedPackages()
        val accessibility=enabledAccessibilityPackages()
        val admins=activeAdminPackages()
        val network=networkState()
        var sideloaded=0

        for(pkg in packages) {
            val app=pkg.applicationInfo ?: continue
            if(app.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            val installer=installerOf(pkg.packageName)
            val requested=pkg.requestedPermissions?.toSet().orEmpty()
            val enabledA11y=pkg.packageName in accessibility
            val admin=pkg.packageName in admins
            val overlay=pm.checkPermission("android.permission.SYSTEM_ALERT_WINDOW",pkg.packageName)==PackageManager.PERMISSION_GRANTED
            val canInstall="android.permission.REQUEST_INSTALL_PACKAGES" in requested
            val debuggable=app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val trusted=installer=="com.android.vending" || installer?.contains("google",true)==true || installer?.contains("samsung",true)==true
            val side=installer.isNullOrBlank() || !trusted
            if(side) sideloaded++

            buckets.forEach { (label,permission) ->
                if(permission in requested) permissionMatrix.getJSONArray(label).put(JSONObject().put("name",appLabel(pkg)).put("packageName",pkg.packageName))
            }

            var score=0
            val reasons=mutableListOf<String>()
            if(side){score+=2;reasons+="unrecognized install source"}
            if(enabledA11y){score+=4;reasons+="enabled Accessibility service"}
            if(admin){score+=4;reasons+="active Device Administrator"}
            if(overlay){score+=2;reasons+="requests overlay capability"}
            if(canInstall){score+=2;reasons+="can request package installs"}
            if(debuggable){score+=1;reasons+="debuggable build"}
            if(enabledA11y && (overlay || canInstall || side)) score+=3
            val level=when{score>=7->"HIGH CAUTION";score>=4->"ELEVATED";score>=2->"REVIEW";else->"CLEAR"}
            appRisks.put(JSONObject().put("name",appLabel(pkg)).put("packageName",pkg.packageName).put("level",level).put("score",score).put("installer",installer?:"unknown").put("reasons",JSONArray(reasons)))
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
            .put("summary","Inspected ${packages.size} packages visible to ScoutTrace and Android-exposed security signals. Findings are indicators for review, not proof of malware.")
            .put("visibility",VisibilityStatus(context).describe(packages.size))
            .put("platform","Android ${Build.VERSION.RELEASE}")
            .put("securityPatch",patch.ifBlank{"unknown"})
            .put("network",network)
            .put("deviceHealth",health)
            .put("counts",JSONObject().put("appsScanned",packages.size).put("findings",findings.size).put("sideloaded",sideloaded))
            .put("findings",JSONArray().apply{findings.sortedBy{rank(it.level)}.forEach{put(JSONObject().put("level",it.level).put("title",it.title).put("detail",it.detail).put("packageName",it.packageName))}})
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
    private fun enabledAccessibilityPackages():Set<String>=try{context.getSystemService(AccessibilityManager::class.java).getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).mapNotNull{it.resolveInfo?.serviceInfo?.packageName}.toSet()}catch(_:Exception){emptySet()}
    private fun activeAdminPackages():Set<String>=try{context.getSystemService(DevicePolicyManager::class.java).activeAdmins?.map{it.packageName}?.toSet().orEmpty()}catch(_:Exception){emptySet()}
    private fun appLabel(pkg:PackageInfo):String=try{pm.getApplicationLabel(pkg.applicationInfo!!).toString()}catch(_:Exception){pkg.packageName}
    private fun rank(level:String)=when(level){"HIGH CAUTION"->0;"ELEVATED"->1;"REVIEW"->2;else->3}
}
