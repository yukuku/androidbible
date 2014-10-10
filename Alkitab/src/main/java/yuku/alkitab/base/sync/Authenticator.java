package yuku.alkitab.base.sync;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;

/*
 * Implement AbstractAccountAuthenticator and stub out all of its methods
 */
public class Authenticator extends AbstractAccountAuthenticator {

	// Simple constructor
	public Authenticator(Context context) {
		super(context);
	}

	// Editing properties is not supported
	@Override
	public Bundle editProperties(final AccountAuthenticatorResponse response, final String accountType) {
		return null;
	}

	// Don't add additional accounts
	@Override
	public Bundle addAccount(final AccountAuthenticatorResponse response, final String accountType, final String authTokenType, final String[] requiredFeatures, final Bundle options) throws NetworkErrorException {
		return null;
	}

	// Ignore attempts to confirm credentials
	@Override
	public Bundle confirmCredentials(final AccountAuthenticatorResponse response, final Account account, final Bundle options) throws NetworkErrorException {
		return null;
	}

	// Getting an authentication token is not supported
	@Override
	public Bundle getAuthToken(final AccountAuthenticatorResponse response, final Account account, final String authTokenType, final Bundle options) throws NetworkErrorException {
		throw new UnsupportedOperationException();
	}

	// Getting a label for the auth token is not supported
	@Override
	public String getAuthTokenLabel(final String authTokenType) {
		throw new UnsupportedOperationException();
	}

	// Updating user credentials is not supported
	@Override
	public Bundle updateCredentials(final AccountAuthenticatorResponse response, final Account account, final String authTokenType, final Bundle options) throws NetworkErrorException {
		throw new UnsupportedOperationException();
	}

	// Checking features for the account is not supported
	@Override
	public Bundle hasFeatures(final AccountAuthenticatorResponse response, final Account account, final String[] features) throws NetworkErrorException {
		throw new UnsupportedOperationException();
	}
}