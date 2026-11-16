package com.example.picup.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The bytes that will be committed, plus the facts the history and templates need. */
data class PreparedImage(
    val bytes: ByteArray,
    val ext: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val originalBytes: Long,
    val originalWidth: Int,
    val originalHeight: Int,
) {
    val resized: Boolean get() = width != originalWidth || height != originalHeight
    val reencoded: Boolean get() = bytes.size.toLong() != originalBytes
}

/**
 * Decode → orient → (optionally) downscale → re-encode.
 *
 * Everything happens in memory, capped twice: the source read is limited to
 * [MAX_SOURCE_BYTES] and the decode is sampled so a 50 MP photo never becomes a
 * 800 MB bitmap on a 4 GB phone. GIFs are copied untouched — re-encoding one
 * frame of an animation is never what the user wanted.
 */
class ImageProcessor(private val context: Context) {

    suspend fun prepare(uri: Uri, options: UploadOptions): PreparedImage = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val source = resolver.openInputStream(uri)?.use { readLimited(it, MAX_SOURCE_BYTES) }
            ?: throw IOException("无法读取所选图片")
        val declaredMime = resolver.getType(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        val sourceWidth = bounds.outWidth.coerceAtLeast(0)
        val sourceHeight = bounds.outHeight.coerceAtLeast(0)
        val isGif = declaredMime == "image/gif" || isGifSignature(source)
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            if (isGif) {
                // Some animated files report no bounds to BitmapFactory but are still fine to store.
                val ext = extForMime(declaredMime) ?: "gif"
                return@withContext PreparedImage(
                    bytes = source,
                    ext = ext,
                    mimeType = mimeForExt(ext),
                    width = 0,
                    height = 0,
                    originalBytes = source.size.toLong(),
                    originalWidth = 0,
                    originalHeight = 0,
                )
            }
            throw IOException("无法解码这张图片，可能文件已损坏或格式不受支持")
        }

        val targetFormat = when {
            isGif -> OutputFormat.PRESERVE
            else -> options.format
        }
        if (targetFormat == OutputFormat.PRESERVE) {
            val ext = detectExt(source, declaredMime)
            val kept = if (options.stripExif) Metadata.strip(source, ext) else source
            return@withContext PreparedImage(
                bytes = kept,
                ext = ext,
                mimeType = mimeForExt(ext),
                width = sourceWidth,
                height = sourceHeight,
                originalBytes = source.size.toLong(),
                originalWidth = sourceWidth,
                originalHeight = sourceHeight,
            )
        }

        val bitmap = decodeSampled(source, options.maxEdge) ?: throw IOException("无法解码这张图片，可能文件已损坏或格式不受支持")
        val oriented = applyExifOrientation(bitmap, source)
        val scaled = scaleTo(oriented, options.maxEdge)
        if (scaled !== oriented) oriented.recycleSafely()

        val (ext, mime) = when (targetFormat) {
            OutputFormat.JPEG -> "jpg" to "image/jpeg"
            OutputFormat.PNG -> "png" to "image/png"
            OutputFormat.WEBP -> "webp" to "image/webp"
            OutputFormat.PRESERVE -> "jpg" to "image/jpeg"
        }
        val encoded = compress(scaled, targetFormat, options.quality)
        val finalWidth = scaled.width
        val finalHeight = scaled.height
        val resized = finalWidth != sourceWidth || finalHeight != sourceHeight
        scaled.recycleSafely()

        // Re-encoding drops everything, so copy the original EXIF block back
        // when the user turned metadata removal off.
        val encodedWithMetadata = if (options.stripExif) {
            encoded
        } else {
            Metadata.extractExif(source, detectExt(source, declaredMime))
                ?.let { exif -> Metadata.injectExif(encoded, ext, exif) }
                ?: encoded
        }

