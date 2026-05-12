package yuku.alkitab.base.compose.colorpicker

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import java.util.Locale
import yuku.alkitab.debug.R

/**
 * iOS-style color picker. Three tabs: Grid (12×10 preset palette), Spectrum (2D
 * hue×lightness plane), Sliders (RGB channels + hex input). Alpha is not exposed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosColorPicker(
    initialColor: Int,
    onColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialHsv = remember(initialColor) {
        val out = floatArrayOf(0f, 0f, 0f)
        AndroidColor.colorToHSV(initialColor or 0xff000000.toInt(), out)
        out
    }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var sat by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    var selectedTab by remember { mutableStateOf(0) }

    val currentRgb = AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))

    LaunchedEffect(currentRgb) {
        onColorChanged(currentRgb)
    }

    val applyRgb: (Int) -> Unit = { rgb ->
        val out = floatArrayOf(0f, 0f, 0f)
        AndroidColor.colorToHSV(rgb, out)
        hue = out[0]
        sat = out[1]
        value = out[2]
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ColorPreviewBar(currentRgb)

        Spacer(Modifier.height(12.dp))

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            val titles = listOf(
                stringResource(R.string.color_picker_tab_grid),
                stringResource(R.string.color_picker_tab_spectrum),
                stringResource(R.string.color_picker_tab_sliders),
            )
            titles.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title, maxLines = 1, softWrap = false) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when (selectedTab) {
            0 -> GridTab(selectedColor = currentRgb, onColorPicked = applyRgb)
            1 -> SpectrumTab(currentRgb = currentRgb, onColorPicked = applyRgb)
            2 -> SlidersTab(rgb = currentRgb, onRgbChange = applyRgb)
        }
    }
}

@Composable
private fun ColorPreviewBar(color: Int) {
    val hex = String.format(Locale.US, "#%06X", 0xFFFFFF and color)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(text = hex, style = MaterialTheme.typography.titleMedium)
    }
}

// --- Grid tab ----------------------------------------------------------------

private const val GRID_COLS = 12
private const val GRID_ROWS = 10

/** 12×10 iOS-style palette. Row 0 is a white→black grey ramp; rows 1–9 are 12 hue
 *  columns stepping from dark to light. */
private val GRID_COLORS: IntArray = intArrayOf(
    0xFEFFFE, 0xEBEBEB, 0xD6D6D6, 0xC2C2C2, 0xADADAD, 0x999999, 0x858585, 0x707070, 0x5C5C5C, 0x474747, 0x333333, 0x000000,
    0x00374A, 0x011D57, 0x11053B, 0x2E063D, 0x3C071B, 0x5C0701, 0x5A1C00, 0x583300, 0x563D00, 0x666100, 0x4F5504, 0x263E0F,
    0x004D65, 0x012F7B, 0x1A0A52, 0x450D59, 0x551029, 0x831100, 0x7B2900, 0x7A4A00, 0x785800, 0x8D8602, 0x6F760A, 0x38571A,
    0x016E8F, 0x0042A9, 0x2C0977, 0x61187C, 0x791A3D, 0xB51A00, 0xAD3E00, 0xA96800, 0xA67B01, 0xC4BC00, 0x9BA50E, 0x4E7A27,
    0x008CB4, 0x0056D6, 0x371A94, 0x7A219E, 0x99244F, 0xE22400, 0xDA5100, 0xD38301, 0xD19D01, 0xF5EC00, 0xC3D117, 0x669D34,
    0x00A1D8, 0x0061FD, 0x4D22B2, 0x982ABC, 0xB92D5D, 0xFF4015, 0xFF6A00, 0xFFAB01, 0xFCC700, 0xFEFB41, 0xD9EC37, 0x76BB40,
    0x01C7FC, 0x3A87FD, 0x5E30EB, 0xBE38F3, 0xE63B7A, 0xFE6250, 0xFE8648, 0xFEB43F, 0xFECB3E, 0xFFF76B, 0xE4EF65, 0x96D35F,
    0x52D6FC, 0x74A7FF, 0x864FFD, 0xD357FE, 0xEE719E, 0xFF8C82, 0xFEA57D, 0xFEC777, 0xFED977, 0xFFF994, 0xEAF28F, 0xB1DD8B,
    0x93E3FC, 0xA7C6FF, 0xB18CFE, 0xE292FE, 0xF4A4C0, 0xFFB5AF, 0xFFC5AB, 0xFED9A8, 0xFDE4A8, 0xFFFBB9, 0xF1F7B7, 0xCDE8B5,
    0xCBF0FF, 0xD2E2FE, 0xD8C9FE, 0xEFCAFE, 0xF9D3E0, 0xFFDAD8, 0xFFE2D6, 0xFEECD4, 0xFEF1D5, 0xFDFBDD, 0xF6FADB, 0xDEEED4,
)

@Composable
private fun GridTab(
    selectedColor: Int,
    onColorPicked: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
    ) {
        for (row in 0 until GRID_ROWS) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until GRID_COLS) {
                    val index = row * GRID_COLS + col
                    val c = GRID_COLORS[index]
                    val swatch = Color(0xff000000.toInt() or c)
                    val isSelected = (c and 0xffffff) == (selectedColor and 0xffffff)
                    // When selected, an inset reveals this backdrop as a ring around the
                    // swatch. When not selected, the backdrop matches the swatch so
                    // sub-pixel gaps from weight()-based layout rounding aren't visible.
                    val backdrop = when {
                        !isSelected -> swatch
                        index == 0 -> Color(0xff999999.toInt())
                        else -> Color.White
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(backdrop)
                            .pointerInput(c) {
                                detectTapGestures { onColorPicked(c or 0xff000000.toInt()) }
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(if (isSelected) 3.dp else 0.dp)
                                .background(swatch),
                        )
                    }
                }
            }
        }
    }
}

