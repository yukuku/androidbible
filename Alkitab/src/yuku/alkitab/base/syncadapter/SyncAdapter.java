package yuku.alkitab.base.syncadapter;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.gson.Gson;
import com.squareup.okhttp.OkHttpClient;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.History;
import yuku.alkitabsync.history.model.common.HistoryEntry;
import yuku.alkitabsync.history.payload.HistorySyncRequestPayload;
import yuku.alkitabsync.history.payload.HistorySyncResponsePayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
	public static final String TAG = SyncAdapter.class.getSimpleName();

	private OkHttpClient client = new OkHttpClient();

	public SyncAdapter(final Context context, final boolean autoInitialize) {
		super(context, autoInitialize);
	}

	@Override
	public void onPerformSync(final Account account, final Bundle extras, final String authority, final ContentProviderClient provider, final SyncResult syncResult) {
		Log.d(TAG, "@@onPerformSync");
		Log.d(TAG, "- account: " + account);
		Log.d(TAG, "- extras: " + extras);
		Log.d(TAG, "- authority: " + authority);
		Log.d(TAG, "- provider: " + provider);
		Log.d(TAG, "- syncresult: " + syncResult);

		final String accountName = Preferences.getString(Prefkey.auth_google_account_name);
		if (accountName == null) {
			Log.e(TAG, "onPerformSync called when we don't have user account saved");
			syncResult.stats.numAuthExceptions++;
			return;
		}

		String token;
		try {
			token = GoogleAuthUtil.getTokenWithNotification(getContext(), accountName, SyncProvider.SCOPE, new Bundle());
			Preferences.setString(Prefkey.auth_google_token, token);
			Log.d(TAG, "token updated");
		} catch (IOException e) {
			Log.w(TAG, "io exception when getTokenWithNotification in onPerformSync");
			syncResult.stats.numIoExceptions++;
			return;
		} catch (GoogleAuthException e) {
			Log.w(TAG, "google auth exception when getTokenWithNotification in onPerformSync");
			syncResult.stats.numIoExceptions++;
			return;
		}

		// Section 1: HISTORY
		final History historyInstance = History.getInstance();
		List<HistoryEntry> historyEntries = historyInstance.getEntriesToSend();
		Log.d(TAG, "Sending " + historyEntries.size() + " entries to server");

		HistorySyncRequestPayload requestPayload = new HistorySyncRequestPayload();
		requestPayload.historyEntries = historyEntries;
		requestPayload.token = token;

		final HistorySyncResponsePayload responsePayload = historySync(requestPayload);
		if (responsePayload == null) {
			syncResult.stats.numIoExceptions++;
			return;
		}

		if (!responsePayload.ok) {
			Log.e(TAG, "Error from server: " + responsePayload.message);
			syncResult.stats.numConflictDetectedExceptions++;
			return;
		}

		Log.d(TAG, "Got " + responsePayload.historyEntries.size() + " entries from server");
		historyInstance.replaceAllWithServerData(responsePayload.historyEntries);
		historyInstance.save();
	}

	public HistorySyncResponsePayload historySync(HistorySyncRequestPayload requestPayload) {
		final Gson gson = new Gson();
		String postBody = gson.toJson(requestPayload);
		final PostResult postResult = post("http://alkitab-sync.appspot.com/HistorySync", postBody);
		if (postResult.exception != null) {
			Log.e(TAG, "@@historySync posting got exception", postResult.exception);
			return null;
		} else {
			try {
				HistorySyncResponsePayload responsePayload = gson.fromJson(postResult.response, HistorySyncResponsePayload.class);
				return responsePayload;
			} catch (Exception e) {
				Log.e(TAG, "@@historySync parsing response got exception", e);
				Log.e(TAG, "@@historySync response is: ");
				Log.e(TAG, "" + postResult.response);
				return null;
			}
		}
	}

	private static class PostResult {
		Throwable exception;
		String response;
	}

	public PostResult post(String url, String body) {
		PostResult res = new PostResult();

		try {
			final HttpURLConnection connection = client.open(new URL(url));
			connection.addRequestProperty("Authorization", "Basic bXNzcGl1c2VyOnBhc3N3MHJk");
			connection.addRequestProperty("Content-Type", "text/xml; charset=UTF-8");

			OutputStream out = null;
			InputStream in = null;
			try {
				// Write the request.
				connection.setRequestMethod("POST");
				out = connection.getOutputStream();
				out.write(body.getBytes("utf-8"));
				out.close();

				// Read the response.
				if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
					Log.w(TAG, "Unexpected HTTP response: " + connection.getResponseCode() + " " + connection.getResponseMessage());
					in = connection.getErrorStream();
				} else {
					in = connection.getInputStream();
				}

				BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
				StringWriter sw = new StringWriter();
				char[] buf = new char[1024];
				while (true) {
					int read = reader.read(buf);
					if (read < 0) break;
					sw.write(buf, 0, read);
				}
				res.response = sw.toString();
			} catch (IOException e) {
				res.exception = e;
			} finally {
				// Clean up.
				try {
					if (out != null) out.close();
					if (in != null) in.close();
				} catch (IOException e) {
					Log.e(TAG, "e", e);
				}
			}

		} catch (Exception e) {
			res.exception = e;
		}

		return res;
	}
}
