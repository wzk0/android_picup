package com.example.picup.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picup.AppContainer
import com.example.picup.data.AppSettings
import com.example.picup.data.CopyFormat
import com.example.picup.data.HistoryLayout
import com.example.picup.data.Naming
import com.example.picup.data.OutputFormat
import com.example.picup.data.QualityPreset
import com.example.picup.data.UploadRecord
import com.example.picup.ui.components.CardRow
import com.example.picup.ui.components.EmptyState
import com.example.picup.ui.components.PicUpIconSegmented
import com.example.picup.ui.components.cardRowPosition
import com.example.picup.ui.components.PicUpSegmented
import com.example.picup.ui.components.PicUpSnackbarHost
import com.example.picup.ui.components.PillChip
import com.example.picup.ui.components.SectionLabel
import com.example.picup.ui.components.Thumbnail
import com.example.picup.ui.components.ThumbnailSource
import com.example.picup.ui.ClipboardPayload
import com.example.picup.ui.copyPlainText
import com.example.picup.ui.openInBrowser
import com.example.picup.ui.readClipboard
import com.example.picup.ui.shareText
import com.example.picup.ui.theme.cardSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ItemFadeSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)

private val ItemPlacementSpec = spring<IntOffset>(
    stiffness = Spring.StiffnessMedium,
    dampingRatio = Spring.DampingRatioNoBouncy,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

@Composable
fun MainScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenPreview: (Int) -> Unit,
) {
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val uploadListState = rememberLazyListState()
    val recentListState = rememberLazyListState()
    val historyGridState = rememberLazyGridState()

    var clipboardText by remember { mutableStateOf<String?>(null) }
    var clipboardName by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(30),
    ) { uris -> if (uris.isNotEmpty()) viewModel.enqueue(uris) }
    val anyFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.enqueue(uris) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbar.showSnackbar(message) }
    }

    val onPasteClipboard: () -> Unit = {
        when (val payload = context.readClipboard()) {
            is ClipboardPayload.Image -> viewModel.enqueueClipboardImage(payload.uri)
            is ClipboardPayload.Text -> {
                clipboardName = defaultClipboardFileName()
                clipboardText = payload.text
            }

            ClipboardPayload.Empty -> viewModel.notify("剪贴板里没有可上传的图片或文字")
        }
    }

    val copyRendered: (UploadRecord, CopyFormat) -> Unit = { record, format ->
        context.copyPlainText(format.label, viewModel.renderCopy(record, format))
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch { snackbar.showSnackbar("已复制${format.label}") }
    }
    val copyActive: (UploadRecord) -> Unit = { record ->
        val format = settings.activeFormat() ?: settings.formatsEnabled().firstOrNull()
        if (format == null) {
            viewModel.notify("没有启用的复制格式")
        } else {
            copyRendered(record, format)
        }
    }
    val preview: (UploadRecord) -> Unit = { record ->
        val index = history.indexOfFirst { it.id == record.id }
        if (index >= 0) onOpenPreview(index)
    }

    clipboardText?.let { text ->
        ClipboardUploadDialog(
            initialName = clipboardName,
            characterCount = text.length,
            onDismiss = { clipboardText = null },
            onConfirm = { name ->
                viewModel.enqueueClipboardText(text, name)
                clipboardText = null
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { PicUpSnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            MainHeader(settings = settings, onOpenSettings = onOpenSettings)
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(12.dp))
                PicUpSegmented(
                    options = listOf(0, 1),
                    selected = pagerState.currentPage,
                    onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    // The indicator tracks the drag frame by frame instead of
                    // jumping when the pager settles past the halfway point.
                    selectionFraction = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                    label = { if (it == 0) "上传" else "历史" },
                )
                Spacer(Modifier.height(18.dp))
                if (settings.activeRemote == null) {
                    EmptyState(
                        icon = Icons.Rounded.Cloud,
                        title = "还没有配置图床仓库",
                        body = "图片会提交到你自己的 Git 仓库，链接直接可用。",
                        actionLabel = "配置仓库",
                        onAction = onOpenSetup,
                    )
                } else {
                    // Swiping anywhere that is not a vertical list scroll moves
                    // between the two pages; the segmented control stays the
                    // discoverable way to jump.
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) { page ->
                        if (page == 0) {
                            UploadTab(
                                settings = settings,
                                tasks = tasks,
                                history = history,
                                listState = uploadListState,
                                recentListState = recentListState,
                                thumbOf = { container.history.thumbFile(it.id) },
                                onPick = {
                                    picker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                onPickAnyFile = { anyFilePicker.launch(arrayOf("*/*")) },
                                onPasteClipboard = onPasteClipboard,
                                onCopy = copyRendered,
                                onCopyActive = copyActive,
                                onRetry = viewModel::retry,
                                onRemoveTask = viewModel::removeTask,
                                onPreview = preview,
                            )
                        } else {
                            HistoryTab(
                                settings = settings,
                                layout = settings.historyLayout,
                                history = history,
                                deleting = deleting,
                                gridState = historyGridState,
                                thumbOf = { container.history.thumbFile(it.id) },
                                onLayoutChange = viewModel::setHistoryLayout,
                                onCopyActive = copyActive,
                                onCopy = copyRendered,
                                onDeleteRemote = viewModel::deleteRemoteFile,
                                onRemove = viewModel::removeFromHistory,
                                onClearHistory = viewModel::clearHistory,
                                onDeleteSelected = viewModel::deleteRemoteFiles,
                                onPreview = preview,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun defaultClipboardFileName(): String {
    val stamp = java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    return "clipboard-$stamp.txt"
}

@Composable
private fun ClipboardUploadDialog(
    initialName: String,
    characterCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上传剪贴板文本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("文件名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Text(
                    text = "共 $characterCount 个字符，按纯文本文件上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) },
            ) { Text("上传") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MainHeader(
    settings: AppSettings,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val remote = settings.activeRemote
    val account = settings.accountOf(remote)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "PicUp",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onBackground,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                AnimatedContent(
                    targetState = if (remote == null) "未配置仓库" else "${remote.name} · ${remote.branch}",
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 })
                    },
                    label = "repo-label",
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (!account?.login.isNullOrBlank()) {
            Text(
                text = account.login,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = colors.onSurface)
        }
    }
}

@Composable
private fun UploadTab(
    settings: AppSettings,
    tasks: List<UploadTask>,
    history: List<UploadRecord>,
    listState: LazyListState,
    recentListState: LazyListState,
    thumbOf: (UploadRecord) -> java.io.File,
    onPick: () -> Unit,
    onPickAnyFile: () -> Unit,
    onPasteClipboard: () -> Unit,
    onCopy: (UploadRecord, CopyFormat) -> Unit,
    onCopyActive: (UploadRecord) -> Unit,
    onRetry: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    onPreview: (UploadRecord) -> Unit,
) {
    val formats = settings.formatsEnabled()
    val context = LocalContext.current
    val recent = history.take(3)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp)) {
            PickCard(onPick = onPick, onPickAnyFile = onPickAnyFile, onPasteClipboard = onPasteClipboard)
        }
        Spacer(Modifier.height(8.dp))
        // Only the section below the picker crossfades; the picker itself and
        // the page chrome stay put.
        AnimatedContent(
            targetState = tasks.isNotEmpty(),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(320)) { height -> height / 16 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(220)) { height -> -height / 16 })
            },
            label = "upload-section",
        ) { showTasks ->
            if (showTasks) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "task-header") {
                        Row(
                            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionLabel("本次上传")
                        }
                    }
                    itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                        CardRow(
                            position = cardRowPosition(index, tasks.size),
                            modifier = Modifier.animateItem(
                                fadeInSpec = ItemFadeSpec,
                                placementSpec = ItemPlacementSpec,
                                fadeOutSpec = tween(160),
                            ),
                        ) {
                            TaskRowContent(
                                task = task,
                                onRetry = onRetry,
                                onRemove = onRemoveTask,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = recentListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (recent.isNotEmpty()) {
                        item(key = "recent-header") {
                            SectionLabel(
                                "最近上传",
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                        }
                        itemsIndexed(recent, key = { _, record -> "recent-${record.id}" }) { index, record ->
                            CardRow(position = cardRowPosition(index, recent.size)) {
                                RecentRowContent(
                                    record = record,
                                    thumb = thumbOf(record),
                                    formats = formats,
                                    onCopy = onCopy,
                                    onCopyActive = onCopyActive,
                                    onOpen = { context.openInBrowser(record.url) },
                                    onPreview = { onPreview(record) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PickCard(
    onPick: () -> Unit,
    onPickAnyFile: () -> Unit,
    onPasteClipboard: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "pick-scale",
    )
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = colors.cardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // Clip before the click so the ripple has no square corners.
            .clip(RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onPick,
            ),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 26.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.AddPhotoAlternate,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = "选择图片上传",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text(
                text = "JPG · PNG · WebP · GIF",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = onPickAnyFile,
                    modifier = Modifier.size(52.dp),
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.UploadFile,
                        contentDescription = "上传任意文件",
                        modifier = Modifier.size(24.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = onPasteClipboard,
                    modifier = Modifier.size(52.dp),
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = "上传剪贴板内容",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}


@Composable
private fun CopyFormatsRow(
    record: UploadRecord,
    formats: List<CopyFormat>,
    onCopy: (UploadRecord, CopyFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (formats.isEmpty()) return
    var copiedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copiedId) {
        if (copiedId != null) {
            delay(1200)
            copiedId = null
        }
    }
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(formats, key = { it.id }) { format ->
            val copied = copiedId == format.id
            PillChip(
                text = if (copied) "已复制" else format.label,
                icon = when {
                    copied -> Icons.Rounded.Check
                    format.label.contains("Markdown", true) -> Icons.Rounded.ContentCopy
                    else -> Icons.Rounded.Link
                },
                selected = copied,
                onClick = {
                    copiedId = format.id
                    onCopy(record, format)
                },
            )
        }
    }
}

@Composable
private fun TaskRowContent(
    task: UploadTask,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(source = ThumbnailSource.PickedUri(task.uri), size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val statusLine = when (task.status) {
                    UploadStatus.QUEUED -> "排队中"
                    UploadStatus.RUNNING -> task.stage.ifBlank { "上传中" }
                    UploadStatus.DONE -> {
                        val record = task.record
                        if (record == null) {
                            "完成"
                        } else if (record.width > 0 && record.height > 0) {
                            "${Naming.formatBytes(record.bytes)} · ${record.width}×${record.height}"
                        } else {
                            Naming.formatBytes(record.bytes)
                        }
                    }

                    UploadStatus.FAILED -> task.error ?: "上传失败"
                }
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == UploadStatus.FAILED) colors.error else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = task.status,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.8f)) togetherWith fadeOut(tween(120))
                },
                label = "task-status",
            ) { status ->
                when (status) {
                    UploadStatus.QUEUED, UploadStatus.RUNNING ->
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)

                    UploadStatus.DONE -> Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "已完成",
                        // Material role rather than a hardcoded green: the
                        // fixed theme is green, Material You follows the
                        // wallpaper, and both stay legible.
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    UploadStatus.FAILED -> Row {
                        IconButton(onClick = { onRetry(task.id) }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "重试", tint = colors.primary)
                        }
                        IconButton(onClick = { onRemove(task.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "移除", tint = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRowContent(
    record: UploadRecord,
    thumb: java.io.File,
    formats: List<CopyFormat>,
    onCopy: (UploadRecord, CopyFormat) -> Unit,
    onCopyActive: (UploadRecord) -> Unit,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCopyActive(record) }
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(
                source = ThumbnailSource.LocalFile(thumb),
                size = 48.dp,
                modifier = Modifier.clickable(onClick = onPreview),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${Naming.formatTime(record.uploadedAt)} · ${record.repoLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "打开链接", tint = colors.primary)
            }
        }
        CopyFormatsRow(
            record = record,
            formats = formats,
            onCopy = onCopy,
            modifier = Modifier
                .fillMaxWidth()
                // Right edge lines up with the open-in-browser button above.
                .padding(start = 12.dp, end = 12.dp),
        )
    }
}

@Composable
private fun HistoryTab(
    settings: AppSettings,
    layout: HistoryLayout,
    history: List<UploadRecord>,
    deleting: Set<String>,
    gridState: LazyGridState,
    thumbOf: (UploadRecord) -> java.io.File,
    onLayoutChange: (HistoryLayout) -> Unit,
    onCopyActive: (UploadRecord) -> Unit,
    onCopy: (UploadRecord, CopyFormat) -> Unit,
    onDeleteRemote: (UploadRecord) -> Unit,
    onRemove: (UploadRecord) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteSelected: (List<UploadRecord>) -> Unit,
    onPreview: (UploadRecord) -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeleteSelection by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selecting = selection.isNotEmpty()
    // Back inside a selection run only clears the selection.
    BackHandler(enabled = selecting) { selection = emptySet() }
    // Drop ids that left the list (deleted, cleared) so the toolbar cannot show
    // a stale count.
    LaunchedEffect(history) {
        val ids = history.map { it.id }.toSet()
        if (selection.any { it !in ids }) selection = selection.intersect(ids)
    }
    fun toggleSelection(id: String) {
        selection = if (id in selection) selection - id else selection + id
    }
    fun addToSelection(id: String) {
        selection = selection + id
    }
    val formats = settings.formatsEnabled()

    // The platform long-press delay (500 ms) reads as lag when the gesture is
    // "select this row". 260 ms feels immediate; the rest of the tap handling
    // still comes from the platform configuration.
    val platformViewConfiguration = LocalViewConfiguration.current
    val quickLongPress = remember(platformViewConfiguration) {
        object : ViewConfiguration by platformViewConfiguration {
            override val longPressTimeoutMillis: Long = 260L
        }
    }

    // Material fade-through: fade the grid out, relayout it with the other
    // column count while it is invisible, then fade it back. `renderMode` is
    // what is on screen; `layout` is what was asked for.
    var renderMode by remember { mutableStateOf(layout) }
    var userChoseMode by remember { mutableStateOf(false) }
    var swapping by remember { mutableStateOf(false) }
    val swap = remember { Animatable(1f) }
    LaunchedEffect(layout) {
        if (layout != renderMode) {
            if (userChoseMode) {
                swap.animateTo(0f, tween(90, easing = FastOutLinearInEasing))
                // Relayouting every visible cell is the expensive part. Do it
                // while the grid is invisible *and* after the toggle's pill has
                // arrived, otherwise the pill visibly freezes mid-travel.
                delay(120)
            }
            swapping = true
            renderMode = layout
        }
        // Never skip the tail: toggling back mid-fade restarts this effect with
        // layout already equal to renderMode, and an early return would strand
        // the grid half-transparent.
        if (swap.value < 1f) swap.animateTo(1f, tween(210, easing = LinearOutSlowInEasing))
        swapping = false
    }
    val swapProgress = swap.asState()
    val modeFade = Modifier.graphicsLayer {
        val progress = swapProgress.value
        alpha = progress
        scaleX = lerp(0.92f, 1f, progress)
        scaleY = scaleX
    }

    val modes = listOf(HistoryLayout.GRID, HistoryLayout.LIST)
    val grouped = remember(history) {
        history.groupBy { Naming.dayLabel(it.uploadedAt) }.toList()
    }

    CompositionLocalProvider(LocalViewConfiguration provides quickLongPress) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The left slot morphs from the record count into the selection
            // actions; the view toggle and clear button keep their place at the
            // right edge, so nothing else moves.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                AnimatedContent(
                    targetState = selecting,
                    transitionSpec = {
                        (fadeIn(tween(170)) + slideInHorizontally(tween(200)) { width -> -width / 4 }) togetherWith
                            (fadeOut(tween(120)) + slideOutHorizontally(tween(140)) { width -> width / 4 })
                    },
                    label = "history-left-slot",
                ) { inSelection ->
                    if (inSelection) {
                        Row {
                            IconButton(onClick = { selection = emptySet() }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "取消选择",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { confirmDeleteSelection = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "删除选中的文件",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    } else {
                        SectionLabel("${history.size} 条记录")
                    }
                }
            }
            PicUpIconSegmented(
                count = modes.size,
                selectedIndex = modes.indexOf(layout),
                onSelect = { index ->
                    userChoseMode = true
                    if (modes[index] != layout) onLayoutChange(modes[index])
                },
                modifier = Modifier.width(68.dp),
            ) { index, active ->
                Icon(
                    imageVector = when (index) {
                        0 -> if (active) Icons.Rounded.GridView else Icons.Outlined.GridView
                        else -> if (active) Icons.Rounded.ViewAgenda else Icons.Outlined.ViewAgenda
                    },
                    contentDescription = if (index == 0) "卡片视图" else "列表视图",
                    tint = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = { confirmClear = true }) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = "清空历史",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (history.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.History,
                title = "还没有上传记录",
                body = "上传成功的图片会出现在这里。",
            )
        } else {
            // One grid for both modes: only the column count changes, so every
            // record keeps its key, its thumbnail node and the scroll anchor.
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(if (renderMode == HistoryLayout.GRID) 3 else 1),
                modifier = Modifier
                    .fillMaxSize()
                    .then(modeFade),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    if (renderMode == HistoryLayout.GRID) 12.dp else 2.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(
                    if (renderMode == HistoryLayout.GRID) 12.dp else 2.dp,
                ),
            ) {
                grouped.forEach { (label, records) ->
                    item(
                        key = "day-$label",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "day",
                    ) {
                        SectionLabel(
                            label,
                            modifier = Modifier.padding(
                                top = if (renderMode == HistoryLayout.GRID) 0.dp else 10.dp,
                                bottom = 2.dp,
                            ),
                        )
                    }
                    itemsIndexed(
                        items = records,
                        key = { _, record -> record.id },
                        span = { _, _ -> GridItemSpan(1) },
                        contentType = { _, _ -> renderMode.name },
                    ) { index, record ->
                        val itemModifier = Modifier.animateItem(
                            fadeInSpec = ItemFadeSpec,
                            placementSpec = if (swapping) null else ItemPlacementSpec,
                            fadeOutSpec = tween(160),
                        )
                        if (renderMode == HistoryLayout.GRID) {
                            HistoryTile(
                                record = record,
                                thumb = thumbOf(record),
                                deleting = record.id in deleting,
                                selecting = selecting,
                                selected = record.id in selection,
                                onPreview = { onPreview(record) },
                                onToggleSelection = { toggleSelection(record.id) },
                                onLongPress = { addToSelection(record.id) },
                                modifier = itemModifier,
                            )
                        } else {
                            CardRow(
                                position = cardRowPosition(index, records.size),
                                modifier = itemModifier,
                            ) {
                                HistoryRowContent(
                                    record = record,
                                    thumb = thumbOf(record),
                                    deleting = record.id in deleting,
                                    formats = formats,
                                    activeFormatId = settings.activeFormatId,
                                    selecting = selecting,
                                    selected = record.id in selection,
                                    onPreview = { onPreview(record) },
                                    onToggleSelection = { toggleSelection(record.id) },
                                    onLongPress = { addToSelection(record.id) },
                                    onCopyActive = onCopyActive,
                                    onCopy = onCopy,
                                    onDeleteRemote = onDeleteRemote,
                                    onRemove = onRemove,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    }

    if (confirmDeleteSelection) {
        val targets = history.filter { it.id in selection }
        AlertDialog(
            onDismissRequest = { confirmDeleteSelection = false },
            title = { Text("删除选中的 ${targets.size} 个文件？") },
            text = {
                Text("会在仓库里生成 ${targets.size} 次删除提交，已经引用这些链接的页面会显示不出来，且无法撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteSelection = false
                    selection = emptySet()
                    onDeleteSelected(targets)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelection = false }) { Text("取消") }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空上传历史？") },
            text = { Text("只删除本机的记录和缩略图，仓库里的图片不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearHistory()
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun HistoryRowContent(
    record: UploadRecord,
    thumb: java.io.File,
    deleting: Boolean,
    formats: List<CopyFormat>,
    activeFormatId: String,
    selecting: Boolean,
    selected: Boolean,
    onPreview: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
    onCopyActive: (UploadRecord) -> Unit,
    onCopy: (UploadRecord, CopyFormat) -> Unit,
    onDeleteRemote: (UploadRecord) -> Unit,
    onRemove: (UploadRecord) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val activeFormat = formats.firstOrNull { it.id == activeFormatId } ?: formats.firstOrNull()
    // Built once per record: selection changes recompose every visible row, and
    // re-formatting the metadata on each of those frames is wasted work.
    val meta = remember(record) {
        buildString {
            append(Naming.formatTime(record.uploadedAt))
            append(" · ")
            append(Naming.formatBytes(record.bytes))
            if (record.width > 0 && record.height > 0) {
                append(" · ")
                append(record.width)
                append('×')
                append(record.height)
            }
        }
    }

    // The ripple is attached to the whole row, so it also covers the overflow
    // button; the button keeps its own click target.
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (selecting) onToggleSelection() else onCopyActive(record) },
                    onLongClick = onLongPress,
                )
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Thumbnail(
                    source = ThumbnailSource.LocalFile(thumb),
                    size = 56.dp,
                    modifier = Modifier.clickable(enabled = !selecting, onClick = onPreview),
                )
                SelectionDot(
                    visible = selecting,
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = record.path,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (deleting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else if (!selecting) {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        HistoryMenuItems(
                            record = record,
                            formats = formats,
                            activeFormat = activeFormat,
                            onCopyActive = onCopyActive,
                            onCopy = onCopy,
                            onDeleteRemote = { confirmDelete = true },
                            onRemove = onRemove,
                            close = { expanded = false },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        DeleteRemoteDialog(
            record = record,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDeleteRemote(record)
            },
        )
    }
}

/** Animated selection circle, shared by the list row and the grid tile. */
/**
 * Animated selection circle.
 *
 * It is drawn as an overlay (alpha + scale in the graphics layer) instead of a
 * row-inset child: animating the layout of every visible row on the first long
 * press was the reason the list stuttered for ~200 ms.
 */
@Composable
private fun SelectionDot(
    visible: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(140),
        label = "selection-dot-alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "selection-dot-scale",
    )
    // Toggling a single item must animate too, not just the first long press.
    val fill by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainerHighest,
        label = "selection-dot-fill",
    )
    val ring by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) colors.primary else colors.outline,
        label = "selection-dot-ring",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(130),
        label = "selection-dot-check-alpha",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "selection-dot-check-scale",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = if (selected) "已选择" else null,
            tint = colors.onPrimary,
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer {
                    this.alpha = checkAlpha
                    scaleX = checkScale
                    scaleY = checkScale
                },
        )
    }
}

@Composable
private fun HistoryTile(
    record: UploadRecord,
    thumb: java.io.File,
    deleting: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onPreview: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(
                    onClick = { if (selecting) onToggleSelection() else onPreview() },
                    onLongClick = onLongPress,
                ),
        ) {
            Thumbnail(
                source = ThumbnailSource.LocalFile(thumb),
                modifier = Modifier.fillMaxSize(),
                size = null,
                corner = 20.dp,
            )
            val tint by animateFloatAsState(
                targetValue = if (selected) 0.22f else 0f,
                animationSpec = tween(160),
                label = "tile-selection-tint",
            )
            if (tint > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = tint)),
                )
            }
            SelectionDot(
                visible = selecting,
                selected = selected,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
            if (deleting) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = record.fileName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HistoryMenuItems(
    record: UploadRecord,
    formats: List<CopyFormat>,
    activeFormat: CopyFormat?,
    onCopyActive: (UploadRecord) -> Unit,
    onCopy: (UploadRecord, CopyFormat) -> Unit,
    onDeleteRemote: () -> Unit,
    onRemove: (UploadRecord) -> Unit,
    close: () -> Unit,
) {
    val context = LocalContext.current
    activeFormat?.let { format ->
        DropdownMenuItem(
            text = { Text("复制${format.label}") },
            onClick = {
                close()
                onCopyActive(record)
            },
        )
    }
    formats.forEach { format ->
        if (format.id != activeFormat?.id) {
            DropdownMenuItem(
                text = { Text("复制${format.label}") },
                onClick = {
                    close()
                    onCopy(record, format)
                },
            )
        }
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("在浏览器打开") },
        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
        onClick = {
            close()
            if (!context.openInBrowser(record.url)) {
                context.copyPlainText("链接", record.url)
            }
        },
    )
    DropdownMenuItem(
        text = { Text("分享链接") },
        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
        onClick = {
            close()
            if (!context.shareText(record.url, record.fileName)) {
                context.copyPlainText("链接", record.url)
            }
        },
    )
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("从仓库删除") },
        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        onClick = {
            close()
            onDeleteRemote()
        },
    )
    DropdownMenuItem(
        text = { Text("从历史移除") },
        leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
        onClick = {
            close()
            onRemove(record)
        },
    )
}

@Composable
private fun DeleteRemoteDialog(
    record: UploadRecord,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从仓库删除这张图片？") },
        text = {
            Text(
                "会在 ${record.repoLabel} 里生成一次删除提交：${record.path}。\n" +
                    "已经引用这个链接的页面会显示不出来，且无法撤销。",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}