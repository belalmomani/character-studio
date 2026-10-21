package com.humanstudio3d.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.humanstudio3d.domain.ImageErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ImageImportException(val kind: ImageErrorKind, cause: Throwable? = null) : Exception(cause)

/**
 * Keeps the user's photo only in the app cache (never in a cloud library). Images are
 * downscaled while decoding so large photos do not exhaust memory, and EXIF orientation is
 * applied so the picture is upright.
 */
class ImageRepository(private val context: Context) {

    private val imagesDir get() = File(context.cacheDir, "images").also { it.mkdirs() }
    private val captureDir get() = File(context.cacheDir, "capture").also { it.mkdirs() }
    private val currentFile get() = File(imagesDir, "current.jpg")

    val current: File? get() = currentFile.takeIf { it.isFile }

    fun newCaptureFile(): File = File(captureDir, "capture.jpg").also { it.delete() }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    suspend fun importFromUri(uri: Uri): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver

            // With inJustDecodeBounds the decoder returns null by design; only outWidth/outHeight matter.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(uri)
                ?: throw ImageImportException(ImageErrorKind.IO)
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw ImageImportException(ImageErrorKind.UNSUPPORTED)
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            val pixelStream = resolver.openInputStream(uri)
                ?: throw ImageImportException(ImageErrorKind.IO)
            val decoded: Bitmap? = try {
                pixelStream.use { BitmapFactory.decodeStream(it, null, options) }
            } catch (e: OutOfMemoryError) {
                throw ImageImportException(ImageErrorKind.TOO_LARGE, e)
            }
            if (decoded == null) throw ImageImportException(ImageErrorKind.UNSUPPORTED)

            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val upright = applyOrientation(decoded, orientation)
            save(upright)
            upright
        }
    }

    /** Rotates the stored photo. Re-encodes the JPEG, so repeated rotations cost a little quality. */
    suspend fun rotateCurrent(degrees: Float): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val source = BitmapFactory.decodeFile(currentFile.path)
                ?: throw ImageImportException(ImageErrorKind.IO)
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (rotated !== source) source.recycle()
            save(rotated)
            rotated
        }
    }

    /** Deletes every temporary image. Called on cancel, replace, app close and next start. */
    fun clearAll() {
        File(context.cacheDir, "images").deleteRecursively()
        File(context.cacheDir, "capture").deleteRecursively()
    }

    private fun save(bitmap: Bitmap) {
        FileOutputStream(currentFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > MAX_SIDE_PX) sample *= 2
        return sample
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (result !== bitmap) bitmap.recycle()
        return result
    }

    private companion object {
        const val MAX_SIDE_PX = 2048
    }
}
