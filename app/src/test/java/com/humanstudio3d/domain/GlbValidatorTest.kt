package com.humanstudio3d.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GlbValidatorTest {

    private fun glb(json: String, declaredLengthDelta: Int = 0, magic: Int = 0x46546C67): ByteArray {
        val padded = StringBuilder(json)
        while (padded.length % 4 != 0) padded.append(' ')
        val jsonBytes = padded.toString().toByteArray(Charsets.UTF_8)
        val total = 12 + 8 + jsonBytes.size
        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(magic)
        buf.putInt(2)
        buf.putInt(total + declaredLengthDelta)
        buf.putInt(jsonBytes.size)
        buf.putInt(0x4E4F534A)
        buf.put(jsonBytes)
        return buf.array()
    }

    @Test
    fun validGlbReportsPresentKeys() {
        val report = GlbValidator.validateBytes(
            glb("""{"asset":{"version":"2.0"},"materials":[],"skins":[]}"""),
        )
        assertTrue(report.valid)
        assertTrue(report.hasMaterials)
        assertTrue(report.hasSkin)
        assertFalse(report.hasAnimations)
        assertFalse(report.hasMorphTargets)
    }

    @Test
    fun wrongMagicIsRejected() {
        val report = GlbValidator.validateBytes(glb("""{"asset":{"version":"2.0"}}""", magic = 0x12345678))
        assertFalse(report.valid)
        assertNotNull(report.problem)
    }

    @Test
    fun lengthMismatchIsRejected() {
        val report = GlbValidator.validateBytes(glb("""{"asset":{"version":"2.0"}}""", declaredLengthDelta = 4))
        assertFalse(report.valid)
    }

    @Test
    fun truncatedFileIsRejected() {
        val full = glb("""{"asset":{"version":"2.0"},"animations":[]}""")
        val report = GlbValidator.validateBytes(full.copyOf(full.size - 8))
        assertFalse(report.valid)
    }

    @Test
    fun tooShortIsRejected() {
        assertEquals(false, GlbValidator.validateBytes(ByteArray(5)).valid)
    }
}
