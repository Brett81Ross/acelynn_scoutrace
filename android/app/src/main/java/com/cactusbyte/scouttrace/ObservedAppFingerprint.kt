package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Fingerprint Android observations only, never calculated scores or app dispositions.
 * Version the stored key when changing this canonical evidence format.
 */
object ObservedAppFingerprint {
    const val VERSION = 3
    private val evidenceFields = listOf(
        "packageName", "installer", "versionCode", "requestedPermissions",
        "grantedRuntimePermissions", "unknownRuntimePermissionGrants", "declaredSpecialAccess",
        "overlayDeclared", "accessibilityState", "deviceAdminState",
        "accessibilityEnabled", "deviceAdminActive"
    )

    fun calculate(result: JSONObject): String {
        val apps = result.optJSONArray("appRisks") ?: JSONArray()
        val canonical = (0 until apps.length()).mapNotNull { index ->
            apps.optJSONObject(index)?.let { app ->
                evidenceFields.joinToString("\u001f") { field ->
                    field + "=" + canonicalValue(app.opt(field))
                }
            }
        }.sorted().joinToString("\n")
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun canonicalValue(value: Any?): String = when (value) {
        is JSONArray -> (0 until value.length()).map { value.optString(it) }
            .distinct().sorted().joinToString("\u001e")
        JSONObject.NULL, null -> ""
        else -> value.toString()
    }
}
