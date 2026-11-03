package yuku.alkitab.base.compose

import android.content.res.Resources
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import yuku.alkitab.base.util.UiTextRole
import yuku.alkitab.base.util.UiTypography

internal fun bibleAppTypography(resources: Resources): Typography {
    fun style(role: UiTextRole): TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight(role.weight),
        fontSize = UiTypography.nominalSp(resources, role.size).sp,
        lineHeight = UiTypography.nominalSp(resources, role.lineHeight).sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = if (role in setOf(UiTextRole.CAPTION, UiTextRole.NUMBER, UiTextRole.KEYPAD, UiTextRole.EMPHASIS)) "tnum" else null,
    )

    val title = style(UiTextRole.TITLE)
    val primary = style(UiTextRole.PRIMARY)
    val emphasis = style(UiTextRole.EMPHASIS)
    val supporting = style(UiTextRole.SUPPORTING)
    val label = style(UiTextRole.LABEL)
    val caption = style(UiTextRole.CAPTION)
    val number = style(UiTextRole.NUMBER)
    val keypad = style(UiTextRole.KEYPAD)

    return Typography(
        displayLarge = number,
        displayMedium = number,
        displaySmall = keypad,
        headlineLarge = title,
        headlineMedium = number,
        headlineSmall = title,
        titleLarge = title,
        titleMedium = emphasis,
        titleSmall = label,
        bodyLarge = primary,
        bodyMedium = supporting,
        bodySmall = caption,
        labelLarge = label,
        labelMedium = caption,
        labelSmall = caption,
    )
}
