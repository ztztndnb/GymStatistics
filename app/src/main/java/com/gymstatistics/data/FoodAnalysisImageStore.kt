package com.gymstatistics.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_FOOD_IMAGE_EDGE = 2048

internal data class FoodImageSize(val width: Int, val height: Int)

internal fun scaledImageSize(width: Int, height: Int, maxEdge: Int = MAX_FOOD_IMAGE_EDGE): FoodImageSize {
    if (width <= maxEdge && height <= maxEdge) return FoodImageSize(width, height)
    val scale = maxEdge.toFloat() / maxOf(width, height)
    return FoodImageSize((width * scale).toInt(), (height * scale).toInt())
}

internal fun foodImageFileName(recordId: String): String {
    val safeId = recordId.filter { it.isLetterOrDigit() || it == '-' }
    return safeId.takeIf { it.isNotBlank() }?.let { "$it.jpg" }.orEmpty()
}

class FoodAnalysisImageStore(private val context: Context) {
    private val directory get() = File(context.filesDir, "food-analysis-images")

    suspend fun save(uri: Uri, recordId: String): String? = withContext(Dispatchers.IO) {
        val name = foodImageFileName(recordId)
        if (name.isBlank()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val source = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            })
        } ?: return@withContext null
        val targetSize = scaledImageSize(source.width, source.height)
        val scaled = if (targetSize.width == source.width && targetSize.height == source.height) source else {
            Bitmap.createScaledBitmap(source, targetSize.width, targetSize.height, true)
        }
        val target = File(directory, name)
        val temporary = runCatching {
            directory.mkdirs()
            File.createTempFile("$name.", ".tmp", directory)
        }.getOrNull() ?: run {
            if (scaled !== source) scaled.recycle()
            source.recycle()
            return@withContext null
        }
        try {
            FileOutputStream(temporary).use { output ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, 90, output))
            }
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) return@withContext null
            name
        } catch (_: Exception) {
            null
        } finally {
            temporary.delete()
            if (scaled !== source) scaled.recycle()
            source.recycle()
        }
    }

    fun file(name: String): File? = validFile(name)?.takeIf(File::exists)

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        validFile(name)?.delete()
    }

    private fun validFile(name: String): File? = name.takeIf { it == foodImageFileName(it.removeSuffix(".jpg")) }
        ?.let { File(directory, it) }

    private fun sampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MAX_FOOD_IMAGE_EDGE * 2 || height / sampleSize > MAX_FOOD_IMAGE_EDGE * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
