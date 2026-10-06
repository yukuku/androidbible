package yuku.alkitab.base.util

import android.os.Parcelable
import android.util.SparseBooleanArray
import kotlinx.parcelize.Parcelize

@Parcelize
class SearchEngineQuery(
    @JvmField
    var query_string: String? = null,

    @JvmField
    var bookIds: SparseBooleanArray? = null,

    @JvmField
    var options: SearchOptions = SearchOptions(),
) : Parcelable

/**
 * Checkbox options. `+word` and quotes still work when these are off.
 *
 * @property exactPhrase whole query is one phrase, in order.
 * @property wholeWords every word acts like `+word`.
 * @property matchCapitals case-sensitive, but "Lord" still finds "LORD".
 */
@Parcelize
data class SearchOptions(
    val exactPhrase: Boolean = false,
    val wholeWords: Boolean = false,
    val matchCapitals: Boolean = false,
) : Parcelable
