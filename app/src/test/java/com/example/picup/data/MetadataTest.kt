package com.example.picup.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * Container surgery is pure byte work, so the whole EXIF round-trip is tested
 * on synthetic files instead of on a device.
 */
class MetadataTest {

    private val exifTiff = byteArrayOf(
        0x49, 0x49, 0x2A, 0x00, // TIFF little-endian header
        0x08, 0x00, 0x00, 0x00,
        0x01, 0x00, 0x0F, 0x01, 0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x41, 0x42, 0x43, 0x00,
    )

    @Test
    fun `jpeg exif is extracted and stripped without touching the image data`() {
        val jpeg = jpegWithExif(exifTiff)
        assertArrayEquals(exifTiff, Metadata.extractExif(jpeg, "jpg"))

        val stripped = Metadata.strip(jpeg, "jpg")
        assertFalse(stripped.containsAscii("Exif"))
        assertNull(Metadata.extractExif(stripped, "jpg"))
        // SOI, the APP0 segment and the scan data survive.
        assertEquals(0xFF.toByte(), stripped[0])
        assertEquals(0xD8.toByte(), stripped[1])
        assertTrue(stripped.containsAscii("JFIF-kept"))
        assertEquals(jpeg[jpeg.size - 4], stripped[stripped.size - 4])
    }

    @Test
    fun `jpeg exif is injected after the soi marker`() {
        val stripped = Metadata.strip(jpegWithExif(exifTiff), "jpg")
        val restored = Metadata.injectExif(stripped, "jpg", exifTiff)
        assertArrayEquals(exifTiff, Metadata.extractExif(restored, "jpg"))
        assertEquals(0xFF.toByte(), restored[1 + 1])
    }

    @Test
    fun `png exif chunk round trips`() {
        val png = pngWithExif(exifTiff)
        assertArrayEquals(exifTiff, Metadata.extractExif(png, "png"))

        val stripped = Metadata.strip(png, "png")
        assertFalse(stripped.containsAscii("eXIf"))
        assertNull(Metadata.extractExif(stripped, "png"))
        assertTrue(stripped.containsAscii("IDAT"))

        val restored = Metadata.injectExif(stripped, "png", exifTiff)
        assertArrayEquals(exifTiff, Metadata.extractExif(restored, "png"))
        // IHDR still comes first, IEND still comes last.
        assertEquals("IHDR", String(restored, 12, 4, Charsets.US_ASCII))
        assertTrue(restored.containsAscii("IEND"))
    }

    @Test
    fun `webp exif chunk round trips when a vp8x header exists`() {
        val webp = webpWithExif(exifTiff)
        assertArrayEquals(exifTiff, Metadata.extractExif(webp, "webp"))

        val stripped = Metadata.strip(webp, "webp")
        assertNull(Metadata.extractExif(stripped, "webp"))
        assertEquals(riffSize(stripped), stripped.size - 8)

        val restored = Metadata.injectExif(stripped, "webp", exifTiff)
        assertArrayEquals(exifTiff, Metadata.extractExif(restored, "webp"))
        assertEquals(riffSize(restored), restored.size - 8)
    }

    @Test
    fun `webp without a vp8x header is left alone`() {
        val simple = webpWithoutVp8x()
        val unchanged = Metadata.injectExif(simple, "webp", exifTiff)
        assertArrayEquals(simple, unchanged)
    }

    @Test
    fun `unknown containers are returned untouched`() {
        val gif = "GIF89a-not-really".toByteArray()
        assertArrayEquals(gif, Metadata.strip(gif, "gif"))
        assertArrayEquals(gif, Metadata.injectExif(gif, "gif", exifTiff))
        assertNull(Metadata.extractExif(gif, "gif"))
    }

    // ----------------------------------------------------------- builders

    private fun jpegWithExif(tiff: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        val payload = "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + tiff
        val length = payload.size + 2
        out.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), (length and 0xFF).toByte()))
        out.write(payload)
        // APP0 with recognisable content so the test can prove it survived.
        val app0 = "JFIF-kept".toByteArray(Charsets.US_ASCII)
        val app0Length = app0.size + 2
        out.write(byteArrayOf(0xFF.toByte(), 0xE0.toByte(), (app0Length shr 8).toByte(), (app0Length and 0xFF).toByte()))
        out.write(app0)
        // Start of scan + fake entropy data.
        out.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02, 0x11, 0x22, 0x33, 0x44))
        return out.toByteArray()
    }

    private fun pngWithExif(tiff: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(PNG_SIGNATURE)
        out.write(pngChunk("IHDR", ByteArray(13) { 7 }))
        out.write(pngChunk("eXIf", tiff))
        out.write(pngChunk("IDAT", byteArrayOf(9, 9, 9)))
        out.write(pngChunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    private fun webpWithExif(tiff: ByteArray): ByteArray {
        val chunks = ByteArrayOutputStream()
        chunks.write(webpChunk("VP8X", byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
        chunks.write(webpChunk("EXIF", tiff))
        chunks.write(webpChunk("VP8 ", byteArrayOf(1, 2, 3, 4)))
        return riff(chunks.toByteArray())
    }

    private fun webpWithoutVp8x(): ByteArray =
        riff(webpChunk("VP8 ", byteArrayOf(1, 2, 3, 4)))

    private fun riff(body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(intLe(body.size + 4))
        out.write("WEBP".toByteArray(Charsets.US_ASCII))
        out.write(body)
        return out.toByteArray()
    }

    private fun webpChunk(fourCc: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(fourCc.toByteArray(Charsets.US_ASCII))
        out.write(intLe(data.size))
        out.write(data)
        if (data.size and 1 == 1) out.write(0)
        return out.toByteArray()
    }

    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        val out = ByteArrayOutputStream()
        out.write(intBe(data.size))
        out.write(typeBytes)
        out.write(data)
        out.write(intBe(crc.value.toInt()))
        return out.toByteArray()
    }

    private fun riffSize(bytes: ByteArray): Int =
        (bytes[4].toInt() and 0xFF) or
            ((bytes[5].toInt() and 0xFF) shl 8) or
            ((bytes[6].toInt() and 0xFF) shl 16) or
            ((bytes[7].toInt() and 0xFF) shl 24)

    private fun ByteArray.containsAscii(needle: String): Boolean {
        val target = needle.toByteArray(Charsets.US_ASCII)
        if (target.isEmpty() || size < target.size) return false
        for (start in 0..(size - target.size)) {
            var matched = true
            for (offset in target.indices) {
                if (this[start + offset] != target[offset]) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }

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

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}