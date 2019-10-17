package yuku.alkitab.base.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import androidx.annotation.NonNull;
import yuku.alkitab.base.App;
import yuku.alkitab.debug.R;

public class SyncUtils {
	/**
	 * Create a new dummy account for the sync adapter
	 */
	@NonNull public static Account getOrCreateSyncAccount() {
		final String ACCOUNT_TYPE = App.context.getString(R.string.account_type);
		final String ACCOUNT_NAME = "dummy_account_name";

		// Get an instance of the Android account manager
		final AccountManager accountManager = (AccountManager) App.context.getSystemService(Context.ACCOUNT_SERVICE);

		// Create the account type and default account
		final Account newAccount = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);

		/*
		 * We do not know if this is success or not.
		 * If the account already exists, it returns false, but that is what we need.
		 * So we can't differentiate between error and already exists. Both returns false.
         */
		accountManager.addAccountExplicitly(newAccount, null, null);
		return newAccount;
	}
}
