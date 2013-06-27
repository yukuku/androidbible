package yuku.alkitab.base.syncadapter;

import android.accounts.Account;
import android.content.ContentResolver;
import android.os.Bundle;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.storage.Prefkey;

public class SyncUtil {
	public static final String TAG = SyncUtil.class.getSimpleName();

	/**
	 * This is no-op if the user is not signed in.
	 */
	public static void requestSync(String source) {
		String accountName = Preferences.getString(Prefkey.auth_google_account_name);
		if (accountName == null) {
			return;
		}

		final Bundle bundle = new Bundle();
		bundle.putString("source", source);
		ContentResolver.requestSync(new Account(accountName, "com.google"), SyncProvider.AUTHORITY, bundle);
	}
}
