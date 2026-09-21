package yuku.alkitab.base.compose.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yuku.alkitab.base.widget.GotoButton
import yuku.alkitab.debug.R

object ReaderToolbarDimens {
    val drawerWidth = 48.dp
    val searchWidth = 48.dp
    val iconSize = 24.dp

    /** Matches the max width [yuku.alkitab.base.widget.NavFrameLayout] applies. */
    val clusterMaxWidth = 250.dp

    /**
     * Chevrons move into the outer strip of their own box only below this
     * width. Wider bars have room for the reference without the overlap.
     */
    val flushBelowWidth = 411.dp
    val flushMargin = 24.dp

    val chipHeight = 32.dp
    val chipMinWidth = 48.dp
    val chipSidePadding = 8.dp
    val speakerWidth = 32.dp

    val referenceTextSize = 16.sp
    val versionTextSize = 14.sp

    const val VERSION_MAX_CHARS = 6
    const val VERSION_TRUNCATED_CHARS = 5
}

data class ReaderToolbarState(
    val reference: String = "",
    /** The same reference with the book abbreviated, used when the bar is too small for the full one. */
    val referenceAbbreviated: String = "",
    val versionInitials: String = "",
    val versionVisible: Boolean = true,
    val audioAvailable: Boolean = false,
    val audioBarVisible: Boolean = false,
)

interface ReaderToolbarActions {
    fun onDrawerClick()
    fun onPreviousChapter()
    fun onNextChapter()
    fun onReferenceClick()
    fun onReferenceLongClick()
    fun onVersionClick()
    fun onAudioClick()
    fun onSearchClick()

    /** Screen coordinates, matching what [GotoButton.FloaterDragListener] reports. */
    fun onReferenceDragStart(screenX: Float, screenY: Float)
    fun onReferenceDragMove(screenX: Float, screenY: Float)
    fun onReferenceDragComplete(screenX: Float, screenY: Float)

    /** Window coordinates of the reference button, for the history showcase tip. */
    fun onReferenceBoundsChanged(x: Int, y: Int, width: Int, height: Int)
}

/**
 * The version label is not length-capped upstream, so a long one is cut rather
 * than allowed to push the reference around.
 */
fun versionLabelFor(initials: String): String =
    if (initials.length > ReaderToolbarDimens.VERSION_MAX_CHARS) {
        initials.take(ReaderToolbarDimens.VERSION_TRUNCATED_CHARS) + "…"
    } else {
        initials
    }

@Composable
fun ReaderToolbar(
    state: ReaderToolbarState,
    actions: ReaderToolbarActions,
    modifier: Modifier = Modifier,
) {
    val arrowWidth = dimensionResource(R.dimen.nav_prevnext_width)
    val bucketMargin = dimensionResource(R.dimen.nav_goto_side_margin)
    val contentColor = Color.White

    // The host frame already paints the bar colour, including the night-mode
    // override the action bar applies, so nothing is drawn here.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val flush = maxWidth < ReaderToolbarDimens.flushBelowWidth
        val margin = if (flush) ReaderToolbarDimens.flushMargin else bucketMargin

        Layout(
            content = {
                BarIconButton(
                    painter = R.drawable.ic_menu_white_24dp,
                    contentDescription = stringResource(R.string.desc_open_drawer),
                    contentColor = contentColor,
                    onClick = actions::onDrawerClick,
                )
                NavCluster(
                    reference = state.reference,
                    referenceAbbreviated = state.referenceAbbreviated,
                    arrowWidth = arrowWidth,
                    margin = margin,
                    flush = flush,
                    contentColor = contentColor,
                    actions = actions,
                )
                VersionAudioControl(
                    state = state,
                    contentColor = contentColor,
                    onVersionClick = actions::onVersionClick,
                    onAudioClick = actions::onAudioClick,
                )
                BarIconButton(
                    painter = R.drawable.ic_search_24,
                    contentDescription = stringResource(R.string.search),
                    contentColor = contentColor,
                    onClick = actions::onSearchClick,
                )
            },
        ) { measurables, constraints ->
            val (drawerSlot, clusterSlot, versionSlot, searchSlot) = measurables
            val height = constraints.maxHeight
            val drawerPx = ReaderToolbarDimens.drawerWidth.roundToPx()
            val searchPx = ReaderToolbarDimens.searchWidth.roundToPx()

            val drawer = drawerSlot.measure(fixed(drawerPx, height))
            val search = searchSlot.measure(fixed(searchPx, height))
            // The version segment sizes itself to its label, then the cluster
            // takes whatever is left.
            val version = versionSlot.measure(
                Constraints(
                    maxWidth = (constraints.maxWidth - drawerPx - searchPx).coerceAtLeast(0),
                    minHeight = height,
                    maxHeight = height,
                )
            )
            val clusterWidth = (constraints.maxWidth - drawerPx - searchPx - version.width)
                .coerceIn(0, ReaderToolbarDimens.clusterMaxWidth.roundToPx())
            val cluster = clusterSlot.measure(fixed(clusterWidth, height))

            layout(constraints.maxWidth, height) {
                drawer.place(0, 0)
                cluster.place(drawerPx, 0)
                version.place(constraints.maxWidth - searchPx - version.width, 0)
                search.place(constraints.maxWidth - searchPx, 0)
            }
        }
    }
}

