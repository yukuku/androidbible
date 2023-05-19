package yuku.alkitab.songs

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A concise record of a song without "opening" its contents.
 */
@Parcelize
class SongInfo(
    @JvmField
    val bookName: String,
    @JvmField
    val code: String,
    @JvmField
    val title: String,
    @JvmField
    val title_original: String?,
) : Parcelable
