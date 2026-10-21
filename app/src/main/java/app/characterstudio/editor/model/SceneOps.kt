package app.characterstudio.editor.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** عمليات نقية على المشهد (لا حالة، قابلة للاختبار). العنصر المقفل لا تتغير خصائصه عبر edit/setTransform. */
object SceneOps {
    const val MIN_SIZE = 16f
    const val MAX_SIZE = 4096f

    fun addLayer(s: Scene, id: String, name: String, kind: LayerKind): Scene {
        val (w, h) = if (kind == LayerKind.Ellipse) 256f to 256f else 320f to 200f
        val t = Transform(s.canvasWidth / 2f, s.canvasHeight / 2f, w, h, 0f)
        return s.copy(layers = s.layers + Layer(id = id, name = name, kind = kind, transform = t, home = t))
    }

    fun deleteLayer(s: Scene, id: String) = s.copy(layers = s.layers.filterNot { it.id == id })

    fun duplicateLayer(s: Scene, id: String, newId: String, name: String): Scene {
        val idx = s.layers.indexOfFirst { it.id == id }
        if (idx < 0) return s
        val src = s.layers[idx]
        val moved = src.transform.copy(x = src.transform.x + 32f, y = src.transform.y + 32f)
        val copy = src.copy(id = newId, name = name, transform = moved, home = moved, locked = false, attachedAnchorId = null)
        return s.copy(layers = s.layers.toMutableList().also { it.add(idx + 1, copy) })
    }

    private fun editAlways(s: Scene, id: String, f: (Layer) -> Layer) =
        s.copy(layers = s.layers.map { if (it.id == id) f(it) else it })

    fun edit(s: Scene, id: String, f: (Layer) -> Layer) =
        s.copy(layers = s.layers.map { if (it.id == id && !it.locked) f(it) else it })

    fun rename(s: Scene, id: String, name: String): Scene {
        val n = name.trim()
        return if (n.isEmpty() || n.length > 60) s else editAlways(s, id) { it.copy(name = n) }
    }

    fun setVisible(s: Scene, id: String, v: Boolean) = editAlways(s, id) { it.copy(visible = v) }
    fun setLocked(s: Scene, id: String, v: Boolean) = editAlways(s, id) { it.copy(locked = v) }

    /** delta > 0 = نحو الأمام (فهرس أكبر). */
    fun moveLayer(s: Scene, id: String, delta: Int): Scene {
        val from = s.layers.indexOfFirst { it.id == id }
        if (from < 0) return s
        val to = (from + delta).coerceIn(0, s.layers.lastIndex)
        if (to == from) return s
        val list = s.layers.toMutableList()
        list.add(to, list.removeAt(from))
        return s.copy(layers = list)
    }

    private fun normalize(r: Float): Float = ((r + 180f) % 360f + 360f) % 360f - 180f

    fun setTransform(s: Scene, id: String, f: (Transform) -> Transform): Scene = edit(s, id) { l ->
        val n = f(l.transform)
        val clamped = n.copy(
            width = n.width.coerceIn(MIN_SIZE, MAX_SIZE),
            height = n.height.coerceIn(MIN_SIZE, MAX_SIZE),
            rotation = normalize(n.rotation),
        )
        val moved = clamped.x != l.transform.x || clamped.y != l.transform.y
        l.copy(transform = clamped, attachedAnchorId = if (moved) null else l.attachedAnchorId)
    }

    fun resetLayer(s: Scene, id: String) = edit(s, id) { it.copy(transform = it.home, attachedAnchorId = null) }

    fun nearestAnchor(s: Scene, x: Float, y: Float, radius: Float = Float.MAX_VALUE): Anchor? =
        s.anchors.map { it to dist(it.x, it.y, x, y) }.filter { it.second <= radius }.minByOrNull { it.second }?.first

    /** يضع مركز العنصر على أقرب نقطة تثبيت ويسجّل الارتباط بها. */
    fun snapToNearestAnchor(s: Scene, id: String): Scene {
        val layer = s.layers.firstOrNull { it.id == id } ?: return s
        val a = nearestAnchor(s, layer.transform.x, layer.transform.y) ?: return s
        val moved = setTransform(s, id) { it.copy(x = a.x, y = a.y) }
        return edit(moved, id) { it.copy(attachedAnchorId = a.id) }
    }

    /** تحريك نقطة تثبيت؛ العناصر غير المقفلة المرتبطة بها تتحرك معها. */
    fun moveAnchorBy(s: Scene, anchorId: String, dx: Float, dy: Float): Scene {
        val a = s.anchors.firstOrNull { it.id == anchorId } ?: return s
        val nx = (a.x + dx).coerceIn(0f, s.canvasWidth.toFloat())
        val ny = (a.y + dy).coerceIn(0f, s.canvasHeight.toFloat())
        val ddx = nx - a.x
        val ddy = ny - a.y
        return s.copy(
            anchors = s.anchors.map { if (it.id == anchorId) it.copy(x = nx, y = ny) else it },
            layers = s.layers.map {
                if (it.attachedAnchorId == anchorId && !it.locked)
                    it.copy(transform = it.transform.copy(x = it.transform.x + ddx, y = it.transform.y + ddy))
                else it
            },
        )
    }

    fun resetAnchors(s: Scene) = s.copy(anchors = DefaultSkeleton.anchors(), layers = s.layers.map {
        // العناصر المرتبطة تعود مع نقطتها إلى موضعها الأصلي
        val def = DefaultSkeleton.anchors().firstOrNull { a -> a.id == it.attachedAnchorId }
        val old = s.anchors.firstOrNull { a -> a.id == it.attachedAnchorId }
        if (def != null && old != null && !it.locked)
            it.copy(transform = it.transform.copy(x = it.transform.x + def.x - old.x, y = it.transform.y + def.y - old.y))
        else it
    })

    /** أعلى عنصر ظاهر تحت النقطة (يشمل المقفل للتحديد). */
    fun hitTest(s: Scene, x: Float, y: Float): String? {
        for (l in s.layers.asReversed()) {
            if (!l.visible) continue
            val t = l.transform
            val rad = -t.rotation * PI.toFloat() / 180f
            val dx = x - t.x
            val dy = y - t.y
            val lx = dx * cos(rad) - dy * sin(rad)
            val ly = dx * sin(rad) + dy * cos(rad)
            val hw = t.width / 2f
            val hh = t.height / 2f
            val inside = when (l.kind) {
                LayerKind.Rect -> abs(lx) <= hw && abs(ly) <= hh
                LayerKind.Ellipse -> (lx / hw) * (lx / hw) + (ly / hh) * (ly / hh) <= 1f
            }
            if (inside) return l.id
        }
        return null
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }
}
