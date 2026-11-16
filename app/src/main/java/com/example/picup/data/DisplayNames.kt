package com.example.picup.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Resolves the user-visible file name behind a content Uri (the picker label). */
class DisplayNames(private val context: Context) {
    fun resolve(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "image" }
    }
}
