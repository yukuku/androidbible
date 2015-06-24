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
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.MarkerListActivity;
import yuku.alkitab.base.ac.MarkersActivity;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.History;
import yuku.alkitab.base.util.Sqlitil;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
	static final String TAG = SyncAdapter.class.getSimpleName();
	public static final String EXTRA_SYNC_SET_NAME = "syncSetName";

	final static Stack<String> syncSetsRunning = new Stack<>(); // need guard when accessing this

	/**
	 * Set up the sync adapter
	 */
	public SyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
	}

	/**
	 * Set up the sync adapter. This form of the constructor maintains compatibility with Android 3.0
	 * and later platform versions, so do not delete.
	 */
	@SuppressWarnings("UnusedDeclaration")
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
		if (syncSetName == null) {
			return;
		}

		synchronized (syncSetsRunning) {
			syncSetsRunning.add(syncSetName);
		}

		try {
			App.getLbm().sendBroadcast(new Intent(SyncSettingsActivity.ACTION_RELOAD));

			SyncRecorder.log(SyncRecorder.EventKind.sync_adapter_on_perform, syncSetName);

			if (!Preferences.getBoolean(Sync.prefkeyForSyncSetEnabled(syncSetName), true)) {
				SyncRecorder.log(SyncRecorder.EventKind.sync_adapter_set_not_enabled, syncSetName);

				return;
			}

			switch (syncSetName) {
				case SyncShadow.SYNC_SET_MABEL:
					syncMabel(syncResult);
					break;
				case SyncShadow.SYNC_SET_HISTORY:
					syncHistory(syncResult);
					break;
				case SyncShadow.SYNC_SET_PINS:
					syncPins(syncResult);
					break;
			}
		} finally {
			Log.d(TAG, "Sync result: " + syncResult);

			synchronized (syncSetsRunning) {
				final String popped = syncSetsRunning.pop();
				if (!popped.equals(syncSetName)) {
					throw new RuntimeException("syncSetsRunning not balanced: popped=" + popped + " actual=" + syncSetName);
				}
			}

			App.getLbm().sendBroadcast(new Intent(SyncSettingsActivity.ACTION_RELOAD));
		}
	}

	public static Set<String> getRunningSyncs() {
		final Set<String> res = new LinkedHashSet<>();
		synchronized (syncSetsRunning) {
			res.addAll(syncSetsRunning);
		}
		return res;
	}

	void syncMabel(final SyncResult sr) {
		final String syncSetName = SyncShadow.SYNC_SET_MABEL;

		final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
		if (simpleToken == null) {
			sr.stats.numAuthExceptions++;
			SyncRecorder.log(SyncRecorder.EventKind.error_no_simple_token, syncSetName);
			return;
		}

		Log.d(TAG, "@@syncMabel step 10: gathering client state");
		final Pair<Sync_Mabel.ClientState, List<Sync.Entity<Sync_Mabel.Content>>> pair = Sync_Mabel.getClientStateAndCurrentEntities();
		final Sync_Mabel.ClientState clientState = pair.first;
		final List<Sync.Entity<Sync_Mabel.Content>> entitiesBeforeSync = pair.second;

		SyncRecorder.log(SyncRecorder.EventKind.current_entities_gathered, syncSetName, "base_revno", clientState.base_revno, "client_delta_operations_size", clientState.delta.operations.size(), "client_entities_size", entitiesBeforeSync.size());

		final String serverPrefix = Sync.getEffectiveServerPrefix();
		Log.d(TAG, "@@syncMabel step 20: building http request. Server prefix: " + serverPrefix);
		final RequestBody requestBody = new MultipartBuilder()
			.type(MultipartBuilder.FORM)
			.addFormDataPart("simpleToken", simpleToken)
			.addFormDataPart("syncSetName", syncSetName)
			.addFormDataPart("installation_id", U.getInstallationId())
			.addFormDataPart("clientState", App.getDefaultGson().toJson(clientState))
			.build();

		final Call call = App.getOkHttpClient().newCall(
			new Request.Builder()
				.url(serverPrefix + "/sync/api/sync")
				.post(requestBody)
				.build()
		);


		Log.d(TAG, "@@syncMabel step 30: doing actual http request in this thread (" + Thread.currentThread().getId() + ":" + Thread.currentThread().toString() + ")");
		try {
			// arbritrary amount of time may pass on the next line. It is possible that marker/labels be modified during this operation.
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_pre, syncSetName);
			final long startTime = System.currentTimeMillis();
			final String response_s = U.inputStreamUtf8ToString(call.execute().body().byteStream());
			Log.d(TAG, "@@syncMabel server response string: " + response_s);
			final Sync_Mabel.SyncResponseJson response = App.getDefaultGson().fromJson(response_s, Sync_Mabel.SyncResponseJson.class);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_response_ok, syncSetName, "duration_ms", System.currentTimeMillis() - startTime);

			if (!response.success) {
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_not_success, syncSetName, "message", response.message);
				Log.d(TAG, "@@syncMabel server response is not success. Message: " + response.message);
				sr.stats.numIoExceptions++;
				return;
			}

			final int final_revno = response.final_revno;
			final Sync.Delta<Sync_Mabel.Content> append_delta = response.append_delta;

			if (append_delta == null) {
				Log.w(TAG, "@@syncMabel append delta is null. This should not happen.");
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_error_append_delta_null, syncSetName);
				sr.stats.numIoExceptions++;
				return;
			}

			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_got_success_data, syncSetName, "final_revno", final_revno, "append_delta_operations_size", append_delta.operations.size());

			final Sync.ApplyAppendDeltaResult applyResult = S.getDb().applyMabelAppendDelta(final_revno, append_delta, entitiesBeforeSync, simpleToken);

			SyncRecorder.log(SyncRecorder.EventKind.apply_result, syncSetName, "apply_result", applyResult.name());

			if (applyResult != Sync.ApplyAppendDeltaResult.ok) {
				Log.w(TAG, "@@syncMabel append delta result is not ok, but " + applyResult);
				sr.stats.numIoExceptions++;
				return;
			}

			// Based on operations in append_delta, fill in SyncStats
			for (final Sync.Operation<Sync_Mabel.Content> o : append_delta.operations) {
				switch (o.opkind) {
					case add:
						sr.stats.numInserts++;
						break;
					case mod:
						sr.stats.numUpdates++;
						break;
					case del:
						sr.stats.numDeletes++;
						break;
				}
			}

			// success! Tell our world.
			SyncRecorder.log(SyncRecorder.EventKind.all_succeeded, syncSetName, "insert_count", sr.stats.numInserts, "update_count", sr.stats.numUpdates, "delete_count", sr.stats.numDeletes);

			App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));
			App.getLbm().sendBroadcast(new Intent(MarkersActivity.ACTION_RELOAD));
			App.getLbm().sendBroadcast(new Intent(MarkerListActivity.ACTION_RELOAD));

			Log.d(TAG, "Final revno: " + final_revno + " Apply result: " + applyResult + " Append delta: " + append_delta);
			SyncRecorder.saveLastSuccessTime(syncSetName, Sqlitil.nowDateTime());
			App.getLbm().sendBroadcast(new Intent(SyncSettingsActivity.ACTION_RELOAD));
		} catch (JsonSyntaxException e) {
			Log.w(TAG, "@@syncMabel exception when parsing json from server", e);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_error_syntax, syncSetName);
			sr.stats.numParseExceptions++;

		} catch (JsonIOException | IOException e) {
			Log.w(TAG, "@@syncMabel exception when executing http call", e);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_error_io, syncSetName);
			sr.stats.numIoExceptions++;
		}
	}

	void syncHistory(final SyncResult sr) {
		final String syncSetName = SyncShadow.SYNC_SET_HISTORY;

		final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
		if (simpleToken == null) {
			sr.stats.numAuthExceptions++;
			SyncRecorder.log(SyncRecorder.EventKind.error_no_simple_token, syncSetName);
			return;
		}

		Log.d(TAG, "@@syncHistory step 10: gathering client state");
		final Pair<Sync_History.ClientState, List<Sync.Entity<Sync_History.Content>>> pair = Sync_History.getClientStateAndCurrentEntities();
		final Sync_History.ClientState clientState = pair.first;
		final List<Sync.Entity<Sync_History.Content>> entitiesBeforeSync = pair.second;

		SyncRecorder.log(SyncRecorder.EventKind.current_entities_gathered, syncSetName, "base_revno", clientState.base_revno, "client_delta_operations_size", clientState.delta.operations.size(), "client_entities_size", entitiesBeforeSync.size());

		final String serverPrefix = Sync.getEffectiveServerPrefix();
		Log.d(TAG, "@@syncHistory step 20: building http request. Server prefix: " + serverPrefix);
		final RequestBody requestBody = new FormEncodingBuilder()
			.add("simpleToken", simpleToken)
			.add("syncSetName", syncSetName)
			.add("installation_id", U.getInstallationId())
			.add("clientState", App.getDefaultGson().toJson(clientState))
			.build();

		final Call call = App.getOkHttpClient().newCall(
			new Request.Builder()
				.url(serverPrefix + "/sync/api/sync")
				.post(requestBody)
				.build()
		);


		Log.d(TAG, "@@syncHistory step 30: doing actual http request in this thread (" + Thread.currentThread().getId() + ":" + Thread.currentThread().toString() + ")");
		try {
			// arbritrary amount of time may pass on the next line. It is possible that marker/labels be modified during this operation.
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_pre, syncSetName);
			final long startTime = System.currentTimeMillis();
			final String response_s = U.inputStreamUtf8ToString(call.execute().body().byteStream());
			Log.d(TAG, "@@syncHistory server response string: " + response_s);
			final Sync_History.SyncResponseJson response = App.getDefaultGson().fromJson(response_s, Sync_History.SyncResponseJson.class);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_response_ok, syncSetName, "duration_ms", System.currentTimeMillis() - startTime);

			if (!response.success) {
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_not_success, syncSetName, "message", response.message);
				Log.d(TAG, "@@syncHistory server response is not success. Message: " + response.message);
				sr.stats.numIoExceptions++;
				return;
			}

			final int final_revno = response.final_revno;
			final Sync.Delta<Sync_History.Content> append_delta = response.append_delta;

			if (append_delta == null) {
				Log.w(TAG, "@@syncHistory append delta is null. This should not happen.");
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_error_append_delta_null, syncSetName);
				sr.stats.numIoExceptions++;
				return;
			}

			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_got_success_data, syncSetName, "final_revno", final_revno, "append_delta_operations_size", append_delta.operations.size());

			final Sync.ApplyAppendDeltaResult applyResult = History.getInstance().applyHistoryAppendDelta(final_revno, append_delta, entitiesBeforeSync, simpleToken);

			SyncRecorder.log(SyncRecorder.EventKind.apply_result, syncSetName, "apply_result", applyResult.name());

			if (applyResult != Sync.ApplyAppendDeltaResult.ok) {
				Log.w(TAG, "@@syncHistory append delta result is not ok, but " + applyResult);
				sr.stats.numIoExceptions++;
				return;
			}

			// Based on operations in append_delta, fill in SyncStats
			for (final Sync.Operation<Sync_History.Content> o : append_delta.operations) {
				switch (o.opkind) {
					case add:
						sr.stats.numInserts++;
						break;
					case mod:
						sr.stats.numUpdates++;
						break;
					case del:
						sr.stats.numDeletes++;
						break;
				}
			}

			// success! Tell our world.
			SyncRecorder.log(SyncRecorder.EventKind.all_succeeded, syncSetName, "insert_count", sr.stats.numInserts, "update_count", sr.stats.numUpdates, "delete_count", sr.stats.numDeletes);

			Log.d(TAG, "Final revno: " + final_revno + " Apply result: " + applyResult + " Append delta: " + append_delta);
			SyncRecorder.saveLastSuccessTime(syncSetName, Sqlitil.nowDateTime());
			App.getLbm().sendBroadcast(new Intent(SyncSettingsActivity.ACTION_RELOAD));
		} catch (JsonSyntaxException e) {
			Log.w(TAG, "@@syncHistory exception when parsing json from server", e);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_error_syntax, syncSetName);
			sr.stats.numParseExceptions++;

		} catch (JsonIOException | IOException e) {
			Log.w(TAG, "@@syncHistory exception when executing http call", e);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_error_io, syncSetName);
			sr.stats.numIoExceptions++;
		}
	}

	void syncPins(final SyncResult sr) {
		final String syncSetName = SyncShadow.SYNC_SET_PINS;

		final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
		if (simpleToken == null) {
			sr.stats.numAuthExceptions++;
			SyncRecorder.log(SyncRecorder.EventKind.error_no_simple_token, syncSetName);
			return;
		}

		Log.d(TAG, "@@syncPins step 10: gathering client state");
		final Pair<Sync_Pins.ClientState, List<Sync.Entity<Sync_Pins.Content>>> pair = Sync_Pins.getClientStateAndCurrentEntities();
		final Sync_Pins.ClientState clientState = pair.first;
		final List<Sync.Entity<Sync_Pins.Content>> entitiesBeforeSync = pair.second;

		SyncRecorder.log(SyncRecorder.EventKind.current_entities_gathered, syncSetName, "base_revno", clientState.base_revno, "client_delta_operations_size", clientState.delta.operations.size(), "client_entities_size", entitiesBeforeSync.size());

		final String serverPrefix = Sync.getEffectiveServerPrefix();
		Log.d(TAG, "@@syncPins step 20: building http request. Server prefix: " + serverPrefix);
		final RequestBody requestBody = new FormEncodingBuilder()
			.add("simpleToken", simpleToken)
			.add("syncSetName", syncSetName)
			.add("installation_id", U.getInstallationId())
			.add("clientState", App.getDefaultGson().toJson(clientState))
			.build();

		final Call call = App.getOkHttpClient().newCall(
			new Request.Builder()
				.url(serverPrefix + "/sync/api/sync")
				.post(requestBody)
				.build()
		);


		Log.d(TAG, "@@syncPins step 30: doing actual http request in this thread (" + Thread.currentThread().getId() + ":" + Thread.currentThread().toString() + ")");
		try {
			// arbritrary amount of time may pass on the next line. It is possible that marker/labels be modified during this operation.
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_pre, syncSetName);
			final long startTime = System.currentTimeMillis();
			final String response_s = U.inputStreamUtf8ToString(call.execute().body().byteStream());
			Log.d(TAG, "@@syncPins server response string: " + response_s);
			final Sync_Pins.SyncResponseJson response = App.getDefaultGson().fromJson(response_s, Sync_Pins.SyncResponseJson.class);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_response_ok, syncSetName, "duration_ms", System.currentTimeMillis() - startTime);

			if (!response.success) {
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_not_success, syncSetName, "message", response.message);
				Log.d(TAG, "@@syncPins server response is not success. Message: " + response.message);
				sr.stats.numIoExceptions++;
				return;
			}

			final int final_revno = response.final_revno;
			final Sync.Delta<Sync_Pins.Content> append_delta = response.append_delta;

			if (append_delta == null) {
				Log.w(TAG, "@@syncPins append delta is null. This should not happen.");
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_error_append_delta_null, syncSetName);
				sr.stats.numIoExceptions++;
				return;
			}

			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_got_success_data, syncSetName, "final_revno", final_revno, "append_delta_operations_size", append_delta.operations.size());

			final Sync.ApplyAppendDeltaResult applyResult = S.getDb().applyPinsAppendDelta(final_revno, append_delta, entitiesBeforeSync, simpleToken);

			SyncRecorder.log(SyncRecorder.EventKind.apply_result, syncSetName, "apply_result", applyResult.name());

			if (applyResult != Sync.ApplyAppendDeltaResult.ok) {
				Log.w(TAG, "@@syncPins append delta result is not ok, but " + applyResult);
				sr.stats.numIoExceptions++;
				return;
			}

			// Based on operations in append_delta, fill in SyncStats
			for (final Sync.Operation<Sync_Pins.Content> o : append_delta.operations) {
				switch (o.opkind) {
					case add:
						sr.stats.numInserts++;
						break;
					case mod:
						sr.stats.numUpdates++;
						break;
					case del:
						sr.stats.numDeletes++;
						break;
				}
			}

			// success! Tell our world.
			SyncRecorder.log(SyncRecorder.EventKind.all_succeeded, syncSetName, "insert_count", sr.stats.numInserts, "update_count", sr.stats.numUpdates, "delete_count", sr.stats.numDeletes);

			Log.d(TAG, "Final revno: " + final_revno + " Apply result: " + applyResult + " Append delta: " + append_delta);
			SyncRecorder.saveLastSuccessTime(syncSetName, Sqlitil.nowDateTime());
			App.getLbm().sendBroadcast(new Intent(SyncSettingsActivity.ACTION_RELOAD));
		} catch (JsonSyntaxException e) {
			Log.w(TAG, "@@syncPins exception when parsing json from server", e);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_error_syntax, syncSetName);
			sr.stats.numParseExceptions++;

		} catch (JsonIOException | IOException e) {
			Log.w(TAG, "@@syncPins exception when executing http call", e);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_error_io, syncSetName);
			sr.stats.numIoExceptions++;
		}
	}
}
