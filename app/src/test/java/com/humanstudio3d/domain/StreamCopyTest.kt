package com.humanstudio3d.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class StreamCopyTest {
    @Test
    fun copyIsByteExactAndReturnsLength() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        val written = StreamCopy.copy(ByteArrayInputStream(data), out)
        assertEquals(data.size.toLong(), written)
        assertArrayEquals(data, out.toByteArray())
    }

    @Test
    fun countMatchesLength() {
        assertEquals(12345L, StreamCopy.count(ByteArrayInputStream(ByteArray(12345))))
    }

    @Test
    fun emptyStream() {
        assertEquals(0L, StreamCopy.copy(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream()))
    }
}
