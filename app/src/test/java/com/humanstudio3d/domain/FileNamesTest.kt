package com.humanstudio3d.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {
    @Test
    fun spacesBecomeUnderscores() = assertEquals("my_char", FileNames.sanitize("my char"))

    @Test
    fun invalidCharactersAreRemoved() = assertEquals("a_b_c", FileNames.sanitize("a/b:c"))

    @Test
    fun existingExtensionIsNotDuplicated() {
        assertEquals("x", FileNames.sanitize("x.glb"))
        assertEquals("Model", FileNames.sanitize("Model.GLB"))
        assertEquals("x.glb", FileNames.glbFileName("x.glb"))
    }

    @Test
    fun emptyOrDotsFallBack() {
        assertEquals("character", FileNames.sanitize(""))
        assertEquals("character", FileNames.sanitize("   "))
        assertEquals("character", FileNames.sanitize("..."))
        assertEquals("character.glb", FileNames.glbFileName("///"))
    }

    @Test
    fun longNamesAreTruncated() {
        assertEquals(60, FileNames.sanitize("a".repeat(100)).length)
    }

    @Test
    fun arabicNamesAreKept() = assertEquals("شخصيتي_الأولى", FileNames.sanitize("شخصيتي الأولى"))
}
