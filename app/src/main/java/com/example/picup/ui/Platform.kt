package com.example.picup.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Clipboard and intent helpers.
 *
 * The platform ClipboardManager is used directly instead of Compose's
 * clipboard APIs: those changed shape twice between Compose 1.8 and 1.13, and
 * Android 13+ already shows its own "copied" confirmation.
 */
fun Context.copyPlainText(label: String, text: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (manager == null) {
        Toast.makeText(this, "无法访问剪贴板", Toast.LENGTH_SHORT).show()
        return
    }
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** What the clipboard holds right now, as far as uploads care. */
sealed interface ClipboardPayload {
    data class Image(val uri: Uri) : ClipboardPayload
    data class Text(val text: String) : ClipboardPayload
    data object Empty : ClipboardPayload
}

/**
 * Reads the primary clip. Image clips arrive as a content Uri the app may read
 * while it is in the foreground; everything else is coerced to text.
 */
fun Context.readClipboard(): ClipboardPayload = runCatching {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return@runCatching ClipboardPayload.Empty
    val clip = manager.primaryClip ?: return@runCatching ClipboardPayload.Empty
    val item = clip.getItemAt(0)
    val text = (item.text?.toString() ?: item.htmlText?.toString()).orEmpty().trim()
    val uri = item.uri
    // Rich clips can carry both text and a URI. Text wins unless the clip is a
    // genuine image the resolver can name — a URI we cannot type is not worth
    // failing an upload over.
    val imageType = uri?.let { runCatching { contentResolver.getType(it) }.getOrNull() }
    val isImage = uri != null &&
        clipboardImageMime(clip.description) &&
        imageType?.startsWith("image/") == true
    if (isImage && text.isBlank()) return ClipboardPayload.Image(uri!!)
    if (text.isNotEmpty()) return ClipboardPayload.Text(text)
    // Last resort: an image clip whose URI has no type (some galleries).
    if (uri != null && clipboardImageMime(clip.description)) return ClipboardPayload.Image(uri)
    val coerced = item.coerceToText(this)?.toString().orEmpty().trim()
    if (coerced.isNotEmpty()) ClipboardPayload.Text(coerced) else ClipboardPayload.Empty
}.getOrDefault(ClipboardPayload.Empty)

private fun clipboardImageMime(description: android.content.ClipDescription): Boolean =
    description.hasMimeType("image/*") || description.mimeTypeCount == 0

fun Context.openInBrowser(url: String): Boolean = runCatching {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}.isSuccess

fun Context.shareText(text: String, subject: String? = null): Boolean = runCatching {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    startActivity(Intent.createChooser(send, "分享链接"))
}.isSuccess