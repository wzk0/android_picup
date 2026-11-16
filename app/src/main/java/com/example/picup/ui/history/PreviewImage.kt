package com.example.picup.ui.history

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.picup.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Three-bitmap cache: the preview pages around the current one are the only
 * images the viewer ever needs at once, and full-size decodes are expensive.
 */
private val previewCache = object : android.util.LruCache<String, Bitmap>(2) {}

/**
 * Loads a preview from the public URL, falling back to the locally stored
 * thumbnail (private repositories have no anonymous raw URL, and offline use
 * should still show something).
 */
suspend fun loadPreviewBitmap(
    url: String,
    localOriginal: File?,
    fallback: File?,
    maxEdge: Int = 1600,
): Bitmap? {
    // Local first: the uploaded bytes are still on disk for recent records, so
    // opening a photo needs no network round-trip at all.
    if (localOriginal?.isFile == true) {
        val localKey = localPreviewKey(localOriginal)
        previewCache.get(localKey)?.let { return it }
        val fromDisk = withContext(Dispatchers.IO) {
            runCatching { decodeSampled(localOriginal.readBytes(), maxEdge) }.getOrNull()
        }
        if (fromDisk != null) {
            previewCache.put(localKey, fromDisk)
            return fromDisk
        }
    }
    previewCache.get(url)?.let { return it }
    val fromNetwork = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = Http.downloadBytes(url)
            decodeSampled(bytes, maxEdge)
        }.getOrNull()
    }
    if (fromNetwork != null) {
        previewCache.put(url, fromNetwork)
        return fromNetwork
    }
    if (fallback == null || !fallback.isFile) return null
    return withContext(Dispatchers.IO) {
        runCatching { decodeSampled(fallback.readBytes(), 1080) }.getOrNull()
    }
}

private fun localPreviewKey(file: File): String =
    "local:${file.absolutePath}:${file.lastModified()}"

/** Synchronous cache read for the first frame of a pager page. */
fun cachedPreviewBitmap(url: String, localOriginal: File?): Bitmap? {
    if (localOriginal?.isFile == true) {
        previewCache.get(localPreviewKey(localOriginal))?.let { return it }
    }
    return previewCache.get(url)
}

private fun decodeSampled(bytes: ByteArray, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/**
 * Full-screen preview with pinch zoom, panning and double-tap zoom.
 *
 * Zoom resets when the page changes: keeping a 3x crop from the previous photo
 * would be disorienting.
 */
@Composable
fun ZoomablePreviewImage(
    url: String,
    localOriginal: File?,
    fallback: File?,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(url, localOriginal) {
        mutableStateOf(cachedPreviewBitmap(url, localOriginal)?.asImageBitmap())
    }
    var failed by remember(url, localOriginal) { mutableStateOf(false) }
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(url, localOriginal) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = loadPreviewBitmap(url, localOriginal, fallback)
        bitmap = loaded?.asImageBitmap()
        failed = loaded == null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            // Two-finger pinch and one-finger panning while zoomed. A plain
            // single-finger drag at 1x is deliberately *not* consumed: it has
            // to reach the pager so swiping changes photos.
            .pointerInput(url) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var lastDistance = 0f
                    var lastCentroid = Offset.Zero
                    var pinchSeen = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val centroid = pressed.fold(Offset.Zero) { acc, change ->
                                acc + change.position
                            } / pressed.size.toFloat()
                            val distance = pressed
                                .map { (it.position - centroid).getDistance() }
                                .average()
                                .toFloat()
                            if (lastDistance > 0f) {
                                val factor = (distance / lastDistance).coerceIn(0.5f, 2f)
                                val next = (scale * factor).coerceIn(1f, 6f)
                                scale = next
                                offset = if (next <= 1f) {
                                    Offset.Zero
                                } else {
                                    clampOffset(offset + (centroid - lastCentroid), next, containerSize)
                                }
                            }
                            lastDistance = distance
                            lastCentroid = centroid
                            pinchSeen = true
                            event.changes.forEach { if (it.pressed) it.consume() }
                        } else if (pressed.size == 1 && (pinchSeen || scale > 1f)) {
                            val change = pressed.first()
                            val delta = change.position - change.previousPosition
                            offset = clampOffset(offset + delta, scale, containerSize)
                            change.consume()
                        } else {
                            // A new single-finger gesture at 1x: leave it alone.
                            lastDistance = 0f
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.6f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        when {
            current != null -> androidx.compose.foundation.Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )

            failed -> Icon(
                imageVector = Icons.Rounded.BrokenImage,
                contentDescription = "无法加载图片",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
            )

            else -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private fun clampOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    val maxX = (size.width * (scale - 1f)) / 2f
    val maxY = (size.height * (scale - 1f)) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}