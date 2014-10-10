package yuku.alkitab.base.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import yuku.alkitab.base.App;

import static yuku.alkitab.base.util.Literals.List;

public class SyncUtils {
	// An account type, in the form of a domain name
	public static final String ACCOUNT_TYPE = "yuku.alkitab";
	// The account name
	public static final String ACCOUNT_NAME = "dummy_account_name";


	/**
	 * Create a new dummy account for the sync adapter
	 */
	public static Account getOrCreateSyncAccount() {
		// Get an instance of the Android account manager
		final AccountManager accountManager = (AccountManager) App.context.getSystemService(Context.ACCOUNT_SERVICE);

		// Create the account type and default account
		final Account newAccount = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);

		{ // Do we already have it?
			final Account[] accounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
			final int pos = List(accounts).indexOf(newAccount);
			if (pos >= 0) {
				return accounts[pos];
			}
		}

		/*
		 * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
		if (accountManager.addAccountExplicitly(newAccount, null, null)) {
			return newAccount;
		} else {
			return null;
		}
	}
}
