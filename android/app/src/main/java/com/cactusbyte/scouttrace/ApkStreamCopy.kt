package com.cactusbyte.scouttrace

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Copies a document selected by the user to a private temporary file.
 * Never interprets document contents as executable code.
 */
internal object ApkStreamCopy {
    const val MAX_BYTES: Long = 256L * 1024L * 1024L

    data class Result(val bytes: Long, val sha256: String)

    fun copy(input: InputStream, output: OutputStream, maxBytes: Long = MAX_BYTES): Result {
        require(maxBytes in 1..MAX_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32768)
        var total = 0L
        var consecutiveZeroReads = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                if (++consecutiveZeroReads > 16) throw IOException(
                    "Selected document provider made no read progress"
                )
                continue
            }
            consecutiveZeroReads = 0
            total += count
            require(total <= maxBytes) { "APK exceeds the selected inspection size limit." }
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
        require(total > 0) { "Selected APK is empty." }
        output.flush()
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        return Result(total, sha256)
    }
}
