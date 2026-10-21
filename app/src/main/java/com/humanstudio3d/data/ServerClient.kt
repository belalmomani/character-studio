package com.humanstudio3d.data

import com.humanstudio3d.domain.FailureReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class ServerException(val reason: FailureReason) : Exception()

enum class HealthResult { OK, AUTH, TIMEOUT, NETWORK, BAD_RESPONSE }

/**
 * HTTP client for the conversion-server contract described in docs/SERVER_CONTRACT.md.
 * It never logs URLs, tokens or image content.
 */
class ServerClient {

    suspend fun health(base: String, token: String?): HealthResult = withContext(Dispatchers.IO) {
        try {
            val conn = open("$base/v1/health", "GET", token)
            try {
                when (conn.responseCode) {
                    in 200..299 -> HealthResult.OK
                    401, 403 -> HealthResult.AUTH
                    else -> HealthResult.BAD_RESPONSE
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: SocketTimeoutException) {
            HealthResult.TIMEOUT
        } catch (e: IOException) {
            HealthResult.NETWORK
        }
    }

    /** Uploads the image and returns the job id. */
    suspend fun createJob(base: String, token: String?, image: File): String = withContext(Dispatchers.IO) {
        val boundary = "----hcs${System.nanoTime()}"
        val conn = open("$base/v1/jobs", "POST", token)
        try {
            conn.doOutput = true
            conn.setChunkedStreamingMode(0)
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.outputStream.use { out ->
                out.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"image\"; " +
                        "filename=\"image.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").toByteArray(),
                )
                image.inputStream().use { input ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            checkStatus(conn)
            val id = parseJson(readBody(conn)).optString("id")
            if (!ID_PATTERN.matches(id)) throw ServerException(FailureReason.UNKNOWN)
            id
        } finally {
            conn.disconnect()
        }
    }

    /** Returns the job status: "queued", "running", "done" or "failed". */
    suspend fun status(base: String, token: String?, id: String): JobStatus = withContext(Dispatchers.IO) {
        val conn = open("$base/v1/jobs/$id", "GET", token)
        try {
            checkStatus(conn)
            val json = parseJson(readBody(conn))
            JobStatus(json.optString("status"), json.optString("error"))
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(base: String, token: String?, id: String, target: File) = withContext(Dispatchers.IO) {
        val conn = open("$base/v1/jobs/$id/result", "GET", token)
        try {
            conn.readTimeout = DOWNLOAD_TIMEOUT_MS
            checkStatus(conn)
            conn.inputStream.use { input ->
                target.outputStream().use { sink ->
                    val buffer = ByteArray(BUFFER)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_RESULT_BYTES) throw ServerException(FailureReason.INVALID_MODEL)
                        sink.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Asks the server to cancel and delete the job. Best effort: the result is ignored. */
    suspend fun deleteJob(base: String, token: String?, id: String) = withContext(Dispatchers.IO) {
        val conn = open("$base/v1/jobs/$id", "DELETE", token)
        try {
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, method: String, token: String?): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/json")
        if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
        return conn
    }

    private fun checkStatus(conn: HttpURLConnection) {
        when (conn.responseCode) {
            in 200..299 -> Unit
            401, 403 -> throw ServerException(FailureReason.AUTH)
            402, 429 -> throw ServerException(FailureReason.QUOTA_EXHAUSTED)
            else -> throw ServerException(FailureReason.UNKNOWN)
        }
    }

    private fun readBody(conn: HttpURLConnection): String =
        conn.inputStream.bufferedReader().use { it.readText() }

    private fun parseJson(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw ServerException(FailureReason.UNKNOWN)
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val DOWNLOAD_TIMEOUT_MS = 120_000
        const val MAX_RESULT_BYTES = 80L * 1024 * 1024
        val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    }
}

data class JobStatus(val status: String, val error: String)
