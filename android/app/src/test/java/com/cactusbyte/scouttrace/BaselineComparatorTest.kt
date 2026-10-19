package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineComparatorTest {
    private fun app(pkg: String, permissions: JSONArray? = null): JSONObject =
        JSONObject().put("packageName", pkg).put("level", "REVIEW").put("score", 2)
            .put("versionCode", 4)
            .apply { if (permissions != null) put("grantedRuntimePermissions", permissions) }

    private fun snapshot(vararg apps: JSONObject) =
        JSONObject().put("level", "REVIEW").put("assessmentModelVersion", AppRiskPolicy.VERSION)
            .put("appRisks", JSONArray().apply { apps.forEach { put(it) } })

    @Test fun permissionGrantAndRevocationAreNamedExactly() {
        val before = snapshot(app("com.example.app", JSONArray().put("android.permission.CAMERA")
            .put("android.permission.RECORD_AUDIO")))
        val after = snapshot(app("com.example.app", JSONArray().put("android.permission.CAMERA")
            .put("android.permission.ACCESS_FINE_LOCATION")))
        val diff = BaselineComparator.compare(before, after, 1234)
        val change = diff.getJSONArray("metadataChanges").getJSONObject(0)
        assertEquals(1, change.getJSONArray("fields").length())
        assertEquals("grantedRuntimePermissions", change.getJSONArray("fields").getString(0))
        assertEquals("android.permission.ACCESS_FINE_LOCATION", change.getJSONArray("grantsAdded").getString(0))
        assertEquals("android.permission.RECORD_AUDIO", change.getJSONArray("grantsRevoked").getString(0))
        assertEquals(1234L, diff.getLong("savedAt"))
    }

    @Test fun schemaUpgradeDoesNotPretendPermissionChanged() {
        val before = snapshot(app("com.example.app"))
        val after = snapshot(app("com.example.app", JSONArray().put("android.permission.CAMERA")))
        val diff = BaselineComparator.compare(before, after, 1)
        assertEquals(0, diff.getJSONArray("metadataChanges").length())
    }

    @Test fun arrayReorderingIsNotSecurityChange() {
        val before = snapshot(app("com.example.app", JSONArray().put("android.permission.CAMERA")
            .put("android.permission.RECORD_AUDIO")))
        val after = snapshot(app("com.example.app", JSONArray().put("android.permission.RECORD_AUDIO")
            .put("android.permission.CAMERA")))
        val diff = BaselineComparator.compare(before, after, 1)
        assertEquals(0, diff.getJSONArray("metadataChanges").length())
    }

    @Test fun visibleInventoryIsNotPresentedAsKnownInstallOrUninstallEvent() {
        val before = snapshot(app("com.example.first"))
        val after = snapshot(app("com.example.second"))
        val diff = BaselineComparator.compare(before, after, 1)
        assertTrue(diff.getBoolean("exists"))
        assertEquals("com.example.second", diff.getJSONArray("newApps").getString(0))
        assertEquals("com.example.first", diff.getJSONArray("removedApps").getString(0))
        assertFalse(diff.has("installedAt"))
    }

    @Test fun scoreAndLevelChangesArePreserved() {
        val before = snapshot(app("com.example.app"))
        val changed = app("com.example.app").put("level", "ELEVATED").put("score", 7)
        val diff = BaselineComparator.compare(before, snapshot(changed), 0)
        val change = diff.getJSONArray("riskChanges").getJSONObject(0)
        assertEquals("REVIEW", change.getString("fromLevel"))
        assertEquals("ELEVATED", change.getString("toLevel"))
        assertEquals(2, change.getInt("fromScore"))
        assertEquals(7, change.getInt("toScore"))
    }

    @Test fun assessmentUpgradeCannotFabricateRiskChange() {
        val prior = snapshot(app("com.example.app")).put("assessmentModelVersion", 1)
        val reclassified = app("com.example.app").put("level", "ELEVATED").put("score", 9)
        val result = BaselineComparator.compare(prior, snapshot(reclassified), 42)
        assertTrue(result.getBoolean("assessmentModelChanged"))
        assertFalse(result.getBoolean("levelChanged"))
        assertEquals(0, result.getJSONArray("riskChanges").length())
        assertEquals(0, result.getJSONArray("metadataChanges").length())
    }

    @Test fun privilegedAccessTurningOnIsNamedAsBaselineRelativeObservation() {
        val prior = snapshot(app("com.example.app").put("accessibilityEnabled", false)
            .put("deviceAdminActive", false))
        val next = snapshot(app("com.example.app").put("accessibilityEnabled", true)
            .put("deviceAdminActive", false))
        val result = BaselineComparator.compare(prior, next, 99)
        val event = result.getJSONArray("metadataChanges").getJSONObject(0)
        assertEquals("accessibilityEnabled", event.getJSONArray("accessEnabled").getString(0))
        assertFalse(event.has("installedAt"))
        assertFalse(result.getBoolean("assessmentModelChanged"))
    }

    @Test fun expandedPermissionCatalogDoesNotInventGrantsForOlderBaseline() {
        val old = snapshot(app("com.example.app", JSONArray().put("android.permission.CAMERA")))
            .put("permissionEvidenceSchemaVersion", 1)
        val expanded = snapshot(app("com.example.app", JSONArray().put("android.permission.CAMERA")
            .put("android.permission.ACCESS_COARSE_LOCATION")))
            .put("permissionEvidenceSchemaVersion", AndroidPermissionCatalog.SCHEMA_VERSION)
        val diff = BaselineComparator.compare(old, expanded, 0)
        assertTrue(diff.getBoolean("permissionEvidenceSchemaChanged"))
        assertEquals(0, diff.getJSONArray("metadataChanges").length())
    }

    @Test fun samePermissionSchemaDetectsRealNewLocationGrant() {
        val old = snapshot(app("com.example.app", JSONArray()))
            .put("permissionEvidenceSchemaVersion", AndroidPermissionCatalog.SCHEMA_VERSION)
        val expanded = snapshot(app("com.example.app", JSONArray()
            .put("android.permission.ACCESS_COARSE_LOCATION")))
            .put("permissionEvidenceSchemaVersion", AndroidPermissionCatalog.SCHEMA_VERSION)
        val diff = BaselineComparator.compare(old, expanded, 0)
        assertEquals("android.permission.ACCESS_COARSE_LOCATION",
            diff.getJSONArray("metadataChanges").getJSONObject(0)
                .getJSONArray("grantsAdded").getString(0))
    }

    @Test fun declaredListenerServiceChangeIsVisibleUnderMatchingEvidenceSchema() {
        val old = snapshot(app("com.example.app").put("declaredSpecialAccess", JSONArray()))
            .put("permissionEvidenceSchemaVersion", AndroidPermissionCatalog.SCHEMA_VERSION)
        val next = snapshot(app("com.example.app")
            .put("declaredSpecialAccess", JSONArray().put("notificationListenerDeclared")))
            .put("permissionEvidenceSchemaVersion", AndroidPermissionCatalog.SCHEMA_VERSION)
        val diff = BaselineComparator.compare(old, next, 0)
        val fields = diff.getJSONArray("metadataChanges").getJSONObject(0).getJSONArray("fields")
        assertEquals("declaredSpecialAccess", fields.getString(0))
    }
}
