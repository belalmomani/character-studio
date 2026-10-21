package com.humanstudio3d.data

import android.content.Context
import android.net.Uri
import com.humanstudio3d.domain.GlbReport
import com.humanstudio3d.domain.GlbValidator
import com.humanstudio3d.domain.ModelErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelImportException(val kind: ModelErrorKind) : Exception()

/**
 * Holds the GLB being viewed in the app cache only (never in a cloud library). The file is
 * copied in, structurally validated, and only then exposed. Everything is deleted when the
 * model is discarded, when the app closes, and at the next start.
 */
class ModelRepository(private val context: Context) {

    private val dir get() = File(context.cacheDir, "models").also { it.mkdirs() }
    private val currentFile get() = File(dir, "current.glb")

    val current: File? get() = currentFile.takeIf { it.isFile }

    suspend fun importGlb(uri: Uri): Result<GlbReport> = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File(dir, "import.tmp")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw ModelImportException(ModelErrorKind.IO)
                var total = 0L
                input.use { source ->
                    temp.outputStream().use { sink ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_BYTES) throw ModelImportException(ModelErrorKind.TOO_LARGE)
                            sink.write(buffer, 0, read)
                        }
                    }
                }

                val report = GlbValidator.validateFile(temp)
                if (!report.valid) throw ModelImportException(ModelErrorKind.INVALID)

                currentFile.delete()
                if (!temp.renameTo(currentFile)) throw ModelImportException(ModelErrorKind.IO)
                report
            } catch (e: java.io.IOException) {
                throw ModelImportException(ModelErrorKind.IO)
            } finally {
                temp.delete()
            }
        }
    }

    /** Moves an already validated file (same cache folder) in as the current model. */
    fun adopt(source: File): File? {
        currentFile.delete()
        return if (source.renameTo(currentFile)) currentFile else null
    }

    fun clear() {
        File(context.cacheDir, "models").deleteRecursively()
    }

    private companion object {
        // The viewer reads the whole file into memory, so keep a conservative ceiling.
        const val MAX_BYTES = 80L * 1024 * 1024
    }
}
