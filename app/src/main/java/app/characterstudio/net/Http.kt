package app.characterstudio.net

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Http {
    class Response(val code: Int, val body: String) { val ok get() = code in 200..299 }

    /** يعيد الاستجابة حتى لو كانت خطأ HTTP (لقراءة رسالة الخادم)؛ أخطاء الشبكة تُرمى كاستثناء IOException. */
    suspend fun call(method: String, url: String, headers: Map<String, String> = emptyMap(), body: String? = null): Response =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                Response(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
            } finally {
                conn.disconnect()
            }
        }
}
