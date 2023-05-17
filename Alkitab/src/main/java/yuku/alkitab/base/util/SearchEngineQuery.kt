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
) : Parcelable
