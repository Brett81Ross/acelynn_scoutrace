package com.cactusbyte.scouttrace

/** Manufacturer labels are diagnostic context, not a license to invent OEM APIs. */
object OemEvidenceGuidance {
    fun settingsMode(manufacturer: String?): String = "GENERIC_ANDROID_SETTINGS"
    fun note(manufacturer: String?): String =
        "Menu names and available app/security details vary with Android version, manufacturer, profile and device policy. Use Android App Info or search Settings; ScoutTrace does not assume manufacturer-only permissions or undocumented APIs."
}
