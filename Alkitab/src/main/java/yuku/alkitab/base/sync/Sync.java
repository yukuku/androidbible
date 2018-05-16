package yuku.alkitab.base.sync;


import android.accounts.Account;
import android.content.ContentResolver;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.util.ArrayMap;
import android.util.Log;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Background;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Sync {
	static final String TAG = Sync.class.getSimpleName();

	public enum Opkind {
		add, mod, del, // do not change the enum value names here. This will be un/serialized by gson.
	}

	public enum ApplyAppendDeltaResult {
		ok,
		unknown_kind,
		/** Entities have changed during sync request */
		dirty_entities,
		/** Sync user account has changed during sync request */
		dirty_sync_account,
		/** Unsupported operation encountered */
		unsupported_operation,
	}

	public static class Operation<C> {
		public Opkind opkind;
		public String kind;
		public String gid;
		public C content;
		public String creator_id; // for now, only used when receiving from server, not sending

		public Operation(final Opkind opkind, final String kind, final String gid, final C content) {
			this.opkind = opkind;
			this.kind = kind;
			this.gid = gid;
			this.content = content;
		}

		@Override
		public String toString() {
			return "{" + opkind +
				" " + kind +
				" " + (gid.length() <= 10? gid: gid.substring(0, 10)) +
				" " + content +
				'}';
		}
	}

	public static class Delta<C> {
		@NonNull public List<Operation<C>> operations;

		public Delta() {
			operations = new ArrayList<>();
		}

		@Override
		public String toString() {
			return "Delta{" +
				"operations=" + operations +
				'}';
		}
	}

	public static class Entity<C> {
		public static final String KIND_MARKER = "Marker";
		public static final String KIND_LABEL = "Label";
		public static final String KIND_MARKER_LABEL = "Marker_Label";
		public static final String KIND_HISTORY_ENTRY = "HistoryEntry";
		public static final String KIND_PINS = "Pins"; // with plural to indicate not only 1 pin but all pins considered as one entity
		public static final String KIND_RP_PROGRESS = "RpProgress";

		/**
		 * Kind of this entity. One of the <code>KIND_</code> constants on {@link yuku.alkitab.base.sync.Sync.Entity}.
		 */
		public final String kind;
		public final String gid;
		public final C content;

		public Entity(final String kind, final String gid, final C content) {
			this.kind = kind;
			this.gid = gid;
			this.content = content;
		}

		//region Boilerplate equals and hashCode
		@Override
		public boolean equals(final Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			final Entity entity = (Entity) o;

			if (content != null ? !content.equals(entity.content) : entity.content != null) return false;
			if (gid != null ? !gid.equals(entity.gid) : entity.gid != null) return false;
			if (kind != null ? !kind.equals(entity.kind) : entity.kind != null) return false;

			return true;
		}

		@Override
		public int hashCode() {
			int result = kind != null ? kind.hashCode() : 0;
			result = 31 * result + (gid != null ? gid.hashCode() : 0);
			result = 31 * result + (content != null ? content.hashCode() : 0);
			return result;
		}
		//endregion
	}

	public static class ClientState<C> {
		public final int base_revno;
		@NonNull public final Delta<C> delta;

		public ClientState(final int base_revno, @NonNull final Delta<C> delta) {
			this.base_revno = base_revno;
			this.delta = delta;
		}
	}

	public static class SyncShadowDataJson<C> {
		public List<Entity<C>> entities;
	}

	public static class SyncResponseJson<C> extends ResponseJson {
		public int final_revno;
		public Delta<C> append_delta;
	}

	public static class GetClientStateResult<C> {
		public final ClientState<C> clientState;
		public final List<Entity<C>> shadowEntities;
		public final List<Entity<C>> currentEntities;

		public GetClientStateResult(final ClientState<C> clientState, final List<Entity<C>> shadowEntities, final List<Entity<C>> currentEntities) {
			this.clientState = clientState;
			this.shadowEntities = shadowEntities;
			this.currentEntities = currentEntities;
		}
	}

	/**
	 * Ignoring order, check if all the entities are the same.
	 */
	public static <C> boolean entitiesEqual(@NonNull final List<Entity<C>> a_, @NonNull final List<Entity<C>> b_) {
		if (a_.size() != b_.size()) return false;

		final ArrayList<Entity<C>> a = new ArrayList<>(a_);
		final ArrayList<Entity<C>> b = new ArrayList<>(b_);

		final Comparator<Entity<C>> cmp = (lhs, rhs) -> lhs.gid.compareTo(rhs.gid);
		Collections.sort(a, cmp);
		Collections.sort(b, cmp);

		return a.equals(b);
	}

	private static final ArrayMap<String, AtomicInteger> syncUpdatesOngoingCounters = new ArrayMap<>();
	private static final ScheduledExecutorService syncExecutor = Executors.newSingleThreadScheduledExecutor();
	private static final ConcurrentLinkedQueue<String> syncSetNameQueue = new ConcurrentLinkedQueue<>();

	/**
	 * Notify that we need to sync with server.
	 * @param syncSetNames The names of the sync set that needs sync with server. Should be list of {@link yuku.alkitab.base.model.SyncShadow#SYNC_SET_MABEL} or others.
	 */
	public static synchronized void notifySyncNeeded(final String... syncSetNames) {
		// if not logged in, do nothing
		if (Preferences.getString(Prefkey.sync_simpleToken) == null) {
			return;
		}

		for (final String syncSetName : syncSetNames) {
			final AtomicInteger counter = syncUpdatesOngoingCounters.get(syncSetName);
			if (counter != null && counter.get() != 0) {
				AppLog.d(TAG, "@@notifySyncNeeded " + syncSetName + " ignored: ongoing counter != 0");
				return;
			}

			{ // check if preferences prevent syncing
				if (!Preferences.getBoolean(prefkeyForSyncSetEnabled(syncSetName), true)) {
					continue;
				}
			}

			SyncRecorder.log(SyncRecorder.EventKind.sync_needed_notified, syncSetName);

			// check if we can omit queueing sync request for this sync set name.
			synchronized (syncSetNameQueue) {
				if (syncSetNameQueue.contains(syncSetName)) {
					AppLog.d(TAG, "@@notifySyncNeeded " + syncSetName + " ignored: sync queue already contains it");
					continue;
				}
				syncSetNameQueue.add(syncSetName);
			}
		}

		syncExecutor.schedule(() -> {
			while (true) {
				final List<String> extraSyncSetNames = new ArrayList<>();
				synchronized (syncSetNameQueue) {
					final String extraSyncSetName = syncSetNameQueue.poll();
					if (extraSyncSetName == null) {
						break;
					}
					extraSyncSetNames.add(extraSyncSetName);
				}

				if (extraSyncSetNames.size() == 0) {
					return;
				}

				final Account account = SyncUtils.getOrCreateSyncAccount();
				final String authority = App.context.getString(R.string.sync_provider_authority);

				// make sure sync is enabled.
				final boolean syncAutomatically = ContentResolver.getSyncAutomatically(account, authority);
				if (!syncAutomatically) {
					ContentResolver.setSyncAutomatically(account, authority, true);
				}

				// request sync.
				final Bundle extras = new Bundle();
				extras.putString(SyncAdapter.EXTRA_SYNC_SET_NAMES, App.getDefaultGson().toJson(extraSyncSetNames));
				ContentResolver.requestSync(account, authority, extras);
			}
		}, 5, TimeUnit.SECONDS);
	}

	/**
	 * Call this method(true) when updating local storage because of sync. Call this method(false) when finished.
	 * Calls to {@link #notifySyncNeeded(String...)} will be a no-op when sync updates are ongoing (marked by this method being called).
	 * @param isRunning true to start, false to stop.
	 */
	public static synchronized void notifySyncUpdatesOngoing(final String syncSetName, final boolean isRunning) {
		AtomicInteger counter = syncUpdatesOngoingCounters.get(syncSetName);
		if (counter == null) {
			counter = new AtomicInteger(0);
			syncUpdatesOngoingCounters.put(syncSetName, counter);
		}

		if (isRunning) {
			counter.incrementAndGet();
		} else {
			counter.decrementAndGet();
		}
	}

	/**
	 * Returns the effective server prefix for syncing.
	 * @return scheme, host, port, with the trailing slash.
	 */
	public static String getEffectiveServerPrefix() {
		final String override = Preferences.getString(Prefkey.sync_server_prefix);
		if (override != null) {
			return override;
		}

		if (BuildConfig.DEBUG) {
			return "http://10.0.3.2:9080/";
		} else {
			return BuildConfig.SERVER_HOST;
		}
	}

	static class RegisterGcmClientResponseJson extends ResponseJson {
		public boolean is_new_registration_id;
	}

	public static void notifyNewGcmRegistrationId(final String newRegistrationId) {
		// must send to server if we are logged in
		final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
		if (simpleToken == null) {
			AppLog.d(TAG, "Got new GCM registration id, but sync is not logged in");
			return;
		}

		Background.run(() -> sendGcmRegistrationId(simpleToken, newRegistrationId));
	}

	public static boolean sendGcmRegistrationId(final String simpleToken, final String registration_id) {
		final RequestBody requestBody = new FormBody.Builder()
			.add("simpleToken", simpleToken)
			.add("sender_id", Gcm.SENDER_ID)  // not really needed, but for logging on server
			.add("registration_id", registration_id)
			.build();

		try {
			final Call call = App.getLongTimeoutOkHttpClient().newCall(
				new Request.Builder()
					.url(getEffectiveServerPrefix() + "sync/api/register_gcm_client")
					.post(requestBody)
					.build()
			);

			SyncRecorder.log(SyncRecorder.EventKind.gcm_send_attempt, null);
			final RegisterGcmClientResponseJson response = App.getDefaultGson().fromJson(call.execute().body().charStream(), RegisterGcmClientResponseJson.class);

			if (!response.success) {
				SyncRecorder.log(SyncRecorder.EventKind.gcm_send_not_success, null, "message", response.message);
				AppLog.d(TAG, "GCM registration id rejected by server: " + response.message);
				return false;
			}

			SyncRecorder.log(SyncRecorder.EventKind.gcm_send_success, null, "is_new_registration_id", response.is_new_registration_id);
			AppLog.d(TAG, "GCM registration id accepted by server: is_new_registration_id=" + response.is_new_registration_id);

			return true;

		} catch (IOException | JsonIOException e) {
			SyncRecorder.log(SyncRecorder.EventKind.gcm_send_error_io, null);
			AppLog.d(TAG, "Failed to send GCM registration id to server", e);
			return false;

		} catch (JsonSyntaxException e) {
			SyncRecorder.log(SyncRecorder.EventKind.gcm_send_error_json, null);
			AppLog.d(TAG, "Server response is not valid JSON", e);
			return false;
		}
	}

	public static class ResponseJson {
		public boolean success;
		public String message;
	}

	public static class RegisterForm {
		public String email;
		public String password;
		public String church;
		public String city;
		public String religion;
	}

	/**
	 * Exception thrown by calls to server that has io/parse exception or when server returns success==false.
	 */
	static class NotOkException extends Exception {
		public NotOkException(final String message) {
			super(message);
		}

		public NotOkException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * Create an own user account.
	 * Must be called from a background thread.
	 */
	@NonNull public static LoginResponseJson register(@NonNull final RegisterForm form) throws NotOkException {
		final FormBody.Builder b = new FormBody.Builder();
		if (form.church != null) b.add("church", form.church);
		if (form.city != null) b.add("city", form.city);
		if (form.religion != null) b.add("religion", form.religion);

		final RequestBody requestBody = b
			.add("email", form.email)
			.add("password", form.password)
			.add("installation_info", U.getInstallationInfoJson())
			.build();

		try {
			final Call call = App.getLongTimeoutOkHttpClient().newCall(
				new Request.Builder()
					.url(getEffectiveServerPrefix() + "sync/api/create_own_user")
					.post(requestBody)
					.build()
			);

			final LoginResponseJson response = App.getDefaultGson().fromJson(call.execute().body().charStream(), LoginResponseJson.class);

			if (!response.success) {
				throw new NotOkException(response.message);
			}

			return response;

		} catch (IOException | JsonIOException e) {
			throw new NotOkException("Failed to send data", e);

		} catch (JsonSyntaxException e) {
			throw new NotOkException("Server response is not a valid JSON", e);
		}
	}

	/**
	 * Log in to own user account using email and password.
	 * Must be called from a background thread.
	 */
	@NonNull public static LoginResponseJson login(@NonNull final String email, @NonNull final String password) throws NotOkException {
		final RequestBody requestBody = new FormBody.Builder()
			.add("email", email)
			.add("password", password)
			.add("installation_info", U.getInstallationInfoJson())
			.build();

		try {
			final Call call = App.getLongTimeoutOkHttpClient().newCall(
				new Request.Builder()
					.url(getEffectiveServerPrefix() + "sync/api/login_own_user")
					.post(requestBody)
					.build()
			);

			final LoginResponseJson response = App.getDefaultGson().fromJson(call.execute().body().charStream(), LoginResponseJson.class);

			if (!response.success) {
				throw new NotOkException(response.message);
			}

			return response;

		} catch (IOException | JsonIOException e) {
			throw new NotOkException("Failed to send data", e);

		} catch (JsonSyntaxException e) {
			throw new NotOkException("Server response is not a valid JSON", e);
		}
	}

	/**
	 * Ask the server to allow user to reset password.
	 * Must be called from a background thread.
	 */
	public static void forgotPassword(@NonNull final String email) throws NotOkException {
		final RequestBody requestBody = new FormBody.Builder()
			.add("email", email)
			.build();

		try {
			final Call call = App.getLongTimeoutOkHttpClient().newCall(
				new Request.Builder()
					.url(getEffectiveServerPrefix() + "sync/api/forgot_password")
					.post(requestBody)
					.build()
			);

			final ResponseJson response = App.getDefaultGson().fromJson(call.execute().body().charStream(), ResponseJson.class);

			if (!response.success) {
				throw new NotOkException(response.message);
			}

		} catch (IOException | JsonIOException e) {
			throw new NotOkException("Failed to send data", e);

		} catch (JsonSyntaxException e) {
			throw new NotOkException("Server response is not a valid JSON", e);
		}
	}

	/**
	 * Ask the server to change password.
	 * Must be called from a background thread.
	 */
	public static void changePassword(@NonNull final String email, @NonNull final String password_old, @NonNull final String password_new) throws NotOkException {
		final RequestBody requestBody = new FormBody.Builder()
			.add("email", email)
			.add("password_old", password_old)
			.add("password_new", password_new)
			.build();

		try {
			final Call call = App.getLongTimeoutOkHttpClient().newCall(
				new Request.Builder()
					.url(getEffectiveServerPrefix() + "sync/api/change_password")
					.post(requestBody)
					.build()
			);

			final ResponseJson response = App.getDefaultGson().fromJson(call.execute().body().charStream(), ResponseJson.class);

			if (!response.success) {
				throw new NotOkException(response.message);
			}

		} catch (IOException | JsonIOException e) {
			throw new NotOkException("Failed to send data", e);

		} catch (JsonSyntaxException e) {
			throw new NotOkException("Server response is not a valid JSON", e);
		}
	}

	public static class LoginResponseJson extends ResponseJson {
		public String simpleToken;
	}

	public static String prefkeyForSyncSetEnabled(final String syncSetName) {
		return "syncSet_" + syncSetName + "_enabled";
	}

	public static void forceSyncNow() {
		final Account account = SyncUtils.getOrCreateSyncAccount();
		final String authority = App.context.getString(R.string.sync_provider_authority);

		SyncRecorder.log(SyncRecorder.EventKind.sync_forced, null);

		// make sure sync is enabled.
		final boolean syncAutomatically = ContentResolver.getSyncAutomatically(account, authority);
		if (!syncAutomatically) {
			ContentResolver.setSyncAutomatically(account, authority, true);
		}

		// request sync.
		final List<String> syncSetNames = new ArrayList<>();
		Collections.addAll(syncSetNames, SyncShadow.ALL_SYNC_SET_NAMES);

		final Bundle extras = new Bundle();
		extras.putString(SyncAdapter.EXTRA_SYNC_SET_NAMES, App.getDefaultGson().toJson(syncSetNames));
		extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
		extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
		ContentResolver.requestSync(account, authority, extras);
	}
}
