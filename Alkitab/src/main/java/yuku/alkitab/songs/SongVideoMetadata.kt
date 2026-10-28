package yuku.alkitab.songs

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun formatSongVideoViewCount(count: Long): String = when {
    count < 1_000 -> count.toString()
    count < 10_000 -> "${count / 1_000}.${count % 1_000 / 100}K"
    count < 1_000_000 -> "${count / 1_000}K"
    count < 10_000_000 -> "${count / 1_000_000}.${count % 1_000_000 / 100_000}M"
    else -> "${count / 1_000_000}M"
}

internal fun formatSongVideoDate(publishedAt: Instant, locale: Locale): String {
    val formatter = when (locale.language) {
        "id", "in" -> SimpleDateFormat("d MMM yyyy", locale)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    }
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date.from(publishedAt))
}

internal fun formatSongVideoMetadata(
    viewCount: Long?,
    isViewCountApproximate: Boolean,
    publishedAt: Instant?,
    locale: Locale,
): String {
    val views = viewCount?.let { "▷${if (isViewCountApproximate) "~" else ""}${formatSongVideoViewCount(it)}" }
    val date = publishedAt?.let { formatSongVideoDate(it, locale) }
    return listOfNotNull(views, date).joinToString(" • ")
}
