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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS-style color picker inspired by UIColorPickerViewController (and the Flutter
 * `ios_color_picker` package). Three tabs: Grid (preset palette), Spectrum (2D
 * saturation × value with a hue slider), Sliders (R/G/B with hex input).
 *
 * Alpha is not exposed — callers strip the alpha channel.
 *
 * @param initialColor starting color (alpha ignored)
 * @param onColorChanged invoked whenever the live color changes (RGB int, alpha = 0xff)
 */
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

    Column(modifier = modifier.fillMaxWidth()) {
        ColorPreviewBar(currentRgb)

        Spacer(Modifier.height(12.dp))

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            val titles = listOf("Grid", "Spectrum", "Sliders")
            titles.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title, maxLines = 1, softWrap = false) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when (selectedTab) {
            0 -> GridTab(
                selectedColor = currentRgb,
                onColorPicked = { picked ->
                    val out = floatArrayOf(0f, 0f, 0f)
                    AndroidColor.colorToHSV(picked, out)
                    hue = out[0]
                    sat = out[1]
                    value = out[2]
                },
            )
            1 -> SpectrumTab(
                hue = hue,
                saturation = sat,
                value = value,
                onHueChange = { hue = it },
                onSatValChange = { s, v -> sat = s; value = v },
            )
            2 -> SlidersTab(
                rgb = currentRgb,
                onRgbChange = { rgb ->
                    val out = floatArrayOf(0f, 0f, 0f)
                    AndroidColor.colorToHSV(rgb, out)
                    hue = out[0]
                    sat = out[1]
                    value = out[2]
                },
            )
        }
    }
}

@Composable
private fun ColorPreviewBar(color: Int) {
    val hex = String.format("#%06X", 0xFFFFFF and color)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(text = hex, style = MaterialTheme.typography.titleMedium)
    }
}

// --- Grid tab ----------------------------------------------------------------

/** iOS-style preset palette. Greys row + saturated hue rows at varying brightness. */
private val GRID_COLORS: List<Int> = buildList {
    val greys = listOf(0x000000, 0x1c1c1e, 0x3a3a3c, 0x636366, 0x8e8e93, 0xaeaeb2, 0xc7c7cc, 0xd1d1d6, 0xe5e5ea, 0xffffff)
    addAll(greys)
    val hues = floatArrayOf(0f, 30f, 60f, 90f, 150f, 180f, 210f, 240f, 280f, 320f)
    // (saturation, value) pairs producing 4 brightness rows: deep, normal, light, pale.
    val sv = listOf(
        1.0f to 0.5f,
        1.0f to 0.85f,
        0.7f to 1.0f,
        0.35f to 1.0f,
    )
    for ((s, v) in sv) {
        for (h in hues) {
            val rgb = AndroidColor.HSVToColor(floatArrayOf(h, s, v)) and 0xffffff
            add(rgb)
        }
    }
}

@Composable
private fun GridTab(
    selectedColor: Int,
    onColorPicked: (Int) -> Unit,
) {
    val cols = 10
    val rows = GRID_COLORS.size / cols
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (col in 0 until cols) {
                    val c = GRID_COLORS[row * cols + col]
                    val isSelected = (c and 0xffffff) == (selectedColor and 0xffffff)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xff000000.toInt() or c))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .pointerInput(c) {
                                detectTapGestures { onColorPicked(c or 0xff000000.toInt()) }
                            },
                    )
                }
            }
        }
    }
}

// --- Spectrum tab ------------------------------------------------------------

@Composable
private fun SpectrumTab(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueChange: (Float) -> Unit,
    onSatValChange: (Float, Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        SatValBox(hue = hue, saturation = saturation, value = value, onSatValChange = onSatValChange)
        Spacer(Modifier.height(12.dp))
        HueSlider(hue = hue, onHueChange = onHueChange)
    }
}

@Composable
private fun SatValBox(
    hue: Float,
    saturation: Float,
    value: Float,
    onSatValChange: (Float, Float) -> Unit,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val pureHue = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, pureHue)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    updateSatVal(offset, size, onSatValChange)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    updateSatVal(change.position, size, onSatValChange)
                }
            }
    ) {
        if (boxSize.width > 0 && boxSize.height > 0) {
            val xFrac = saturation.coerceIn(0f, 1f)
            val yFrac = (1f - value).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (xFrac * boxSize.width - 12.dp.toPx()).toInt(),
                            (yFrac * boxSize.height - 12.dp.toPx()).toInt(),
                        )
                    }
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .border(1.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

private fun updateSatVal(
    pos: Offset,
    size: IntSize,
    onSatValChange: (Float, Float) -> Unit,
) {
    if (size.width <= 0 || size.height <= 0) return
    val s = (pos.x / size.width).coerceIn(0f, 1f)
    val v = (1f - pos.y / size.height).coerceIn(0f, 1f)
    onSatValChange(s, v)
}

@Composable
private fun HueSlider(hue: Float, onHueChange: (Float) -> Unit) {
    val hueColors = remember {
        listOf(
            Color(0xffff0000), Color(0xffffff00), Color(0xff00ff00),
            Color(0xff00ffff), Color(0xff0000ff), Color(0xffff00ff), Color(0xffff0000),
        )
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(hueColors))
        )
        Slider(
            value = hue,
            onValueChange = onHueChange,
            valueRange = 0f..360f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
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

    var hexInput by remember(rgb) { mutableStateOf(String.format("%06X", 0xffffff and rgb)) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        OutlinedTextField(
            value = hexInput,
            onValueChange = { input ->
                val cleaned = input.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                hexInput = cleaned
                if (cleaned.length == 6) {
                    val parsed = cleaned.toLong(16).toInt() and 0xffffff
                    onRgbChange(parsed or 0xff000000.toInt())
                }
            },
            label = { Text("Hex") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        ChannelSlider("R", r, Color(0xffe53935)) { newR ->
            onRgbChange((0xff000000.toInt()) or (newR shl 16) or (g shl 8) or b)
        }
        ChannelSlider("G", g, Color(0xff43a047)) { newG ->
            onRgbChange((0xff000000.toInt()) or (r shl 16) or (newG shl 8) or b)
        }
        ChannelSlider("B", b, Color(0xff1e88e5)) { newB ->
            onRgbChange((0xff000000.toInt()) or (r shl 16) or (g shl 8) or newB)
        }
    }
}

@Composable
private fun ChannelSlider(label: String, value: Int, tint: Color, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(20.dp), fontSize = 14.sp)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = tint,
                activeTrackColor = tint,
            ),
        )
        Text(
            text = value.toString(),
            modifier = Modifier.width(36.dp),
            fontSize = 14.sp,
        )
    }
}
