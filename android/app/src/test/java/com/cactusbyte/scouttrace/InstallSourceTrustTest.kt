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
}
