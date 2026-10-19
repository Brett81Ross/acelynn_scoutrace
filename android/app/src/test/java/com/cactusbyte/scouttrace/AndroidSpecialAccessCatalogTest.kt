package com.cactusbyte.scouttrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSpecialAccessCatalogTest {
    @Test fun serviceBindingsMustNotBeConfusedWithUsesPermissions() {
        val requested = setOf("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
        val observed = AndroidSpecialAccessCatalog.declared(requested, emptySet(), 35)
        assertFalse(observed.any { it.bucket == "notificationListenerDeclared" })
        val withService = AndroidSpecialAccessCatalog.declared(emptySet(), requested, 35)
        assertTrue(withService.any { it.bucket == "notificationListenerDeclared" })
        assertEquals("SERVICE_BINDING", withService.single().source)
    }

    @Test fun allSpecialCapabilitiesAreDeclarationsWithoutGrantVerdicts() {
        val permissions = setOf("android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.WRITE_SETTINGS", "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.SCHEDULE_EXACT_ALARM")
        val services = setOf("android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "android.permission.BIND_VPN_SERVICE")
        val observed = AndroidSpecialAccessCatalog.declared(permissions, services, 35)
        assertEquals(AndroidSpecialAccessCatalog.specs.size, observed.size)
        observed.forEach {
            val item = AndroidSpecialAccessCatalog.matrixItem("Sample", "com.example.sample", it)
            assertEquals("DECLARED_UNVERIFIED", item.getString("state"))
            assertTrue(item.isNull("granted"))
        }
    }

    @Test fun newerPlatformDeclarationsRespectApiGates() {
        val requested = setOf("android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.SYSTEM_ALERT_WINDOW")
        val old = AndroidSpecialAccessCatalog.declared(requested, emptySet(), 29)
        assertEquals(listOf("overlayDeclared"), old.map { it.bucket })
        val modern = AndroidSpecialAccessCatalog.declared(requested, emptySet(), 35)
        assertEquals(3, modern.size)
    }

    @Test fun noExtraAppPermissionCanBeInferredFromBoundServiceAlone() {
        val services = setOf("android.permission.BIND_ACCESSIBILITY_SERVICE")
        val declared = AndroidSpecialAccessCatalog.declared(emptySet(), services, 35)
        assertEquals(1, declared.size)
        assertEquals(0, AndroidSpecialAccessCatalog.declaredNames(emptyList()).length())
        assertEquals("DECLARED_UNVERIFIED",
            AndroidSpecialAccessCatalog.evidence(declared).getJSONObject(0).getString("state"))
    }
}
