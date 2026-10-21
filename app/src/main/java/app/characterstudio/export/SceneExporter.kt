package app.characterstudio.export

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.characterstudio.editor.model.Scene
import app.characterstudio.editor.render.RenderOptions
import app.characterstudio.editor.render.drawScene
import java.io.OutputStream

/** يرسم المشهد بنفس دالة drawScene المستخدمة في المحرر، فتتطابق الطبقات والألوان والمواضع. */
object SceneExporter {
    fun render(scene: Scene, options: ExportOptions): Bitmap {
        val size = options.size
        val bg = ExportRules.effectiveBackground(options, scene.backgroundArgb)
        val image = ImageBitmap(size, size, ImageBitmapConfig.Argb8888, hasAlpha = true)
        val scale = size.toFloat() / scene.canvasWidth
        // الخلفية تُرسم هنا بدل scene.backgroundArgb لتخضع لقواعد الصيغة (JPEG بلا شفافية)
        val drawn = scene.copy(backgroundArgb = bg)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(size.toFloat(), size.toFloat())) {
            withTransform({ scale(scale, scale, Offset.Zero) }) {
                drawScene(
                    drawn,
                    RenderOptions(
                        showSkeleton = options.includeSkeleton,
                        showAnchors = options.includeSkeleton,
                        showChecker = false,
                        selectedLayerId = null,
                        unit = 1f / scale,
                    ),
                )
            }
        }
        return image.asAndroidBitmap()
    }

    fun encode(bitmap: Bitmap, options: ExportOptions, out: OutputStream): Boolean {
        val format = when (options.format) {
            ExportFormat.Png -> Bitmap.CompressFormat.PNG
            ExportFormat.Jpeg -> Bitmap.CompressFormat.JPEG
            ExportFormat.Webp ->
                if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
                else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }
        return bitmap.compress(format, options.quality, out)
    }
}
