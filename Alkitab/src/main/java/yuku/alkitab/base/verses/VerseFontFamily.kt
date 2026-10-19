package yuku.alkitab.base.verses

import android.content.Context
import android.graphics.Typeface
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
        VerseTypefaceFont(base, FontWeight.Normal, FontStyle.Normal),
        VerseTypefaceFont(base, FontWeight.Bold, FontStyle.Normal),
        VerseTypefaceFont(base, FontWeight.Normal, FontStyle.Italic),
        VerseTypefaceFont(base, FontWeight.Bold, FontStyle.Italic),
    )
}

private data class VerseTypefaceFont(
    val base: Typeface,
    override val weight: FontWeight,
    override val style: FontStyle,
) : AndroidFont(FontLoadingStrategy.Blocking, VerseTypefaceLoader, FontVariation.Settings())

private object VerseTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface {
        font as VerseTypefaceFont
        val androidStyle = (if (font.weight == FontWeight.Bold) Typeface.BOLD else Typeface.NORMAL) or
            (if (font.style == FontStyle.Italic) Typeface.ITALIC else Typeface.NORMAL)
        return Typeface.create(font.base, androidStyle)
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = loadBlocking(context, font)
}
