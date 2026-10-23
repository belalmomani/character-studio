package app.characterstudio.editor.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import app.characterstudio.editor.model.DefaultSkeleton
import app.characterstudio.editor.model.Layer
import app.characterstudio.editor.model.LayerKind
import app.characterstudio.editor.model.Scene

data class RenderOptions(
    val showSkeleton: Boolean,
    val showAnchors: Boolean,
    val showChecker: Boolean = false,
    val selectedLayerId: String? = null,
    /** طول بكسل شاشة واحد بوحدات اللوحة (1/scale) لرسم خطوط بسمك ثابت على الشاشة. */
    val unit: Float = 1f,
    /** يوفّر صورة طبقة الأصل (أو null إن لم تُحمَّل بعد: يُرسم مستطيل بديل). */
    val images: (Layer) -> ImageBitmap? = { null },
)

/**
 * رسم المشهد بوحدات اللوحة. المحرر والتصدير (المرحلة 5) يستخدمان هذه الدالة نفسها
 * لضمان تطابق المعاينة مع الملف المصدَّر. المستدعي يضبط التحويل (translate/scale).
 */
fun DrawScope.drawScene(scene: Scene, o: RenderOptions) {
    val w = scene.canvasWidth.toFloat()
    val h = scene.canvasHeight.toFloat()
    val bg = scene.backgroundArgb
    if (bg != null) {
        drawRect(Color(bg), Offset.Zero, Size(w, h))
    } else if (o.showChecker) {
        val cell = 64f
        var yi = 0
        var y = 0f
        while (y < h) {
            var x = 0f
            var xi = 0
            while (x < w) {
                drawRect(if ((xi + yi) % 2 == 0) Color(0xFFF2F2F2) else Color(0xFFDADADA), Offset(x, y), Size(cell, cell))
                x += cell; xi++
            }
            y += cell; yi++
        }
    }

    for (layer in scene.layers) if (layer.visible) drawLayer(layer, o)

    if (o.showSkeleton) {
        val byId = scene.anchors.associateBy { it.id }
        val bone = Color(0xFFE91E63).copy(alpha = 0.55f)
        for ((a, b) in DefaultSkeleton.bones) {
            val pa = byId[a] ?: continue
            val pb = byId[b] ?: continue
            drawLine(bone, Offset(pa.x, pa.y), Offset(pb.x, pb.y), strokeWidth = 3f * o.unit)
        }
    }
    if (o.showAnchors) {
        for (a in scene.anchors) {
            drawCircle(Color(0xFFFFFFFF), 9f * o.unit, Offset(a.x, a.y))
            drawCircle(Color(0xFFE91E63), 6f * o.unit, Offset(a.x, a.y))
        }
    }

    scene.layers.firstOrNull { it.id == o.selectedLayerId }?.let { l ->
        val t = l.transform
        rotate(t.rotation, Offset(t.x, t.y)) {
            drawRect(
                Color(0xFF1E88E5), Offset(t.x - t.width / 2, t.y - t.height / 2), Size(t.width, t.height),
                style = Stroke(width = 2.5f * o.unit),
            )
        }
    }
}

private fun DrawScope.drawLayer(l: Layer, o: RenderOptions) {
    val t = l.transform
    val color = Color(l.colorArgb)
    rotate(t.rotation, Offset(t.x, t.y)) {
        val tl = Offset(t.x - t.width / 2, t.y - t.height / 2)
        val size = Size(t.width, t.height)
        when (l.kind) {
            LayerKind.Ellipse -> drawOval(color, tl, size, alpha = l.opacity)
            LayerKind.Rect -> drawRect(color, tl, size, alpha = l.opacity)
            LayerKind.Asset -> {
                val img = o.images(l)
                if (img != null) {
                    withTransform({
                        translate(tl.x, tl.y)
                        scale(t.width / img.width, t.height / img.height, Offset.Zero)
                    }) { drawImage(img, Offset.Zero, alpha = l.opacity) }
                } else {
                    // صورة الأصل غير متاحة محليًا: نُظهر مكانها بدل إخفائه بصمت
                    drawRect(Color(0x33808080), tl, size)
                    drawRect(Color(0xFF808080), tl, size, style = Stroke(width = 2f * o.unit))
                }
            }
        }
    }
}
