package com.example.picup.data

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * EXIF/XMP handling that works on the container itself instead of re-encoding.
 *
 * The "remove EXIF" switch has to behave the same in both directions: with it
 * on, a JPEG keeps its pixels and loses its metadata; with it off, a re-encoded
 * file gets the original EXIF block copied back in. Only the TIFF payload
 * travels — that is the part JPEG (APP1), PNG (eXIf) and WebP (EXIF chunk)
 * share.
 *
 * Everything here is pure `ByteArray` work, so it runs in JVM unit tests.
 */
object Metadata {

    /** Removes EXIF/XMP/IPTC tags, keeping ICC profiles (dropping them shifts colours). */
    fun strip(bytes: ByteArray, ext: String): ByteArray = runCatching {
        when (ext.lowercase()) {
            "jpg", "jpeg" -> stripJpeg(bytes)
            "png" -> stripPng(bytes)
            "webp" -> stripWebp(bytes)
            else -> bytes
        }
    }.getOrDefault(bytes)

    /** The EXIF TIFF block, without the container-specific wrapper. */
    fun extractExif(bytes: ByteArray, ext: String): ByteArray? = runCatching {
        when (ext.lowercase()) {
            "jpg", "jpeg" -> {
                val payload = jpegSegments(bytes)
                    .map { segment -> segment.payload }
                    .firstOrNull { payload -> payload.startsWith(EXIF_PREFIX) }
                if (payload == null) null else payload.copyOfRange(EXIF_PREFIX.size, payload.size)
            }

            "png" -> pngChunks(bytes).firstOrNull { it.type == "eXIf" }?.data
            "webp" -> webpChunks(bytes).firstOrNull { it.fourCc == "EXIF" }?.data
            else -> null
        }
    }.getOrNull()

    /**
     * Copies [exifTiff] into [bytes]. Returns [bytes] unchanged when the
     * container cannot carry it (WebP without a VP8X header).
     */
    fun injectExif(bytes: ByteArray, ext: String, exifTiff: ByteArray): ByteArray = runCatching {
        when (ext.lowercase()) {
            "jpg", "jpeg" -> injectJpeg(bytes, exifTiff)
            "png" -> injectPng(bytes, exifTiff)
            "webp" -> injectWebp(bytes, exifTiff)
            else -> bytes
        }
    }.getOrDefault(bytes)

    // ---------------------------------------------------------------- JPEG

    private class JpegSegment(val marker: Int, val start: Int, val end: Int, val payload: ByteArray)

    private fun jpegSegments(bytes: ByteArray): List<JpegSegment> {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return emptyList()
        val segments = mutableListOf<JpegSegment>()
        var index = 2
        while (index + 1 < bytes.size) {
            if (bytes[index] != 0xFF.toByte()) break
            var markerIndex = index + 1
            while (markerIndex < bytes.size && bytes[markerIndex] == 0xFF.toByte()) markerIndex++
            if (markerIndex >= bytes.size) break
            val marker = bytes[markerIndex].toInt() and 0xFF
            if (marker == 0x01 || marker in 0xD0..0xD7) {
                index = markerIndex + 1
                continue
            }
            if (marker == 0xDA || markerIndex + 2 >= bytes.size) break
            val length = ((bytes[markerIndex + 1].toInt() and 0xFF) shl 8) or
                (bytes[markerIndex + 2].toInt() and 0xFF)
            val end = markerIndex + 1 + length
            if (length < 2 || end > bytes.size) break
            val payloadStart = markerIndex + 3
            segments += JpegSegment(
                marker = marker,
                start = index,
                end = end,
                payload = bytes.copyOfRange(payloadStart, minOf(end, bytes.size)),
            )
            index = end
        }
        return segments
    }

    private fun stripJpeg(bytes: ByteArray): ByteArray {
        val segments = jpegSegments(bytes)
        if (segments.isEmpty()) return bytes
        val out = ByteArrayOutputStream(bytes.size)
        out.write(bytes, 0, 2)
        segments.forEach { segment ->
            // APP1 = EXIF/XMP, APP13 = IPTC, COM = comment.
            val drop = segment.marker == 0xE1 || segment.marker == 0xED || segment.marker == 0xFE
            if (!drop) out.write(bytes, segment.start, segment.end - segment.start)
        }
        val tail = segments.last().end
        if (tail < bytes.size) out.write(bytes, tail, bytes.size - tail)
        return out.toByteArray()
    }

    private fun injectJpeg(bytes: ByteArray, exifTiff: ByteArray): ByteArray {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return bytes
        val payload = EXIF_PREFIX + exifTiff
        val length = payload.size + 2
        if (length > 0xFFFF) return bytes
        val out = ByteArrayOutputStream(bytes.size + payload.size + 4)
        out.write(bytes, 0, 2)
        out.write(0xFF)
        out.write(0xE1)
        out.write((length shr 8) and 0xFF)
        out.write(length and 0xFF)
        out.write(payload)
        out.write(bytes, 2, bytes.size - 2)
        return out.toByteArray()
    }

    // ----------------------------------------------------------------- PNG

    private class PngChunk(val type: String, val start: Int, val end: Int, val data: ByteArray)

