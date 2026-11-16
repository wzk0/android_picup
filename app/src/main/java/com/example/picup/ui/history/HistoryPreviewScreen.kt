package com.example.picup.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picup.AppContainer
import com.example.picup.data.CopyFormat
import com.example.picup.data.UploadRecord
import com.example.picup.ui.components.PicUpSnackbarHost
import com.example.picup.ui.copyPlainText
import com.example.picup.ui.main.MainViewModel
import kotlinx.coroutines.launch

/**
 * Full-screen viewer for the local history.
 *
 * Swiping changes photos, double tap toggles 2.6x zoom, pinch zooms, and the
 * chips in the top-right copy the current photo in any enabled format — the
 * reason to open a preview is usually "I need a different link flavour for
 * this one".
 */
@Composable
fun HistoryPreviewScreen(
    container: AppContainer,
    initialIndex: Int,
    onClose: () -> Unit,
) {
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val formats = settings.formatsEnabled()
    val startPage = initialIndex.coerceAtLeast(0).coerceAtMost((history.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { history.size })

    // History is loaded asynchronously in this destination's own ViewModel; if
    // the list was still empty on the first frame, land on the requested photo
    // once it arrives instead of showing page one.
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(history.size) {
        if (!landed && history.isNotEmpty()) {
            if (startPage != pagerState.currentPage) pagerState.scrollToPage(startPage)
            landed = true
        }
    }

    BackHandler { onClose() }

    val recordAt: (Int) -> UploadRecord? = { page -> history.getOrNull(page) }
    val onCopy: (UploadRecord, CopyFormat) -> Unit = { record, format ->
        context.copyPlainText(format.label, viewModel.renderCopy(record, format))
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch { snackbar.showSnackbar("已复制${format.label}") }
    }

    Scaffold(
        containerColor = Color.Black,
        // The photo paints behind the system bars; the scrims add their own
        // status/navigation padding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            Box(Modifier.navigationBarsPadding()) { PicUpSnackbarHost(snackbar) }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            if (history.isEmpty()) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val record = recordAt(page)
                    if (record != null) {
                        ZoomablePreviewImage(
                            url = record.url,
                            localOriginal = container.history.previewFile(record.id),
                            fallback = container.history.thumbFile(record.id),
                        )
                    }
                }
            }

            val current = recordAt(pagerState.currentPage)

            // Top scrim: close, counter, and the copy chips.
            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭预览", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${pagerState.currentPage + 1} / ${history.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                if (current != null && formats.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(formats, key = { it.id }) { format ->
                            PreviewActionChip(
                                label = format.label,
                                onClick = { onCopy(current, format) },
                            )
                        }
                    }
                }
            }

            // Bottom scrim: file identity.
            if (current != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = current.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = current.path,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewActionChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.16f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}