package yuku.alkitab.base.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Pair;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.MarkerListActivity;
import yuku.alkitab.base.ac.MarkersActivity;
import yuku.alkitab.base.ac.ReadingPlanActivity;
import yuku.alkitab.base.connection.Connections;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.History;
import yuku.alkitab.base.util.HistorySyncUtil;
import yuku.alkitab.base.util.InstallationUtil;
import yuku.alkitab.base.util.Sqlitil;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
	static final String TAG = SyncAdapter.class.getSimpleName();
	public static final String EXTRA_SYNC_SET_NAMES = "syncSetNames";
	private static final int PARTIAL_SYNC_THRESHOLD = 100;

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

	/**
	 * Patches entities with operations, returning new set of entities.
	 * Add and mod overwrites without merge, del deletes immediately
	 * @param entities before patch
	 * @param operations patch in temporal order (old first then new)
	 * @param <C> type of content
	 * @return after patch
	 */
	public static <C> List<Sync.Entity<C>> patchNoConflict(final List<Sync.Entity<C>> entities, final List<Sync.Operation<C>> operations) {
		/*
		Ported from server code:

		def patchNoConflict(entities, operations):
			# convert to map for faster lookup
			entities_map = {
				(e.gid, e.kind): e
				for e in entities
			}

			for o in operations:
				if o.opkind == 'del':
					del entities_map[(o.gid, o.kind)]
				else: # add and mod are treated the same: just overwrite!
					entities_map[(o.gid, o.kind)] = Entity(o.kind, o.gid, content=o.content, creator_id=o.creator_id)

			return entities_map.values()

		 */

		final Map<Pair<String, String>, Sync.Entity<C>> entities_map = new HashMap<>();
		for (final Sync.Entity<C> entity : entities) {
			entities_map.put(Pair.create(entity.gid, entity.kind), entity);
		}

		for (final Sync.Operation<C> o : operations) {
			switch (o.opkind) {
				case del:
					entities_map.remove(Pair.create(o.gid, o.kind));
					break;
				case add:
				case mod:
					entities_map.put(Pair.create(o.gid, o.kind), new Sync.Entity<>(o.kind, o.gid, o.content));
					break;
			}
		}

		return new ArrayList<>(entities_map.values());
	}

	/*
	 * Specify the code you want to run in the sync adapter. The entire
	 * sync adapter runs in a background thread, so you don't have to set
	 * up your own background processing.
	 */
	@Override
	public void onPerformSync(final Account account, final Bundle extras, final String authority, final ContentProviderClient provider, final SyncResult syncResult) {
		AppLog.d(TAG, "@@onPerformSync account:" + account + " extras:" + extras + " authority:" + authority);

		final String[] syncSetNames = App.getDefaultGson().fromJson(extras.getString(EXTRA_SYNC_SET_NAMES), String[].class);
		if (syncSetNames == null || syncSetNames.length == 0) {
			return;
		}

		for (final String syncSetName : syncSetNames) {
			synchronized (syncSetsRunning) {
				syncSetsRunning.add(syncSetName);
			}

			try {
				App.getLbm().sendBroadcast(new Intent(SyncSettingsActivity.ACTION_RELOAD));

				SyncRecorder.log(SyncRecorder.EventKind.sync_adapter_on_perform, syncSetName);

				if (!Preferences.getBoolean(Sync.prefkeyForSyncSetEnabled(syncSetName), true)) {
					SyncRecorder.log(SyncRecorder.EventKind.sync_adapter_set_not_enabled, syncSetName);

					continue;
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
					case SyncShadow.SYNC_SET_RP:
						syncRp(syncResult);
						break;
				}
			} finally {
				synchronized (syncSetsRunning) {
					final String popped = syncSetsRunning.pop();
					if (!popped.equals(syncSetName)) {
						throw new RuntimeException("syncSetsRunning not balanced: popped=" + popped + " actual=" + syncSetName);
					}
				}

				App.getLbm().sendBroadcast(new Intent(SyncSettingsActivity.ACTION_RELOAD));
			}
		}

		AppLog.d(TAG, "Sync result: " + syncResult + " hasSoftError=" + syncResult.hasSoftError() + " hasHardError=" + syncResult.hasHardError() + " ioex=" + syncResult.stats.numIoExceptions);
	}

	public static Set<String> getRunningSyncs() {
		final Set<String> res = new LinkedHashSet<>();
		synchronized (syncSetsRunning) {
			res.addAll(syncSetsRunning);
		}
		return res;
	}

	/** Based on operations in append_delta, fill in SyncStats */
	static <C> void fillInStatsFromAppendDelta(final Sync.Delta<C> append_delta, final SyncResult sr) {
		for (final Sync.Operation<C> o : append_delta.operations) {
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
	}

	/**
	 * If the client state's delta is too big, we remove some of the changes, so only some changes are transmitted to the server,
	 * and the server will not time out anymore.
	 *
	 * WARNING: If you are using partial sync by this method, do not create sync shadow from the current state, but you must
	 * create it from an existing sync shadow by applying the client delta AND the append delta (given from the server).
	 * Also, the current state must be updated using the append delta from the server.
	 *
	 * @param clientState will be modified
	 * @return true iff chopped
	 */
	static <C> boolean chopClientState(final Sync.ClientState<C> clientState, final String syncSetName) {
		final List<Sync.Operation<C>> src = clientState.delta.operations;

		// fast path if the operation count is below threshold
		if (src.size() <= PARTIAL_SYNC_THRESHOLD) {
			return false;
		}

		final List<Sync.Operation<C>> dst = new ArrayList<>();

		// Add operations until we reach the threshold
		for (final Sync.Operation<C> o : src) {
			if (dst.size() >= PARTIAL_SYNC_THRESHOLD) {
				break;
			}

			dst.add(o);
		}

		SyncRecorder.log(SyncRecorder.EventKind.partial_sync_info, syncSetName, "client_delta_operations_size_original", src.size(), "client_delta_operations_size_chopped", dst.size());

		final boolean res = dst.size() < src.size();

		src.clear();
		src.addAll(dst);

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

		AppLog.d(TAG, "@@syncMabel step 10: gathering client state");
		final Sync.GetClientStateResult<Sync_Mabel.Content> pair = Sync_Mabel.getClientStateAndCurrentEntities();
		final Sync.ClientState<Sync_Mabel.Content> clientState = pair.clientState;
		final List<Sync.Entity<Sync_Mabel.Content>> shadowEntities = pair.shadowEntities;
		final List<Sync.Entity<Sync_Mabel.Content>> entitiesBeforeSync = pair.currentEntities;

		SyncRecorder.log(SyncRecorder.EventKind.current_entities_gathered, syncSetName, "base_revno", clientState.base_revno, "client_delta_operations_size", clientState.delta.operations.size(), "client_entities_size", entitiesBeforeSync.size());

		final boolean isPartial = chopClientState(clientState, syncSetName);

		final String serverPrefix = Sync.getEffectiveServerPrefix();
		AppLog.d(TAG, "@@syncMabel step 20: building http request. Server prefix: " + serverPrefix);
		final RequestBody requestBody = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("simpleToken", simpleToken)
			.addFormDataPart("syncSetName", syncSetName)
			.addFormDataPart("installation_id", InstallationUtil.getInstallationId())
			.addFormDataPart("clientState", App.getDefaultGson().toJson(clientState))
			.build();

		final Call call = Connections.getLongTimeoutOkHttpClient().newCall(
			new Request.Builder()
				.url(serverPrefix + "sync/api/sync")
				.post(requestBody)
				.build()
		);


		AppLog.d(TAG, "@@syncMabel step 30: doing actual http request in this thread (" + Thread.currentThread().getId() + ":" + Thread.currentThread().toString() + ")");
		try {
			// arbritrary amount of time may pass on the next line. It is possible for the current data to be modified during this operation.
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_pre, syncSetName, "serverPrefix", Sync.getEffectiveServerPrefix());
			final long startTime = System.currentTimeMillis();
			final String response_s = call.execute().body().string();
			AppLog.d(TAG, "@@syncMabel server response string: " + response_s);
			final Sync.SyncResponseJson<Sync_Mabel.Content> response = App.getDefaultGson().fromJson(response_s, new TypeToken<Sync.SyncResponseJson<Sync_Mabel.Content>>() {}.getType());
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_response_ok, syncSetName, "duration_ms", System.currentTimeMillis() - startTime);

			if (!response.success) {
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_not_success, syncSetName, "message", response.message);
				AppLog.d(TAG, "@@syncMabel server response is not success. Message: " + response.message);
				sr.stats.numIoExceptions++;
				return;
			}

			final int final_revno = response.final_revno;
			final Sync.Delta<Sync_Mabel.Content> append_delta = response.append_delta;

			if (append_delta == null) {
				AppLog.w(TAG, "@@syncMabel append delta is null. This should not happen.");
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_error_append_delta_null, syncSetName);
				sr.stats.numIoExceptions++;
				return;
			}

			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_got_success_data, syncSetName, "final_revno", final_revno, "append_delta_operations_size", append_delta.operations.size());

			final Sync.ApplyAppendDeltaResult applyResult = S.getDb().applyMabelAppendDelta(final_revno, shadowEntities, clientState, append_delta, entitiesBeforeSync, simpleToken);

			SyncRecorder.log(SyncRecorder.EventKind.apply_result, syncSetName, "apply_result", applyResult.name());

			if (applyResult != Sync.ApplyAppendDeltaResult.ok) {
				AppLog.w(TAG, "@@syncMabel append delta result is not ok, but " + applyResult);
				sr.stats.numIoExceptions++;
				return;
			}

			fillInStatsFromAppendDelta(append_delta, sr);

			if (isPartial) {
				Sync.notifySyncNeeded(syncSetName);
			}

			// success! Tell our world.
			SyncRecorder.log(SyncRecorder.EventKind.all_succeeded, syncSetName, "insert_count", sr.stats.numInserts, "update_count", sr.stats.numUpdates, "delete_count", sr.stats.numDeletes);

			App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));
			App.getLbm().sendBroadcast(new Intent(MarkersActivity.ACTION_RELOAD));
			App.getLbm().sendBroadcast(new Intent(MarkerListActivity.ACTION_RELOAD));

			AppLog.d(TAG, "Final revno: " + final_revno + " Apply result: " + applyResult + " Append delta: " + append_delta);
			SyncRecorder.saveLastSuccessTime(syncSetName, Sqlitil.nowDateTime());
			App.getLbm().sendBroadcast(new Intent(SyncSettingsActivity.ACTION_RELOAD));
		} catch (JsonSyntaxException e) {
			AppLog.w(TAG, "@@syncMabel exception when parsing json from server", e);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_error_syntax, syncSetName);
			sr.stats.numParseExceptions++;

		} catch (JsonIOException | IOException e) {
			AppLog.w(TAG, "@@syncMabel exception when executing http call", e);
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

		AppLog.d(TAG, "@@syncHistory step 10: gathering client state");
		final Pair<Sync.ClientState<Sync_History.Content>, List<Sync.Entity<Sync_History.Content>>> pair = Sync_History.getClientStateAndCurrentEntities();
		final Sync.ClientState<Sync_History.Content> clientState = pair.first;
		final List<Sync.Entity<Sync_History.Content>> entitiesBeforeSync = pair.second;

		SyncRecorder.log(SyncRecorder.EventKind.current_entities_gathered, syncSetName, "base_revno", clientState.base_revno, "client_delta_operations_size", clientState.delta.operations.size(), "client_entities_size", entitiesBeforeSync.size());

		final String serverPrefix = Sync.getEffectiveServerPrefix();
		AppLog.d(TAG, "@@syncHistory step 20: building http request. Server prefix: " + serverPrefix);
		final RequestBody requestBody = new FormBody.Builder()
			.add("simpleToken", simpleToken)
			.add("syncSetName", syncSetName)
			.add("installation_id", InstallationUtil.getInstallationId())
			.add("clientState", App.getDefaultGson().toJson(clientState))
			.build();

		final Call call = Connections.getLongTimeoutOkHttpClient().newCall(
			new Request.Builder()
				.url(serverPrefix + "sync/api/sync")
				.post(requestBody)
				.build()
		);


		AppLog.d(TAG, "@@syncHistory step 30: doing actual http request in this thread (" + Thread.currentThread().getId() + ":" + Thread.currentThread().toString() + ")");
		try {
			// arbritrary amount of time may pass on the next line. It is possible for the current data to be modified during this operation.
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_pre, syncSetName, "serverPrefix", Sync.getEffectiveServerPrefix());
			final long startTime = System.currentTimeMillis();
			final String response_s = call.execute().body().string();
			AppLog.d(TAG, "@@syncHistory server response string: " + response_s);
			final Sync.SyncResponseJson<Sync_History.Content> response = App.getDefaultGson().fromJson(response_s, new TypeToken<Sync.SyncResponseJson<Sync_History.Content>>() {}.getType());
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_response_ok, syncSetName, "duration_ms", System.currentTimeMillis() - startTime);

			if (!response.success) {
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_not_success, syncSetName, "message", response.message);
				AppLog.d(TAG, "@@syncHistory server response is not success. Message: " + response.message);
				sr.stats.numIoExceptions++;
				return;
			}

			final int final_revno = response.final_revno;
			final Sync.Delta<Sync_History.Content> append_delta = response.append_delta;

			if (append_delta == null) {
				AppLog.w(TAG, "@@syncHistory append delta is null. This should not happen.");
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_error_append_delta_null, syncSetName);
				sr.stats.numIoExceptions++;
				return;
			}

			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_got_success_data, syncSetName, "final_revno", final_revno, "append_delta_operations_size", append_delta.operations.size());

			final Sync.ApplyAppendDeltaResult applyResult = HistorySyncUtil.applyHistoryAppendDelta(History.INSTANCE, final_revno, append_delta, entitiesBeforeSync, simpleToken);

			SyncRecorder.log(SyncRecorder.EventKind.apply_result, syncSetName, "apply_result", applyResult.name());

			if (applyResult != Sync.ApplyAppendDeltaResult.ok) {
				AppLog.w(TAG, "@@syncHistory append delta result is not ok, but " + applyResult);
				sr.stats.numIoExceptions++;
				return;
			}

			fillInStatsFromAppendDelta(append_delta, sr);

			// success! Tell our world.
			SyncRecorder.log(SyncRecorder.EventKind.all_succeeded, syncSetName, "insert_count", sr.stats.numInserts, "update_count", sr.stats.numUpdates, "delete_count", sr.stats.numDeletes);

			AppLog.d(TAG, "Final revno: " + final_revno + " Apply result: " + applyResult + " Append delta: " + append_delta);
			SyncRecorder.saveLastSuccessTime(syncSetName, Sqlitil.nowDateTime());
			App.getLbm().sendBroadcast(new Intent(SyncSettingsActivity.ACTION_RELOAD));
		} catch (JsonSyntaxException e) {
			AppLog.w(TAG, "@@syncHistory exception when parsing json from server", e);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_error_syntax, syncSetName);
			sr.stats.numParseExceptions++;

		} catch (JsonIOException | IOException e) {
			AppLog.w(TAG, "@@syncHistory exception when executing http call", e);
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

		AppLog.d(TAG, "@@syncPins step 10: gathering client state");
		final Pair<Sync.ClientState<Sync_Pins.Content>, List<Sync.Entity<Sync_Pins.Content>>> pair = Sync_Pins.getClientStateAndCurrentEntities();
		final Sync.ClientState<Sync_Pins.Content> clientState = pair.first;
		final List<Sync.Entity<Sync_Pins.Content>> entitiesBeforeSync = pair.second;

		SyncRecorder.log(SyncRecorder.EventKind.current_entities_gathered, syncSetName, "base_revno", clientState.base_revno, "client_delta_operations_size", clientState.delta.operations.size(), "client_entities_size", entitiesBeforeSync.size());

		final String serverPrefix = Sync.getEffectiveServerPrefix();
		AppLog.d(TAG, "@@syncPins step 20: building http request. Server prefix: " + serverPrefix);
		final RequestBody requestBody = new FormBody.Builder()
			.add("simpleToken", simpleToken)
			.add("syncSetName", syncSetName)
			.add("installation_id", InstallationUtil.getInstallationId())
			.add("clientState", App.getDefaultGson().toJson(clientState))
			.build();

		final Call call = Connections.getLongTimeoutOkHttpClient().newCall(
			new Request.Builder()
				.url(serverPrefix + "sync/api/sync")
				.post(requestBody)
				.build()
		);


		AppLog.d(TAG, "@@syncPins step 30: doing actual http request in this thread (" + Thread.currentThread().getId() + ":" + Thread.currentThread().toString() + ")");
		try {
			// arbritrary amount of time may pass on the next line. It is possible for the current data to be modified during this operation.
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_pre, syncSetName, "serverPrefix", Sync.getEffectiveServerPrefix());
			final long startTime = System.currentTimeMillis();
			final String response_s = call.execute().body().string();
			AppLog.d(TAG, "@@syncPins server response string: " + response_s);
			final Sync.SyncResponseJson<Sync_Pins.Content> response = App.getDefaultGson().fromJson(response_s, new TypeToken<Sync.SyncResponseJson<Sync_Pins.Content>>() {}.getType());
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_response_ok, syncSetName, "duration_ms", System.currentTimeMillis() - startTime);

			if (!response.success) {
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_not_success, syncSetName, "message", response.message);
				AppLog.d(TAG, "@@syncPins server response is not success. Message: " + response.message);
				sr.stats.numIoExceptions++;
				return;
			}

			final int final_revno = response.final_revno;
			final Sync.Delta<Sync_Pins.Content> append_delta = response.append_delta;

			if (append_delta == null) {
				AppLog.w(TAG, "@@syncPins append delta is null. This should not happen.");
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_error_append_delta_null, syncSetName);
				sr.stats.numIoExceptions++;
				return;
			}

			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_got_success_data, syncSetName, "final_revno", final_revno, "append_delta_operations_size", append_delta.operations.size());

			final Sync.ApplyAppendDeltaResult applyResult = S.getDb().applyPinsAppendDelta(final_revno, append_delta, entitiesBeforeSync, simpleToken);

			SyncRecorder.log(SyncRecorder.EventKind.apply_result, syncSetName, "apply_result", applyResult.name());

			if (applyResult != Sync.ApplyAppendDeltaResult.ok) {
				AppLog.w(TAG, "@@syncPins append delta result is not ok, but " + applyResult);
				sr.stats.numIoExceptions++;
				return;
			}

			fillInStatsFromAppendDelta(append_delta, sr);

			// success! Tell our world.
			SyncRecorder.log(SyncRecorder.EventKind.all_succeeded, syncSetName, "insert_count", sr.stats.numInserts, "update_count", sr.stats.numUpdates, "delete_count", sr.stats.numDeletes);

			App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));

			AppLog.d(TAG, "Final revno: " + final_revno + " Apply result: " + applyResult + " Append delta: " + append_delta);
			SyncRecorder.saveLastSuccessTime(syncSetName, Sqlitil.nowDateTime());
			App.getLbm().sendBroadcast(new Intent(SyncSettingsActivity.ACTION_RELOAD));
		} catch (JsonSyntaxException e) {
			AppLog.w(TAG, "@@syncPins exception when parsing json from server", e);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_error_syntax, syncSetName);
			sr.stats.numParseExceptions++;

		} catch (JsonIOException | IOException e) {
			AppLog.w(TAG, "@@syncPins exception when executing http call", e);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_error_io, syncSetName);
			sr.stats.numIoExceptions++;
		}
	}

	void syncRp(final SyncResult sr) {
		final String syncSetName = SyncShadow.SYNC_SET_RP;

		final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
		if (simpleToken == null) {
			sr.stats.numAuthExceptions++;
			SyncRecorder.log(SyncRecorder.EventKind.error_no_simple_token, syncSetName);
			return;
		}

		AppLog.d(TAG, "@@syncRp step 10: gathering client state");
		final Pair<Sync.ClientState<Sync_Rp.Content>, List<Sync.Entity<Sync_Rp.Content>>> pair = Sync_Rp.getClientStateAndCurrentEntities();
		final Sync.ClientState<Sync_Rp.Content> clientState = pair.first;
		final List<Sync.Entity<Sync_Rp.Content>> entitiesBeforeSync = pair.second;

		SyncRecorder.log(SyncRecorder.EventKind.current_entities_gathered, syncSetName, "base_revno", clientState.base_revno, "client_delta_operations_size", clientState.delta.operations.size(), "client_entities_size", entitiesBeforeSync.size());

		final String serverPrefix = Sync.getEffectiveServerPrefix();
		AppLog.d(TAG, "@@syncRp step 20: building http request. Server prefix: " + serverPrefix);
		final RequestBody requestBody = new FormBody.Builder()
			.add("simpleToken", simpleToken)
			.add("syncSetName", syncSetName)
			.add("installation_id", InstallationUtil.getInstallationId())
			.add("clientState", App.getDefaultGson().toJson(clientState))
			.build();

		final Call call = Connections.getLongTimeoutOkHttpClient().newCall(
			new Request.Builder()
				.url(serverPrefix + "sync/api/sync")
				.post(requestBody)
				.build()
		);


		AppLog.d(TAG, "@@syncRp step 30: doing actual http request in this thread (" + Thread.currentThread().getId() + ":" + Thread.currentThread().toString() + ")");
		try {
			// arbritrary amount of time may pass on the next line. It is possible for the current data to be modified during this operation.
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_pre, syncSetName, "serverPrefix", Sync.getEffectiveServerPrefix());
			final long startTime = System.currentTimeMillis();
			final String response_s = call.execute().body().string();
			AppLog.d(TAG, "@@syncRp server response string: " + response_s);
			final Sync.SyncResponseJson<Sync_Rp.Content> response = App.getDefaultGson().fromJson(response_s, new TypeToken<Sync.SyncResponseJson<Sync_Rp.Content>>() {}.getType());
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_response_ok, syncSetName, "duration_ms", System.currentTimeMillis() - startTime);

			if (!response.success) {
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_not_success, syncSetName, "message", response.message);
				AppLog.d(TAG, "@@syncRp server response is not success. Message: " + response.message);
				sr.stats.numIoExceptions++;
				return;
			}

			final int final_revno = response.final_revno;
			final Sync.Delta<Sync_Rp.Content> append_delta = response.append_delta;

			if (append_delta == null) {
				AppLog.w(TAG, "@@syncRp append delta is null. This should not happen.");
				SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_error_append_delta_null, syncSetName);
				sr.stats.numIoExceptions++;
				return;
			}

			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_got_success_data, syncSetName, "final_revno", final_revno, "append_delta_operations_size", append_delta.operations.size());

			final Sync.ApplyAppendDeltaResult applyResult = S.getDb().applyRpAppendDelta(final_revno, append_delta, entitiesBeforeSync, simpleToken);

			SyncRecorder.log(SyncRecorder.EventKind.apply_result, syncSetName, "apply_result", applyResult.name());

			if (applyResult != Sync.ApplyAppendDeltaResult.ok) {
				AppLog.w(TAG, "@@syncRp append delta result is not ok, but " + applyResult);
				sr.stats.numIoExceptions++;
				return;
			}

			fillInStatsFromAppendDelta(append_delta, sr);

			// success! Tell our world.
			SyncRecorder.log(SyncRecorder.EventKind.all_succeeded, syncSetName, "insert_count", sr.stats.numInserts, "update_count", sr.stats.numUpdates, "delete_count", sr.stats.numDeletes);

			App.getLbm().sendBroadcast(new Intent(ReadingPlanActivity.ACTION_READING_PLAN_PROGRESS_CHANGED));

			AppLog.d(TAG, "Final revno: " + final_revno + " Apply result: " + applyResult + " Append delta: " + append_delta);
			SyncRecorder.saveLastSuccessTime(syncSetName, Sqlitil.nowDateTime());
			App.getLbm().sendBroadcast(new Intent(SyncSettingsActivity.ACTION_RELOAD));
		} catch (JsonSyntaxException e) {
			AppLog.w(TAG, "@@syncRp exception when parsing json from server", e);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_error_syntax, syncSetName);
			sr.stats.numParseExceptions++;

		} catch (JsonIOException | IOException e) {
			AppLog.w(TAG, "@@syncRp exception when executing http call", e);
			SyncRecorder.log(SyncRecorder.EventKind.sync_to_server_post_error_io, syncSetName);
			sr.stats.numIoExceptions++;
		}
	}
}
