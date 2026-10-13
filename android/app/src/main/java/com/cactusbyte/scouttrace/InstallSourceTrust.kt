package com.cactusbyte.scouttrace

/** An installer identity is context, not a malware verdict or cryptographic store proof. */
object InstallSourceTrust {
    private val knownStorePackageNames = setOf(
        "com.android.vending",
        "com.sec.android.app.samsungapps"
    )

    /** Null installer means Android did not expose the origin, not proof of sideloading. */
    enum class Source { KNOWN_STORE, OTHER_INSTALLER, UNKNOWN }

    fun classify(installerPackage: String?): Source = when {
        installerPackage.isNullOrBlank() -> Source.UNKNOWN
        isKnownStore(installerPackage) -> Source.KNOWN_STORE
        else -> Source.OTHER_INSTALLER
    }

    fun isKnownStore(installerPackage: String?): Boolean =
        installerPackage != null && installerPackage in knownStorePackageNames
}
