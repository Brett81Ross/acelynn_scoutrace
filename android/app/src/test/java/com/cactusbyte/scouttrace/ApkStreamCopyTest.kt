package com.cactusbyte.scouttrace

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ApkStreamCopyTest {
    @Test fun copiesActualBytesAndReturnsSha256() {
        val output = ByteArrayOutputStream()
        val result = ApkStreamCopy.copy(ByteArrayInputStream("abc".toByteArray()), output)
        assertEquals(3L, result.bytes)
        assertEquals("abc", output.toString("UTF-8"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.sha256)
    }

    @Test fun intermittentZeroLengthReadsAreNotMistakenForEof() {
        val stream = object : ByteArrayInputStream("valid".toByteArray()) {
            var zeroReads = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (zeroReads++ < 2) return 0
                return super.read(b, off, len)
            }
        }
        val output = ByteArrayOutputStream()
        val result = ApkStreamCopy.copy(stream, output)
        assertEquals(5L, result.bytes)
        assertEquals("valid", output.toString("UTF-8"))
    }

    @Test fun repeatedlyStalledProviderFailsInsteadOfSpinning() {
        val stalled = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        try {
            ApkStreamCopy.copy(stalled, ByteArrayOutputStream())
            fail("A document provider that never progresses must fail")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("no read progress"))
        }
    }

    @Test fun oversizedFileIsRejectedBeforeItsBytesAreWritten() {
        val output = ByteArrayOutputStream()
        try {
            ApkStreamCopy.copy(ByteArrayInputStream("12345".toByteArray()), output, maxBytes = 4)
            fail("Files past the configured size limit must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("size limit"))
        }
        assertEquals(0, output.size())
    }

    @Test fun emptyFileIsRejected() {
        try {
            ApkStreamCopy.copy(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
            fail("Empty documents are not APKs")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("empty"))
        }
    }
}
