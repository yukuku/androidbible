package yuku.alkitab.base.util

import android.content.ContentResolver
import android.net.Uri

/**
 * Returns null on error
 */
fun ContentResolver.safeQuery(
    uri: Uri,
    projection: Array<String?>?, selection: String?,
    selectionArgs: Array<String?>?, sortOrder: String?,
) = try {
    query(uri, projection, selection, selectionArgs, sortOrder, null)
} catch (e: Exception) {
    null
}
