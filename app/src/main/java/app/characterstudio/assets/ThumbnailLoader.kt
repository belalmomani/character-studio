package app.characterstudio.assets

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** تحميل الصور المصغرة مع ذاكرة مؤقتة في الذاكرة وعلى القرص (cacheDir يمكن للنظام مسحه). */
object ThumbnailLoader {
    private val memory = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 16).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun name(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun load(context: Context, url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memory.get(url)?.let { return@withContext it }
        val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        val file = File(dir, name(url))
        if (!file.exists()) {
            val tmp = File(dir, file.name + ".part")
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 20_000
                    if (conn.responseCode != 200) return@withContext null
                    var total = 0L
                    conn.inputStream.use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(8 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > 2L * 1024 * 1024) throw java.io.IOException("too_large")
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    if (!tmp.renameTo(file)) return@withContext null
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                return@withContext null
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }
        BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { memory.put(url, it) }
    }
}
