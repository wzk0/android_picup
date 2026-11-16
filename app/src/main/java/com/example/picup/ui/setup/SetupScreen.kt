package com.example.picup.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.picup.AppContainer
import com.example.picup.data.DEFAULT_PATH_TEMPLATE
import com.example.picup.data.PathTemplate
import com.example.picup.data.RemoteUrls
import com.example.picup.data.TemplateValues
import com.example.picup.git.RepoSummary
import com.example.picup.git.plugins.GitHostPlugin
import com.example.picup.git.plugins.GitHostPlugins
import com.example.picup.ui.components.EmptyState
import com.example.picup.ui.components.CardRow
import com.example.picup.ui.components.IconBubble
import com.example.picup.ui.components.cardRowPosition
import com.example.picup.ui.components.PicUpSegmented
import com.example.picup.ui.components.PillChip
import com.example.picup.ui.components.SectionLabel
import com.example.picup.ui.components.TopBarBack
import com.example.picup.ui.theme.cardSurface
import com.example.picup.ui.openInBrowser
import java.time.LocalDateTime

@Composable
fun SetupScreen(
    container: AppContainer,
    sessionKey: Int,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel: SetupViewModel = viewModel(
        key = "setup-$sessionKey",
        factory = SetupViewModel.factory(container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val plugins = GitHostPlugins.enabled(settings.enabledPlugins)

    // If the chosen plugin was disabled in settings, land on one that is on.
    LaunchedEffect(plugins) {
        if (plugins.isNotEmpty() && plugins.none { it.id == state.provider }) {
            viewModel.setProvider(plugins.first().id)
        }
    }

    // Step 0 is a real route change, so the root predictive-back handler owns
    // it; steps 1 and 2 are in-wizard navigation.
    BackHandler(enabled = state.step > 0) { viewModel.back() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TopBarBack(
                title = when (state.step) {
                    0 -> "登录 Git 账号"
                    1 -> "选择仓库"
                    else -> "存放位置"
                },
                subtitle = when (state.step) {
                    0 -> "Token 只保存在本机"
                    1 -> state.login.ifBlank { null }
                    else -> "${state.selectedRepo?.fullName.orEmpty()} · ${state.branch}"
                },
                onBack = { if (!viewModel.back()) onBack() },
            )
            StepIndicator(step = state.step)
            AnimatedContent(
                targetState = state.step,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    val forward = targetState > initialState
                    val springSpec = spring<IntOffset>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = IntOffset.VisibilityThreshold,
                    )
                    val enter = slideInHorizontally(
                        animationSpec = springSpec,
                        initialOffsetX = { width -> if (forward) width / 3 else -width / 3 },
                    ) + fadeIn(tween(220))
                    val exit = slideOutHorizontally(
                        animationSpec = springSpec,
                        targetOffsetX = { width -> if (forward) -width / 3 else width / 3 },
                    ) + fadeOut(tween(160))
                    enter togetherWith exit
                },
                label = "setup-step",
            ) { step ->
                when (step) {
                    0 -> AccountStep(
                        state = state,
                        existingAccounts = settings.accounts,
                        onProvider = viewModel::setProvider,
                        onHost = viewModel::setHost,
                        onToken = viewModel::setToken,
                        onToggleVisible = viewModel::toggleTokenVisible,
                        onUseAccount = viewModel::useExistingAccount,
                        plugins = plugins,
                        onOpenTokenPage = {
                            context.openInBrowser(
                                GitHostPlugins.byId(state.provider).tokenPageUrl(state.host),
                            )
                        },
                    )

                    1 -> RepositoryStep(
                        state = state,
                        onQuery = viewModel::setRepoQuery,
                        onSelect = viewModel::selectRepo,
                        onManualMode = viewModel::setManualMode,
                        onManualOwner = viewModel::setManualOwner,
                        onManualRepo = viewModel::setManualRepo,
                        onApplyManual = viewModel::applyManualRepo,
                        onBranch = viewModel::setBranch,
                    )

                    else -> LocationStep(
                        state = state,
                        onTemplate = viewModel::setPathTemplate,
                        onPublicBase = viewModel::setPublicBase,
                        onUseJsdelivr = viewModel::useJsdelivr,
                    )
                }
            }
            // Keep the last message while the card animates out, otherwise the
            // text would blank one frame before the collapse finishes.
            var shownError by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(state.error) {
                if (state.error != null) shownError = state.error
            }
            AnimatedVisibility(
                visible = state.error != null,
                enter = expandVertically(tween(220)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(140)),
            ) {
                shownError?.let { message ->
                    ErrorCard(message = message, onDismiss = viewModel::dismissError)
                }
            }
            BottomBar(
                step = state.step,
                busy = state.verifying || state.saving,
                enabled = when (state.step) {
                    0 -> state.token.isNotBlank() && !state.verifying
                    1 -> state.selectedRepo != null
                    else -> !state.saving
                },
                label = when (state.step) {
                    0 -> if (state.verifying) "正在校验…" else "登录并读取仓库"
                    1 -> "下一步"
                    else -> if (state.saving) "正在保存…" else "保存并开始使用"
                },
                onBack = { viewModel.back() },
                onPrimary = {
                    focus.clearFocus()
                    if (state.step == 2) viewModel.save(onDone) else viewModel.next()
                },
            )
        }
    }
}

