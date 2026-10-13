package com.cactusbyte.scouttrace

/** An installer identity is context, not a malware verdict or cryptographic store proof. */
object InstallSourceTrust {
    private val knownStorePackageNames = setOf(
        "com.android.vending",
        "com.sec.android.app.samsungapps"
    )

    fun isKnownStore(installerPackage: String?): Boolean =
        installerPackage != null && installerPackage in knownStorePackageNames
}
