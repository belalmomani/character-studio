package app.characterstudio.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportRulesTest {
    private val mb = 1024L * 1024

    @Test fun supportedSizesDependOnHeap() {
        assertEquals(listOf(1024), ExportRules.supportedSizes(32 * mb))
        assertEquals(listOf(1024, 2048), ExportRules.supportedSizes(128 * mb))
        assertEquals(listOf(1024, 2048, 4096), ExportRules.supportedSizes(512 * mb))
    }

    @Test fun sanitizedFallsBackToSupportedSizeAndClampsQuality() {
        val o = ExportRules.sanitized(ExportOptions(size = 4096, quality = 500), 32 * mb)
        assertEquals(1024, o.size)
        assertEquals(100, o.quality)
        assertEquals(1, ExportRules.sanitized(ExportOptions(quality = -3), 512 * mb).quality)
    }

    @Test fun transparentPngHasNoBackground() {
        assertNull(ExportRules.effectiveBackground(ExportOptions(ExportFormat.Png, transparent = true), null))
        assertNull(ExportRules.effectiveBackground(ExportOptions(ExportFormat.Webp, transparent = true), null))
    }

    @Test fun jpegAlwaysGetsOpaqueBackground() {
        assertEquals(0xFFFFFFFFL, ExportRules.effectiveBackground(ExportOptions(ExportFormat.Jpeg, transparent = true), null))
        assertEquals(0xFF112233L, ExportRules.effectiveBackground(
            ExportOptions(ExportFormat.Jpeg, backgroundArgb = 0xFF112233), null))
    }

    @Test fun nonTransparentPngUsesChosenColor() {
        assertEquals(0xFF00FF00L, ExportRules.effectiveBackground(
            ExportOptions(ExportFormat.Png, transparent = false, backgroundArgb = 0xFF00FF00), null))
    }

    @Test fun savedSceneBackgroundWins() {
        assertEquals(0xFFABCDEFL, ExportRules.effectiveBackground(ExportOptions(transparent = true), 0xFFABCDEF))
    }

    @Test fun fileNameIsSafe() {
        assertEquals("بطل_رائع_2048.webp", ExportRules.fileName(" بطل رائع ", ExportOptions(ExportFormat.Webp, size = 2048)))
        assertEquals("a_b_1024.png", ExportRules.fileName("a/b", ExportOptions()))
        assertEquals("character_1024.jpg", ExportRules.fileName("   ", ExportOptions(ExportFormat.Jpeg)))
        assertEquals(40 + "_1024.png".length, ExportRules.fileName("x".repeat(100), ExportOptions()).length)
    }
}
