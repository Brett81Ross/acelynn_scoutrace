package com.cactusbyte.scouttrace

import android.os.Build
import org.json.JSONObject

object DeviceProfile {
    fun current(): JSONObject {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val family = when {
            manufacturer.contains("samsung", true) -> "SAMSUNG"
            manufacturer.contains("google", true) -> "PIXEL"
            manufacturer.contains("motorola", true) -> "MOTOROLA"
            else -> "ANDROID"
        }
        return JSONObject()
            .put("manufacturer", manufacturer.ifBlank { "unknown" })
            .put("model", Build.MODEL.orEmpty().ifBlank { "unknown" })
            .put("sdk", Build.VERSION.SDK_INT)
            .put("androidVersion", Build.VERSION.RELEASE.orEmpty().ifBlank { "unknown" })
            .put("oemFamily", family)
            .put("remediationMode", OemEvidenceGuidance.settingsMode(manufacturer))
            .put("remediationCaveat", OemEvidenceGuidance.note(manufacturer))
    }
}
