package app.characterstudio.editor

import app.characterstudio.editor.model.*
import org.junit.Assert.*
import org.junit.Test

class SceneOpsTest {
    private fun base(): Scene {
        var s = Scene()
        s = SceneOps.addLayer(s, "a", "أ", LayerKind.Rect)
        s = SceneOps.addLayer(s, "b", "ب", LayerKind.Ellipse)
        s = SceneOps.addLayer(s, "c", "ج", LayerKind.Rect)
        return s
    }

    @Test fun addPlacesAtCanvasCenterOnTop() {
        val s = base()
        assertEquals(listOf("a", "b", "c"), s.layers.map { it.id })
        assertEquals(512f, s.layers[0].transform.x)
        assertEquals(s.layers[0].transform, s.layers[0].home)
    }

    @Test fun moveLayerChangesOrderAndClamps() {
        var s = SceneOps.moveLayer(base(), "a", +1)
        assertEquals(listOf("b", "a", "c"), s.layers.map { it.id })
        s = SceneOps.moveLayer(s, "c", +5)
        assertEquals(listOf("b", "a", "c"), s.layers.map { it.id })
        s = SceneOps.moveLayer(s, "c", -2)
        assertEquals(listOf("c", "b", "a"), s.layers.map { it.id })
    }

    @Test fun duplicateInsertsAboveSourceWithNewIdAndUnlocked() {
        var s = SceneOps.setLocked(base(), "a", true)
        s = SceneOps.duplicateLayer(s, "a", "a2", "أ نسخة")
        assertEquals(listOf("a", "a2", "b", "c"), s.layers.map { it.id })
        assertFalse(s.layers[1].locked)
        assertEquals(544f, s.layers[1].transform.x)
    }

    @Test fun lockedLayerIgnoresEdits() {
        var s = SceneOps.setLocked(base(), "a", true)
        val before = s
        s = SceneOps.setTransform(s, "a") { it.copy(x = 10f) }
        s = SceneOps.edit(s, "a") { it.copy(opacity = 0.1f) }
        assertEquals(before, s)
    }

    @Test fun hiddenAndLockedStateCanStillBeToggledWhileLocked() {
        var s = SceneOps.setLocked(base(), "a", true)
        s = SceneOps.setVisible(s, "a", false)
        assertFalse(s.layers[0].visible)
        s = SceneOps.setLocked(s, "a", false)
        assertFalse(s.layers[0].locked)
    }

    @Test fun sizeIsClampedAndRotationNormalized() {
        var s = SceneOps.setTransform(base(), "a") { it.copy(width = 1f, height = 99999f, rotation = 270f) }
        val t = s.layers[0].transform
        assertEquals(SceneOps.MIN_SIZE, t.width)
        assertEquals(SceneOps.MAX_SIZE, t.height)
        assertEquals(-90f, t.rotation, 0.001f)
    }

    @Test fun resetLayerRestoresHome() {
        var s = SceneOps.setTransform(base(), "a") { it.copy(x = 50f, rotation = 30f, width = 100f) }
        s = SceneOps.resetLayer(s, "a")
        assertEquals(s.layers[0].home, s.layers[0].transform)
    }

    @Test fun renameRejectsBlank() {
        val s = base()
        assertEquals(s, SceneOps.rename(s, "a", "   "))
        assertEquals("جديد", SceneOps.rename(s, "a", " جديد ").layers[0].name)
    }

    @Test fun hitTestRespectsOrderVisibilityAndRotation() {
        var s = base() // كلها في المركز؛ c في الأعلى
        assertEquals("c", SceneOps.hitTest(s, 512f, 512f))
        s = SceneOps.setVisible(s, "c", false)
        assertEquals("b", SceneOps.hitTest(s, 512f, 512f))
        assertNull(SceneOps.hitTest(s, 5f, 5f))
        // مستطيل 320x200 مُدار 90°: يصبح طوله العمودي 320
        var r = SceneOps.addLayer(Scene(), "r", "r", LayerKind.Rect)
        r = SceneOps.setTransform(r, "r") { it.copy(rotation = 90f) }
        assertEquals("r", SceneOps.hitTest(r, 512f, 512f + 150f))
        assertNull(SceneOps.hitTest(r, 512f + 150f, 512f))
    }

