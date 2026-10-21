package com.humanstudio3d.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.humanstudio3d.domain.ExportErrorKind
import com.humanstudio3d.domain.StreamCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ExportException(val kind: ExportErrorKind) : Exception()

/**
 * Saves the opened GLB to a location the user picked with the system file dialog (no storage
 * permission needed). Success is reported only after reading the written file back and
 * confirming it has exactly the source's size; a partial file is deleted on failure.
 */
class ModelExporter(private val context: Context) {

    suspend fun export(source: File, target: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val expected = source.length()
            try {
                val sink = resolver.openOutputStream(target, "w")
                    ?: throw ExportException(ExportErrorKind.IO)
                val written = source.inputStream().use { input ->
                    sink.use { output -> StreamCopy.copy(input, output) }
                }
                if (written != expected) throw ExportException(ExportErrorKind.VERIFY_FAILED)

                val stored = resolver.openInputStream(target)?.use { StreamCopy.count(it) }
                    ?: throw ExportException(ExportErrorKind.VERIFY_FAILED)
                if (stored != expected) throw ExportException(ExportErrorKind.VERIFY_FAILED)
                expected
            } catch (e: Exception) {
                deleteQuietly(target)
                if (e is ExportException) throw e
                throw ExportException(ExportErrorKind.IO)
            }
        }
    }

    private fun deleteQuietly(target: Uri) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, target)
        } catch (e: Exception) {
            // Not every provider allows deleting; the user is told the save failed either way.
        }
    }
}
