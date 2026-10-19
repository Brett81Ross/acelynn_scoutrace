package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject

/**
 * Compares only fields present in both locally observed snapshots.
 * Missing old fields after a ScoutTrace upgrade are not evidence of device change.
 */
object BaselineComparator {
    private val metadataFields = listOf(
        "versionCode", "installer", "installSourceStatus", "requestedPermissions",
        "grantedRuntimePermissions", "unknownRuntimePermissionGrants", "declaredSpecialAccess",
        "overlayDeclared", "accessibilityEnabled", "deviceAdminActive",
        "accessibilityState", "deviceAdminState"
    )

    fun compare(before: JSONObject, after: JSONObject, savedAt: Long): JSONObject {
        val previous = appMap(before.optJSONArray("appRisks"))
        val current = appMap(after.optJSONArray("appRisks"))
        val beforePkgs = previous.keys
        val afterPkgs = current.keys
        // Existing baselines may have been classified under a different policy.
        // Never portray an algorithm change alone as a new security incident.
        val priorModel = before.optInt("assessmentModelVersion", -1)
        val currentModel = after.optInt("assessmentModelVersion", -1)
        val modelChanged = priorModel <= 0 || currentModel <= 0 || priorModel != currentModel
        val priorPermissions = before.optInt("permissionEvidenceSchemaVersion", 1)
        val currentPermissions = after.optInt("permissionEvidenceSchemaVersion", 1)
        val permissionSchemaChanged = priorPermissions != currentPermissions
        val risks = JSONArray()
        val metadata = JSONArray()
        (beforePkgs intersect afterPkgs).sorted().forEach { pkg ->
            val old = previous.getValue(pkg)
            val now = current.getValue(pkg)
            if (!modelChanged && (old.optString("level") != now.optString("level") ||
                old.optInt("score") != now.optInt("score"))) {
                risks.put(JSONObject().put("packageName", pkg)
                    .put("fromLevel", old.optString("level"))
                    .put("toLevel", now.optString("level"))
                    .put("fromScore", old.optInt("score"))
                    .put("toScore", now.optInt("score")))
            }

            val fields = JSONArray()
            metadataFields.forEach { field ->
                val newObservationField = field in setOf("grantedRuntimePermissions",
                    "unknownRuntimePermissionGrants", "declaredSpecialAccess",
                    "accessibilityState", "deviceAdminState")
                if ((!permissionSchemaChanged || !newObservationField) &&
                    old.has(field) && now.has(field) &&
                    comparableValue(old.opt(field)) != comparableValue(now.opt(field))) {
                    fields.put(field)
                }
            }
            if (fields.length() > 0) {
                val event = JSONObject().put("packageName", pkg).put("fields", fields)
                if ((0 until fields.length()).any { fields.optString(it) == "grantedRuntimePermissions" }) {
                    val oldGrants = strings(old.optJSONArray("grantedRuntimePermissions"))
                    val newGrants = strings(now.optJSONArray("grantedRuntimePermissions"))
                    event.put("grantsAdded", JSONArray((newGrants - oldGrants).sorted()))
                        .put("grantsRevoked", JSONArray((oldGrants - newGrants).sorted()))
                }
                if ((0 until fields.length()).any { fields.optString(it) == "declaredSpecialAccess" }) {
                    val oldDeclarations = strings(old.optJSONArray("declaredSpecialAccess"))
                    val nowDeclarations = strings(now.optJSONArray("declaredSpecialAccess"))
                    event.put("specialDeclarationsAdded",JSONArray((nowDeclarations - oldDeclarations).sorted()))
                        .put("specialDeclarationsRemoved",JSONArray((oldDeclarations - nowDeclarations).sorted()))
                }
                val enabled = JSONArray()
                val disabled = JSONArray()
                listOf("accessibilityEnabled", "deviceAdminActive").forEach { field ->
                    if (old.has(field) && now.has(field) &&
                        old.optBoolean(field) != now.optBoolean(field)) {
                        if (now.optBoolean(field)) enabled.put(field) else disabled.put(field)
                    }
                }
                if (enabled.length() > 0) event.put("accessEnabled", enabled)
                if (disabled.length() > 0) event.put("accessDisabled", disabled)
                metadata.put(event)
            }
        }

        return JSONObject().put("exists", true).put("savedAt", savedAt)
            .put("newApps", JSONArray((afterPkgs - beforePkgs).sorted()))
            .put("removedApps", JSONArray((beforePkgs - afterPkgs).sorted()))
            .put("riskChanges", risks)
            .put("metadataChanges", metadata)
            .put("assessmentModelChanged", modelChanged)
            .put("permissionEvidenceSchemaChanged", permissionSchemaChanged)
            .put("levelChanged", !modelChanged && before.optString("level") != after.optString("level"))
    }

    private fun appMap(array: JSONArray?): Map<String, JSONObject> {
        if (array == null) return emptyMap()
        return (0 until array.length()).mapNotNull { i ->
            val app = array.optJSONObject(i) ?: return@mapNotNull null
            app.optString("packageName").takeIf(String::isNotBlank)?.let { it to app }
        }.toMap()
    }

    private fun strings(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return (0 until array.length()).mapNotNull { i ->
            array.optString(i).takeIf(String::isNotBlank)
        }.toSet()
    }

    private fun comparableValue(value: Any?): String = when (value) {
        is JSONArray -> strings(value).sorted().joinToString("\u001e")
        else -> value?.toString() ?: ""
    }
}
