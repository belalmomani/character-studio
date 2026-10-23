package app.characterstudio.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudinaryUrlsTest {
    @Test fun originalUrlHasNoTransformation() {
        assertEquals("https://res.cloudinary.com/demo/image/upload/characterstudio/assets/abc-123.png",
            CloudinaryUrls.original("demo", "characterstudio/assets/abc-123", "png"))
    }

    @Test fun thumbUrlUsesWebpAndClampsSize() {
        assertEquals("https://res.cloudinary.com/demo/image/upload/c_limit,w_256,h_256,f_webp,q_auto/a/b",
            CloudinaryUrls.thumb("demo", "a/b"))
        assertTrue(CloudinaryUrls.thumb("demo", "a/b", 99999).contains("w_1024,h_1024"))
    }

    @Test fun rejectsInjectionInIdsAndCloudName() {
        listOf("a b", "a?x=1", "../etc", "a/../b", "a#b", "").forEach { bad ->
            assertTrue(bad, runCatching { CloudinaryUrls.original("demo", bad, "png") }.isFailure)
        }
        assertTrue(runCatching { CloudinaryUrls.original("de mo", "a/b", "png") }.isFailure)
        assertTrue(runCatching { CloudinaryUrls.original("demo", "a/b", "svg") }.isFailure)
    }

    @Test fun fileNamesAreSafe() {
        assertEquals("1234-abcd_2.png", AssetFileNames.of("1234-abcd", 2, "png"))
        assertEquals("___etc_passwd_1.webp", AssetFileNames.of("../etc/passwd", 1, "webp"))
        assertTrue(runCatching { AssetFileNames.of("x", 0, "png") }.isFailure)
        assertTrue(runCatching { AssetFileNames.of("x", 1, "gif") }.isFailure)
    }

    @Test fun safeExistingAcceptsOnlyPlainNames() {
        assertEquals("a-1_2.png", AssetFileNames.safeExisting("a-1_2.png"))
        listOf("../x.png", "a/b.png", "a.png.exe", "a.gif", "").forEach { bad ->
            assertTrue(bad, runCatching { AssetFileNames.safeExisting(bad) }.isFailure)
        }
    }
}
