package app.characterstudio.editor.model

import kotlin.math.min

/** عرض مساحة العمل (تكبير/تحريك) — منفصل تمامًا عن أبعاد المشروع ولا يُحفظ فيها. */
data class Viewport(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f) {
    fun panBy(dx: Float, dy: Float) = copy(panX = panX + dx, panY = panY + dy)

    /** يكبّر حول نقطة على الشاشة بحيث تبقى نقطة المشهد تحتها ثابتة. */
    fun zoomAbout(cx: Float, cy: Float, viewCx: Float, viewCy: Float, factor: Float): Viewport {
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val r = newZoom / zoom
        return Viewport(
            zoom = newZoom,
            panX = cx - viewCx - (cx - viewCx - panX) * r,
            panY = cy - viewCy - (cy - viewCy - panY) * r,
        )
    }

    companion object {
        const val MIN_ZOOM = 0.3f
        const val MAX_ZOOM = 8f
    }
}

class ViewMapping(val scale: Float, val ox: Float, val oy: Float) {
    fun toSceneX(x: Float) = (x - ox) / scale
    fun toSceneY(y: Float) = (y - oy) / scale

    companion object {
        fun of(viewW: Float, viewH: Float, canvasW: Int, canvasH: Int, vp: Viewport): ViewMapping {
            val base = min(viewW / canvasW, viewH / canvasH) * 0.92f
            val s = base * vp.zoom
            return ViewMapping(s, viewW / 2f + vp.panX - canvasW / 2f * s, viewH / 2f + vp.panY - canvasH / 2f * s)
        }
    }
}
