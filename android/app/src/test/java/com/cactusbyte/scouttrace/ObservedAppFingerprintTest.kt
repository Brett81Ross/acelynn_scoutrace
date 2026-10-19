package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ObservedAppFingerprintTest {
    private fun app(pkg: String, grants: JSONArray = JSONArray().put("android.permission.CAMERA")) =
        JSONObject().put("packageName", pkg).put("installer", "com.android.vending")
            .put("versionCode", 5)
            .put("requestedPermissions", JSONArray().put("android.permission.CAMERA")
                .put("android.permission.RECORD_AUDIO"))
            .put("grantedRuntimePermissions", grants)
            .put("overlayDeclared", false)
            .put("accessibilityEnabled", false).put("deviceAdminActive", false)
            .put("level", "REVIEW").put("score", 2)

    private fun snapshot(vararg apps: JSONObject): JSONObject =
        JSONObject().put("appRisks", JSONArray().apply { apps.forEach { put(it) } })

    @Test fun policyScoreAndClassificationChangesDoNotLookLikeDeviceEvents() {
        val original = app("com.example.app")
        val reclassified = JSONObject(original.toString()).put("level", "HIGH CAUTION")
            .put("score", 99).put("assessmentRule", "TEST").put("evidence", JSONObject())
        assertEquals(ObservedAppFingerprint.calculate(snapshot(original)),
            ObservedAppFingerprint.calculate(snapshot(reclassified)))
    }

    @Test fun appAndPermissionArrayOrderDoesNotCreateFalseChanges() {
        val first = app("com.example.first")
        val second = app("com.example.second")
        val reordered = app("com.example.first").put("requestedPermissions",
            JSONArray().put("android.permission.RECORD_AUDIO").put("android.permission.CAMERA"))
        assertEquals(ObservedAppFingerprint.calculate(snapshot(first, second)),
            ObservedAppFingerprint.calculate(snapshot(second, reordered)))
    }

    @Test fun actualRuntimeGrantChangeUpdatesFingerprint() {
        val before = snapshot(app("com.example.app"))
        val after = snapshot(app("com.example.app", JSONArray()))
        assertNotEquals(ObservedAppFingerprint.calculate(before), ObservedAppFingerprint.calculate(after))
    }

    @Test fun changedInstallerOrPrivilegeUpdatesFingerprint() {
        val original = snapshot(app("com.example.app"))
        val changed = app("com.example.app").put("installer", "org.fdroid.fdroid")
            .put("accessibilityEnabled", true)
        assertNotEquals(ObservedAppFingerprint.calculate(original),
            ObservedAppFingerprint.calculate(snapshot(changed)))
    }
}
