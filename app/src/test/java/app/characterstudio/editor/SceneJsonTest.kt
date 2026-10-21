package app.characterstudio.editor

import app.characterstudio.editor.model.*
import org.junit.Assert.*
import org.junit.Test

class SceneJsonTest {
    @Test fun emptyDocumentDecodesToNewSceneWithSkeleton() {
        assertEquals(Scene(), SceneJson.decode("{}"))
        assertEquals(Scene(), SceneJson.decode(""))
        assertEquals(24, SceneJson.decode("{}").anchors.size)
    }

    @Test fun roundTripKeepsLayerOrderAnchorsAndFlags() {
        var s = Scene()
        s = SceneOps.addLayer(s, "a", "أ", LayerKind.Rect)
        s = SceneOps.addLayer(s, "b", "ب", LayerKind.Ellipse)
        s = SceneOps.moveLayer(s, "a", +1)
        s = SceneOps.setTransform(s, "b") { it.copy(x = 123.5f, rotation = 45f, width = 77f) }
        s = SceneOps.edit(s, "b") { it.copy(opacity = 0.4f, colorArgb = 0xFFE53935) }
        s = SceneOps.setVisible(s, "a", false)
        s = SceneOps.setLocked(s, "a", true)
        s = SceneOps.moveAnchorBy(s, "neck", 7f, 9f)
        s = s.copy(showSkeleton = false, backgroundArgb = 0xFFFFFFFF)
        val back = SceneJson.decode(SceneJson.encode(s))
        assertEquals(s, back)
        assertEquals(listOf("b", "a"), back.layers.map { it.id })
    }

    @Test fun unknownKeysAreIgnored() {
        val text = SceneJson.encode(Scene()).replaceFirst("{", "{\"futureField\":123,")
        assertEquals(Scene(), SceneJson.decode(text))
    }

    @Test fun newerVersionIsRejectedNotSilentlyDowngraded() {
        val text = SceneJson.encode(Scene(version = SCENE_VERSION + 1))
        val e = runCatching { SceneJson.decode(text) }.exceptionOrNull()
        assertTrue(e is UnsupportedSceneVersion)
    }

    @Test fun garbageThrows() {
        assertTrue(runCatching { SceneJson.decode("not json") }.isFailure)
    }
}
