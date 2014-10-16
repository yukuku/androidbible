package yuku.alkitab.base.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.SecretSyncDebugActivity;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.InternalDb;
import yuku.alkitab.base.storage.Prefkey;

import java.io.IOException;
import java.util.List;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
	static final String TAG = SyncAdapter.class.getSimpleName();
	public static final String EXTRA_SYNC_SET_NAME = "syncSetName";

	/**
	 * Set up the sync adapter
	 */
	public SyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
	}

	/**
	 * Set up the sync adapter. This form of the constructor maintains compatibility with Android 3.0
	 * and later platform versions
	 */
	public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
		super(context, autoInitialize, allowParallelSyncs);
	}

	/*
	 * Specify the code you want to run in the sync adapter. The entire
	 * sync adapter runs in a background thread, so you don't have to set
	 * up your own background processing.
	 */
	@Override
	public void onPerformSync(final Account account, final Bundle extras, final String authority, final ContentProviderClient provider, final SyncResult syncResult) {
		Log.d(TAG, "@@onPerformSync account:" + account + " extras:" + extras + " authority:" + authority);

		final String syncSetName = extras.getString(EXTRA_SYNC_SET_NAME);
		if (syncSetName != null) {
			Log.d(TAG, "Syncing: " + syncSetName);

			if (SyncShadow.SYNC_SET_MABEL.equals(syncSetName)) {
				syncMabel(syncResult);
			}
		}

		Log.d(TAG, "Sync result: " + syncResult);
	}

	void syncMabel(final SyncResult sr) {
		final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
		if (simpleToken == null) {
			sr.stats.numAuthExceptions++;
			Log.w(TAG, "@@syncMabel no simpleToken, we are not set for sync");
			return;
		}

		Log.d(TAG, "@@syncMabel step 10: gathering client state");
		final Pair<Sync.MabelClientState, List<Sync.Entity<Sync.MabelContent>>> pair = Sync.getMabelClientStateAndCurrentEntities();
		final Sync.MabelClientState clientState = pair.first;
		final List<Sync.Entity<Sync.MabelContent>> entitiesBeforeSync = pair.second;


		final String serverPrefix = Sync.getEffectiveServerPrefix();
		Log.d(TAG, "@@syncMabel step 20: building http request. Server prefix: " + serverPrefix);
		final RequestBody requestBody = new FormEncodingBuilder()
			.add("simpleToken", simpleToken)
			.add("syncSetName", SyncShadow.SYNC_SET_MABEL)
			.add("installation_id", Sync.getInstallationId())
			.add("clientState", new Gson().toJson(clientState))
			.build();

		final Call call = App.getOkHttpClient().newCall(
			new Request.Builder()
				.url(serverPrefix + "/sync/api/debug_sync")
				.post(requestBody)
				.build()
		);


		Log.d(TAG, "@@syncMabel step 30: doing actual http request in this thread (" + Thread.currentThread().getId() + ":" + Thread.currentThread().toString() + ")");
		try {
			// arbritrary amount of time may pass on the next line. It is possible that marker/labels be modified during this operation.
			final SecretSyncDebugActivity.DebugSyncResponseJson response = new Gson().fromJson(call.execute().body().charStream(), SecretSyncDebugActivity.DebugSyncResponseJson.class);

			if (!response.success) {
				Log.d(TAG, "@@syncMabel server response is not success. Message: " + response.message);
				sr.stats.numIoExceptions++;
				return;
			}

			final int final_revno = response.final_revno;
			final Sync.Delta<Sync.MabelContent> append_delta = response.append_delta;

			if (append_delta == null) {
				Log.w(TAG, "@@syncMabel append delta is null. This should not happen.");
				sr.stats.numIoExceptions++;
				return;
			}

			final InternalDb.ApplyAppendDeltaResult applyResult = S.getDb().applyAppendDelta(final_revno, append_delta, entitiesBeforeSync, simpleToken);
			if (applyResult != InternalDb.ApplyAppendDeltaResult.ok) {
				Log.w(TAG, "@@syncMabel append delta result is not ok, but " + applyResult);
				sr.stats.numIoExceptions++;
				return;
			}

			// Based on operations in append_delta, fill in SyncStats
			for (final Sync.Operation<Sync.MabelContent> o : append_delta.operations) {
				switch (o.opkind) {
					case add: sr.stats.numInserts++; break;
					case mod: sr.stats.numUpdates++; break;
					case del: sr.stats.numDeletes++; break;
				}
			}

			// success!
			App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));

			Log.d(TAG, "Final revno: " + final_revno + " Apply result: " + applyResult + " Append delta: " + append_delta);
		} catch (JsonSyntaxException e) {
			Log.w(TAG, "@@syncMabel exception when parsing json from server", e);
			sr.stats.numParseExceptions++;

		} catch (JsonIOException | IOException e) {
			Log.w(TAG, "@@syncMabel exception when executing http call", e);
			sr.stats.numIoExceptions++;
		}
	}
}
