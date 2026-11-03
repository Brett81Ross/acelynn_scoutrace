package com.cactusbyte.scouttrace

import org.junit.Assert.*
import org.junit.Test

class ObservedSpecialAccessTest {
    @Test fun validListenerListParsesMultipleComponentsAndDeduplicatesPackages() {
        val raw = "com.example.one/.Service:com.example.two/com.example.two.Listener:com.example.one/.Other"
        assertEquals(setOf("com.example.one", "com.example.two"),
            ObservedSpecialAccess.notificationListenerPackages(raw))
    }

    @Test fun malformedPartialAndUnavailableSettingsNeverBecomeNoAccess() {
        assertNull(ObservedSpecialAccess.notificationListenerPackages(null))
        assertNull(ObservedSpecialAccess.notificationListenerPackages("garbage"))
        assertNull(ObservedSpecialAccess.notificationListenerPackages(
            "com.example.valid/.Listener:invalid"))
        assertNull(ObservedSpecialAccess.notificationListenerPackages(
            "com.example.valid/.Listener:"))
        assertEquals(emptySet<String>(), ObservedSpecialAccess.notificationListenerPackages(""))
    }

    @Test fun parserRejectsMissingComponentsAndSpoofedPackageStrings() {
        assertNull(ObservedSpecialAccess.packageFromComponent("com.android.vending.evil"))
        assertNull(ObservedSpecialAccess.packageFromComponent("com.example.app/"))
        assertNull(ObservedSpecialAccess.packageFromComponent("bad package/.Listener"))
        assertNull(ObservedSpecialAccess.packageFromComponent("com.example.app/.Listener/Extra"))
        assertEquals("com.example.app",
            ObservedSpecialAccess.packageFromComponent("com.example.app/.Listener"))
    }

    @Test fun selectedKeyboardIsNotEnabledListenerOrProofOfRecording() {
        val snapshot = ObservedSpecialAccess.Snapshot(
            emptySet(), ObservedSpecialAccess.selectedInputMethodPackage("com.example.keyboard/.Ime"))
        assertEquals("OS_REPORTED_SELECTED_SETTING", snapshot.keyboardState("com.example.keyboard"))
        assertEquals("NOT_IN_REPORTED_SETTING", snapshot.listenerState("com.example.keyboard"))
        assertFalse(snapshot.listenerEnabled("com.example.keyboard")!!)
    }

    @Test fun unavailableSettingsStayUnknown() {
        val snapshot = ObservedSpecialAccess.Snapshot(null, null)
        assertNull(snapshot.listenerEnabled("com.example.app"))
        assertNull(snapshot.keyboardSelected("com.example.app"))
        assertEquals("UNKNOWN", snapshot.listenerState("com.example.app"))
        assertEquals("UNKNOWN", snapshot.keyboardState("com.example.app"))
    }
}
