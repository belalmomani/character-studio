package com.humanstudio3d.domain

import java.io.InputStream
import java.io.OutputStream

/** Byte-exact stream helpers used by export so the written size can be verified. */
object StreamCopy {
    private const val BUFFER_BYTES = 64 * 1024

    /** Copies everything and returns the number of bytes written. Flushes but does not close. */
    fun copy(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
        }
        output.flush()
        return total
    }

    /** Reads the whole stream and returns its length in bytes. */
    fun count(input: InputStream): Long {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
        }
        return total
    }
}
