package com.humanstudio3d.domain

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Result of a structural GLB check.
 *
 * The has* flags are a cheap text search in the glTF JSON chunk (they say a key is present, not
 * that the rig or animation is correct). Real quality checks (joint deformation, weights) are
 * not done here.
 */
data class GlbReport(
    val valid: Boolean,
    val problem: String? = null,
    val hasMaterials: Boolean = false,
    val hasSkin: Boolean = false,
    val hasAnimations: Boolean = false,
    val hasMorphTargets: Boolean = false,
)

/** Validates the GLB container header and reads the JSON chunk. Pure Kotlin, unit-testable. */
object GlbValidator {
    private const val MAGIC = 0x46546C67 // "glTF"
    private const val JSON_CHUNK = 0x4E4F534A // "JSON"
    private const val MAX_JSON_BYTES = 32 * 1024 * 1024
    private const val HEADER_BYTES = 20 // 12 file header + 8 first chunk header

    fun validateFile(file: File): GlbReport {
        if (!file.isFile) return invalid("الملف غير موجود")
        return try {
            file.inputStream().buffered().use { validateStream(it, file.length()) }
        } catch (e: java.io.IOException) {
            invalid("تعذّرت قراءة الملف")
        }
    }

    fun validateBytes(bytes: ByteArray): GlbReport =
        validateStream(ByteArrayInputStream(bytes), bytes.size.toLong())

    private fun validateStream(input: InputStream, totalLength: Long): GlbReport {
        val header = ByteArray(HEADER_BYTES)
        if (!readFully(input, header)) return invalid("الملف قصير جدًا")
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        if (buf.getInt(0) != MAGIC) return invalid("ليس ملف GLB")
        if (buf.getInt(4) != 2) return invalid("إصدار glTF غير مدعوم")
        if ((buf.getInt(8).toLong() and 0xFFFFFFFFL) != totalLength) {
            return invalid("طول الملف لا يطابق الترويسة")
        }
        val jsonLength = buf.getInt(12)
        if (buf.getInt(16) != JSON_CHUNK) return invalid("الجزء الأول ليس JSON")
        if (jsonLength <= 0 || jsonLength > MAX_JSON_BYTES || HEADER_BYTES.toLong() + jsonLength > totalLength) {
            return invalid("حجم جزء JSON غير صالح")
        }

        val json = ByteArray(jsonLength)
        if (!readFully(input, json)) return invalid("جزء JSON ناقص")
        val text = String(json, Charsets.UTF_8)
        return GlbReport(
            valid = true,
            hasMaterials = "\"materials\"" in text,
            hasSkin = "\"skins\"" in text,
            hasAnimations = "\"animations\"" in text,
            hasMorphTargets = "\"targets\"" in text,
        )
    }

    private fun invalid(problem: String) = GlbReport(valid = false, problem = problem)

    private fun readFully(input: InputStream, target: ByteArray): Boolean {
        var read = 0
        while (read < target.size) {
            val n = input.read(target, read, target.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }
}
