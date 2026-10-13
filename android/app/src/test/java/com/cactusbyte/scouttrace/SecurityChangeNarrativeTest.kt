package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityChangeNarrativeTest {
    @Test fun noBaselineNeverInventsInstallationTimestamp() {
        val message = SecurityChangeNarrative.describe(JSONObject().put("exists", false))
        assertTrue(message.contains("observation"))
        assertTrue(message.contains("Save a trusted baseline"))
        assertFalse(message.contains("installed on"))
    }

    @Test fun baselineDetailIsExplicitlyRelativeToSavedState() {
        val comparison = JSONObject().put("exists", true)
            .put("newApps", JSONArray().put("org.example.sample"))
            .put("removedApps", JSONArray())
            .put("riskChanges", JSONArray())
            .put("metadataChanges", JSONArray())
        val message = SecurityChangeNarrative.describe(comparison)
        assertTrue(message.contains("saved baseline"))
        assertTrue(message.contains("newly visible"))
        assertTrue(message.contains("Package visibility"))
        assertFalse(message.contains("org.example.sample"))
    }

    @Test fun fingerprintChangeDoesNotRequireBaselineChange() {
        val comparison = JSONObject().put("exists", true)
            .put("newApps", JSONArray()).put("removedApps", JSONArray())
            .put("riskChanges", JSONArray()).put("metadataChanges", JSONArray())
        val message = SecurityChangeNarrative.describe(comparison)
        assertTrue(message.contains("No difference"))
    }
}
