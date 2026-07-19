package yuku.alkitab.songs

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A concise record of a song without "opening" its contents.
 *
 * [snippet] is populated only by the deep ("include lyrics") search path
 * ([yuku.alkitab.base.storage.SongDb.listSongInfosByBookNameAndDeepFilter]) and
 * holds up to a couple of matching lyric lines to preview under the search result.
 * It is null for ordinary metadata listings.
 */
@Parcelize
class SongInfo @JvmOverloads constructor(
    @JvmField
    val bookName: String,
    @JvmField
    val code: String,
    @JvmField
    val title: String,
    @JvmField
    val title_original: String?,
    @JvmField
    val snippet: String? = null,
) : Parcelable