/**
 * The progress bar is one continuous value animated between steps, so the
 * highlight slides and stretches across the three slots instead of jumping.
 */
/** Small identity mark per plugin, used on the saved-account chips. */
private fun pluginIcon(pluginId: String): androidx.compose.ui.graphics.vector.ImageVector = when (pluginId) {
    "github" -> Icons.Rounded.Code
    "gitlab" -> Icons.Rounded.Terminal
    "gitee" -> Icons.Rounded.Public
    "codeberg" -> Icons.Rounded.TravelExplore
    else -> Icons.Rounded.Cloud
}

/** Compact label: the wizard needs a tighter title→field rhythm than settings. */
@Composable
private fun StepLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp),
    )
}

@Composable
private fun StepIndicator(step: Int) {
    val progress by animateFloatAsState(
        targetValue = step.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "step-progress",
    )
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { index ->
            val fill = (progress - index).coerceIn(0f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.surfaceContainerHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fill)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.primary),
                )
            }
        }
    }
}

@Composable
private fun AccountStep(
    state: SetupState,
    existingAccounts: List<com.example.picup.data.AccountProfile>,
    plugins: List<GitHostPlugin>,
    onProvider: (String) -> Unit,
    onHost: (String) -> Unit,
    onToken: (String) -> Unit,
    onToggleVisible: () -> Unit,
    onUseAccount: (com.example.picup.data.AccountProfile) -> Unit,
    onOpenTokenPage: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepLabel("平台")
        val plugin = GitHostPlugins.byId(state.provider)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(plugins, key = { it.id }) { candidate ->
                PillChip(
                    text = candidate.displayName,
                    selected = candidate.id == state.provider,
                    onClick = { onProvider(candidate.id) },
                )
            }
        }
        Text(
            text = plugin.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )

        if (plugin.hostEditable) {
            OutlinedTextField(
                value = state.host,
                onValueChange = onHost,
                label = { Text(plugin.hostFieldLabel) },
                placeholder = { Text(plugin.hostPlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
        }

        if (existingAccounts.isNotEmpty()) {
            StepLabel("已登录账号")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(existingAccounts, key = { it.id }) { account ->
                    val accountPlugin = GitHostPlugins.byId(account.provider)
                    PillChip(
                        text = "${accountPlugin.displayName}/${account.login.ifBlank { "?" }}",
                        icon = pluginIcon(accountPlugin.id),
                        selected = state.account?.id == account.id,
                        onClick = { onUseAccount(account) },
                    )
                }
            }

        }

        StepLabel("访问令牌")
        OutlinedTextField(
            value = state.token,
            onValueChange = onToken,
            label = { Text("Token") },
            placeholder = {
                Text(if (state.provider == "github") "github_pat_… 或 ghp_…" else "粘贴访问令牌")
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            visualTransformation = if (state.tokenVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {}),
            trailingIcon = {
                IconButton(onClick = onToggleVisible) {
                    Icon(
                        imageVector = if (state.tokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (state.tokenVisible) "隐藏 Token" else "显示 Token",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenTokenPage) { Text("在浏览器生成 Token") }
        }
        HintCard(text = plugin.tokenHint)
    }
}

@Composable
private fun RepositoryStep(
    state: SetupState,
    onQuery: (String) -> Unit,
    onSelect: (RepoSummary) -> Unit,
    onManualMode: (Boolean) -> Unit,
    onManualOwner: (String) -> Unit,
    onManualRepo: (String) -> Unit,
    onApplyManual: () -> Unit,
    onBranch: (String) -> Unit,
) {
    val query = state.repoQuery
    val repos = if (query.isBlank()) {
        state.repos
    } else {
        state.repos.filter {
            it.fullName.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索仓库") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip(
                text = if (state.manualMode) "返回列表" else "手动填写",
                selected = state.manualMode,
                onClick = { onManualMode(!state.manualMode) },
            )
            if (state.loadingRepos) {
                PillChip(text = "读取中…")
            } else {
                PillChip(text = "${state.repos.size} 个仓库")
            }
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f)) {
            when {
                state.manualMode -> Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = state.manualOwner,
                        onValueChange = onManualOwner,
                        label = { Text("owner 或 namespace") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = state.manualRepo,
                        onValueChange = onManualRepo,
                        label = { Text("仓库名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Button(onClick = onApplyManual, modifier = Modifier.fillMaxWidth()) {
                        Text("使用这个仓库")
                    }
                }

                state.loadingRepos && state.repos.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }

                repos.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Cloud,
                    title = "没有找到仓库",
                    body = "换个关键词，或手动填写 owner/仓库名。",
                    actionLabel = "手动填写",
                    onAction = { onManualMode(true) },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(repos, key = { _, repo -> repo.fullName }) { index, repo ->
                        CardRow(position = cardRowPosition(index, repos.size)) {
                            RepoRow(
                                repo = repo,
                                selected = state.selectedRepo?.fullName == repo.fullName,
                                onClick = { onSelect(repo) },
                            )
                        }
                    }
                }
            }
        }

        if (state.selectedRepo != null) {
            Spacer(Modifier.height(8.dp))
            BranchSection(state = state, onBranch = onBranch)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RepoRow(repo: RepoSummary, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bubbleColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.secondaryContainer,
        label = "repo-bubble",
    )
    val bubbleTint by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSecondaryContainer,
        label = "repo-bubble-tint",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (fadeIn(tween(160)) + androidx.compose.animation.scaleIn(tween(200), initialScale = 0.7f)) togetherWith
                    fadeOut(tween(120))
            },
            label = "repo-selected",
        ) { isSelected ->
            IconBubble(
                icon = if (isSelected) Icons.Rounded.Check else Icons.Rounded.Folder,
                containerColor = bubbleColor,
                tint = bubbleTint,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = repo.fullName,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "默认分支 ${repo.defaultBranch}" + if (repo.description.isNotBlank()) " · ${repo.description}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (repo.private) {
            PillChip(text = "私有", selected = false)
        }
    }
}

@Composable
private fun BranchSection(state: SetupState, onBranch: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.branch,
            onValueChange = onBranch,
            label = { Text("分支") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        if (state.branches.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.branches) { name ->
                    PillChip(text = name, selected = name == state.branch, onClick = { onBranch(name) })
                }
            }
        } else if (state.loadingBranches) {
            Text(
                text = "正在读取分支…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocationStep(
    state: SetupState,
    onTemplate: (String) -> Unit,
    onPublicBase: (String) -> Unit,
    onUseJsdelivr: () -> Unit,
) {
    val repo = state.selectedRepo
    val previewPath = PathTemplate.render(
        state.pathTemplate.ifBlank { DEFAULT_PATH_TEMPLATE },
        TemplateValues(
            name = "sunset",
            ext = "webp",
            width = 1920,
            height = 1080,
            sizeBytes = 248_000,
            hash = "3f9a1c2d5e6b",
            owner = repo?.owner.orEmpty(),
            repo = repo?.name.orEmpty(),
            branch = state.branch,
            provider = state.provider,
            now = LocalDateTime.now(),
            random = "a1b2c3d4e5f6",
        ),
    )
    val previewUrl = GitHostPlugins.byId(state.provider).publicUrl(
        host = state.host,
        owner = repo?.owner.orEmpty(),
        repo = repo?.name.orEmpty(),
        branch = state.branch.ifBlank { "main" },
        path = previewPath,
        publicBase = state.publicBase,
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepLabel("路径模板")
        OutlinedTextField(
            value = state.pathTemplate,
            onValueChange = onTemplate,
            label = { Text("文件在仓库里的位置") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            placeholder = { Text(DEFAULT_PATH_TEMPLATE) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        Text(
            text = "预览：$previewPath",
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

        StepLabel("公开链接")
        OutlinedTextField(
            value = state.publicBase,
            onValueChange = onPublicBase,
            label = { Text("链接前缀（可选）") },
            placeholder = { Text("留空使用平台 raw 地址") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.provider == "github") {
                PillChip(text = "使用 jsDelivr CDN", onClick = onUseJsdelivr)
            }
            if (state.publicBase.isNotBlank()) {
                PillChip(text = "清空前缀", onClick = { onPublicBase("") })
            }
        }
        HintCard(
            text = "raw.githubusercontent.com 在国内常连不上：公共仓库可用 jsDelivr 前缀，私有仓库请留空或自建反代。",
        )

        StepLabel("确认")
        CardRow {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine("账号", state.login)
                InfoLine("仓库", repo?.fullName.orEmpty())
                InfoLine("分支", state.branch)
                InfoLine("上传后示例链接", previewUrl, mono = true)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value.ifBlank { "—" },
            style = if (mono) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HintCard(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.cardSurface,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text("关闭", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun BottomBar(
    step: Int,
    busy: Boolean,
    enabled: Boolean,
    label: String,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                OutlinedButton(onClick = onBack) { Text("上一步") }
            }
            Button(
                onClick = onPrimary,
                enabled = enabled && !busy,
                modifier = Modifier.weight(1f),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(label)
            }
        }
    }
}