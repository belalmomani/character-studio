package app.characterstudio.editor

import app.characterstudio.editor.model.*
import org.junit.Assert.*
import org.junit.Test

class ViewportTest {
    @Test fun zoomKeepsScenePointUnderFingerFixed() {
        val vw = 800f; val vh = 1200f
        val vp = Viewport(zoom = 1.5f, panX = 30f, panY = -20f)
        val m1 = ViewMapping.of(vw, vh, 1024, 1024, vp)
        val cx = 300f; val cy = 400f
        val px = m1.toSceneX(cx); val py = m1.toSceneY(cy)
        val vp2 = vp.zoomAbout(cx, cy, vw / 2, vh / 2, 2f)
        val m2 = ViewMapping.of(vw, vh, 1024, 1024, vp2)
        assertEquals(px, m2.toSceneX(cx), 0.01f)
        assertEquals(py, m2.toSceneY(cy), 0.01f)
        assertEquals(3f, vp2.zoom, 0.001f)
    }

    @Test fun zoomIsClamped() {
        val vp = Viewport().zoomAbout(0f, 0f, 0f, 0f, 1000f)
        assertEquals(Viewport.MAX_ZOOM, vp.zoom, 0f)
        assertEquals(Viewport.MIN_ZOOM, Viewport().zoomAbout(0f, 0f, 0f, 0f, 0.0001f).zoom, 0f)
    }

    @Test fun viewportNeverChangesCanvasSize() {
        val s = Scene()
        Viewport(zoom = 5f, panX = 100f).panBy(5f, 5f)
        assertEquals(1024, s.canvasWidth)
    }
}
