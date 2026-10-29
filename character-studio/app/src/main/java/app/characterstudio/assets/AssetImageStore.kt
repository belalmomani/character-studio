package app.characterstudio.assets

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * يفك صور الأصول المحفوظة محليًا. المحرر يستخدم ذاكرة مؤقتة محدودة (peek/load)؛
 * التصدير يفك بلا ذاكرة مؤقتة (decodeUncached) ثم يحرر الصور بنفسه.
 */
class AssetImageStore(private val files: AssetFiles) {
    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val _revision = MutableStateFlow(0)
    /** يزيد كلما اكتمل تحميل صورة جديدة، ليعيد المحرر الرسم. */
    val revision: StateFlow<Int> = _revision

    private fun key(file: String, maxSide: Int) = "$file@$maxSide"

    fun peek(file: String, maxSide: Int): ImageBitmap? = cache.get(key(file, maxSide))

    suspend fun load(file: String, maxSide: Int): ImageBitmap? {
        cache.get(key(file, maxSide))?.let { return it }
        val bmp = decodeUncached(file, maxSide) ?: return null
        cache.put(key(file, maxSide), bmp)
        _revision.value = _revision.value + 1
        return bmp
    }

    suspend fun decodeUncached(file: String, maxSide: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val f = files.fileOf(file)
            if (!f.exists()) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()
        }.getOrNull()
    }
}
