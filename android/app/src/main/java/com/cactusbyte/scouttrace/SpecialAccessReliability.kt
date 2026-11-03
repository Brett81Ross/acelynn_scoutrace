package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject

/**
 * Distinguishes Android-reported facts from unavailable or malformed OEM settings.
 * A readable enabled setting is not proof that a service is connected or used.
 */
object SpecialAccessReliability {
    const val VERSION = 1

    fun settingStatus(raw: String?, parsed: Any?): String = when {
        raw == null -> "UNAVAILABLE"
        parsed == null -> "MALFORMED"
        else -> "READABLE"
    }

    fun comparableState(old: String?, current: String?): Boolean =
        old != null && current != null &&
        old.isNotBlank() && current.isNotBlank() &&
        old != "UNKNOWN" && current != "UNKNOWN"

    /** Android may return null services for apps without services or with limited metadata. */
    fun serviceMetadataState(serviceArrayPresent: Boolean): String =
        if (serviceArrayPresent) "REPORTED" else "NOT_REPORTED_OR_EMPTY"

    /** Only a complete observation may advance a timeline fingerprint checkpoint. */
    fun assess(coverage: JSONObject, apps: JSONArray?): JSONObject {
        val warnings = mutableListOf<String>()
        val blocking = mutableListOf<String>()
        val expected = linkedMapOf(
            "accessibilityQuery" to "ANDROID_REPORTED",
            "deviceAdminQuery" to "ANDROID_REPORTED",
            "notificationListenerSetting" to "ANDROID_SETTING_REPORTED",
            "selectedKeyboardSetting" to "ANDROID_SETTING_REPORTED"
        )
        expected.forEach { (key, success) ->
            if (coverage.optString(key) != success) {
                warnings += key + "_UNVERIFIED"
                if (key == "accessibilityQuery" || key == "deviceAdminQuery")
                    blocking += key + "_UNVERIFIED"
            }
        }
        for (i in 0 until (apps?.length() ?: 0)) {
            val app = apps?.optJSONObject(i) ?: continue
            if ((app.optJSONArray("unknownRuntimePermissionGrants")?.length() ?: 0) > 0) {
                warnings += "RUNTIME_PERMISSION_GRANT_UNAVAILABLE"
                blocking += "RUNTIME_PERMISSION_GRANT_UNAVAILABLE"
                break
            }
        }
        return JSONObject()
            .put("complete", warnings.isEmpty())
            .put("fingerprintEligible", blocking.isEmpty())
            .put("checkpointLimitations", JSONArray(blocking.distinct()))
            .put("status", if (warnings.isEmpty()) "COMPLETE_FOR_SCOUTTRACE_SCOPE" else "PARTIAL")
            .put("limitations", JSONArray(warnings.distinct()))
            .put("nextStep", if (warnings.isEmpty()) "Compare with your saved baseline."
                else "Some settings were unavailable. Core verified observations can be compared when eligible; optional settings remain unknown. Retry the scan or check Android Settings.")
    }
}
