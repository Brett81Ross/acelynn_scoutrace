package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject

/**
 * Cross-package special AppOps and roles are not generally verifiable without
 * privileged access. Service binding declarations are manifest evidence only.
 */
object AndroidSpecialAccessCatalog {
    data class Spec(val bucket: String, val permission: String, val source: String,
                    val minSdk: Int = 26)

    val specs = listOf(
        Spec("overlayDeclared", "android.permission.SYSTEM_ALERT_WINDOW", "USES_PERMISSION"),
        Spec("installPackagesDeclared", "android.permission.REQUEST_INSTALL_PACKAGES", "USES_PERMISSION"),
        Spec("usageAccessDeclared", "android.permission.PACKAGE_USAGE_STATS", "USES_PERMISSION"),
        Spec("modifySettingsDeclared", "android.permission.WRITE_SETTINGS", "USES_PERMISSION"),
        Spec("allFilesDeclared", "android.permission.MANAGE_EXTERNAL_STORAGE", "USES_PERMISSION", 30),
        Spec("exactAlarmsDeclared", "android.permission.SCHEDULE_EXACT_ALARM", "USES_PERMISSION", 31),
        Spec("accessibilityServiceDeclared", "android.permission.BIND_ACCESSIBILITY_SERVICE", "SERVICE_BINDING"),
        Spec("notificationListenerDeclared", "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "SERVICE_BINDING"),
        Spec("vpnServiceDeclared", "android.permission.BIND_VPN_SERVICE", "SERVICE_BINDING")
    )

    val bucketNames = specs.map { it.bucket }

    fun declared(requestedPermissions: Set<String>, serviceBindings: Set<String>, sdk: Int): List<Spec> =
        specs.filter { spec ->
            sdk >= spec.minSdk &&
                if (spec.source == "SERVICE_BINDING") spec.permission in serviceBindings
                else spec.permission in requestedPermissions
        }

    fun matrixItem(appName: String, packageName: String, spec: Spec): JSONObject =
        JSONObject()
            .put("name", appName).put("packageName", packageName)
            .put("permission", spec.permission).put("source", spec.source)
            .put("state", "DECLARED_UNVERIFIED")
            .put("granted", JSONObject.NULL)
            .put("note", "Declaration detected. Active special access was not verified.")

    fun declaredNames(observations: List<Spec>): JSONArray =
        JSONArray(observations.map { it.bucket }.sorted())

    fun evidence(observations: List<Spec>): JSONArray =
        JSONArray().apply {
            observations.forEach { spec ->
                put(JSONObject().put("id", spec.bucket).put("source", spec.source)
                    .put("state", "DECLARED_UNVERIFIED").put("permission", spec.permission))
            }
        }
}
