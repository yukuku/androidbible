package yuku.alkitab.base.compose.verseactions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import yuku.alkitab.base.actionmode.CopyShareVariant
import yuku.alkitab.debug.R

/**
 * Bottom-anchored verse-actions sheet rendered when the experimental
 * `pref_useComposeVerseActions` flag is on.
 *
 * Two-state design:
 *  - **Collapsed dock** (default when verses get selected): a slim ~140dp
 *    panel showing the drag handle, the reference + count, a close button,
 *    and the primary action buttons. Reader content shrinks by exactly this
 *    much, so on every screen orientation (including landscape with the
 *    side-by-side horizontal split) the reader keeps most of its real
 *    estate.
 *  - **Expanded sheet**: drag the dock upward to reveal the secondary
 *    chip row (Bandingkan, Panduan, Tafsiran, Kamus, Koreksi AYT,
 *    extensions) and the contiguous-selection hint. The reader shrinks
 *    further only while expanded.
 *
 * Gestures:
 *  - Drag the dock upward past the midpoint between dock height and full
 *    height → snap open to the full sheet.
 *  - Drag the full sheet downward past that midpoint → snap back to the
 *    dock.
 *  - Drag the dock downward past ~40% of its height → animate the whole
 *    thing off-screen and unchecks the selection via
 *    [VerseActionsSheetCallbacks.onClose].
 *
 * The composable owns no selection state; [VerseActionsSheetState] is
 * recomputed by [ComposeVerseActionsController] on every selection change.
 */
private val DOCK_HEIGHT = 140.dp
private const val DRAG_SETTLE_ANIMATION_MS = 220
private const val SHOW_HIDE_ANIMATION_MS = 220
private const val DISMISS_FRACTION = 0.4f

@Composable
fun VerseActionsSheet(
    visible: Boolean,
    state: VerseActionsSheetState?,
    callbacks: VerseActionsSheetCallbacks,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dockPx = with(density) { DOCK_HEIGHT.toPx() }
    val scope = rememberCoroutineScope()

    val height = remember { Animatable(0f) }
    var fullPx by remember { mutableFloatStateOf(0f) }

    // Drive open/close on the `visible` flag. Once shown, the user controls
    // expansion by dragging; we only animate to 0 when hidden externally.
    LaunchedEffect(visible) {
        if (visible) {
            if (height.value < dockPx) height.animateTo(dockPx, tween(SHOW_HIDE_ANIMATION_MS))
        } else {
            if (height.value > 0f) height.animateTo(0f, tween(SHOW_HIDE_ANIMATION_MS))
        }
    }

    // When the visible selection becomes empty, the controller pushes
    // `visible = false`; nothing left to render once the collapse animation
    // finishes.
    if (!visible && height.value == 0f && state == null) return

    val s = state

    val visibleHeightPx = height.value

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        scope.launch {
                            val cap = if (fullPx > 0f) fullPx else dockPx
                            val next = (height.value - delta).coerceIn(0f, cap)
                            height.snapTo(next)
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            val target = nearestAnchor(height.value, dockPx, fullPx)
                            height.animateTo(target, tween(DRAG_SETTLE_ANIMATION_MS))
                            if (target == 0f) callbacks.onClose()
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            val target = nearestAnchor(height.value, dockPx, fullPx)
                            height.animateTo(target, tween(DRAG_SETTLE_ANIMATION_MS))
                        }
                    },
                )
            },
        content = {
            if (s != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 24.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                    ) {
                        DragHandle()
                        Header(state = s, onClose = callbacks::onClose)
                        PrimaryActionRow(state = s, callbacks = callbacks)
                        SecondaryChipRow(state = s, callbacks = callbacks)
                        if (!s.isContiguous && s.verseCount > 1) {
                            ContiguousHint()
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        },
    ) { measurables, constraints ->
        // Measure the Surface unbounded so we learn its full natural height
        // (otherwise the parent's `visible` height would clip the measurement
        // and we'd never be able to grow past the dock).
        val unbounded = constraints.copy(maxHeight = Constraints.Infinity)
        val placeable = measurables.firstOrNull()?.measure(unbounded)
        val natural = placeable?.height ?: 0
        if (natural > 0 && natural.toFloat() != fullPx) fullPx = natural.toFloat()
        val visible = visibleHeightPx.roundToInt().coerceIn(0, natural)
        layout(constraints.maxWidth, visible) {
            placeable?.placeRelative(0, 0)
        }
    }
}

private fun nearestAnchor(current: Float, dock: Float, full: Float): Float {
    if (dock <= 0f) return 0f
    val dismissBound = dock * DISMISS_FRACTION
    return when {
        current <= dismissBound -> 0f
        full <= dock -> dock
        current < (dock + full) * 0.5f -> dock
        else -> full
    }
}

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun Header(state: VerseActionsSheetState, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.reference,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val countText = if (state.isSingle) {
                stringResource(R.string.verse_select_one_verse_selected)
            } else {
                stringResource(R.string.verse_select_multiple_verse_selected, state.verseCount.toString())
            }
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.desc_close),
            )
        }
    }
}

