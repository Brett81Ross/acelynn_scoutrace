package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialAccessReliabilityTest {
    private fun readable(): JSONObject = JSONObject()
        .put("accessibilityQuery", "ANDROID_REPORTED")
        .put("deviceAdminQuery", "ANDROID_REPORTED")
        .put("notificationListenerSetting", "ANDROID_SETTING_REPORTED")
        .put("selectedKeyboardSetting", "ANDROID_SETTING_REPORTED")

    @Test fun differentiatesMissingMalformedAndEmptyButReadable() {
        assertEquals("UNAVAILABLE", SpecialAccessReliability.settingStatus(null, null))
        assertEquals("MALFORMED", SpecialAccessReliability.settingStatus("broken-setting", null))
        assertEquals("READABLE", SpecialAccessReliability.settingStatus("", emptySet<String>()))
        assertEquals("READABLE", SpecialAccessReliability.settingStatus("a/.B", setOf("a")))
    }

    @Test fun unknownOemSettingNeverBecomesVerifiedNegative() {
        assertFalse(SpecialAccessReliability.comparableState("UNKNOWN", "NOT_OBSERVED"))
        assertFalse(SpecialAccessReliability.comparableState("OS_REPORTED_ENABLED_SETTING", "UNKNOWN"))
        assertTrue(SpecialAccessReliability.comparableState("OS_REPORTED_ENABLED_SETTING",
            "NOT_IN_REPORTED_SETTING"))
    }

    @Test fun optionalOemReadoutCanBePartialWithoutBlockingCoreTimeline() {
        val status=SpecialAccessReliability.assess(readable()
            .put("notificationListenerSetting", "MALFORMED"), JSONArray())
        assertFalse(status.getBoolean("complete"))
        assertTrue(status.getBoolean("fingerprintEligible"))
        assertEquals("PARTIAL", status.getString("status"))
        assertEquals(1, status.getJSONArray("limitations").length())
    }

    @Test fun failedPermissionQueryDoesNotAdvanceCheckpoint() {
        val app=JSONObject().put("unknownRuntimePermissionGrants",
            JSONArray().put("android.permission.CAMERA"))
        val status=SpecialAccessReliability.assess(readable(), JSONArray().put(app))
        assertFalse(status.getBoolean("complete"))
        assertFalse(status.getBoolean("fingerprintEligible"))
        assertEquals("RUNTIME_PERMISSION_GRANT_UNAVAILABLE",
            status.getJSONArray("limitations").getString(0))
    }

    @Test fun fullyReadableSnapshotRemainsEligibleForTimeline() {
        val result=SpecialAccessReliability.assess(readable(),JSONArray()
            .put(JSONObject().put("unknownRuntimePermissionGrants",JSONArray())))
        assertTrue(result.getBoolean("complete"))
        assertTrue(result.getBoolean("fingerprintEligible"))
        assertEquals(0,result.getJSONArray("limitations").length())
    }

    @Test fun missingServiceMetadataIsNotAConfirmedNoServiceFinding() {
        assertEquals("NOT_REPORTED_OR_EMPTY",SpecialAccessReliability.serviceMetadataState(false))
        assertEquals("REPORTED",SpecialAccessReliability.serviceMetadataState(true))
    }

    @Test fun missingCoreAccessibilityReadBlocksCheckpointEvenIfOptionalSettingsRead() {
        val status=SpecialAccessReliability.assess(readable()
            .put("accessibilityQuery","UNAVAILABLE"),JSONArray())
        assertFalse(status.getBoolean("fingerprintEligible"))
        assertEquals(1,status.getJSONArray("checkpointLimitations").length())
    }
}
