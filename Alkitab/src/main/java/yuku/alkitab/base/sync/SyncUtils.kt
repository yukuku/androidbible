package yuku.alkitab.base.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.debug.R

object SyncUtils {
    /**
     * Create a new dummy account for the sync adapter
     */
    @JvmStatic
    fun getOrCreateSyncAccount(): Account {
        val ACCOUNT_TYPE = App.context.getString(R.string.account_type)
        val placeholderAccountName = "dummy_account_name"

        // Get an instance of the Android account manager
        val accountManager = App.context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager

        // New account using user's email
        val newAccountName = Preferences.getString(R.string.pref_syncAccountName_key) ?: placeholderAccountName

        for (oldAccount in accountManager.getAccountsByTypeForPackage(ACCOUNT_TYPE, App.context.packageName)) {
            when (oldAccount.name) {
                placeholderAccountName -> if (newAccountName != placeholderAccountName) {
                    // we are seeing placeholder, but if the new account is also a placeholder, do not remove
                    accountManager.removeAccountExplicitly(oldAccount)
                }

                newAccountName -> {
                    // do not do anything to the account that already matches the new account
                }

                else -> {
                    accountManager.removeAccountExplicitly(oldAccount)
                }
            }
        }

        /*
         * We do not know if this is success or not.
         * If the account already exists, it returns false, but that is what we need.
         * So we can't differentiate between error and already exists. Both returns false.
         */
        val newAccount = Account(newAccountName, ACCOUNT_TYPE)
        accountManager.addAccountExplicitly(newAccount, null, null)

        return newAccount
    }

    @JvmStatic
    fun removeAllSyncAccounts() {
        val ACCOUNT_TYPE = App.context.getString(R.string.account_type)
        val accountManager = App.context.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager

        for (oldAccount in accountManager.getAccountsByTypeForPackage(ACCOUNT_TYPE, App.context.packageName)) {
            accountManager.removeAccountExplicitly(oldAccount)
        }
    }

    @JvmStatic
    fun <C> isSameContent(a: Sync.Entity<C>, b: Sync.Entity<C>): Boolean {
        if (a.gid != b.gid) return false
        if (a.kind != b.kind) return false
        return a.content == b.content
    }

    @JvmStatic
    fun <C> findEntity(list: List<Sync.Entity<C>>, gid: String, kind: String): Sync.Entity<C>? {
        for (entity in list) {
            if (gid == entity.gid && kind == entity.kind) {
                return entity
            }
        }
        return null
    }

    interface ThrowEverythingRunnable {
        @Throws(Exception::class)
        fun run()
    }

    @JvmStatic
    fun wontThrow(r: ThrowEverythingRunnable) {
        try {
            r.run()
        } catch (e: Exception) {
            throw RuntimeException("ThrowEverythingRunnable is passed but caused exception: $r", e)
        }
    }
}
