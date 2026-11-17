package com.example.picup.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.picup.AppContainer
import com.example.picup.data.AccountProfile
import com.example.picup.data.AppSettings
import com.example.picup.data.CopyFormat
import com.example.picup.data.CopyFormatRenderer
import com.example.picup.data.DEFAULT_PATH_TEMPLATE
import com.example.picup.data.LinkValues
import com.example.picup.data.OutputFormat
import com.example.picup.data.PathTemplate
import com.example.picup.data.QualityPreset
import com.example.picup.data.Remote
import com.example.picup.data.RemoteUrls
import com.example.picup.git.plugins.GitHostPlugins
import com.example.picup.data.TemplateValues
import com.example.picup.data.ThemeChoice
import com.example.picup.data.UploadOptions
import com.example.picup.ui.components.CardRow
import com.example.picup.ui.components.CardRowPosition
import com.example.picup.ui.components.IconBubble
import com.example.picup.ui.components.PicUpDropdownMenu
import com.example.picup.ui.components.PicUpSegmented
import com.example.picup.ui.components.PillChip
import com.example.picup.ui.components.SectionLabel
import com.example.picup.ui.components.SettingRow
import com.example.picup.ui.components.SettingSwitchRow
import com.example.picup.ui.components.TopBarBack
import com.example.picup.ui.components.cardRowPosition
import com.example.picup.ui.openInBrowser
import com.example.picup.ui.theme.floatingSurface
import java.time.LocalDateTime

