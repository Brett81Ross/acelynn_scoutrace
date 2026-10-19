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
        (AndroidPermissionCatalog.bucketNames + AndroidSpecialAccessCatalog.bucketNames +
            listOf("accessibilityEnabled", "deviceAdminActive"))
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
            val enabledA11y=accessibility?.contains(pkg.packageName) == true
            val admin=admins?.contains(pkg.packageName) == true
            // This checks the APK declaration, not active AppOps overlay access.
            val overlay="android.permission.SYSTEM_ALERT_WINDOW" in requested
            val canInstall="android.permission.REQUEST_INSTALL_PACKAGES" in requested
            val debuggable=app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val source=InstallSourceTrust.classify(installer)
            val side=source == InstallSourceTrust.Source.OTHER_INSTALLER
            if(side) otherInstallers++
            if(source == InstallSourceTrust.Source.UNKNOWN) unknownInstallers++

            val label = appLabel(pkg)
            val observedPermissions=AndroidPermissionCatalog.observe(requested, Build.VERSION.SDK_INT) { permission ->
                when(pm.checkPermission(permission, pkg.packageName)) {
                    PackageManager.PERMISSION_GRANTED -> true
                    PackageManager.PERMISSION_DENIED -> false
                    else -> null
                }
            }
            observedPermissions.forEach { observation ->
                permissionMatrix.getJSONArray(observation.spec.bucket)
                    .put(AndroidPermissionCatalog.matrixItem(label, pkg.packageName, observation))
            }
            // ServiceInfo.permission is a service binding, not an app's uses-permission.
            val serviceBindings=pkg.services?.mapNotNull { it.permission }?.toSet().orEmpty()
            val declaredSpecial=AndroidSpecialAccessCatalog.declared(
                requested, serviceBindings, Build.VERSION.SDK_INT)
            declaredSpecial.forEach { spec ->
                permissionMatrix.getJSONArray(spec.bucket)
                    .put(AndroidSpecialAccessCatalog.matrixItem(label, pkg.packageName, spec))
            }
            fun activeSpecial(bucket:String) {
                permissionMatrix.getJSONArray(bucket).put(JSONObject()
                    .put("name", label).put("packageName",pkg.packageName)
                    .put("state","OS_REPORTED_ACTIVE").put("granted",true))
            }
            if(enabledA11y) activeSpecial("accessibilityEnabled")
            if(admin) activeSpecial("deviceAdminActive")

            val decision = AppRiskPolicy.evaluate(AppRiskPolicy.Signals(
                otherInstaller = side,
                accessibilityEnabled = enabledA11y,
                deviceAdminActive = admin,
                overlayDeclared = overlay,
                installRequestsDeclared = canInstall,
                debuggable = debuggable
            ))
            val evidence = EvidenceModel.assess(side,enabledA11y,admin,overlay,canInstall,debuggable)
            val level = decision.level
            val reasons = decision.reasons
            appRisks.put(JSONObject().put("name",appLabel(pkg)).put("packageName",pkg.packageName).put("level",level).put("score",decision.score).put("assessmentRule",decision.rule)
                .put("activePrivilegeCount",decision.activePrivilegeCount)
                .put("declaredCapabilityCount",decision.declaredCapabilityCount)
                .put("contextIndicatorCount",decision.contextIndicatorCount).put("installer",installer?:"unknown").put("installSourceStatus",source.name).put("versionCode",if(Build.VERSION.SDK_INT>=28)pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong()).put("requestedPermissions",JSONArray(requested.sorted()))
                .put("grantedRuntimePermissions",AndroidPermissionCatalog.grantedNames(observedPermissions))
                .put("unknownRuntimePermissionGrants",AndroidPermissionCatalog.unavailableNames(observedPermissions))
                .put("declaredSpecialAccess",AndroidSpecialAccessCatalog.declaredNames(declaredSpecial))
                .put("specialAccessEvidence",AndroidSpecialAccessCatalog.evidence(declaredSpecial))
                .put("accessibilityState",if(accessibility==null) "UNKNOWN" else if(enabledA11y) "ENABLED" else "NOT_OBSERVED")
                .put("deviceAdminState",if(admins==null) "UNKNOWN" else if(admin) "ACTIVE" else "NOT_OBSERVED")
                .put("overlayDeclared", overlay)
                .apply {
                    // Failed privileged queries are unknown, never observed negatives.
                    if(accessibility!=null) put("accessibilityEnabled", enabledA11y)
                    if(admins!=null) put("deviceAdminActive", admin)
                }.put("reasons",JSONArray(reasons)).put("evidence",evidence))
            if(level!="CLEAR") findings+=Finding(level,appLabel(pkg),reasons.joinToString(" • "),pkg.packageName)
        }

        if(network.optBoolean("vpnActive")) findings+=Finding("REVIEW","VPN is active","Network traffic is routed through a VPN. Verify that you recognize the provider.")
        if(network.optBoolean("captivePortal")) findings+=Finding("REVIEW","Captive portal detected","The current network requires a sign-in or interception page. Avoid sensitive activity until the network is validated.")
        val patch=Build.VERSION.SECURITY_PATCH
        val patchReview=SecurityPatchReview.assess(patch)
        if(patchReview.reviewRecommended) {
            val title=if(patchReview.status==SecurityPatchReview.Status.OLDER_DATE)
                "Review Android security updates" else "Security patch date unavailable"
            findings+=Finding("REVIEW",title,patchReview.detail)
        }
        val health=deviceHealth()
        if(health.optBoolean("developerOptionsEnabled")) findings+=Finding("REVIEW","Developer options enabled","Developer options are enabled. This may be intentional; review the setting if you did not enable it.")

        val highest=when{findings.any{it.level=="HIGH CAUTION"}->"HIGH CAUTION";findings.any{it.level=="ELEVATED"}->"ELEVATED";findings.any{it.level=="REVIEW"}->"REVIEW";else->"CLEAR"}
        return JSONObject()
            .put("level",highest)
            .put("assessmentModelVersion", AppRiskPolicy.VERSION)
            .put("permissionEvidenceSchemaVersion", AndroidPermissionCatalog.SCHEMA_VERSION)
            .put("specialAccessCoverage",JSONObject()
                .put("accessibilityQuery",if(accessibility==null) "UNAVAILABLE" else "ANDROID_REPORTED")
                .put("deviceAdminQuery",if(admins==null) "UNAVAILABLE" else "ANDROID_REPORTED")
                .put("otherAppSpecialAppOps","NOT_VERIFIED")
                .put("note","Special-access and bound-service declarations do not prove active use. Android does not provide ScoutTrace with an authoritative cross-app AppOps inventory."))
            .put("summary","Analyzed ${appRisks.length()} non-system apps from ${packages.size} Android-visible packages. Findings are indicators for review, not proof of malware.")
            .put("visibility",VisibilityStatus(context).describe(packages.size))
            .put("coverage",ScanCoverage.packageCoverage(packages.size))
            .put("reputation",ReputationStatus.current())
            .put("platform","Android ${Build.VERSION.RELEASE}")
            .put("securityPatch",patch.ifBlank{"unknown"})
            .put("securityPatchReview",JSONObject()
                .put("status",patchReview.status.name)
                .put("ageDays",patchReview.ageDays ?: JSONObject.NULL)
                .put("detail",patchReview.detail))
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

    private fun installedPackages():List<PackageInfo> {
        val flags=PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        return if(Build.VERSION.SDK_INT>=33)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        else @Suppress("DEPRECATION") pm.getInstalledPackages(flags)
    }
    private fun installerOf(pkg:String):String?=try{if(Build.VERSION.SDK_INT>=30)pm.getInstallSourceInfo(pkg).installingPackageName else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)}catch(_:Exception){null}
    private fun enabledAccessibilityPackages():Set<String>? = try{
        context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapNotNull{it.resolveInfo?.serviceInfo?.packageName}.toSet()
    }catch(_:Exception){null}
    private fun activeAdminPackages():Set<String>? = try{
        context.getSystemService(DevicePolicyManager::class.java)
            .activeAdmins?.map{it.packageName}?.toSet().orEmpty()
    }catch(_:Exception){null}
    private fun appLabel(pkg:PackageInfo):String=try{pm.getApplicationLabel(pkg.applicationInfo!!).toString()}catch(_:Exception){pkg.packageName}
    private fun rank(level:String)=when(level){"HIGH CAUTION"->0;"ELEVATED"->1;"REVIEW"->2;else->3}
}
