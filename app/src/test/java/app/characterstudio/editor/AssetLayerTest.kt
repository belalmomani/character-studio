package app.characterstudio.editor

import app.characterstudio.editor.model.*
import org.junit.Assert.*
import org.junit.Test

class AssetLayerTest {
    private fun withAsset(w: Int = 600, h: Int = 300): Scene =
        SceneOps.addAssetLayer(Scene(), "L1", "رأس", "asset-1", 3, "asset-1_3.png", w, h)

    @Test fun largeImageIsScaledToHalfCanvasKeepingAspect() {
        val l = withAsset(2000, 1000).layers[0]
        assertEquals(LayerKind.Asset, l.kind)
        assertEquals(512f, l.transform.width, 0.01f)
        assertEquals(256f, l.transform.height, 0.01f)
        assertEquals(512f, l.transform.x, 0.01f)
        assertEquals(l.transform, l.home)
    }

    @Test fun smallImageKeepsNativeSize() {
        val l = withAsset(200, 100).layers[0]
        assertEquals(200f, l.transform.width, 0.01f); assertEquals(100f, l.transform.height, 0.01f)
    }

    @Test fun layerRemembersAssetIdentityAndFile() {
        val l = withAsset().layers[0]
        assertEquals("asset-1", l.assetId); assertEquals(3, l.assetVersion); assertEquals("asset-1_3.png", l.assetFile)
    }

    @Test fun assetLayerIsHitTestedAsRectangle() {
        val s = withAsset(200, 100)
        assertEquals("L1", SceneOps.hitTest(s, 512f + 95f, 512f + 45f))
        assertNull(SceneOps.hitTest(s, 512f + 110f, 512f))
    }

    @Test fun duplicateKeepsAssetFile() {
        val s = SceneOps.duplicateLayer(withAsset(), "L1", "L2", "نسخة")
        assertEquals("asset-1_3.png", s.layers[1].assetFile)
    }

    @Test fun jsonRoundTripKeepsAssetLayer() {
        val s = withAsset(900, 450)
        assertEquals(s, SceneJson.decode(SceneJson.encode(s)))
    }

    @Test fun oldSceneWithoutAssetFieldsStillDecodes() {
        val old = """{"version":1,"canvasWidth":1024,"canvasHeight":1024,"layers":[{"id":"a","name":"x","kind":"rect",
            "transform":{"x":1,"y":2,"width":30,"height":40},"home":{"x":1,"y":2,"width":30,"height":40}}]}"""
        val s = SceneJson.decode(old)
        assertNull(s.layers[0].assetFile)
        assertEquals(LayerKind.Rect, s.layers[0].kind)
    }
}