    @Test fun ellipseCornerIsOutside() {
        val s = SceneOps.addLayer(Scene(), "e", "e", LayerKind.Ellipse) // 256x256
        assertNull(SceneOps.hitTest(s, 512f + 120f, 512f + 120f))
        assertEquals("e", SceneOps.hitTest(s, 512f + 100f, 512f))
    }

    @Test fun defaultSkeletonHasRequiredAnchors() {
        val ids = Scene().anchors.map { it.id }.toSet()
        listOf("head_center","head_base","neck","shoulder_r","shoulder_l","elbow_r","elbow_l","wrist_r","wrist_l",
            "hand_r","hand_l","torso_top","torso_bottom","hip_r","hip_l","knee_r","knee_l","ankle_r","ankle_l",
            "weapon_r","weapon_l","accessory_head").forEach { assertTrue(it, it in ids) }
        assertEquals(ids.size, Scene().anchors.size)
        DefaultSkeleton.bones.forEach { (a, b) -> assertTrue(a in ids && b in ids) }
    }

    @Test fun snapAttachesAndAnchorMoveDragsAttachedLayers() {
        var s = SceneOps.addLayer(Scene(), "w", "w", LayerKind.Rect)
        s = SceneOps.setTransform(s, "w") { it.copy(x = 350f, y = 630f) }
        s = SceneOps.snapToNearestAnchor(s, "w")
        assertEquals("weapon_r", s.layers[0].attachedAnchorId)
        assertEquals(340f, s.layers[0].transform.x)
        s = SceneOps.moveAnchorBy(s, "weapon_r", 10f, -20f)
        assertEquals(350f, s.layers[0].transform.x)
        assertEquals(620f, s.layers[0].transform.y)
        assertEquals("weapon_r", s.layers[0].attachedAnchorId)
        // تحريك العنصر يدويًا يفك الارتباط
        s = SceneOps.setTransform(s, "w") { it.copy(x = 400f) }
        assertNull(s.layers[0].attachedAnchorId)
    }

    @Test fun lockedAttachedLayerDoesNotFollowAnchor() {
        var s = SceneOps.addLayer(Scene(), "w", "w", LayerKind.Rect)
        s = SceneOps.snapToNearestAnchor(SceneOps.setTransform(s, "w") { it.copy(x = 340f, y = 640f) }, "w")
        s = SceneOps.setLocked(s, "w", true)
        s = SceneOps.moveAnchorBy(s, "weapon_r", 30f, 0f)
        assertEquals(340f, s.layers[0].transform.x)
    }

    @Test fun resetAnchorsRestoresDefaultsAndMovesAttachedLayersBack() {
        var s = SceneOps.addLayer(Scene(), "w", "w", LayerKind.Rect)
        s = SceneOps.snapToNearestAnchor(SceneOps.setTransform(s, "w") { it.copy(x = 340f, y = 640f) }, "w")
        s = SceneOps.moveAnchorBy(s, "weapon_r", 50f, 50f)
        s = SceneOps.resetAnchors(s)
        assertEquals(DefaultSkeleton.anchors(), s.anchors)
        assertEquals(340f, s.layers[0].transform.x)
        assertEquals(640f, s.layers[0].transform.y)
    }

    @Test fun anchorsStayInsideCanvas() {
        val s = SceneOps.moveAnchorBy(Scene(), "neck", -5000f, 5000f)
        val a = s.anchors.first { it.id == "neck" }
        assertEquals(0f, a.x); assertEquals(1024f, a.y)
    }
}
