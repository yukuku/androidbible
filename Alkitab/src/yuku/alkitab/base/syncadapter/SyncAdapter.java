package yuku.alkitab.base.syncadapter;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
	public static final String TAG = SyncAdapter.class.getSimpleName();

	public SyncAdapter(final Context context, final boolean autoInitialize) {
		super(context, autoInitialize);
	}

	@Override
	public void onPerformSync(final Account account, final Bundle extras, final String authority, final ContentProviderClient provider, final SyncResult syncResult) {
		Log.d(TAG, "account: " + account);
		Log.d(TAG, "extras: " + extras);
		Log.d(TAG, "authority: " + authority);
		Log.d(TAG, "provider: " + provider);
		Log.d(TAG, "syncresult: " + syncResult);

		syncResult.stats.numInserts += 4;
		syncResult.stats.numIoExceptions += (Math.random() < 0.3)? 1: 0;
	}
}