@Composable
private fun PrimaryActionRow(
    state: VerseActionsSheetState,
    callbacks: VerseActionsSheetCallbacks,
) {
    val configuration = LocalConfiguration.current
    val horizontalPad = if (configuration.screenWidthDp < 360) 4.dp else 12.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPad, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        SplitableActionButton(
            icon = Icons.Outlined.ContentCopy,
            label = stringResource(R.string.salin_ayat),
            isSplit = state.isSplit,
            splitVariantLabels = listOf(
                R.string.copy_split0,
                R.string.copy_split1,
                R.string.copy_both_splits,
            ),
            onInvoke = callbacks::onCopy,
        )
        SplitableActionButton(
            icon = Icons.Outlined.Share,
            label = stringResource(R.string.menuShare),
            isSplit = state.isSplit,
            splitVariantLabels = listOf(
                R.string.menuShareSplit0,
                R.string.menuShareSplit1,
                R.string.menuShareBothSplits,
            ),
            onInvoke = callbacks::onShare,
        )
        PrimaryActionButton(
            icon = Icons.Outlined.BorderColor,
            label = stringResource(R.string.highlight_stabilo),
            enabled = true,
            onClick = callbacks::onAddHighlight,
        )
        PrimaryActionButton(
            icon = Icons.Outlined.Bookmark,
            label = stringResource(R.string.tambah_pembatas_buku),
            enabled = state.isContiguous,
            onClick = callbacks::onAddBookmark,
        )
        PrimaryActionButton(
            icon = Icons.Outlined.EditNote,
            label = stringResource(R.string.tulis_catatan),
            enabled = state.isContiguous,
            onClick = callbacks::onAddNote,
        )
    }
}

@Composable
private fun SecondaryChipRow(
    state: VerseActionsSheetState,
    callbacks: VerseActionsSheetCallbacks,
) {
    val anyChip = state.showCompare || state.showGuide || state.showCommentary ||
        state.showDictionary || state.showRibkaReport || state.showEsvsb ||
        state.extensions.isNotEmpty()
    if (!anyChip) return

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.showCompare) {
            SecondaryChip(
                label = stringResource(R.string.menu_compare),
                icon = Icons.AutoMirrored.Outlined.CompareArrows,
                enabled = state.isSingle,
                onClick = callbacks::onCompare,
            )
        }
        if (state.showGuide) {
            SecondaryChip(
                label = stringResource(R.string.menuGuide),
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                enabled = true,
                onClick = callbacks::onGuide,
            )
        }
        if (state.showCommentary) {
            SecondaryChip(
                label = stringResource(R.string.menuCommentary),
                icon = Icons.Outlined.Translate,
                enabled = true,
                onClick = callbacks::onCommentary,
            )
        }
        if (state.showDictionary) {
            SecondaryChip(
                label = stringResource(R.string.menuDictionary),
                icon = Icons.Outlined.Spellcheck,
                enabled = true,
                onClick = callbacks::onDictionary,
            )
        }
        if (state.showRibkaReport) {
            SecondaryChip(
                label = stringResource(R.string.ribka_menu_report),
                icon = Icons.Outlined.Flag,
                enabled = true,
                onClick = callbacks::onRibkaReport,
            )
        }
        if (state.showEsvsb) {
            SecondaryChip(
                label = "ESV Study Bible",
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                enabled = true,
                onClick = callbacks::onEsvsb,
            )
        }
        state.extensions.forEach { ext ->
            SecondaryChip(
                label = ext.label,
                icon = Icons.Outlined.Extension,
                enabled = true,
                onClick = { callbacks.onExtension(ext.index) },
            )
        }
    }
}

@Composable
private fun ContiguousHint() {
    Text(
        text = stringResource(R.string.verse_actions_sheet_hint_contiguous_only),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun PrimaryActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val container = MaterialTheme.colorScheme.secondaryContainer
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainer,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Copy / Share — single tap acts directly when there's only one version active;
 * when split is on, opens a Material 3 dropdown asking which version(s) to use.
 *
 * [splitVariantLabels] is `[primary, secondary, both]` and must use Copy- vs
 * Share-specific phrasing because the existing strings include the verb
 * ("Salin versi utama" vs "Bagikan versi utama").
 */
@Composable
private fun SplitableActionButton(
    icon: ImageVector,
    label: String,
    isSplit: Boolean,
    splitVariantLabels: List<Int>,
    onInvoke: (CopyShareVariant) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PrimaryActionButton(
            icon = icon,
            label = label,
            enabled = true,
            onClick = {
                if (isSplit) expanded = true
                else onInvoke(CopyShareVariant.SinglePrimary)
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(splitVariantLabels[0])) },
                onClick = {
                    expanded = false
                    onInvoke(CopyShareVariant.SplitPrimary)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(splitVariantLabels[1])) },
                onClick = {
                    expanded = false
                    onInvoke(CopyShareVariant.SplitSecondary)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(splitVariantLabels[2])) },
                onClick = {
                    expanded = false
                    onInvoke(CopyShareVariant.SplitBoth)
                },
            )
        }
    }
}

@Composable
private fun SecondaryChip(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    AssistChipDefaults.assistChipColors().leadingIconContentColor
                } else {
                    AssistChipDefaults.assistChipColors().disabledLeadingIconContentColor
                },
                modifier = Modifier.size(18.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(),
    )
}
