package yuku.alkitab.base.compose

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

// Generic Compose families resolve system names again, which can select an OEM's custom font.
// Register each style explicitly because a single Typeface wrapper ignores weight and italics.
internal fun composeFontFamilyFor(typeface: Typeface?): FontFamily {
    val base = typeface ?: Typeface.DEFAULT
    return FontFamily(
        buildList(18) {
            for (weight in 100..900 step 100) {
                val fontWeight = FontWeight(weight)
                add(PlatformTypefaceFont(base, fontWeight, FontStyle.Normal))
                add(PlatformTypefaceFont(base, fontWeight, FontStyle.Italic))
            }
        }
    )
}

private data class PlatformTypefaceFont(
    val base: Typeface,
    override val weight: FontWeight,
    override val style: FontStyle,
) : AndroidFont(FontLoadingStrategy.Blocking, PlatformTypefaceLoader, FontVariation.Settings())

private object PlatformTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface {
        font as PlatformTypefaceFont
        val isItalic = font.style == FontStyle.Italic
        // Normal/bold follow TextView.setTypeface; other weights need the numeric API.
        if (Build.VERSION.SDK_INT >= 28 && font.weight != FontWeight.Normal && font.weight != FontWeight.Bold) {
            return Typeface.create(font.base, font.weight.weight, isItalic)
        }
        val androidStyle = if (font.weight >= FontWeight.SemiBold) {
            if (isItalic) Typeface.BOLD_ITALIC else Typeface.BOLD
        } else {
            if (isItalic) Typeface.ITALIC else Typeface.NORMAL
        }
        return Typeface.create(font.base, androidStyle)
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = loadBlocking(context, font)
}
