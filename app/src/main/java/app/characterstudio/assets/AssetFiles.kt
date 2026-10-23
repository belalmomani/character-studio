package app.characterstudio.assets

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * نسخ الأصول المستخدمة في المشاريع. تُحفظ في filesDir (لا cacheDir) ومستقلة عن قاعدة ذاكرة المكتبة،
 * فلا تضيع عند مسحها ولا عند إخفاء الأصل من الخادم. كل ملف يُتحقق من بصمته SHA-256 قبل اعتماده.
 */
class AssetFiles(context: Context, private val cloudName: String) {
    private val dir = File(context.filesDir, "project_assets").apply { mkdirs() }

    fun fileOf(name: String): File = File(dir, AssetFileNames.safeExisting(name))

    /** يضمن وجود نسخة سليمة محليًا ويرجع اسم الملف، أو فشلًا واضحًا. */
    suspend fun ensureLocal(a: AssetEntity): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = AssetFileNames.of(a.assetId, a.version, a.fileType)
            val target = File(dir, name)
            if (target.exists() && sha256Hex(target) == a.sha256) return@runCatching name
            if (cloudName.isEmpty()) throw IOException("cloudinary_not_configured")
            download(CloudinaryUrls.original(cloudName, a.filePublicId, a.fileType), target, a.sha256)
            name
        }
    }

    private fun download(url: String, target: File, expectedSha: String) {
        val tmp = File(dir, target.name + ".part")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode != 200) throw IOException("http_${conn.responseCode}")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IOException("file_too_large")
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha, ignoreCase = true)) throw IOException("sha_mismatch")
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("rename_failed")
        } finally {
            conn.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }

    companion object {
        const val MAX_BYTES = 5L * 1024 * 1024 + 1024

        fun sha256Hex(file: File): String {
            val d = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { s ->
                val buf = ByteArray(16 * 1024)
                while (true) { val n = s.read(buf); if (n < 0) break; d.update(buf, 0, n) }
            }
            return d.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
