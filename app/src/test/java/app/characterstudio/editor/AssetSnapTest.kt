package app.characterstudio.editor

import app.characterstudio.editor.model.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class AssetSnapTest {
    private fun layerScene(imgW: Int, imgH: Int, w: Float, h: Float, rot: Float, anchors: List<AssetAnchor>): Scene {
        var s = SceneOps.addAssetLayer(Scene(), "L", "x", "a", 1, "a_1.png", imgW, imgH, anchors)
        s = SceneOps.setTransform(s, "L") { it.copy(width = w, height = h, rotation = rot) }
        return s
    }

    /** الإسقاط الأمامي المستقل: أين تقع نقطة الصورة (px) في إحداثيات اللوحة بعد التحويل؟ */
    private fun worldOf(l: Layer, ax: Float, ay: Float): Pair<Float, Float> {
        val t = l.transform
        val lx = (ax / l.srcWidth!! - 0.5f) * t.width
        val ly = (ay / l.srcHeight!! - 0.5f) * t.height
        val r = t.rotation * PI.toFloat() / 180f
        return (t.x + lx * cos(r) - ly * sin(r)) to (t.y + lx * sin(r) + ly * cos(r))
    }

    private fun near(a: Float, b: Float, eps: Float = 0.01f) = abs(a - b) <= eps

    @Test fun anchorAtImageCenterPutsLayerCenterOnTarget() {
        val s = layerScene(200, 100, 200f, 100f, 0f, listOf(AssetAnchor("neck", 100f, 50f)))
        val out = SceneOps.snapAssetAnchor(s, "L", "neck")
        val neck = out.anchors.first { it.id == "neck" }
        assertTrue(near(out.layers[0].transform.x, neck.x)); assertTrue(near(out.layers[0].transform.y, neck.y))
        assertEquals("neck", out.layers[0].attachedAnchorId)
    }

    @Test fun cornerAnchorShiftsCenterByHalfSize() {
        val s = layerScene(200, 100, 200f, 100f, 0f, listOf(AssetAnchor("neck", 0f, 0f)))
        val out = SceneOps.snapAssetAnchor(s, "L", "neck")
        val neck = out.anchors.first { it.id == "neck" }
        assertTrue(near(out.layers[0].transform.x, neck.x + 100f)); assertTrue(near(out.layers[0].transform.y, neck.y + 50f))
    }

    @Test fun scaleIsAccountedFor() {
        // الصورة 400x200 معروضة 200x100: النقطة (100,50) تقع على (-50,-25) من المركز
        val s = layerScene(400, 200, 200f, 100f, 0f, listOf(AssetAnchor("hand_r", 100f, 50f)))
        val out = SceneOps.snapAssetAnchor(s, "L", "hand_r")
        val t = out.anchors.first { it.id == "hand_r" }
        assertTrue(near(out.layers[0].transform.x, t.x + 50f)); assertTrue(near(out.layers[0].transform.y, t.y + 25f))
    }

    @Test fun rotationIsAccountedFor() {
        val s = layerScene(400, 200, 200f, 100f, 90f, listOf(AssetAnchor("hand_r", 100f, 50f)))
        val out = SceneOps.snapAssetAnchor(s, "L", "hand_r")
        val t = out.anchors.first { it.id == "hand_r" }
        // الإزاحة (-50,-25) مدوّرة 90° باتجاه عقارب الساعة = (25,-50) → المركز = الهدف - الإزاحة
        assertTrue(near(out.layers[0].transform.x, t.x - 25f)); assertTrue(near(out.layers[0].transform.y, t.y + 50f))
        assertEquals(90f, out.layers[0].transform.rotation, 0.001f)
    }

    @Test fun forwardProjectionLandsExactlyOnSkeletonAnchor() {
        for (rot in listOf(-135f, -30f, 0f, 45f, 120f)) {
            val s = layerScene(512, 768, 180f, 270f, rot, listOf(AssetAnchor("shoulder_l", 123f, 456f)))
            val out = SceneOps.snapAssetAnchor(s, "L", "shoulder_l")
            val tgt = out.anchors.first { it.id == "shoulder_l" }
            val (wx, wy) = worldOf(out.layers[0], 123f, 456f)
            assertTrue("rot=$rot", near(wx, tgt.x) && near(wy, tgt.y))
        }
    }

    @Test fun sizeAndRotationAreUnchanged() {
        val s = layerScene(200, 100, 150f, 75f, 33f, listOf(AssetAnchor("neck", 10f, 10f)))
        val before = s.layers[0].transform
        val after = SceneOps.snapAssetAnchor(s, "L", "neck").layers[0].transform
        assertEquals(before.width, after.width, 0f); assertEquals(before.height, after.height, 0f)
        assertEquals(before.rotation, after.rotation, 0.001f)
    }

    @Test fun layerFollowsSkeletonAnchorAfterSnap() {
        val snapped = SceneOps.snapAssetAnchor(layerScene(200, 100, 200f, 100f, 0f, listOf(AssetAnchor("neck", 100f, 50f))), "L", "neck")
        val moved = SceneOps.moveAnchorBy(snapped, "neck", 10f, -5f)
        val neck = moved.anchors.first { it.id == "neck" }
        assertTrue(near(moved.layers[0].transform.x, neck.x)); assertTrue(near(moved.layers[0].transform.y, neck.y))
    }

    @Test fun ignoredWhenNotApplicable() {
        val s = layerScene(200, 100, 200f, 100f, 0f, listOf(AssetAnchor("neck", 100f, 50f), AssetAnchor("custom_only", 1f, 1f)))
        assertEquals(s, SceneOps.snapAssetAnchor(s, "L", "nope"))               // معرّف غير موجود في الأصل
        assertEquals(s, SceneOps.snapAssetAnchor(s, "L", "custom_only"))        // لا مقابل له في الهيكل
        assertEquals(s, SceneOps.snapAssetAnchor(s, "missing-layer", "neck"))
        val locked = SceneOps.setLocked(s, "L", true)
        assertEquals(locked, SceneOps.snapAssetAnchor(locked, "L", "neck"))     // المقفل لا يتحرك
        val plain = SceneOps.addLayer(Scene(), "R", "r", LayerKind.Rect)
        assertEquals(plain, SceneOps.snapAssetAnchor(plain, "R", "neck"))
    }

    @Test fun matchingAnchorsListOnlyThoseInSkeleton() {
        val s = layerScene(200, 100, 200f, 100f, 0f, listOf(AssetAnchor("neck", 1f, 1f), AssetAnchor("zzz", 2f, 2f)))
        assertEquals(listOf("neck"), SceneOps.matchingAnchors(s, s.layers[0]).map { it.id })
    }

    @Test fun parsingServerAnchorsIsTolerant() {
        val ok = AssetAnchors.parse("""[{"id":"neck","x":120,"y":40.5},{"id":"neck","x":1,"y":1},{"id":"","x":1,"y":1},{"id":"a","x":-3,"y":1}]""")
        assertEquals(listOf("neck"), ok.map { it.id }); assertEquals(120f, ok[0].x, 0f); assertEquals(40.5f, ok[0].y, 0f)
        assertTrue(AssetAnchors.parse("not json").isEmpty()); assertTrue(AssetAnchors.parse("{}").isEmpty()); assertTrue(AssetAnchors.parse("[]").isEmpty())
    }

    @Test fun anchorsSurviveSaveAndReopen() {
        val s = layerScene(200, 100, 200f, 100f, 0f, listOf(AssetAnchor("neck", 100f, 50f)))
        val back = SceneJson.decode(SceneJson.encode(s))
        assertEquals(s, back); assertEquals(200, back.layers[0].srcWidth); assertEquals("neck", back.layers[0].assetAnchors[0].id)
    }
}
