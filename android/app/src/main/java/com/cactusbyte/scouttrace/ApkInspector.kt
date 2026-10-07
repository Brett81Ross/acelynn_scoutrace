package com.cactusbyte.scouttrace

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ApkInspector(private val context: Context) {
    fun inspect(uri: Uri): JSONObject {
        val temp = File.createTempFile("scouttrace_", ".apk", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(temp.absolutePath, PackageManager.GET_PERMISSIONS)
                ?: return JSONObject().put("ok", false).put("error", "Android could not parse the selected APK.")
            val permissions = info.requestedPermissions?.toList().orEmpty()
            return JSONObject()
                .put("ok", true)
                .put("sha256", digest.digest().joinToString("") { "%02x".format(it) })
                .put("packageName", info.packageName)
                .put("versionName", info.versionName ?: "unknown")
                .put("requestedPermissions", JSONArray(permissions))
                .put("note", "Local static APK metadata inspection only.")
        } finally {
            temp.delete()
        }
    }
}
