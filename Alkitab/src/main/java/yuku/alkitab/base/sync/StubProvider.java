package yuku.alkitab.base.sync;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/*
 * Define an implementation of ContentProvider that stubs out all methods
 */
public class StubProvider extends ContentProvider {
	/*
	 * Always return true, indicating that the provider loaded correctly.
     */
	@Override
	public boolean onCreate() {
		return true;
	}

	/*
	 * Return an empty String for MIME type
	 */
	@Override
	public String getType(final Uri uri) {
		return "";
	}

	/*
	 * query() always returns no results
	 */
	@Override
	public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs, final String sortOrder) {
		return null;
	}

	/*
	 * insert() always returns null (no URI)
	 */

	@Override
	public Uri insert(final Uri uri, final ContentValues values) {
		return null;
	}

	/*
	 * delete() always returns "no rows affected" (0)
	 */
	@Override
	public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
		return 0;
	}

	/*
	 * update() always returns "no rows affected" (0)
	 */
	@Override
	public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
		return 0;
	}
}