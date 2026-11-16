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