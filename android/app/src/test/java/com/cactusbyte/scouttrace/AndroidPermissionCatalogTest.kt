package com.cactusbyte.scouttrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPermissionCatalogTest {
    @Test fun spansMoreThanOriginalSixSensitiveCategories() {
        assertTrue(AndroidPermissionCatalog.bucketNames.size >= 20)
        assertTrue(AndroidPermissionCatalog.bucketNames.containsAll(listOf(
            "camera", "microphone", "approximateLocation", "backgroundLocation",
            "selectedPhotos", "nearbyWifi", "callLogs", "calendar", "notifications")))
        assertEquals(AndroidPermissionCatalog.bucketNames.size, AndroidPermissionCatalog.bucketNames.distinct().size)
    }

    @Test fun android12DoesNotCheckAndroid13Or14Permissions() {
        var checks = 0
        val observed = AndroidPermissionCatalog.observe(setOf(
            "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            "android.permission.ACCESS_COARSE_LOCATION"), 32) { checks++; true }
        assertEquals(1, observed.size)
        assertEquals("approximateLocation", observed[0].spec.bucket)
        assertEquals(1, checks)
    }

    @Test fun selectedPhotosRemainDistinctFromFullMediaPermission() {
        val observed = AndroidPermissionCatalog.observe(setOf(
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            "android.permission.READ_MEDIA_IMAGES"), 34) { permission ->
            permission == "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        }
        assertEquals(2, observed.size)
        val selected = observed.first { it.spec.bucket == "selectedPhotos" }
        val photos = observed.first { it.spec.bucket == "photos" }
        assertEquals("USER_SELECTED_PHOTOS_AND_VIDEOS_ONLY", selected.spec.scope)
        assertTrue(selected.granted == true)
        assertFalse(photos.granted == true)
        assertEquals(1, AndroidPermissionCatalog.grantedNames(observed).length())
    }

    @Test fun failuresRemainUnknownRatherThanDenials() {
        val observed = AndroidPermissionCatalog.observe(setOf("android.permission.CAMERA"), 35) {
            throw SecurityException("Not visible")
        }
        assertNull(observed.single().granted)
        assertEquals("GRANT_UNAVAILABLE", observed.single().state)
        val item = AndroidPermissionCatalog.matrixItem("Sample", "com.example.sample", observed.single())
        assertTrue(item.isNull("granted"))
        assertEquals(1, AndroidPermissionCatalog.unavailableNames(observed).length())
    }

    @Test fun noCheckForPermissionNotRequestedByTarget() {
        var called = false
        val observed = AndroidPermissionCatalog.observe(emptySet(), 35) {
            called = true
            true
        }
        assertTrue(observed.isEmpty())
        assertFalse(called)
    }

    @Test fun backgroundAndApproximateLocationAreIndependent() {
        val requested = setOf("android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION")
        val observed = AndroidPermissionCatalog.observe(requested, 35) { it.endsWith("COARSE_LOCATION") }
        assertEquals(1, AndroidPermissionCatalog.grantedNames(observed).length())
        assertEquals("android.permission.ACCESS_COARSE_LOCATION",
            AndroidPermissionCatalog.grantedNames(observed).getString(0))
    }
}
