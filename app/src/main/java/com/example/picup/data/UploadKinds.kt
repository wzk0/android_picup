package com.example.picup.data

/**
 * Decides whether an upload goes through the image pipeline.
 *
 * A `content://` Uri carries a MIME type, but `file://` sources (clipboard text
 * written to cache, clipboard images copied locally) carry none — for those the
 * file extension is the only signal. Anything with a non-image MIME is opaque
 * no matter what its name looks like.
 */
object UploadKinds {
    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif",
    )

    fun isImage(mime: String, fileName: String): Boolean {
        val normalised = mime.trim().lowercase()
        if (normalised.startsWith("image/")) return true
        if (normalised.isNotEmpty()) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in IMAGE_EXTENSIONS
    }
}