private fun fixed(width: Int, height: Int) = Constraints(width, width, height, height)

@Composable
private fun BarIconButton(
    painter: Int,
    contentDescription: String?,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
) {
    Box(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp, color = contentColor),
                onClick = onClick,
            ),
        contentAlignment = contentAlignment,
    ) {
        Icon(
            painter = painterResource(painter),
            contentDescription = contentDescription,
            modifier = Modifier.size(ReaderToolbarDimens.iconSize),
            tint = contentColor,
        )
    }
}

@Composable
private fun NavCluster(
    reference: String,
    referenceAbbreviated: String,
    arrowWidth: Dp,
    margin: Dp,
    flush: Boolean,
    contentColor: Color,
    actions: ReaderToolbarActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        ReferenceTarget(
            arrowWidth = arrowWidth,
            contentColor = contentColor,
            actions = actions,
        )
        ReferenceLabel(
            reference = reference,
            referenceAbbreviated = referenceAbbreviated,
            margin = margin,
            contentColor = contentColor,
            modifier = Modifier.align(Alignment.Center),
        )
        BarIconButton(
            painter = R.drawable.ic_chevron_start_24,
            contentDescription = stringResource(R.string.desc_previous_chapter),
            contentColor = contentColor,
            onClick = actions::onPreviousChapter,
            modifier = Modifier.align(Alignment.CenterStart).width(arrowWidth),
            contentAlignment = if (flush) Alignment.CenterStart else Alignment.Center,
        )
        BarIconButton(
            painter = R.drawable.ic_chevron_end_24,
            contentDescription = stringResource(R.string.desc_next_chapter),
            contentColor = contentColor,
            onClick = actions::onNextChapter,
            modifier = Modifier.align(Alignment.CenterEnd).width(arrowWidth),
            contentAlignment = if (flush) Alignment.CenterEnd else Alignment.Center,
        )
    }
}

@Composable
private fun ReferenceTarget(
    arrowWidth: Dp,
    contentColor: Color,
    actions: ReaderToolbarActions,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .padding(horizontal = arrowWidth)
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                origin = coordinates.localToScreen(Offset.Zero)
                val bounds = coordinates.boundsInWindow()
                actions.onReferenceBoundsChanged(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.width.toInt(),
                    bounds.height.toInt(),
                )
            }
            // Runs on the initial pass so a drag out of the button can consume
            // the gesture before the click below reacts to it.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val screenX = origin.x + change.position.x
                        val screenY = origin.y + change.position.y
                        if (!change.pressed) {
                            if (dragging) actions.onReferenceDragComplete(screenX, screenY)
                            break
                        }
                        if (dragging) {
                            actions.onReferenceDragMove(screenX, screenY)
                            change.consume()
                        } else {
                            val p = change.position
                            if (p.x < 0f || p.y < 0f || p.x > size.width || p.y > size.height) {
                                dragging = true
                                actions.onReferenceDragStart(screenX, screenY)
                                change.consume()
                            }
                        }
                    }
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = contentColor),
                onClick = actions::onReferenceClick,
                onLongClick = actions::onReferenceLongClick,
            )
    )
}

