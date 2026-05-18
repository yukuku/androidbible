package yuku.alkitab.base.compose.verseactions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
 * Hosted by the `verse_actions_sheet` [ComposeView][androidx.compose.ui.platform.ComposeView]
 * placed inside `overlayContainer` (the activity's root FrameLayout) with
 * `layout_gravity=bottom`. The sheet sits on top of the reader — it never
 * resizes `nontoolbar` — so the verse list keeps its full height.
 *
 * Two-state design:
 *  - **Compact dock**: a slim panel with a drag handle, a single-line
 *    count + close button row, and a row of icon-only action buttons.
 *  - **Expanded sheet**: drag upward to reveal the secondary chip row
 *    (Bandingkan, Panduan, Tafsiran, Kamus, Koreksi AYT, extensions) and
 *    the contiguous-only hint.
 *
 * Gestures:
 *  - Drag dock upward past midpoint → snap open.
 *  - Drag expanded downward past midpoint → snap to dock.
 *  - Drag dock downward past `DISMISS_FRACTION` → off-screen + clears
 *    selection through [VerseActionsSheetCallbacks.onClose].
 *
 * Custom [Layout] measures the Surface unbounded (`Constraints.Infinity`)
 * so the natural full-content height is known regardless of the
 * currently-clipped visible region.
 */
private val DOCK_HEIGHT = 120.dp
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

    LaunchedEffect(visible) {
        if (visible) {
            if (height.value < dockPx) height.animateTo(dockPx, tween(SHOW_HIDE_ANIMATION_MS))
        } else {
            if (height.value > 0f) height.animateTo(0f, tween(SHOW_HIDE_ANIMATION_MS))
        }
    }

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
                        HeaderRow(state = s, onClose = callbacks::onClose)
                        ActionIconRow(state = s, callbacks = callbacks)
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
            .padding(top = 8.dp, bottom = 2.dp),
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
private fun HeaderRow(state: VerseActionsSheetState, onClose: () -> Unit) {
    val countText = if (state.isSingle) {
        stringResource(R.string.verse_select_one_verse_selected)
    } else {
        stringResource(R.string.verse_select_multiple_verse_selected, state.verseCount.toString())
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = countText,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.desc_close),
            )
        }
    }
}

@Composable
private fun ActionIconRow(
    state: VerseActionsSheetState,
    callbacks: VerseActionsSheetCallbacks,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SplitableActionIcon(
            icon = Icons.Outlined.ContentCopy,
            contentDescription = stringResource(R.string.salin_ayat),
            isSplit = state.isSplit,
            splitVariantLabels = listOf(
                R.string.copy_split0,
                R.string.copy_split1,
                R.string.copy_both_splits,
            ),
            onInvoke = callbacks::onCopy,
        )
        SplitableActionIcon(
            icon = Icons.Outlined.Share,
            contentDescription = stringResource(R.string.menuShare),
            isSplit = state.isSplit,
            splitVariantLabels = listOf(
                R.string.menuShareSplit0,
                R.string.menuShareSplit1,
                R.string.menuShareBothSplits,
            ),
            onInvoke = callbacks::onShare,
        )
        ActionIcon(
            icon = Icons.Outlined.BorderColor,
            contentDescription = stringResource(R.string.highlight_stabilo),
            enabled = true,
            onClick = callbacks::onAddHighlight,
        )
        ActionIcon(
            icon = Icons.Outlined.Bookmark,
            contentDescription = stringResource(R.string.tambah_pembatas_buku),
            enabled = state.isContiguous,
            onClick = callbacks::onAddBookmark,
        )
        ActionIcon(
            icon = Icons.Outlined.EditNote,
            contentDescription = stringResource(R.string.tulis_catatan),
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
            .padding(horizontal = 16.dp, vertical = 6.dp),
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
private fun ActionIcon(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/**
 * Copy / Share — single tap acts directly when only one version is active;
 * with split on, opens a Material 3 dropdown asking which version(s).
 *
 * [splitVariantLabels] is `[primary, secondary, both]` and must use Copy- vs
 * Share-specific phrasing because the existing strings include the verb
 * ("Salin versi utama" vs "Bagikan versi utama").
 */
@Composable
private fun SplitableActionIcon(
    icon: ImageVector,
    contentDescription: String,
    isSplit: Boolean,
    splitVariantLabels: List<Int>,
    onInvoke: (CopyShareVariant) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActionIcon(
            icon = icon,
            contentDescription = contentDescription,
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
