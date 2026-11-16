package com.example.picup.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Small bitmap loader for list thumbnails and the upload tray.
 *
 * Coil is deliberately absent: the app shows either a locally picked photo or
 * a locally generated thumbnail, so two decodes with `inSampleSize` cover
 * everything and keep the APK free of an image-loading stack.
 */
/**
 * A small cache keeps scrolling from re-decoding the same eight visible rows.
 * Bitmaps are not recycled on eviction: a frame that is still drawing must not
 * lose its pixels, so the GC owns them.
 */
private val thumbnailCache = object : android.util.LruCache<String, Bitmap>(24) {}

/** Decode edge for list thumbnails; the stored WebP is 320 px, so this is a 1:1 decode. */
const val THUMB_EDGE = 320

private fun uriKey(uri: Uri, maxEdge: Int): String = "uri:$uri:$maxEdge"

private fun fileKey(file: File, maxEdge: Int): String =
    "file:${file.absolutePath}:${file.lastModified()}:$maxEdge"

/**
 * Synchronous cache read. Composables use it as their initial state, so a row
 * that scrolls back into view — or a tab that is re-composed — paints its
 * thumbnail on the first frame instead of flashing the placeholder.
 */
fun cachedThumbnail(uri: Uri, maxEdge: Int = THUMB_EDGE): Bitmap? =
    thumbnailCache.get(uriKey(uri, maxEdge))

fun cachedThumbnail(file: File, maxEdge: Int = THUMB_EDGE): Bitmap? =
    thumbnailCache.get(fileKey(file, maxEdge))

suspend fun loadThumbnail(context: Context, uri: Uri, maxEdge: Int = THUMB_EDGE): Bitmap? =
    withContext(Dispatchers.IO) {
        val key = uriKey(uri, maxEdge)
        thumbnailCache.get(key)?.let { return@withContext it }
        val loaded = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return@withContext null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                readOrientation(stream)
            } ?: ExifInterface.ORIENTATION_NORMAL
            rotateForOrientation(bitmap, orientation)
        }.getOrNull()
        if (loaded != null) thumbnailCache.put(key, loaded)
        loaded
    }

suspend fun loadThumbnail(context: Context, file: File, maxEdge: Int = THUMB_EDGE): Bitmap? =
    withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        val key = fileKey(file, maxEdge)
        thumbnailCache.get(key)?.let { return@withContext it }
        val loaded = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@withContext null
            val orientation = runCatching {
                ExifInterface(file).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            rotateForOrientation(bitmap, orientation)
        }.getOrNull()
        if (loaded != null) thumbnailCache.put(key, loaded)
        loaded
    }

private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
    return sample
}

private fun readOrientation(stream: InputStream): Int =
    runCatching {
        ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

private fun rotateForOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}