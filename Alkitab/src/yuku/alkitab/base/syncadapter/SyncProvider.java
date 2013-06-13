package yuku.alkitab.base.syncadapter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public class SyncProvider extends ContentProvider {
	public static final String TAG = SyncProvider.class.getSimpleName();
	public static final String AUTHORITY = "yuku.alkitab.syncadapter.provider";

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs, final String sortOrder) {
		return null;
	}

	@Override
	public String getType(final Uri uri) {
		return null;
	}

	@Override
	public Uri insert(final Uri uri, final ContentValues values) {
		return null;
	}

	@Override
	public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
		return 0;
	}

	@Override
	public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
		return 0;
	}
}