// --- Spectrum tab (single 2D HSL plane) -------------------------------------

@Composable
private fun SpectrumTab(
    currentRgb: Int,
    onColorPicked: (Int) -> Unit,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    // Horizontal: fully-saturated rainbow at L=0.5. Vertical overlay: white → transparent → black.
    val rainbow = remember {
        listOf(
            Color(0xffff0000), Color(0xffffff00), Color(0xff00ff00),
            Color(0xff00ffff), Color(0xff0000ff), Color(0xffff00ff), Color(0xffff0000),
        )
    }

    val hsl = remember(currentRgb) {
        val out = floatArrayOf(0f, 0f, 0f)
        ColorUtils.colorToHSL(currentRgb or 0xff000000.toInt(), out)
        out
    }
    val indicatorXFrac = (hsl[0] / 360f).coerceIn(0f, 1f)
    val indicatorYFrac = (1f - hsl[2]).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(rainbow))
            .background(
                Brush.verticalGradient(
                    0f to Color.White,
                    0.5f to Color.Transparent,
                    1f to Color.Black,
                )
            )
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset -> pickHsl(offset, size, onColorPicked) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> pickHsl(change.position, size, onColorPicked) }
            }
    ) {
        if (boxSize.width > 0 && boxSize.height > 0) {
            val strokeColor = if (useWhiteForeground(currentRgb)) Color.White else Color.Black
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (indicatorXFrac * boxSize.width - 14.dp.toPx()).toInt(),
                            (indicatorYFrac * boxSize.height - 14.dp.toPx()).toInt(),
                        )
                    }
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(2.dp, strokeColor, CircleShape)
            )
        }
    }
}

/** Picks white or black foreground based on perceptual luminance of [rgb]. */
private fun useWhiteForeground(rgb: Int): Boolean {
    val r = (rgb shr 16) and 0xff
    val g = (rgb shr 8) and 0xff
    val b = rgb and 0xff
    val v = kotlin.math.sqrt(r * r * 0.299 + g * g * 0.587 + b * b * 0.114)
    return v < 130
}

private fun pickHsl(pos: Offset, size: IntSize, onColorPicked: (Int) -> Unit) {
    if (size.width <= 0 || size.height <= 0) return
    val xFrac = (pos.x / size.width).coerceIn(0f, 1f)
    val yFrac = (pos.y / size.height).coerceIn(0f, 1f)
    val hue = xFrac * 360f
    val l = 1f - yFrac
    val rgb = ColorUtils.HSLToColor(floatArrayOf(hue, 1f, l)) or 0xff000000.toInt()
    onColorPicked(rgb)
}

// --- Sliders tab -------------------------------------------------------------

@Composable
private fun SlidersTab(
    rgb: Int,
    onRgbChange: (Int) -> Unit,
) {
    val r = (rgb shr 16) and 0xff
    val g = (rgb shr 8) and 0xff
    val b = rgb and 0xff

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChannelSlider(
            label = stringResource(R.string.color_picker_channel_red),
            value = r,
            trackLeft = Color(0xff000000.toInt() or (0 shl 16) or (g shl 8) or b),
            trackRight = Color(0xff000000.toInt() or (255 shl 16) or (g shl 8) or b),
            onValueChange = { newR ->
                onRgbChange((0xff000000.toInt()) or (newR shl 16) or (g shl 8) or b)
            },
        )
        ChannelSlider(
            label = stringResource(R.string.color_picker_channel_green),
            value = g,
            trackLeft = Color(0xff000000.toInt() or (r shl 16) or (0 shl 8) or b),
            trackRight = Color(0xff000000.toInt() or (r shl 16) or (255 shl 8) or b),
            onValueChange = { newG ->
                onRgbChange((0xff000000.toInt()) or (r shl 16) or (newG shl 8) or b)
            },
        )
        ChannelSlider(
            label = stringResource(R.string.color_picker_channel_blue),
            value = b,
            trackLeft = Color(0xff000000.toInt() or (r shl 16) or (g shl 8) or 0),
            trackRight = Color(0xff000000.toInt() or (r shl 16) or (g shl 8) or 255),
            onValueChange = { newB ->
                onRgbChange((0xff000000.toInt()) or (r shl 16) or (g shl 8) or newB)
            },
        )

        Spacer(Modifier.height(4.dp))

        HexInputRow(rgb = rgb, onRgbChange = onRgbChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    trackLeft: Color,
    trackRight: Color,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
                valueRange = 0f..255f,
                modifier = Modifier.weight(1f),
                track = { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.horizontalGradient(listOf(trackLeft, trackRight)))
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
                    )
                },
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(0.5.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                    )
                },
            )
            Spacer(Modifier.width(8.dp))
            ValueBox(text = value.toString())
        }
    }
}

@Composable
private fun ValueBox(text: String) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun HexInputRow(rgb: Int, onRgbChange: (Int) -> Unit) {
    var hexInput by remember(rgb) {
        mutableStateOf(String.format(Locale.US, "%06X", 0xffffff and rgb))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.color_picker_hex_label),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(110.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = hexInput,
                onValueChange = { input ->
                    val cleaned = input.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                    hexInput = cleaned
                    if (cleaned.length == 6) {
                        val parsed = cleaned.toLong(16).toInt() and 0xffffff
                        onRgbChange(parsed or 0xff000000.toInt())
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
