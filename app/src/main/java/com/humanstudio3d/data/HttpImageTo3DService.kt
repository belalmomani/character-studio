package com.humanstudio3d.data

import android.content.Context
import com.humanstudio3d.domain.FailureReason
import com.humanstudio3d.domain.ImageTo3DResult
import com.humanstudio3d.domain.ImageTo3DService
import com.humanstudio3d.domain.ProcessingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * [ImageTo3DService] that talks to the user's own conversion server over HTTPS using the contract in
 * docs/SERVER_CONTRACT.md. Stages reported to the UI are real: upload, wait for the server job,
 * download. The caller must have obtained the user's consent before calling [generate].
 */
class HttpImageTo3DService(
    private val context: Context,
    private val settings: ServerSettings,
    private val client: ServerClient = ServerClient(),
) : ImageTo3DService {

    override val isConfigured: Boolean get() = settings.baseUrl != null

    override suspend fun generate(
        image: File,
        onState: (ProcessingState) -> Unit,
    ): ImageTo3DResult {
        val base = settings.baseUrl ?: return ImageTo3DResult.Failure(FailureReason.PROVIDER_NOT_CONFIGURED)
        val token = settings.token
        val output = File(File(context.cacheDir, "models").apply { mkdirs() }, "result.tmp")
        var jobId: String? = null
        var succeeded = false
        try {
            onState(ProcessingState.Uploading)
            val id = client.createJob(base, token, image)
            jobId = id

            onState(ProcessingState.Generating)
            waitUntilDone(base, token, id)

            onState(ProcessingState.Downloading)
            client.download(base, token, id, output)
            succeeded = true
            return ImageTo3DResult.Success(output)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServerException) {
            return ImageTo3DResult.Failure(e.reason)
        } catch (e: SocketTimeoutException) {
            return ImageTo3DResult.Failure(FailureReason.TIMEOUT)
        } catch (e: IOException) {
            return ImageTo3DResult.Failure(FailureReason.NETWORK)
        } finally {
            if (!succeeded) output.delete()
            val id = jobId
            if (id != null) {
                // Runs even when cancelled: ask the server to cancel and delete the job's files.
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        client.deleteJob(base, token, id)
                    } catch (e: Exception) {
                        // Best effort; the server also expires jobs on its own (see the contract).
                    }
                }
            }
        }
    }

    private suspend fun waitUntilDone(base: String, token: String?, id: String) {
        val deadline = System.nanoTime() + JOB_TIMEOUT_NANOS
        while (true) {
            val job = client.status(base, token, id)
            when (job.status) {
                "done" -> return
                "failed" -> throw ServerException(
                    if (job.error == "quota") FailureReason.QUOTA_EXHAUSTED else FailureReason.UNKNOWN,
                )
                "queued", "running" -> Unit
                else -> throw ServerException(FailureReason.UNKNOWN)
            }
            if (System.nanoTime() > deadline) throw ServerException(FailureReason.TIMEOUT)
            delay(POLL_INTERVAL_MS)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val JOB_TIMEOUT_NANOS = 10L * 60 * 1_000_000_000
    }
}
