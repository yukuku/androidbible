package yuku.alkitab.base.compose

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.tabs.TabLayout
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.afw.App
import yuku.alkitab.base.util.UiTextRole
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [26, 34])
class BibleAppTypographyTest {
    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Alkitab)

    @Test
    fun `localized UI labels avoid capitals used only for styling`() {
        val languages = listOf("en", "af", "bg", "ceb", "cs", "da", "de", "el", "es", "fr", "hu", "id", "it", "ja", "ko", "lv", "ms", "my", "nl", "pl", "pt", "pt-BR", "ro", "ru", "th", "tl", "tr", "uk", "vi", "zh-CN", "zh-TW")
        val labels = listOf(R.string.ok, R.string.labels_titikdua, R.string.judul_titikdua, R.string.sn_bookselector_all, R.string.color_picker_channel_red, R.string.color_picker_channel_green, R.string.color_picker_channel_blue, R.string.jump_to_prompt)
        for (language in languages) {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val resources = context.createConfigurationContext(configuration).resources
            for (label in labels) {
                val value = resources.getText(label).toString()
                val sample = if (label == R.string.jump_to_prompt) value.substringBefore('—') else value
                val letters = sample.filter { it.lowercaseChar() != it.uppercaseChar() }
                assertFalse("$language: $value", letters.length > 1 && letters.none { it.isLowerCase() })
            }
        }
    }

    @Test
    fun `native buttons and dialog actions preserve sentence case in every app theme`() {
        for (theme in listOf(R.style.Theme_Alkitab, R.style.Ribka_Theme, R.style.AlkitabFeedback_Theme)) {
            val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
            controller.get().setTheme(theme)
            val activity = controller.setup().get()
            val views = mutableListOf<TextView>(Button(activity), AppCompatButton(activity))
            if (theme != R.style.AlkitabFeedback_Theme) {
                views.add(MaterialButton(activity))
                val dialog = MaterialAlertDialogBuilder(activity).setMessage("Example").setPositiveButton("Open file", null).setNegativeButton("Cancel", null).show()
                views.add(dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE))
                views.add(dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE))
            }
            val login = activity.layoutInflater.inflate(R.layout.activity_sync_login, null)
            views.add(login.findViewById(R.id.bLogin))
            views.forEach { view ->
                view.text = "Open file"
                assertEquals("${view.javaClass.simpleName} in theme $theme", "Open file", view.transformationMethod?.getTransformation(view.text, view)?.toString() ?: view.text.toString())
            }
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `Compose roles use the eight agreed size weight and line height combinations`() {
        val typography = bibleAppTypography(context.resources)
        val expected = listOf(
            Triple(typography.titleLarge, 20f, 26f),
            Triple(typography.bodyLarge, 16f, 24f),
            Triple(typography.titleMedium, 16f, 24f),
            Triple(typography.bodyMedium, 14f, 20f),
            Triple(typography.labelLarge, 14f, 20f),
            Triple(typography.bodySmall, 12f, 16f),
            Triple(typography.headlineMedium, 24f, 32f),
            Triple(typography.displaySmall, 28f, 36f),
        )
        expected.forEach { (style, size, lineHeight) ->
            assertEquals(size.sp, style.fontSize)
            assertEquals(lineHeight.sp, style.lineHeight)
            assertEquals(0.sp, style.letterSpacing)
        }
        assertEquals(listOf(500, 400, 500, 400, 500, 400, 500, 500), expected.map { it.first.fontWeight?.weight })
    }

    @Test
    fun `shared text appearances and Compose agree on every typography role`() {
        val typography = bibleAppTypography(context.resources)
        val styles: List<TextStyle> = listOf(typography.titleLarge, typography.bodyLarge, typography.titleMedium, typography.bodyMedium, typography.labelLarge, typography.bodySmall, typography.headlineMedium, typography.displaySmall)
        UiTextRole.entries.zip(styles).forEach { (role, style) ->
            val view = MaterialTextView(context)
            view.setTextAppearance(context, role.appearance)
            val expectedSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value, context.resources.displayMetrics)
            assertEquals(role.name, expectedSize, view.textSize, 0.01f)
            assertEquals(role.name, context.resources.getDimensionPixelSize(role.lineHeight), view.lineHeight)
            assertEquals(0f, view.letterSpacing)
            context.obtainStyledAttributes(role.appearance, intArrayOf(android.R.attr.fontFamily)).let { attributes ->
                assertEquals(role.name, if (role.weight == 500) "sans-serif-medium" else "sans-serif", attributes.getString(0))
                attributes.recycle()
            }
        }
    }

    @Test
    fun `inflated UI uses the agreed sizes and line heights on Android 8 and newer devices`() {
        listOf(R.style.Theme_Alkitab, R.style.Ribka_Theme).forEach { theme ->
            val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
            controller.get().setTheme(theme)
            val activity = controller.create().get()
            val row = activity.layoutInflater.inflate(R.layout.item_version, null)
            val shortName = row.findViewById<TextView>(R.id.tShortName)
            assertEquals(context.resources.getDimensionPixelSize(UiTextRole.PRIMARY.size).toFloat(), shortName.textSize, 0.01f)
            assertEquals(context.resources.getDimensionPixelSize(UiTextRole.PRIMARY.lineHeight), shortName.lineHeight)
            assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, context.resources.displayMetrics), row.findViewById<TextView>(R.id.tLongName).textSize, 0.01f)
            assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics), row.findViewById<TextView>(R.id.tLanguage).textSize, 0.01f)
            val attributes = activity.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary, android.R.attr.textColorSecondary))
            val primary = requireNotNull(attributes.getColorStateList(0))
            val secondary = requireNotNull(attributes.getColorStateList(1))
            attributes.recycle()
            val name = row.findViewById<TextView>(R.id.tShortName)
            assertEquals(primary.getColorForState(name.drawableState, primary.defaultColor), name.currentTextColor)
            val toolbar = Toolbar(activity).apply { title = "Title"; subtitle = "Subtitle" }
            for (index in 0 until toolbar.childCount) {
                val view = toolbar.getChildAt(index)
                if (view is TextView) {
                    val colors = if (view.text == "Title") primary else secondary
                    assertEquals(colors.getColorForState(view.drawableState, colors.defaultColor), view.currentTextColor)
                }
            }
            val tabColors = requireNotNull(TabLayout(activity).tabTextColors)
            val enabled = intArrayOf(android.R.attr.state_enabled)
            val selected = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_selected)
            assertEquals(primary.getColorForState(enabled, primary.defaultColor), tabColors.getColorForState(selected, tabColors.defaultColor))
            assertEquals(secondary.getColorForState(enabled, secondary.defaultColor), tabColors.getColorForState(enabled, tabColors.defaultColor))
            controller.destroy()
        }
    }

    @Test
    fun `Compose keeps nominal sp values when Android resource dimensions are scaled`() {
        val scaled = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 1.5f })
        val typography = bibleAppTypography(scaled.resources)
        assertEquals(20.sp, typography.titleLarge.fontSize)
        assertEquals(26.sp, typography.titleLarge.lineHeight)
        assertEquals(16.sp, typography.bodyLarge.fontSize)
        assertEquals(12.sp, typography.bodySmall.fontSize)
    }
}
