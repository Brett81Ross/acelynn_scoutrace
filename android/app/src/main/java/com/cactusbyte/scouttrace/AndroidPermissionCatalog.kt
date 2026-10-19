package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only manifest and Android-reported grant observations for Android-visible apps.
 * A granted runtime permission is not evidence that data was accessed or recorded.
 * AppOps, one-time grants, scoped media selections and OEM policy can further limit access.
 */
object AndroidPermissionCatalog {
    const val SCHEMA_VERSION = 2

    data class Spec(val bucket: String, val permission: String, val minSdk: Int = 26,
                    val maxSdk: Int = Int.MAX_VALUE, val scope: String = "RUNTIME_GRANT_ONLY")
    data class Observation(val spec: Spec, val granted: Boolean?) {
        val state: String get() = when (granted) {
            true -> "OS_REPORTED_GRANTED"
            false -> "OS_REPORTED_DENIED"
            null -> "GRANT_UNAVAILABLE"
        }
    }

    // Individual buckets avoid conflating approximate/precise or full/selected media access.
    val specs: List<Spec> = listOf(
        Spec("camera", "android.permission.CAMERA"),
        Spec("microphone", "android.permission.RECORD_AUDIO"),
        Spec("location", "android.permission.ACCESS_FINE_LOCATION"),
        Spec("approximateLocation", "android.permission.ACCESS_COARSE_LOCATION"),
        Spec("backgroundLocation", "android.permission.ACCESS_BACKGROUND_LOCATION", minSdk = 29),
        Spec("contacts", "android.permission.READ_CONTACTS"),
        Spec("contactsWrite", "android.permission.WRITE_CONTACTS"),
        Spec("sms", "android.permission.READ_SMS"),
        Spec("smsSend", "android.permission.SEND_SMS"),
        Spec("phone", "android.permission.READ_PHONE_STATE"),
        Spec("callLogs", "android.permission.READ_CALL_LOG"),
        Spec("calendar", "android.permission.READ_CALENDAR"),
        Spec("calendarWrite", "android.permission.WRITE_CALENDAR"),
        Spec("bodySensors", "android.permission.BODY_SENSORS"),
        Spec("backgroundBodySensors", "android.permission.BODY_SENSORS_BACKGROUND", minSdk = 33),
        Spec("activityRecognition", "android.permission.ACTIVITY_RECOGNITION", minSdk = 29),
        Spec("bluetoothScan", "android.permission.BLUETOOTH_SCAN", minSdk = 31),
        Spec("bluetoothConnect", "android.permission.BLUETOOTH_CONNECT", minSdk = 31),
        Spec("nearbyWifi", "android.permission.NEARBY_WIFI_DEVICES", minSdk = 33),
        Spec("notifications", "android.permission.POST_NOTIFICATIONS", minSdk = 33),
        Spec("photos", "android.permission.READ_MEDIA_IMAGES", minSdk = 33, scope = "MEDIA_GRANT_NOT_FULL_LIBRARY_PROOF"),
        Spec("videos", "android.permission.READ_MEDIA_VIDEO", minSdk = 33, scope = "MEDIA_GRANT_NOT_FULL_LIBRARY_PROOF"),
        Spec("audioFiles", "android.permission.READ_MEDIA_AUDIO", minSdk = 33),
        Spec("selectedPhotos", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            minSdk = 34, scope = "USER_SELECTED_PHOTOS_AND_VIDEOS_ONLY"),
        Spec("legacyStorage", "android.permission.READ_EXTERNAL_STORAGE",
            maxSdk = 32, scope = "LEGACY_STORAGE_PERMISSION_NOT_UNRESTRICTED_ACCESS")
    )

    val bucketNames: List<String> = specs.map { it.bucket }.distinct()

    fun observe(requested: Set<String>, sdk: Int,
                checkGrant: (String) -> Boolean?): List<Observation> =
        specs.filter { sdk >= it.minSdk && sdk <= it.maxSdk && it.permission in requested }
            .map { spec -> Observation(spec, try { checkGrant(spec.permission) } catch (_: Exception) { null }) }

    fun matrixItem(appName: String, packageName: String, observation: Observation): JSONObject =
        JSONObject()
            .put("name", appName).put("packageName", packageName)
            .put("permission", observation.spec.permission)
            .put("requested", true)
            .put("granted", observation.granted ?: JSONObject.NULL)
            .put("state", observation.state)
            .put("scope", observation.spec.scope)

    fun grantedNames(observations: List<Observation>): JSONArray =
        JSONArray(observations.filter { it.granted == true }.map { it.spec.permission }.distinct().sorted())

    fun unavailableNames(observations: List<Observation>): JSONArray =
        JSONArray(observations.filter { it.granted == null }.map { it.spec.permission }.distinct().sorted())
}
