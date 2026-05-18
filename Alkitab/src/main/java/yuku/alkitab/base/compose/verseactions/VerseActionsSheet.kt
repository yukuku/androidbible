package yuku.alkitab.base.compose.verseactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import yuku.alkitab.base.actionmode.CopyShareVariant
import yuku.alkitab.debug.R

/**
 * Bottom-anchored verse-actions sheet rendered when the experimental
 * `pref_useComposeVerseActions` flag is on.
 *
 * Non-modal by design: it leaves the chapter content above untouched so the user
 * can extend the selection (tap more verses) without dismissing the sheet, and
 * sits near the thumb regardless of the user's top/bottom toolbar preference.
 *
 * Drag the sheet downward to dismiss — past `DISMISS_THRESHOLD_FRACTION` of the
 * sheet height it animates fully off-screen and unchecks the selection through
 * [VerseActionsSheetCallbacks.onClose]; below the threshold it springs back.
 *
 * Visibility is driven externally — pass `visible = true/false` to slide in/out.
 * The composable owns no selection state; [VerseActionsSheetState] is recomputed
 * by [ComposeVerseActionsController] on every selection change.
 */
@Composable
fun VerseActionsSheet(
    visible: Boolean,
    state: VerseActionsSheetState?,
    callbacks: VerseActionsSheetCallbacks,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && state != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        val s = state ?: return@AnimatedVisibility
        SheetContent(state = s, callbacks = callbacks)
    }
}

private const val DISMISS_THRESHOLD_FRACTION = 0.35f
private const val DRAG_SETTLE_ANIMATION_MS = 200

@Composable
private fun SheetContent(
    state: VerseActionsSheetState,
    callbacks: VerseActionsSheetCallbacks,
) {
    val scope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
            .onSizeChanged { sheetHeightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val next = (dragOffsetY.value + dragAmount).coerceIn(
                                minimumValue = 0f,
                                maximumValue = if (sheetHeightPx > 0f) sheetHeightPx else Float.MAX_VALUE,
                            )
                            dragOffsetY.snapTo(next)
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            val dismissPx = sheetHeightPx * DISMISS_THRESHOLD_FRACTION
                            if (sheetHeightPx > 0f && dragOffsetY.value >= dismissPx) {
                                dragOffsetY.animateTo(sheetHeightPx, tween(DRAG_SETTLE_ANIMATION_MS))
                                callbacks.onClose()
                            } else {
                                dragOffsetY.animateTo(0f, tween(DRAG_SETTLE_ANIMATION_MS))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffsetY.animateTo(0f, tween(DRAG_SETTLE_ANIMATION_MS)) }
                    },
                )
            },
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
            Header(state = state, onClose = callbacks::onClose)
            PrimaryActionRow(state = state, callbacks = callbacks)
            SecondaryChipRow(state = state, callbacks = callbacks)
            if (!state.isContiguous && state.verseCount > 1) {
                ContiguousHint()
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // Defensive: when the sheet remounts (selection appeared again after a
    // drag-out dismissal) Animatable is reconstructed fresh by `remember`, but
    // make the contract obvious here.
    LaunchedEffect(state.reference, state.verseCount) {
        if (dragOffsetY.value != 0f) dragOffsetY.snapTo(0f)
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
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun Header(state: VerseActionsSheetState, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
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
                style = MaterialTheme.typography.labelMedium,
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
            .padding(horizontal = horizontalPad, vertical = 6.dp),
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
            .padding(vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
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