@Composable
private fun ReferenceLabel(
    reference: String,
    referenceAbbreviated: String,
    margin: Dp,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(
        color = contentColor,
        fontSize = ReaderToolbarDimens.referenceTextSize,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = margin)) {
        val density = LocalDensity.current
        val available = with(density) { maxWidth.toPx() }
        val box = with(density) {
            Constraints(maxWidth = maxWidth.roundToPx(), maxHeight = maxHeight.roundToPx())
        }

        val display = remember(reference, referenceAbbreviated, available, box, style) {
            // Reuses the view implementation so both toolbars break a long
            // reference at the same place.
            fun wrap(text: String) = GotoButton.balanceWrap(text, available) { s, start, end ->
                measurer.measure(s.subSequence(start, end).toString(), style).size.width.toFloat()
            }

            fun fitsWhole(text: String) = !measurer.measure(
                text = text,
                style = style,
                overflow = TextOverflow.Ellipsis,
                maxLines = REFERENCE_MAX_LINES,
                constraints = box,
            ).hasVisualOverflow

            // The chapter number is at the end, so anything cut takes it with
            // it. The abbreviation is the one shorter form there is, and if
            // that is still cut there is nothing further to try.
            val full = wrap(reference)
            when {
                fitsWhole(full) || referenceAbbreviated.isEmpty() -> full
                else -> wrap(referenceAbbreviated)
            }
        }

        Text(
            text = display,
            style = style,
            maxLines = REFERENCE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val REFERENCE_MAX_LINES = 2

/**
 * One stadium holding the version changer and the audio button, because audio
 * availability follows from which version is being read.
 *
 * Either half can be absent: the split view hides the version changer, and a
 * version without a recording has no audio button. Whichever half is left
 * keeps the stadium to itself, and the speaker takes the full 48dp minimum
 * once it no longer sits inside a target the reader is already aiming at.
 */
@Composable
private fun VersionAudioControl(
    state: ReaderToolbarState,
    contentColor: Color,
    onVersionClick: () -> Unit,
    onAudioClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showVersion = state.versionVisible
    val showAudio = state.audioAvailable
    if (!showVersion && !showAudio) {
        Box(modifier)
        return
    }

    val outline = contentColor.copy(alpha = 0.6f)
    val label = versionLabelFor(state.versionInitials)
    val labelStyle = TextStyle(
        color = contentColor,
        fontSize = ReaderToolbarDimens.versionTextSize,
        fontWeight = FontWeight.Bold,
    )
    val measurer = rememberTextMeasurer()
    val labelWidth = with(LocalDensity.current) {
        measurer.measure(label, labelStyle).size.width.toDp()
    }
    val chipHeight = ReaderToolbarDimens.chipHeight
    val split = showVersion && showAudio

    Row(
        modifier
            .fillMaxHeight()
            .drawBehind {
                val stadium = chipHeight.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(0f, (size.height - stadium) / 2f),
                    size = Size(size.width, stadium),
                    cornerRadius = CornerRadius(stadium / 2f),
                    style = Stroke(1.dp.toPx()),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showVersion) {
            SegmentHalf(
                width = maxOf(
                    ReaderToolbarDimens.chipMinWidth,
                    labelWidth + ReaderToolbarDimens.chipSidePadding * 2,
                ),
                shape = if (split) {
                    RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                } else {
                    RoundedCornerShape(percent = 50)
                },
                contentColor = contentColor,
                onClick = onVersionClick,
            ) {
                Text(text = label, style = labelStyle, maxLines = 1)
            }
        }

        if (split) {
            Box(Modifier.width(1.dp).height(chipHeight).background(outline))
        }

        if (showAudio) {
            SegmentHalf(
                width = if (split) ReaderToolbarDimens.speakerWidth else ReaderToolbarDimens.chipMinWidth,
                shape = if (split) {
                    RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                } else {
                    RoundedCornerShape(percent = 50)
                },
                contentColor = contentColor,
                onClick = onAudioClick,
                background = if (state.audioBarVisible) contentColor.copy(alpha = 0.24f) else Color.Transparent,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio),
                    contentDescription = stringResource(R.string.menu_audio),
                    modifier = Modifier.size(ReaderToolbarDimens.iconSize),
                    tint = contentColor,
                )
            }
        }
    }
}

/**
 * The whole bar height takes the touch, while the ripple is clipped to the
 * stadium so it reads as one chip rather than a full-height block.
 */
@Composable
private fun SegmentHalf(
    width: Dp,
    shape: Shape,
    contentColor: Color,
    onClick: () -> Unit,
    background: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ReaderToolbarDimens.chipHeight)
                .clip(shape)
                .background(background)
                .indication(interactionSource, ripple(color = contentColor)),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
