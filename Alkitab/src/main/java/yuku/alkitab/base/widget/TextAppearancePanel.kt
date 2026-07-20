package yuku.alkitab.base.widget

import android.app.Activity
import android.graphics.Typeface as AndroidTypeface
import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import yuku.afw.App
import yuku.afw.storage.Preferences
import yuku.alkitab.base.ac.ColorSettingsActivity
import yuku.alkitab.base.ac.FontManagerActivity
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.FontManager
import yuku.alkitab.debug.R

/**
 * The "Aa" text-appearance panel ("tampilan"), rendered as a **non-modal**
 * Compose bottom sheet. It is attached as a bottom-anchored child of the reader's
 * overlay [FrameLayout], so it floats above the verses without a scrim — the user
 * can keep scrolling and interacting with the text while adjusting the display.
 *
 * The public API (constructor + [show]/[hide]/[displayValues]/[onActivityResult]/
 * [setSplitVersion]/[clearSplitVersion]) is kept identical to the previous
 * View-based panel so `IsiActivity`, [ReaderGestureHost], [SplitViewHost] and
 * [SplitViewManager] can drive it unchanged.
 */
class TextAppearancePanel(
    private val activity: Activity,
    private val parent: FrameLayout,
    private val listener: Listener,
    private val reqcodeGetFonts: Int,
    private val reqcodeCustomColors: Int,
) {
    interface Listener {
        fun onValueChanged()
        fun onCloseButtonClick()
    }

    // --- Compose-backed state. Mutated by displayValues()/setSplitVersion() and
    //     read during composition, so external callers can refresh the sheet. ---
    private var uiFontEntries by mutableStateOf<List<FontManager.FontEntry>>(emptyList())
    private var uiFontName by mutableStateOf<String?>(null)
    private var uiBold by mutableStateOf(false)
    private var uiTextSize by mutableStateOf(DEFAULT_TEXT_SIZE)
    private var uiLineSpacing by mutableStateOf(DEFAULT_LINE_SPACING)
    private var uiSplitVersionId by mutableStateOf<String?>(null)
    private var uiSplitVersionLongName by mutableStateOf<String?>(null)
    private var uiPerVersionMult by mutableStateOf(1f)
    private var uiNightMode by mutableStateOf(false)
    private var uiColors by mutableStateOf(intArrayOf(0, 0, 0, 0))

    private var shown = false

    private val composeView = ComposeView(activity)

    init {
        // The reader manages its own window insets; don't let this overlay swallow them.
        composeView.consumeWindowInsets = false
        composeView.setContent {
            BibleAppTheme {
                SheetUi()
            }
        }
        uiFontEntries = FontManager.getInstalledFonts()
        displayValues()
    }

    fun show() {
        if (shown) return
        val lp = FrameLayout.LayoutParams(
            activity.resources.getDimensionPixelSize(R.dimen.panel_text_appearance_width),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        )
        parent.addView(composeView, lp)
        shown = true
    }

    fun hide() {
        if (!shown) return
        parent.removeView(composeView)
        shown = false
    }

    /** Re-read all preference-derived values into the Compose state, refreshing the sheet. */
    fun displayValues() {
        uiFontName = Preferences.getString(Prefkey.jenisHuruf)
        uiBold = Preferences.getBoolean(Prefkey.boldHuruf, false)
        uiTextSize = Preferences.getFloat(
            Prefkey.ukuranHuruf2,
            App.context.resources.getInteger(R.integer.pref_ukuranHuruf2_default).toFloat(),
        )
        uiLineSpacing = Preferences.getFloat(Prefkey.lineSpacingMult, DEFAULT_LINE_SPACING)
        uiNightMode = Preferences.getBoolean(Prefkey.is_night_mode, false)
        uiColors = ColorThemes.getCurrentColors(uiNightMode)

        val splitId = uiSplitVersionId
        if (splitId != null) {
            val settings = yuku.alkitab.base.App.services.storage.db.getPerVersionSettings(splitId)
            uiPerVersionMult = settings.fontSizeMultiplier
        }
    }

    fun onActivityResult(requestCode: Int) {
        when (requestCode) {
            reqcodeGetFonts -> {
                uiFontEntries = FontManager.getInstalledFonts()
                displayValues()
            }

            reqcodeCustomColors -> displayValues()
        }
    }

    fun setSplitVersion(splitVersionId: String, splitVersionLongName: String) {
        uiSplitVersionId = splitVersionId
        uiSplitVersionLongName = splitVersionLongName
        displayValues()
    }

    fun clearSplitVersion() {
        uiSplitVersionId = null
        uiSplitVersionLongName = null
        displayValues()
    }

    // ------------------------------------------------------------------
    // Callbacks that persist a changed value and notify the reader.
    // ------------------------------------------------------------------

    private fun onFontSelected(prefName: String?) {
        if (prefName == null) {
            activity.startActivityForResult(FontManagerActivity.createIntent(), reqcodeGetFonts)
        } else {
            Preferences.setString(Prefkey.jenisHuruf, prefName)
            uiFontName = prefName
            listener.onValueChanged()
        }
    }

    private fun onBoldChanged(bold: Boolean) {
        Preferences.setBoolean(Prefkey.boldHuruf, bold)
        uiBold = bold
        listener.onValueChanged()
    }

    private fun onTextSizeChanged(value: Float) {
        val snapped = (value * 2f).roundToInt() / 2f // 0.5 steps, matching the old seek bar
        Preferences.setFloat(Prefkey.ukuranHuruf2, snapped)
        uiTextSize = snapped
        listener.onValueChanged()
    }

    private fun onLineSpacingChanged(value: Float) {
        val snapped = (value * 20f).roundToInt() / 20f // 0.05 steps
        Preferences.setFloat(Prefkey.lineSpacingMult, snapped)
        uiLineSpacing = snapped
        listener.onValueChanged()
    }

    private fun onPerVersionSizeChanged(value: Float) {
        val splitId = uiSplitVersionId ?: return
        val snapped = (value * 20f).roundToInt() / 20f // 0.05 steps
        val db = yuku.alkitab.base.App.services.storage.db
        val settings = db.getPerVersionSettings(splitId)
        settings.fontSizeMultiplier = snapped
        db.storePerVersionSettings(splitId, settings)
        uiPerVersionMult = snapped
        listener.onValueChanged()
    }

    private fun onThemeSelected(colors: IntArray) {
        ColorThemes.setCurrentColors(colors, uiNightMode)
        uiColors = colors
        listener.onValueChanged()
    }

    private fun onCustomColorsRequested() {
        activity.startActivityForResult(ColorSettingsActivity.createIntent(uiNightMode), reqcodeCustomColors)
    }

    // ------------------------------------------------------------------
    // Composable UI
    // ------------------------------------------------------------------

    @Composable
    private fun SheetUi() {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val dismissThresholdPx = with(density) { 96.dp.toPx() }
        val offsetY = remember { Animatable(0f) }

        val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(initialOffsetY = { it }),
        ) {
            Surface(
                modifier = Modifier
                    .width(dimensionResource(R.dimen.panel_text_appearance_width))
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    // Non-modal: only touches that land on the sheet itself are
                    // consumed; everything above it falls through to the verses.
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 8.dp),
                ) {
                    DragHandle(
                        onDrag = { delta ->
                            scope.launch { offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f)) }
                        },
                        onDragEnd = {
                            if (offsetY.value > dismissThresholdPx) {
                                listener.onCloseButtonClick()
                            } else {
                                scope.launch { offsetY.animateTo(0f) }
                            }
                        },
                    )

                    HeaderRow(onClose = { listener.onCloseButtonClick() })

                    FontRow()

                    SectionLabel(stringResource(R.string.text_appearance_text_size))
                    SliderRow(
                        value = uiTextSize,
                        valueRange = 2f..42f,
                        valueLabel = String.format(Locale.US, "%.1f", uiTextSize),
                        onValueChange = ::onTextSizeChanged,
                    )

                    if (uiSplitVersionId != null) {
                        val ctx = LocalContext.current
                        val versionName = uiSplitVersionLongName ?: ""
                        // The string uses a `^1` template placeholder, not a printf arg.
                        val perVersionLabel = remember(versionName) {
                            android.text.TextUtils.expandTemplate(
                                ctx.getText(R.string.text_appearance_text_size_for_version),
                                versionName,
                            ).toString()
                        }
                        SectionLabel(perVersionLabel)
                        SliderRow(
                            value = uiPerVersionMult,
                            valueRange = 0.5f..1.5f,
                            valueLabel = "${(uiPerVersionMult * 100).roundToInt()}%",
                            onValueChange = ::onPerVersionSizeChanged,
                        )
                    }

                    SectionLabel(stringResource(R.string.text_appearance_line_spacing))
                    SliderRow(
                        value = uiLineSpacing,
                        valueRange = 1f..2f,
                        valueLabel = String.format(Locale.US, "%.2f", uiLineSpacing),
                        onValueChange = ::onLineSpacingChanged,
                    )

                    ColorThemeRow()
                }
            }
        }
    }

    @Composable
    private fun DragHandle(onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = 32.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            ) {}
        }
    }

    @Composable
    private fun HeaderRow(onClose: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.text_appearance_font),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.desc_close),
                )
            }
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        Text(
            text = text,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }

    @Composable
    private fun SliderRow(
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        valueLabel: String,
        onValueChange: (Float) -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                modifier = Modifier.width(56.dp).padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FontRow() {
        val escapeColor = colorResource(R.color.escape)
        val options = buildFontOptions(uiFontEntries, escapeColor)
        val currentDisplay = displayNameForFont(uiFontName, uiFontEntries)
        val currentFamily = fontFamilyForName(uiFontName, uiFontEntries)

        var expanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = currentDisplay,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = currentFamily),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.display,
                                    fontFamily = option.fontFamily,
                                    color = option.color ?: Color.Unspecified,
                                )
                            },
                            onClick = {
                                expanded = false
                                onFontSelected(option.prefName)
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = uiBold, onCheckedChange = ::onBoldChanged)
                Text(stringResource(R.string.text_appearance_bold))
            }
        }
    }

    @Composable
    private fun ColorThemeRow() {
        var showDialog by remember { mutableStateOf(false) }

        SectionLabel(stringResource(R.string.text_appearance_color_theme))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) { detectTapGestures { showDialog = true } },
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // All four theme colors previewed as swatches: text, background,
                // verse number, red text (same order as the old MultiColorView).
                uiColors.take(4).forEach { c ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(c),
                        shape = RoundedCornerShape(4.dp),
                    ) {}
                }
            }
        }

        if (showDialog) {
            ColorThemeDialog(
                onDismiss = { showDialog = false },
                onSelect = { colors ->
                    showDialog = false
                    onThemeSelected(colors)
                },
                onCustom = {
                    showDialog = false
                    onCustomColorsRequested()
                },
            )
        }
    }

    @Composable
    private fun ColorThemeDialog(
        onDismiss: () -> Unit,
        onSelect: (IntArray) -> Unit,
        onCustom: () -> Unit,
    ) {
        val themeValues = stringArrayResource(R.array.pref_colorTheme_values)
        val themeLabels = stringArrayResource(R.array.pref_colorTheme_labels)
        val themes = remember(themeValues) { themeValues.map { ColorThemes.themeStringToColors(it) } }
        val selectedIndex = themes.indexOfFirst { it.contentEquals(uiColors) }

        // The theme list is long (17 presets + Custom), so it must scroll and
        // stay clear of the screen edges.
        val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp

        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxDialogHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    themes.forEachIndexed { index, colors ->
                        ThemeChoiceRow(
                            label = themeLabels.getOrElse(index) { "" },
                            number = (index + 1).toString(),
                            textColor = Color(colors[0]),
                            bgColor = Color(colors[1]),
                            verseNumberColor = Color(colors[2]),
                            selected = index == selectedIndex,
                            onClick = { onSelect(colors) },
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) { detectTapGestures { onCustom() } }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.text_appearance_theme_custom),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selectedIndex == -1) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ThemeChoiceRow(
        label: String,
        number: String,
        textColor: Color,
        bgColor: Color,
        verseNumberColor: Color,
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) { detectTapGestures { onClick() } },
            color = bgColor,
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = number,
                    color = verseNumberColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = textColor,
                    modifier = Modifier.weight(1f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                if (selected) {
                    Text(text = "✓", color = textColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Font option helpers
    // ------------------------------------------------------------------

    private data class FontOption(
        val prefName: String?, // null => "get more fonts"
        val display: String,
        val fontFamily: FontFamily?,
        val color: Color? = null,
    )

    private fun buildFontOptions(entries: List<FontManager.FontEntry>, escapeColor: Color): List<FontOption> {
        val options = ArrayList<FontOption>(entries.size + 4)
        options += FontOption("DEFAULT", "Roboto", FontFamily.SansSerif)
        options += FontOption("SERIF", "Droid Serif", FontFamily.Serif)
        options += FontOption("MONOSPACE", "Droid Mono", FontFamily.Monospace)
        for (entry in entries) {
            options += FontOption(entry.name, entry.name, familyForCustomFont(entry.name))
        }
        options += FontOption(null, App.context.getString(R.string.get_more_fonts), null, escapeColor)
        return options
    }

    private fun displayNameForFont(prefName: String?, entries: List<FontManager.FontEntry>): String = when (prefName) {
        null, "DEFAULT" -> "Roboto"
        "SERIF" -> "Droid Serif"
        "MONOSPACE" -> "Droid Mono"
        else -> entries.firstOrNull { it.name == prefName }?.name ?: prefName
    }

    private fun fontFamilyForName(prefName: String?, entries: List<FontManager.FontEntry>): FontFamily? = when (prefName) {
        null, "DEFAULT" -> FontFamily.SansSerif
        "SERIF" -> FontFamily.Serif
        "MONOSPACE" -> FontFamily.Monospace
        else -> if (entries.any { it.name == prefName }) familyForCustomFont(prefName) else FontFamily.SansSerif
    }

    private fun familyForCustomFont(name: String): FontFamily? = try {
        val tf: AndroidTypeface? = FontManager.typeface(name)
        if (tf != null) FontFamily(Typeface(tf)) else null
    } catch (_: Throwable) {
        null
    }

    /**
     * Reading of the four theme colors (text, background, verse number, red text)
     * from the day/night preference set, and writing them back. Ported verbatim
     * from the previous View-based panel.
     */
    private object ColorThemes {
        fun themeStringToColors(themeString: String): IntArray = intArrayOf(
            java.lang.Long.parseLong(themeString.substring(0, 8), 16).toInt(),
            java.lang.Long.parseLong(themeString.substring(9, 17), 16).toInt(),
            java.lang.Long.parseLong(themeString.substring(18, 26), 16).toInt(),
            java.lang.Long.parseLong(themeString.substring(27, 35), 16).toInt(),
        )

        fun getCurrentColors(forNightMode: Boolean): IntArray = if (forNightMode) {
            intArrayOf(
                Preferences.getInt(R.string.pref_textColor_night_key, R.integer.pref_textColor_night_default),
                Preferences.getInt(R.string.pref_backgroundColor_night_key, R.integer.pref_backgroundColor_night_default),
                Preferences.getInt(R.string.pref_verseNumberColor_night_key, R.integer.pref_verseNumberColor_night_default),
                Preferences.getInt(R.string.pref_redTextColor_night_key, R.integer.pref_redTextColor_night_default),
            )
        } else {
            intArrayOf(
                Preferences.getInt(R.string.pref_textColor_key, R.integer.pref_textColor_default),
                Preferences.getInt(R.string.pref_backgroundColor_key, R.integer.pref_backgroundColor_default),
                Preferences.getInt(R.string.pref_verseNumberColor_key, R.integer.pref_verseNumberColor_default),
                Preferences.getInt(R.string.pref_redTextColor_key, R.integer.pref_redTextColor_default),
            )
        }

        fun setCurrentColors(colors: IntArray, forNightMode: Boolean) {
            val c = App.context
            if (forNightMode) {
                Preferences.setInt(c.getString(R.string.pref_textColor_night_key), colors[0])
                Preferences.setInt(c.getString(R.string.pref_backgroundColor_night_key), colors[1])
                Preferences.setInt(c.getString(R.string.pref_verseNumberColor_night_key), colors[2])
                Preferences.setInt(c.getString(R.string.pref_redTextColor_night_key), colors[3])
            } else {
                Preferences.setInt(c.getString(R.string.pref_textColor_key), colors[0])
                Preferences.setInt(c.getString(R.string.pref_backgroundColor_key), colors[1])
                Preferences.setInt(c.getString(R.string.pref_verseNumberColor_key), colors[2])
                Preferences.setInt(c.getString(R.string.pref_redTextColor_key), colors[3])
            }
        }
    }

    companion object {
        private const val DEFAULT_TEXT_SIZE = 17f
        private const val DEFAULT_LINE_SPACING = 1.15f
    }
}