        // Keeping the original is only honest when it also honours the size cap:
        // otherwise "smaller file" would silently ship a 6000 px photo.
        val keepSource = options.keepSmallerOriginal && source.size <= encodedWithMetadata.size && !resized
        if (keepSource) {
            val sourceExt = detectExt(source, declaredMime)
            val kept = if (options.stripExif) Metadata.strip(source, sourceExt) else source
            return@withContext PreparedImage(
                bytes = kept,
                ext = sourceExt,
                mimeType = mimeForExt(sourceExt),
                width = finalWidth,
                height = finalHeight,
                originalBytes = source.size.toLong(),
                originalWidth = sourceWidth,
                originalHeight = sourceHeight,
            )
        }
        PreparedImage(
            bytes = encodedWithMetadata,
            ext = ext,
            mimeType = mime,
            width = finalWidth,
            height = finalHeight,
            originalBytes = source.size.toLong(),
            originalWidth = sourceWidth,
            originalHeight = sourceHeight,
        )
    }

    /**
     * Any non-image file: bytes go up untouched, no decode, no resize.
     * Used by the "arbitrary file" picker; the path template still names it.
     */
    suspend fun prepareOpaque(uri: Uri, mime: String?, displayName: String): PreparedImage =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)
                ?.use { readLimited(it, MAX_SOURCE_BYTES) }
                ?: throw IOException("无法读取所选文件")
            val (_, nameExt) = Naming.splitExtension(displayName)
            val ext = extForMime(mime) ?: nameExt
            PreparedImage(
                bytes = bytes,
                ext = ext,
                mimeType = mime?.takeIf { it.isNotBlank() } ?: mimeForExt(ext),
                width = 0,
                height = 0,
                originalBytes = bytes.size.toLong(),
                originalWidth = 0,
                originalHeight = 0,
            )
        }

    /** Small WebP copy for the history list; failures are non-fatal. */
    suspend fun writeThumbnail(target: File, bytes: ByteArray, maxEdge: Int = 320) = withContext(Dispatchers.IO) {
        val bitmap = decodeSampled(bytes, maxEdge) ?: return@withContext
        val thumb = scaleTo(bitmap, maxEdge)
        if (thumb !== bitmap) bitmap.recycleSafely()
        target.parentFile?.mkdirs()
        runCatching {
            target.outputStream().use { out ->
                thumb.compress(webpFormat(), 72, out)
            }
        }
        thumb.recycleSafely()
    }

    suspend fun thumbnailFor(uri: Uri, maxEdge: Int = 320): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_SOURCE_BYTES) }
        }.getOrNull() ?: return@withContext null
        decodeSampled(bytes, maxEdge)
    }

    suspend fun thumbnailFor(file: File, maxEdge: Int = 320): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }.getOrNull()
    }

    private fun decodeSampled(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Sample to at most twice the target so the final scale stays high quality,
        // and always cap at MAX_DECODE_EDGE to keep peak memory bounded.
        val decodeEdge = if (maxEdge > 0) max(maxEdge * 2, 1024) else MAX_DECODE_EDGE
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, decodeEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        if (maxEdge <= 0) return 1
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    private fun applyExifOrientation(bitmap: Bitmap, source: ByteArray): Bitmap {
        val orientation = runCatching {
            val exif = ExifInterface(ByteArrayInputStream(source))
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return bitmap
        }
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
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        // `createBitmap` returns the same instance when the matrix is a no-op.
        if (rotated !== bitmap) bitmap.recycleSafely()
        return rotated
    }

    private fun scaleTo(bitmap: Bitmap, maxEdge: Int): Bitmap {
        if (maxEdge <= 0) return bitmap
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        val width = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycleSafely()
        return scaled
    }

    private fun compress(bitmap: Bitmap, format: OutputFormat, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val compressFormat = when (format) {
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            OutputFormat.JPEG, OutputFormat.PRESERVE -> Bitmap.CompressFormat.JPEG
            OutputFormat.WEBP -> webpFormat()
        }
        // JPEG has no alpha channel: a transparent PNG would turn black, so
        // flatten it onto white first instead of shipping a broken-looking image.
        val source = if (compressFormat == Bitmap.CompressFormat.JPEG && bitmap.hasAlpha()) {
            flattenOntoWhite(bitmap)
        } else {
            bitmap
        }
        source.compress(compressFormat, quality.coerceIn(20, 100), out)
        if (source !== bitmap) source.recycleSafely()
        return out.toByteArray()
    }

    private fun flattenOntoWhite(bitmap: Bitmap): Bitmap {
        val canvasBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(canvasBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return canvasBitmap
    }

    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) runCatching { recycle() }
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("图片大于 ${Naming.formatBytes(limit)}，拒绝处理")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * The picker's MIME type is missing often enough (custom providers, some
     * file managers) that the magic bytes have the final word: a JPEG stored
     * with a `.webp` extension would serve a broken Content-Type.
     */
    private fun detectExt(bytes: ByteArray, declaredMime: String?): String {
        extForMime(declaredMime)?.let { return it }
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpg"
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) return "png"
        if (bytes.size >= 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte()) return "webp"
        if (isGifSignature(bytes)) return "gif"
        if (bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) return "bmp"
        return "jpg"
    }

    private fun isGifSignature(bytes: ByteArray): Boolean =
        bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte()

    companion object {
        const val MAX_SOURCE_BYTES: Long = 64L * 1024 * 1024
        const val MAX_DECODE_EDGE = 8192

        fun extForMime(mime: String?): String? = when (mime?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp", "image/x-ms-bmp" -> "bmp"
            "image/heic", "image/heif" -> "heic"
            "image/avif" -> "avif"
            "image/svg+xml" -> "svg"
            else -> null
        }

        fun mimeForExt(ext: String): String = when (ext.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "avif" -> "image/avif"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }
}