@Composable
fun SettingsScreen(
    container: AppContainer,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onAddRemote: () -> Unit,
) {
    // The ViewModel is the activity-scoped one from the root: creating a
    // destination-scoped copy would start from `AppSettings()` defaults and
    // every real row would animate in a frame after the screen opens.
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var editRemote by remember { mutableStateOf<Remote?>(null) }
    var editFormat by remember { mutableStateOf<CopyFormat?>(null) }
    var addFormat by remember { mutableStateOf(false) }
    var deleteRemote by remember { mutableStateOf<Remote?>(null) }
    var deleteAccount by remember { mutableStateOf<AccountProfile?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    val accentContainer = MaterialTheme.colorScheme.tertiaryContainer
    val accentInk = MaterialTheme.colorScheme.onTertiaryContainer
    val accent = MaterialTheme.colorScheme.tertiary

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TopBarBack(title = "设置", onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // ---------------------------------------------------------- repositories
                item(key = "repos-label") { GroupLabel("图床仓库", settingsItem(), first = true) }
                val repoTotal = settings.remotes.size + 1
                itemsIndexed(settings.remotes, key = { _, remote -> "repo-${remote.id}" }) { index, remote ->
                    SettingsCard(
                        position = cardRowPosition(index, repoTotal),
                        modifier = settingsItem(),
                    ) {
                        RemoteRow(
                            remote = remote,
                            account = settings.accountOf(remote),
                            selected = remote.id == settings.activeRemote?.id,
                            onSelect = { viewModel.setActiveRemote(remote.id) },
                            onEdit = { editRemote = remote },
                            onDelete = { deleteRemote = remote },
                        )
                    }
                }
                item(key = "repos-add") {
                    SettingsCard(
                        position = cardRowPosition(repoTotal - 1, repoTotal),
                        modifier = settingsItem(),
                    ) {
                        SettingRow(
                            icon = Icons.Rounded.Add,
                            title = "添加仓库",
                            subtitle = "可配置多个仓库",
                            onClick = onAddRemote,
                            iconContainer = accentContainer,
                            iconTint = accentInk,
                            titleColor = accent,
                        )
                    }
                }

                // ---------------------------------------------------------- accounts
                item(key = "accounts-label") { GroupLabel("账号", settingsItem()) }
                val accountTotal = settings.accounts.size + 1
                itemsIndexed(settings.accounts, key = { _, account -> "account-${account.id}" }) { index, account ->
                    SettingsCard(
                        position = cardRowPosition(index, accountTotal),
                        modifier = settingsItem(),
                    ) {
                        AccountRow(account = account, onDelete = { deleteAccount = account })
                    }
                }
                item(key = "accounts-add") {
                    SettingsCard(
                        position = cardRowPosition(accountTotal - 1, accountTotal),
                        modifier = settingsItem(),
                    ) {
                        SettingRow(
                            icon = Icons.Rounded.Key,
                            title = "添加账号",
                            subtitle = "GitHub · GitLab · Gitea",
                            onClick = onAddRemote,
                            iconContainer = accentContainer,
                            iconTint = accentInk,
                            titleColor = accent,
                        )
                    }
                }

                // ---------------------------------------------------------- plugins
                item(key = "plugins-label") { GroupLabel("插件", settingsItem()) }
                val pluginTotal = GitHostPlugins.all.size
                GitHostPlugins.all.forEachIndexed { index, plugin ->
                    item(key = "plugin-${plugin.id}") {
                        SettingsCard(
                            position = cardRowPosition(index, pluginTotal),
                            modifier = settingsItem(),
                        ) {
                            SettingSwitchRow(
                                icon = Icons.Rounded.Extension,
                                title = plugin.displayName,
                                subtitle = plugin.summary,
                                checked = plugin.id in settings.enabledPlugins,
                                onCheckedChange = { enabled ->
                                    viewModel.setPluginEnabled(plugin.id, enabled)
                                },
                            )
                        }
                    }
                }

                // ---------------------------------------------------------- upload
                item(key = "upload-label") { GroupLabel("上传", settingsItem()) }
                item(key = "upload-format") {
                    SettingsCard(position = CardRowPosition.FIRST, modifier = settingsItem()) {
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PicUpSegmented(
                                options = listOf(
                                    OutputFormat.PRESERVE,
                                    OutputFormat.WEBP,
                                    OutputFormat.JPEG,
                                    OutputFormat.PNG,
                                ),
                                selected = settings.upload.format,
                                onSelect = { format ->
                                    viewModel.updateUpload { it.copy(format = format) }
                                },
                                label = {
                                    when (it) {
                                        OutputFormat.PRESERVE -> "原格式"
                                        OutputFormat.WEBP -> "WebP"
                                        OutputFormat.JPEG -> "JPEG"
                                        OutputFormat.PNG -> "PNG"
                                    }
                                },
                            )
                            if (settings.upload.format == OutputFormat.WEBP ||
                                settings.upload.format == OutputFormat.JPEG
                            ) {
                                PicUpSegmented(
                                    options = QualityPreset.entries,
                                    selected = settings.upload.qualityPreset,
                                    onSelect = { preset ->
                                        viewModel.updateUpload { it.copy(quality = preset.quality) }
                                    },
                                    label = {
                                        when (it) {
                                            QualityPreset.ORIGINAL -> "原图"
                                            QualityPreset.BALANCED -> "压缩"
                                            QualityPreset.LIGHT -> "轻量"
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "upload-exif") {
                    SettingsCard(position = CardRowPosition.MIDDLE, modifier = settingsItem()) {
                        SettingSwitchRow(
                            icon = Icons.Rounded.Photo,
                            title = "移除 EXIF",
                            subtitle = null,
                            checked = settings.upload.stripExif,
                            onCheckedChange = { viewModel.updateUpload { options -> options.copy(stripExif = it) } },
                        )
                    }
                }
                item(key = "upload-case") {
                    SettingsCard(position = CardRowPosition.MIDDLE, modifier = settingsItem()) {
                        SettingSwitchRow(
                            icon = Icons.Rounded.Tune,
                            title = "文件名转小写",
                            subtitle = null,
                            checked = settings.upload.lowerCaseNames,
                            onCheckedChange = { viewModel.updateUpload { options -> options.copy(lowerCaseNames = it) } },
                        )
                    }
                }
                item(key = "upload-path") {
                    SettingsCard(position = CardRowPosition.LAST, modifier = settingsItem()) {
                        PathTemplateField(
                            options = settings.upload,
                            onChange = { updated -> viewModel.updateUpload { _ -> updated } },
                        )
                    }
                }

                // ---------------------------------------------------------- copy formats
                item(key = "formats-label") { GroupLabel("复制格式", settingsItem()) }
                val formatTotal = settings.formats.size + 2
                itemsIndexed(settings.formats, key = { _, format -> "format-${format.id}" }) { index, format ->
                    SettingsCard(
                        position = cardRowPosition(index, formatTotal),
                        modifier = settingsItem(),
                    ) {
                        FormatRow(
                            format = format,
                            active = format.id == settings.activeFormatId && format.enabled,
                            onToggle = { enabled -> viewModel.setFormatEnabled(format.id, enabled) },
                            onSetActive = { viewModel.setActiveFormat(format.id) },
                            onEdit = { editFormat = format },
                        )
                    }
                }
                item(key = "formats-add") {
                    SettingsCard(
                        position = cardRowPosition(formatTotal - 2, formatTotal),
                        modifier = settingsItem(),
                    ) {
                        SettingRow(
                            icon = Icons.Rounded.Add,
                            title = "添加自定义格式",
                            subtitle = "支持 {url} {name} {path} 等占位符",
                            onClick = { addFormat = true },
                            iconContainer = accentContainer,
                            iconTint = accentInk,
                            titleColor = accent,
                        )
                    }
                }
                item(key = "formats-reset") {
                    SettingsCard(
                        position = cardRowPosition(formatTotal - 1, formatTotal),
                        modifier = settingsItem(),
                    ) {
                        SettingRow(
                            icon = Icons.Rounded.Palette,
                            title = "恢复默认格式",
                            onClick = viewModel::resetFormats,
                        )
                    }
                }

                // ---------------------------------------------------------- appearance
                item(key = "appearance-label") { GroupLabel("外观", settingsItem()) }
                item(key = "appearance-theme") {
                    SettingsCard(position = CardRowPosition.FIRST, modifier = settingsItem()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            PicUpSegmented(
                                options = ThemeChoice.entries,
                                selected = settings.theme,
                                onSelect = viewModel::setTheme,
                                label = {
                                    when (it) {
                                        ThemeChoice.SYSTEM -> "跟随系统"
                                        ThemeChoice.LIGHT -> "浅色"
                                        ThemeChoice.DARK -> "深色"
                                    }
                                },
                            )
                        }
                    }
                }
                item(key = "appearance-dynamic") {
                    SettingsCard(position = CardRowPosition.LAST, modifier = settingsItem()) {
                        SettingSwitchRow(
                            icon = Icons.Rounded.Palette,
                            title = "动态取色",
                            subtitle = "使用壁纸配色（Android 12+）",
                            checked = settings.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    }
                }

                // ---------------------------------------------------------- about
                item(key = "about-label") { GroupLabel("关于", settingsItem()) }
                item(key = "about-version") {
                    val version = androidx.compose.ui.res.stringResource(
                        com.example.picup.R.string.picup_release_version,
                    )
                    SettingsCard(modifier = settingsItem()) {
                        SettingRow(
                            icon = Icons.Rounded.Info,
                            title = "PicUp v$version",
                            subtitle = "Git 仓库图床",
                            onClick = { showAbout = true },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                    }
                }
                item(key = "bottom-gap") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    editRemote?.let { remote ->
        RemoteEditDialog(
            remote = remote,
            providerId = settings.accountOf(remote)?.provider ?: GitHostPlugins.fallback.id,
            host = settings.accountOf(remote)?.host.orEmpty(),
            branchesProvider = { viewModel.branchesFor(remote) },
            onDismiss = { editRemote = null },
            onSave = { updated ->
                viewModel.saveRemote(updated)
                editRemote = null
            },
        )
    }

    if (addFormat || editFormat != null) {
        FormatEditDialog(
            existing = editFormat,
            onDismiss = {
                addFormat = false
                editFormat = null
            },
            onSave = { format ->
                viewModel.saveFormat(format)
                addFormat = false
                editFormat = null
            },
            onDelete = { id ->
                viewModel.deleteFormat(id)
                addFormat = false
                editFormat = null
            },
        )
    }

    deleteRemote?.let { remote ->
        AlertDialog(
            onDismissRequest = { deleteRemote = null },
            title = { Text("移除仓库 ${remote.name}？") },
            text = { Text("只从 PicUp 里移除这个仓库的配置，仓库本身和已经上传的图片都不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRemote(remote)
                    deleteRemote = null
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { deleteRemote = null }) { Text("取消") } },
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    deleteAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteAccount = null },
            title = { Text("退出账号 ${account.login}？") },
            text = { Text("Token 会从本机安全存储里删除，并使用这个账号的仓库配置也会一起移除。远端仓库与图片不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAccount(account)
                    deleteAccount = null
                }) { Text("退出并移除") }
            },
            dismissButton = { TextButton(onClick = { deleteAccount = null }) { Text("取消") } },
        )
    }
}

private val RowFadeIn = androidx.compose.animation.core.spring<Float>(
    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
)

private val RowPlacement = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(
    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
    visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold,
)

private val RowFadeOut = androidx.compose.animation.core.tween<Float>(200)

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val version = androidx.compose.ui.res.stringResource(com.example.picup.R.string.picup_release_version)
    val context = LocalContext.current
    val repo = "https://github.com/wzk0/android_picup"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于 PicUp") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "版本 v$version",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "更好的 Git 图床 app。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    com.example.picup.ui.components.CardRow(
                        position = cardRowPosition(0, 3),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        SettingRow(
                            icon = Icons.Rounded.Code,
                            title = "源码仓库",
                            subtitle = null,
                            onClick = { context.openInBrowser(repo) },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    com.example.picup.ui.components.CardRow(
                        position = cardRowPosition(1, 3),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        SettingRow(
                            icon = Icons.Outlined.BugReport,
                            title = "问题反馈",
                            subtitle = null,
                            onClick = { context.openInBrowser("$repo/issues") },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    com.example.picup.ui.components.CardRow(
                        position = cardRowPosition(2, 3),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        SettingRow(
                            icon = Icons.Rounded.Description,
                            title = "GPL V3",
                            subtitle = null,
                        )
                    }
                }
                Text(
                    text = "Jetpack Compose · Material Design 3 Expressive",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun onSaveFormat(
    existing: CopyFormat?,
    label: String,
    template: String,
    onSave: (CopyFormat) -> Unit,
) {
    onSave(
        existing?.copy(label = label.trim(), template = template)
            ?: CopyFormat(
                id = "custom-${System.currentTimeMillis()}",
                label = label.trim(),
                template = template,
            ),
    )
}

@Composable
private fun GroupLabel(
    text: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
) {
    SectionLabel(
        text = text,
        modifier = modifier.padding(top = if (first) 6.dp else 14.dp, bottom = 2.dp),
    )
}

/** Insert/remove motion for one settings entry. */
@Suppress("ModifierFactoryExtensionFunction")
private fun androidx.compose.foundation.lazy.LazyItemScope.settingsItem(): Modifier = Modifier.animateItem(
    fadeInSpec = RowFadeIn,
    placementSpec = RowPlacement,
    fadeOutSpec = RowFadeOut,
)

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    position: CardRowPosition = CardRowPosition.SINGLE,
    content: @Composable ColumnScope.() -> Unit,
) {
    CardRow(modifier = modifier, position = position, content = content)
}

@Composable
private fun RemoteRow(
    remote: Remote,
    account: AccountProfile?,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val plugin = GitHostPlugins.byId(account?.provider)
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bubbleColor by animateColorAsState(
            targetValue = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            label = "remote-bubble",
        )
        val bubbleTint by animateColorAsState(
            targetValue = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            label = "remote-bubble-tint",
        )
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.7f)) togetherWith fadeOut(tween(120))
            },
            label = "remote-selected",
        ) { isSelected ->
            IconBubble(
                icon = if (isSelected) Icons.Rounded.Check else Icons.Rounded.Cloud,
                containerColor = bubbleColor,
                tint = bubbleTint,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = remote.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${plugin.displayName} · ${remote.branch}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "仓库选项",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PicUpDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("编辑") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        menu = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("移除") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}


@Composable
private fun AccountRow(account: AccountProfile, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(
            icon = Icons.Rounded.Person,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = account.login.ifBlank { "未命名账号" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${GitHostPlugins.byId(account.provider).displayName} · ${
                    GitHostPlugins.byId(account.provider).displayHost(account.host)
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "账号选项",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PicUpDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("退出账号") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}


@Composable
private fun FormatRow(
    format: CopyFormat,
    active: Boolean,
    onToggle: (Boolean) -> Unit,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(
            icon = Icons.Rounded.AlternateEmail,
            containerColor = if (format.enabled) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            tint = if (format.enabled) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = format.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (format.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedVisibility(
                    visible = active,
                    enter = fadeIn(tween(180)) + scaleIn(tween(200), initialScale = 0.8f),
                    exit = fadeOut(tween(120)),
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(
                            text = "默认",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = format.template,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val checkTint by animateColorAsState(
            targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            label = "format-check",
        )
        IconButton(onClick = onSetActive) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "设为默认",
                tint = checkTint,
            )
        }
        androidx.compose.material3.Switch(
            checked = format.enabled,
            onCheckedChange = onToggle,
        )
    }
}


@Composable
private fun PathTemplateField(options: UploadOptions, onChange: (UploadOptions) -> Unit) {
    var template by remember(options.pathTemplate) { mutableStateOf(options.pathTemplate) }
    val preview = remember(template) { renderPathPreview(template) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "路径模板",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = template,
            onValueChange = { template = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            placeholder = { Text(DEFAULT_PATH_TEMPLATE) },
            shape = RoundedCornerShape(16.dp),
        )
        Text(
            text = "预览：$preview",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "占位符：{yyyy} {MM} {dd} 年/月/日，{HH} {mm} {ss} 时/分/秒，{name} 文件名，" +
                "{ext} 扩展名，{rand6} 随机串，{hash} 内容哈希，{width} {height} 像素尺寸，" +
                "{size} 字节数，{owner} {repo} {branch} 仓库信息。",
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(PATH_TOKENS) { token ->
                PillChip(text = token, onClick = { template += token })
            }
        }
        if (template != options.pathTemplate) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onChange(options.copy(pathTemplate = template)) }) { Text("保存模板") }
                TextButton(onClick = { template = options.pathTemplate }) { Text("撤销") }
                TextButton(onClick = { template = DEFAULT_PATH_TEMPLATE }) { Text("恢复默认") }
            }
        }
    }
}


private val PATH_TOKENS = listOf(
    "{yyyy}", "{MM}", "{dd}", "{HH}", "{mm}", "{ss}",
    "{name}", "{ext}", "{rand6}", "{hash}", "{width}", "{height}", "{size}",
)

private fun renderPathPreview(template: String): String = PathTemplate.render(
    template,
    TemplateValues(
        name = "sunset",
        ext = "webp",
        width = 1920,
        height = 1080,
        sizeBytes = 248_000,
        hash = "3f9a1c2d5e6b",
        owner = "you",
        repo = "images",
        branch = "main",
        provider = "github",
        now = LocalDateTime.now(),
        random = "a1b2c3d4e5f6",
    ),
)


@Composable
private fun RemoteEditDialog(
    remote: Remote,
    providerId: String,
    host: String,
    branchesProvider: suspend () -> List<String>,
    onDismiss: () -> Unit,
    onSave: (Remote) -> Unit,
) {
    var branch by remember(remote.id) { mutableStateOf(remote.branch) }
    var template by remember(remote.id) { mutableStateOf(remote.pathTemplate) }
    var publicBase by remember(remote.id) { mutableStateOf(remote.publicBase) }
    var label by remember(remote.id) { mutableStateOf(remote.label) }
    var branches by remember(remote.id) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(remote.id) { branches = branchesProvider() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑 ${remote.slug}") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("分支") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                if (branches.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(branches) { name ->
                            PillChip(text = name, selected = name == branch, onClick = { branch = name })
                        }
                    }
                }
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("路径模板（留空跟随全局）") },
                    placeholder = { Text(DEFAULT_PATH_TEMPLATE) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = publicBase,
                    onValueChange = { publicBase = it },
                    label = { Text("公开链接前缀（可选）") },
                    placeholder = { Text("https://cdn.jsdelivr.net/gh/{owner}/{repo}@{branch}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (providerId == "github") {
                        PillChip(
                            text = "使用 jsDelivr",
                            icon = Icons.Rounded.Cloud,
                            onClick = {
                                publicBase = RemoteUrls.jsdelivrBase(
                                    remote.owner,
                                    remote.repo,
                                    branch.ifBlank { remote.branch },
                                )
                            },
                        )
                    }
                    if (publicBase.isNotBlank()) {
                        PillChip(text = "清除前缀", onClick = { publicBase = "" })
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("显示名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Text(
                    text = "预览：${
                        GitHostPlugins.byId(providerId).publicUrl(
                            host = host,
                            owner = remote.owner,
                            repo = remote.repo,
                            branch = branch.ifBlank { remote.branch },
                            path = renderPathPreview(template.ifBlank { DEFAULT_PATH_TEMPLATE }),
                            publicBase = publicBase,
                        )
                    }",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    remote.copy(
                        branch = branch.trim().ifBlank { remote.branch },
                        pathTemplate = template.trim(),
                        publicBase = publicBase.trim(),
                        label = label.trim(),
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}


@Composable
private fun FormatEditDialog(
    existing: CopyFormat?,
    onDismiss: () -> Unit,
    onSave: (CopyFormat) -> Unit,
    onDelete: (String) -> Unit,
) {
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var template by remember { mutableStateOf(existing?.template ?: "{url}") }
    val preview = CopyFormatRenderer.render(
        template,
        LinkValues(
            url = "https://cdn.example.com/uploads/2026/01/sunset-a1b2c3.webp",
            path = "uploads/2026/01/sunset-a1b2c3.webp",
            fileName = "sunset-a1b2c3.webp",
            ext = "webp",
            repo = "you/images",
            branch = "main",
            sizeBytes = 248_000,
            width = 1920,
            height = 1080,
            uploadedAtMillis = System.currentTimeMillis(),
        ),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加自定义格式" else "编辑自定义格式") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("模板") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(14.dp),
                )
                Text(
                    text = "预览：$preview",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CopyFormatRenderer.knownPlaceholders()) { token ->
                        PillChip(text = "{$token}", onClick = { template += "{$token}" })
                    }
                }
                Text(
                    text = "占位符：{url} 图片直链，{markdown} 现成的 Markdown 图片语法，{path} 仓库路径，" +
                        "{name} 文件名，{file} 完整文件名，{ext} 扩展名，{repo} 仓库，{branch} 分支，" +
                        "{size} 体积，{width} {height} 尺寸，{date} {time} 上传时间。",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (existing != null) {
                // 删除 / 取消 / 保存 share one row and one weight each, so the
                // gaps between them are identical by construction.
                Row(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { onDelete(existing.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                    TextButton(
                        enabled = label.isNotBlank() && template.isNotBlank(),
                        onClick = { onSaveFormat(existing, label, template, onSave) },
                        modifier = Modifier.weight(1f),
                    ) { Text("保存") }
                }
            } else {
                TextButton(
                    enabled = label.isNotBlank() && template.isNotBlank(),
                    onClick = { onSaveFormat(null, label, template, onSave) },
                ) { Text("保存") }
            }
        },
        dismissButton = if (existing == null) {
            { TextButton(onClick = onDismiss) { Text("取消") } }
        } else {
            null
        },
    )
}
