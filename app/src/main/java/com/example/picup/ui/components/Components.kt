package com.example.picup.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picup.ui.theme.cardSurface
import com.example.picup.ui.theme.chipSurface
import com.example.picup.ui.theme.floatingSurface
import com.example.picup.ui.cachedThumbnail
import com.example.picup.ui.loadThumbnail

private val SegmentHeight: Dp = 38.dp

/**
 * Segmented control with a sliding pill.
 *
 * The previous project's version supported dragging the pill across options.
 * Two to four short options do not need that gesture, and a tap-only control
 * cannot both scroll and select by accident, so this one animates the pill and
 * leaves gestures to the surrounding scroll container.
 */
@Composable
fun <T> PicUpSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * When driven by a pager, pass a lambda returning page + offset. It is read
     * during layout, so dragging the pager invalidates placement only — no
     * recomposition per frame.
     */
    selectionFraction: (() -> Float)? = null,
    label: (T) -> String = { it.toString() },
) {
    if (options.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.chipSurface)
            .padding(4.dp),
    ) {
        val itemWidth: Dp = maxWidth / options.size
        val animatedOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f),
            label = "segment-offset",
        )
        val pillModifier = if (selectionFraction != null) {
            Modifier.offset {
                val fraction = selectionFraction().coerceIn(0f, (options.size - 1).toFloat())
                androidx.compose.ui.unit.IntOffset((itemWidth * fraction).roundToPx(), 0)
            }
        } else {
            Modifier.offset { androidx.compose.ui.unit.IntOffset(animatedOffset.roundToPx(), 0) }
        }
        Box(
            pillModifier
                .width(itemWidth)
                .height(SegmentHeight)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.primary),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val ink by animateColorAsState(
                    targetValue = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                    label = "segment-ink",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(SegmentHeight)
                        .clip(RoundedCornerShape(20.dp))
                        // No ripple: a bounded indication paints a second
                        // capsule over the ones that scroll past it.
                        .clickable(
                            enabled = !isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(option)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        color = ink,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * A two-slot pill toggle for icon choices (grid / list), matching the compact
 * view-mode control in the reference app: 68 dp wide, filled icon when active,
 * outlined when not.
 */
@Composable
fun PicUpIconSegmented(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    if (count <= 0) return
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.chipSurface)
            .padding(3.dp),
    ) {
        val itemWidth: Dp = maxWidth / count
        val offset by animateDpAsState(
            targetValue = itemWidth * selectedIndex.coerceIn(0, count - 1),
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f),
            label = "icon-segment-offset",
        )
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offset.roundToPx(), 0) }
                .width(itemWidth)
                .height(28.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.surface),
        )
        Row(Modifier.fillMaxWidth()) {
            repeat(count) { index ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .clickable(
                            enabled = !isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    content(index, isSelected)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/**
 * Where a row sits inside a connected settings list.
 *
 * Only the outer corners of the group get the large radius; rows in the middle
 * keep a small one, so a group reads as one continuous plate instead of a pile
 * of bubbles.
 */
enum class CardRowPosition { SINGLE, FIRST, MIDDLE, LAST }

fun cardRowPosition(index: Int, total: Int): CardRowPosition = when {
    total <= 1 -> CardRowPosition.SINGLE
    index == 0 -> CardRowPosition.FIRST
    index == total - 1 -> CardRowPosition.LAST
    else -> CardRowPosition.MIDDLE
}

@Composable
fun CardRow(
    modifier: Modifier = Modifier,
    position: CardRowPosition = CardRowPosition.SINGLE,
    /** Defaults to the page-level card role; dialogs override it. */
    containerColor: Color = MaterialTheme.colorScheme.cardSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val big = 24.dp
    val small = 6.dp
    val shape = when (position) {
        CardRowPosition.SINGLE -> RoundedCornerShape(big)
        CardRowPosition.FIRST -> RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small)
        CardRowPosition.MIDDLE -> RoundedCornerShape(small)
        CardRowPosition.LAST -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
    ) {
        Column(content = content)
    }
}

/** Every overflow menu uses the same rounded floating surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicUpDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        // surfaceContainerHigh instead of the floating surface: white menus on a
        // near-white page read as a hole in the layout.
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        content = content,
    )
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    iconContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, iconContainer, iconTint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) {
                haptics.performHapticFeedback(
                    if (!checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun IconBubble(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 40.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size / 2))
    }
}

@Composable
fun PillChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.chipSurface,
        label = "chip-container",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        label = "chip-ink",
    )
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            )
            // Clip first: the ripple then follows the pill instead of painting
            // a rectangle with square corners over it.
            .clip(RoundedCornerShape(50))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(50),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TopBarBack(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.chipSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            Surface(
                modifier = Modifier.clickable(onClick = onAction),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
fun BusyRow(text: String = "处理中") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What a [Thumbnail] should load. */
sealed interface ThumbnailSource {
    data class PickedUri(val uri: android.net.Uri) : ThumbnailSource
    data class LocalFile(val file: java.io.File) : ThumbnailSource
    data object None : ThumbnailSource
}

@Composable
fun Thumbnail(
    source: ThumbnailSource,
    modifier: Modifier = Modifier,
    /** Null fills whatever the caller sized (grid tiles). */
    size: Dp? = 56.dp,
    corner: Dp = 14.dp,
) {
    val context = LocalContext.current
    // Seed from the in-memory cache so a re-composed row paints immediately.
    var bitmap by remember(source) {
        val cached = when (source) {
            is ThumbnailSource.PickedUri -> cachedThumbnail(source.uri)?.asImageBitmap()
            is ThumbnailSource.LocalFile -> cachedThumbnail(source.file)?.asImageBitmap()
            ThumbnailSource.None -> null
        }
        mutableStateOf(cached)
    }
    androidx.compose.runtime.LaunchedEffect(source) {
        if (bitmap != null) return@LaunchedEffect
        bitmap = when (source) {
            is ThumbnailSource.PickedUri -> loadThumbnail(context, source.uri)?.asImageBitmap()
            is ThumbnailSource.LocalFile -> loadThumbnail(context, source.file)?.asImageBitmap()
            ThumbnailSource.None -> null
        }
    }
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "thumbnail-alpha",
    )
    Box(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.chipSurface),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            androidx.compose.foundation.Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(contentAlpha),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((size ?: 56.dp) / 3),
            )
        }
    }
}
