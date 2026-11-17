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
    val uri = item.uri
    val image = uri != null && (
        clip.description.hasMimeType("image/*") ||
            contentResolver.getType(uri)?.startsWith("image/") == true
        )
    if (image && uri != null) return ClipboardPayload.Image(uri)
    val text = (item.text?.toString() ?: item.coerceToText(this)?.toString()).orEmpty().trim()
    if (text.isBlank()) ClipboardPayload.Empty else ClipboardPayload.Text(text)
}.getOrDefault(ClipboardPayload.Empty)

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