    private fun pngChunks(bytes: ByteArray): List<PngChunk> {
        if (bytes.size < 16) return emptyList()
        val chunks = mutableListOf<PngChunk>()
        var index = 8
        while (index + 12 <= bytes.size) {
            val length = readIntBe(bytes, index)
            if (length < 0) break
            val type = String(bytes, index + 4, 4, Charsets.US_ASCII)
            val end = index + 12 + length
            if (end > bytes.size) break
            chunks += PngChunk(
                type = type,
                start = index,
                end = end,
                data = bytes.copyOfRange(index + 8, index + 8 + length),
            )
            index = end
            if (type == "IEND") break
        }
        return chunks
    }

    private fun stripPng(bytes: ByteArray): ByteArray {
        val chunks = pngChunks(bytes)
        if (chunks.isEmpty()) return bytes
        val out = ByteArrayOutputStream(bytes.size)
        out.write(bytes, 0, 8)
        chunks.forEach { chunk ->
            if (chunk.type != "eXIf" && chunk.type != "tEXt" && chunk.type != "iTXt" && chunk.type != "zTXt") {
                out.write(bytes, chunk.start, chunk.end - chunk.start)
            }
        }
        return out.toByteArray()
    }

    private fun injectPng(bytes: ByteArray, exifTiff: ByteArray): ByteArray {
        val chunks = pngChunks(bytes)
        if (chunks.isEmpty()) return bytes
        val ihdr = chunks.firstOrNull { it.type == "IHDR" } ?: return bytes
        val out = ByteArrayOutputStream(bytes.size + exifTiff.size + 12)
        out.write(bytes, 0, ihdr.end)
        writePngChunk(out, "eXIf", exifTiff)
        out.write(bytes, ihdr.end, bytes.size - ihdr.end)
        return out.toByteArray()
    }

    private fun writePngChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        out.write(intBe(data.size))
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        out.write(intBe(crc.value.toInt()))
    }

    // ---------------------------------------------------------------- WebP

    private class WebpChunk(val fourCc: String, val start: Int, val end: Int, val data: ByteArray)

    private fun webpChunks(bytes: ByteArray): List<WebpChunk> {
        if (bytes.size < 12) return emptyList()
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF") return emptyList()
        if (String(bytes, 8, 4, Charsets.US_ASCII) != "WEBP") return emptyList()
        val chunks = mutableListOf<WebpChunk>()
        var index = 12
        while (index + 8 <= bytes.size) {
            val fourCc = String(bytes, index, 4, Charsets.US_ASCII)
            val size = readIntLe(bytes, index + 4)
            if (size < 0) break
            val end = index + 8 + size + (size and 1)
            if (end > bytes.size) break
            chunks += WebpChunk(
                fourCc = fourCc,
                start = index,
                end = end,
                data = bytes.copyOfRange(index + 8, index + 8 + size),
            )
            index = end
        }
        return chunks
    }

    private fun stripWebp(bytes: ByteArray): ByteArray {
        val chunks = webpChunks(bytes)
        if (chunks.isEmpty()) return bytes
        val kept = chunks.filter { it.fourCc != "EXIF" && it.fourCc != "XMP " }
        return buildWebp(bytes, kept)
    }

    /**
     * WebP only allows this when a VP8X header exists to advertise the feature,
     * so files produced by plain `Bitmap.compress` are returned untouched.
     */
    private fun injectWebp(bytes: ByteArray, exifTiff: ByteArray): ByteArray {
        val chunks = webpChunks(bytes)
        if (chunks.none { it.fourCc == "VP8X" }) return bytes
        val out = ByteArrayOutputStream(bytes.size + exifTiff.size + 8)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(intLe(0))
        out.write("WEBP".toByteArray(Charsets.US_ASCII))
        chunks.forEach { chunk ->
            if (chunk.fourCc == "VP8X" && chunk.data.size >= 4) {
                // Set the EXIF feature bit in the first flags byte.
                val data = chunk.data.copyOf()
                data[0] = (data[0].toInt() or 0x08).toByte()
                out.write("VP8X".toByteArray(Charsets.US_ASCII))
                out.write(intLe(data.size))
                out.write(data)
            } else {
                out.write(bytes, chunk.start, chunk.end - chunk.start)
            }
        }
        out.write("EXIF".toByteArray(Charsets.US_ASCII))
        out.write(intLe(exifTiff.size))
        out.write(exifTiff)
        if (exifTiff.size and 1 == 1) out.write(0)
        val body = out.toByteArray()
        val total = body.size - 8
        val sizeBytes = intLe(total)
        body[4] = sizeBytes[0]
        body[5] = sizeBytes[1]
        body[6] = sizeBytes[2]
        body[7] = sizeBytes[3]
        return body
    }

    private fun buildWebp(original: ByteArray, chunks: List<WebpChunk>): ByteArray {
        val out = ByteArrayOutputStream(original.size)
        out.write(original, 0, 12)
        chunks.forEach { out.write(original, it.start, it.end - it.start) }
        val body = out.toByteArray()
        val sizeBytes = intLe(body.size - 8)
        body[4] = sizeBytes[0]
        body[5] = sizeBytes[1]
        body[6] = sizeBytes[2]
        body[7] = sizeBytes[3]
        return body
    }

    // -------------------------------------------------------------- helpers

    private val EXIF_PREFIX = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII)

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }

    private fun readIntBe(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun readIntLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun intBe(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun intLe(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )
}