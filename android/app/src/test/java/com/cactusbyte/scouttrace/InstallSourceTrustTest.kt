package com.cactusbyte.scouttrace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallSourceTrustTest {
    @Test fun officialKnownStoreIdsMatchExactly() {
        assertTrue(InstallSourceTrust.isKnownStore("com.android.vending"))
        assertTrue(InstallSourceTrust.isKnownStore("com.sec.android.app.samsungapps"))
    }

    @Test fun unknownAndSpoofedNamesAreNotKnownStores() {
        assertFalse(InstallSourceTrust.isKnownStore("com.fake.google.market"))
        assertFalse(InstallSourceTrust.isKnownStore("com.fake.samsungapps"))
        assertFalse(InstallSourceTrust.isKnownStore("com.android.vending.evil"))
        assertFalse(InstallSourceTrust.isKnownStore("COM.ANDROID.VENDING"))
        assertFalse(InstallSourceTrust.isKnownStore(""))
        assertFalse(InstallSourceTrust.isKnownStore(null))
    }
    @Test fun unavailableInstallerIsNotReportedAsSideloading() {
        assertTrue(InstallSourceTrust.classify(null) == InstallSourceTrust.Source.UNKNOWN)
        assertTrue(InstallSourceTrust.classify("") == InstallSourceTrust.Source.UNKNOWN)
        assertTrue(InstallSourceTrust.classify("  ") == InstallSourceTrust.Source.UNKNOWN)
    }

    @Test fun knownVersusAlternativeInstallerIdentitiesStayDistinct() {
        assertTrue(InstallSourceTrust.classify("com.android.vending") == InstallSourceTrust.Source.KNOWN_STORE)
        assertTrue(InstallSourceTrust.classify("com.sec.android.app.samsungapps") == InstallSourceTrust.Source.KNOWN_STORE)
        assertTrue(InstallSourceTrust.classify("com.fake.google.market") == InstallSourceTrust.Source.OTHER_INSTALLER)
        assertTrue(InstallSourceTrust.classify("org.fdroid.fdroid") == InstallSourceTrust.Source.OTHER_INSTALLER)
    }
}
