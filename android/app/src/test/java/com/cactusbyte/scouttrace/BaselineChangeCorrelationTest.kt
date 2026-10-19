package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineChangeCorrelationTest {
    private fun app(pkg: String, a11y: Boolean = false, admin: Boolean = false) =
        JSONObject().put("packageName", pkg).put("name", "Example")
            .put("accessibilityEnabled", a11y).put("deviceAdminActive", admin)
    private fun change(pkg: String, grants: Array<String> = emptyArray(),
        active: Array<String> = emptyArray(), declarations: Array<String> = emptyArray()) =
        JSONObject().put("packageName",pkg).put("grantsAdded",JSONArray(grants.toList()))
            .put("accessEnabled",JSONArray(active.toList()))
            .put("specialDeclarationsAdded",JSONArray(declarations.toList()))
    private fun baseline(vararg changes: JSONObject) =
        JSONObject().put("exists",true).put("metadataChanges",
            JSONArray().apply { changes.forEach { put(it) } })

    @Test fun noBaselineNeverInventsChangeCorrelation() {
        val result = BaselineChangeCorrelation.correlate(
            JSONObject().put("exists", false), JSONArray().put(app("com.example.app", true, true)))
        assertEquals(0, result.length())
    }

    @Test fun twoActivePrivilegesRiseAheadOfNewGrant() {
        val diff=baseline(
            change("com.example.grant",arrayOf("android.permission.CAMERA")),
            change("com.example.active", active=arrayOf("accessibilityEnabled")))
        val risks=JSONArray().put(app("com.example.grant"))
            .put(app("com.example.active", true, true))
        val result=BaselineChangeCorrelation.correlate(diff,risks)
        assertEquals(2, result.length())
        assertEquals("com.example.active", result.getJSONObject(0).getString("packageName"))
        assertEquals("PRIORITY_REVIEW", result.getJSONObject(0).getString("priority"))
        assertFalse(result.getJSONObject(0).getBoolean("observedAtIsChangeTime"))
    }

    @Test fun declarationOnlyCannotClaimActiveAccess() {
        val result=BaselineChangeCorrelation.correlate(
            baseline(change("com.example.app", declarations=arrayOf("vpnServiceDeclared"))),
            JSONArray().put(app("com.example.app")))
        assertEquals("CONTEXT_ONLY",result.getJSONObject(0).getString("priority"))
        assertEquals(0,result.getJSONObject(0).getJSONArray("activeAccessEnabled").length())
        assertTrue(result.getJSONObject(0).getString("detail").contains("not proof"))
    }

    @Test fun newSensitiveGrantCannotProvePermissionUse() {
        val result=BaselineChangeCorrelation.correlate(
            baseline(change("com.example.app",grants=arrayOf("android.permission.RECORD_AUDIO"))),
            JSONArray().put(app("com.example.app")))
        assertEquals("REVIEW_NEW_GRANT",result.getJSONObject(0).getString("priority"))
        assertTrue(result.getJSONObject(0).getString("detail").contains("does not prove"))
    }

    @Test fun revokedPermissionsAreNotNewAccess() {
        val change=change("com.example.app").put("grantsRevoked",
            JSONArray().put("android.permission.CAMERA"))
        assertEquals(0,BaselineChangeCorrelation.correlate(
            baseline(change),JSONArray().put(app("com.example.app"))).length())
    }

    @Test fun missingVisibleAppProducesNoSpeculativeFinding() {
        assertEquals(0,BaselineChangeCorrelation.correlate(
            baseline(change("com.example.other",arrayOf("android.permission.CAMERA"))),
            JSONArray().put(app("com.example.app"))).length())
    }

    @Test fun multipleSignalsProduceOneEventPerApp() {
        val result=BaselineChangeCorrelation.correlate(
            baseline(change("com.example.app",arrayOf("android.permission.CAMERA"),
                arrayOf("deviceAdminActive"),arrayOf("overlayDeclared"))),
            JSONArray().put(app("com.example.app",true,true)))
        assertEquals(1,result.length())
        assertEquals(1,result.getJSONObject(0).getJSONArray("specialDeclarationsAdded").length())
    }
}
