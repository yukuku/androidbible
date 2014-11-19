package yuku.alkitab.base.sync;


import android.accounts.Account;
import android.content.ContentResolver;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.alkitab.model.Marker_Label;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static yuku.alkitab.base.util.Literals.List;

public class Sync {
	static final String TAG = Sync.class.getSimpleName();

	/** From developer console: Client ID for web application */
	public static final String CLIENT_ID = "979768394162-d3ev7tnali57es0tca97snvl24d2jbl2.apps.googleusercontent.com";

	/**
	 * The reason we use an installation id instead of just the simpleToken
	 * to identify originating device, is so that the GCM messages does not
	 * contain simpleToken, which is sensitive.
	 */
	public synchronized static String getInstallationId() {
		String res = Preferences.getString(Prefkey.installation_id, null);
		if (res == null) {
			res = "i1:" + UUID.randomUUID().toString();
			Preferences.setString(Prefkey.installation_id, res);
		}
		return res;
	}

	public enum Opkind {
		add, mod, del, // do not change the enum value names here. This will be un/serialized by gson.
	}

	public static class Operation<C> {
		public Opkind opkind;
		public String kind;
		public String gid;
		public C content;

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
				" " + gid.substring(0, 10) +
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

		/** Kind of this entity. Currently can be {@link #KIND_MARKER}, {@link #KIND_LABEL}, {@link #KIND_MARKER_LABEL}. */
		public String kind;
		public String gid;
		public C content;

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

	/**
	 * Entity content for {@link yuku.alkitab.model.Marker} and {@link yuku.alkitab.model.Label}.
	 */
	public static class MabelContent {
		public Integer ari; // marker
		public Integer kind; // marker
		public String caption; // marker
		public Integer verseCount; // marker
		public Integer createTime; // marker
		public Integer modifyTime; // marker
		public String title; // label
		public Integer ordering; // label
		public String backgroundColor; // label
		public String marker_gid; // marker_label
		public String label_gid; // marker_label

		//region boilerplate equals and hashCode methods
		@Override
		public boolean equals(final Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			final MabelContent that = (MabelContent) o;

			if (ari != null ? !ari.equals(that.ari) : that.ari != null) return false;
			if (backgroundColor != null ? !backgroundColor.equals(that.backgroundColor) : that.backgroundColor != null) return false;
			if (caption != null ? !caption.equals(that.caption) : that.caption != null) return false;
			if (createTime != null ? !createTime.equals(that.createTime) : that.createTime != null) return false;
			if (kind != null ? !kind.equals(that.kind) : that.kind != null) return false;
			if (label_gid != null ? !label_gid.equals(that.label_gid) : that.label_gid != null) return false;
			if (marker_gid != null ? !marker_gid.equals(that.marker_gid) : that.marker_gid != null) return false;
			if (modifyTime != null ? !modifyTime.equals(that.modifyTime) : that.modifyTime != null) return false;
			if (ordering != null ? !ordering.equals(that.ordering) : that.ordering != null) return false;
			if (title != null ? !title.equals(that.title) : that.title != null) return false;
			if (verseCount != null ? !verseCount.equals(that.verseCount) : that.verseCount != null) return false;

			return true;
		}

		@Override
		public int hashCode() {
			int result = ari != null ? ari.hashCode() : 0;
			result = 31 * result + (kind != null ? kind.hashCode() : 0);
			result = 31 * result + (caption != null ? caption.hashCode() : 0);
			result = 31 * result + (verseCount != null ? verseCount.hashCode() : 0);
			result = 31 * result + (createTime != null ? createTime.hashCode() : 0);
			result = 31 * result + (modifyTime != null ? modifyTime.hashCode() : 0);
			result = 31 * result + (title != null ? title.hashCode() : 0);
			result = 31 * result + (ordering != null ? ordering.hashCode() : 0);
			result = 31 * result + (backgroundColor != null ? backgroundColor.hashCode() : 0);
			result = 31 * result + (marker_gid != null ? marker_gid.hashCode() : 0);
			result = 31 * result + (label_gid != null ? label_gid.hashCode() : 0);
			return result;
		}
		//endregion

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("{");
			if (ari != null) sb.append(ari).append(' ');
			if (kind != null) sb.append(kind).append(' ');
			if (caption != null) sb.append(q(caption)).append(' ');
			if (verseCount != null) sb.append(verseCount).append(' ');
			if (createTime != null) sb.append(createTime).append(' ');
			if (modifyTime != null) sb.append(modifyTime).append(' ');
			if (title != null) sb.append(q(title)).append(' ');
			if (ordering != null) sb.append(ordering).append(' ');
			if (backgroundColor != null) sb.append(backgroundColor).append(' ');
			if (marker_gid != null) sb.append(marker_gid.substring(0, 10)).append(' ');
			if (label_gid != null) sb.append(label_gid.substring(0, 10)).append(' ');

			sb.setLength(sb.length() - 1);
			sb.append('}');
			return sb.toString();
		}

		@NonNull static String q(@NonNull String s) {
			final String c;
			if (s.length() > 20) {
				c = s.substring(0, 19).replace("\n", "\\n") + "…";
			} else {
				c = s.replace("\n", "\\n");
			}
			return "'" + c + "'";
		}
	}

	public static class SyncShadowMabelDataJson {
		public List<Entity<MabelContent>> entities;
	}

	public static class MabelClientState {
		public final int base_revno;
		@NonNull public final Delta<MabelContent> delta;

		public MabelClientState(final int base_revno, @NonNull final Delta<MabelContent> delta) {
			this.base_revno = base_revno;
			this.delta = delta;
		}
	}

	/**
	 * @return base revno, delta of shadow -> current.
	 */
	public static Pair<MabelClientState, List<Entity<MabelContent>>> getMabelClientStateAndCurrentEntities() {
		final SyncShadow ss = S.getDb().getSyncShadowBySyncSetName(SyncShadow.SYNC_SET_MABEL);

		final List<Entity<MabelContent>> srcs = ss == null? List(): mabelEntitiesFromShadow(ss);
		final List<Entity<MabelContent>> dsts = getMabelEntitiesFromCurrent();

		final Delta<MabelContent> delta = new Delta<>();

		// additions and modifications
		for (final Entity<MabelContent> dst : dsts) {
			final Entity<MabelContent> existing = findMabelEntity(srcs, dst.gid, dst.kind);

			if (existing == null) {
				delta.operations.add(new Operation<>(Opkind.add, dst.kind, dst.gid, dst.content));
			} else {
				if (!isSameMabelContent(dst, existing)) { // only when it changes
					delta.operations.add(new Operation<>(Opkind.mod, dst.kind, dst.gid, dst.content));
				}
			}
		}

		// deletions
		for (final Entity<MabelContent> src : srcs) {
			final Entity<MabelContent> still_have = findMabelEntity(dsts, src.gid, src.kind);
			if (still_have == null) {
				delta.operations.add(new Operation<>(Opkind.del, src.kind, src.gid, null));
			}
		}

		return Pair.create(new MabelClientState(ss == null ? 0 : ss.revno, delta), dsts);
	}

	private static boolean isSameMabelContent(final Entity<MabelContent> a, final Entity<MabelContent> b) {
		if (!U.equals(a.gid, b.gid)) return false;
		if (!U.equals(a.kind, b.kind)) return false;

		return U.equals(a.content, b.content);
	}

	private static Entity<MabelContent> findMabelEntity(final List<Entity<MabelContent>> list, final String gid, final String kind) {
		for (final Entity<MabelContent> entity : list) {
			if (U.equals(gid, entity.gid) && U.equals(kind, entity.kind)) {
				return entity;
			}
		}
		return null;
	}

	private static List<Entity<MabelContent>> mabelEntitiesFromShadow(@NonNull final SyncShadow ss) {
		final SyncShadowMabelDataJson data = App.getDefaultGson().fromJson(U.utf8BytesToString(ss.data), SyncShadowMabelDataJson.class);
		return data.entities;
	}

	@NonNull public static SyncShadow shadowFromMabelEntities(@NonNull final List<Entity<MabelContent>> entities, final int revno) {
		final SyncShadowMabelDataJson data = new SyncShadowMabelDataJson();
		data.entities = entities;
		final String s = App.getDefaultGson().toJson(data);
		final SyncShadow res = new SyncShadow();
		res.data = U.stringToUtf8Bytes(s);
		res.syncSetName = SyncShadow.SYNC_SET_MABEL;
		res.revno = revno;
		return res;
	}

	@NonNull public static List<Entity<MabelContent>> getMabelEntitiesFromCurrent() {
		final List<Entity<MabelContent>> res = new ArrayList<>();

		{ // markers
			for (final Marker marker : S.getDb().listAllMarkers()) {
				final Entity<MabelContent> entity = new Entity<>();
				entity.kind = Entity.KIND_MARKER;
				entity.gid = marker.gid;
				final MabelContent content = entity.content = new MabelContent();
				content.ari = marker.ari;
				content.caption = marker.caption;
				content.kind = marker.kind.code;
				content.verseCount = marker.verseCount;
				content.createTime = Sqlitil.toInt(marker.createTime);
				content.modifyTime = Sqlitil.toInt(marker.modifyTime);
				res.add(entity);
			}
		}

		{ // labels
			for (final Label label : S.getDb().listAllLabels()) {
				final Entity<MabelContent> entity = new Entity<>();
				entity.kind = Entity.KIND_LABEL;
				entity.gid = label.gid;
				final MabelContent content = entity.content = new MabelContent();
				content.title = label.title;
				content.backgroundColor = label.backgroundColor;
				content.ordering = label.ordering;
				res.add(entity);
			}
		}

		{ // marker_labels
			for (final Marker_Label marker_label : S.getDb().listAllMarker_Labels()) {
				final Entity<MabelContent> entity = new Entity<>();
				entity.kind = Entity.KIND_MARKER_LABEL;
				entity.gid = marker_label.gid;
				final MabelContent content = entity.content = new MabelContent();
				content.marker_gid = marker_label.marker_gid;
				content.label_gid = marker_label.label_gid;
				res.add(entity);
			}
		}

		return res;
	}

	/**
	 * Modify or create a marker from an entity content. This is called when the server append delta
	 * asks for an add or a mod operation.
	 * This will not merge content, will only overwrite.
	 * @param marker an existing marker (content will be modified), or null to create a new marker
	 * @param content entity content, containing the new data.
	 */
	@NonNull public static Marker updateMarkerWithEntityContent(@Nullable final Marker marker, @NonNull final String gid, @NonNull final MabelContent content) {
		final Marker res = marker != null ? marker : Marker.createEmptyMarker();

		res.gid = gid;
		res.ari = content.ari;
		res.kind = Marker.Kind.fromCode(content.kind);
		res.caption = content.caption;
		res.verseCount = content.verseCount;
		res.createTime = Sqlitil.toDate(content.createTime);
		res.modifyTime = Sqlitil.toDate(content.modifyTime);

		return res;
	}

	/**
	 * Modify or create a label from an entity content. This is called when the server append delta
	 * asks for an add or a mod operation.
	 * This will not merge content, will only overwrite.
	 * @param label an existing label (content will be modified), or null to create a new label
	 * @param content entity content, containing the new data.
	 */
	@NonNull public static Label updateLabelWithEntityContent(@Nullable final Label label, @NonNull final String gid, @NonNull final MabelContent content) {
		final Label res = label != null ? label : Label.createEmptyLabel();

		res.gid = gid;
		res.title = content.title;
		res.ordering = content.ordering;
		res.backgroundColor = content.backgroundColor;

		return res;
	}

	/**
	 * Modify or create a marker-label association from an entity content. This is called when the server append delta
	 * asks for an add or a mod operation.
	 * This will not merge content, will only overwrite.
	 * @param marker_label an existing marker-label association (content will be modified), or null to create a new marker-label association
	 * @param content entity content, containing the new data.
	 */
	@NonNull public static Marker_Label updateMarker_LabelWithEntityContent(@Nullable final Marker_Label marker_label, @NonNull final String gid, @NonNull final MabelContent content) {
		final Marker_Label res = marker_label != null ? marker_label : Marker_Label.createEmptyMarker_Label();

		res.gid = gid;
		res.marker_gid = content.marker_gid;
		res.label_gid = content.label_gid;

		return res;
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
	 * @param syncSetName The name of the sync set that needs sync with server. Should be {@link yuku.alkitab.base.model.SyncShadow#SYNC_SET_MABEL} or others.
	 */
	public static synchronized void notifySyncNeeded(final String syncSetName) {
		AtomicInteger counter = syncUpdatesOngoingCounters.get(syncSetName);
		if (counter != null && counter.get() != 0) {
			Log.d(TAG, "@@notifySyncNeeded " + syncSetName + " ignored: ongoing counter != 0");
			return;
		}

		// if not logged in, do nothing
		if (Preferences.getString(Prefkey.sync_simpleToken) == null) {
			return;
		}

		{ // check if preferences prevent syncing
			if (!Preferences.getBoolean(prefkeyForSyncSetEnabled(syncSetName), true)) {
				return;
			}
		}

		SyncRecorder.log(SyncRecorder.EventKind.sync_needed_notified, syncSetName);

		// check if we can omit queueing sync request for this sync set name.
		synchronized (syncSetNameQueue) {
			if (syncSetNameQueue.contains(syncSetName)) {
				Log.d(TAG, "@@notifySyncNeeded " + syncSetName + " ignored: sync queue already contains it");
				return;
			}
			syncSetNameQueue.add(syncSetName);
		}

		syncExecutor.schedule(() -> {
			while (true) {
				final String extraSyncSetName;
				synchronized (syncSetNameQueue) {
					extraSyncSetName = syncSetNameQueue.poll();
					if (extraSyncSetName == null) {
						return;
					}
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
				extras.putString(SyncAdapter.EXTRA_SYNC_SET_NAME, extraSyncSetName);
				ContentResolver.requestSync(account, authority, extras);
			}
		}, 5, TimeUnit.SECONDS);
	}

	/**
	 * Call this method(true) when updating local storage because of sync. Call this method(false) when finished.
	 * Calls to {@link #notifySyncNeeded(String)} will be a no-op when sync updates are ongoing (marked by this method being called).
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
	 * @return scheme, host, port, without the trailing slash.
	 */
	public static String getEffectiveServerPrefix() {
		final String override = Preferences.getString(Prefkey.sync_server_prefix);
		if (override != null) {
			return override;
		}

		if (BuildConfig.DEBUG) {
			return "http://10.0.3.2:9080";
		} else {
			return "http://sync.bibleforandroid.com";
		}
	}

	static class RegisterGcmClientResponseJson extends ResponseJson {
		public boolean is_new_registration_id;
	}

	public static void notifyNewGcmRegistrationId(final String newRegistrationId) {
		// must send to server if we are logged in
		final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
		if (simpleToken == null) {
			Log.d(TAG, "Got new GCM registration id, but sync is not logged in");
			return;
		}

		new Thread(() -> sendGcmRegistrationId(simpleToken, newRegistrationId)).start();
	}

	public static boolean sendGcmRegistrationId(final String simpleToken, final String registration_id) {
		final RequestBody requestBody = new FormEncodingBuilder()
			.add("simpleToken", simpleToken)
			.add("registration_id", registration_id)
			.build();

		try {
			final Call call = App.getOkHttpClient().newCall(
				new Request.Builder()
					.url(getEffectiveServerPrefix() + "/sync/api/register_gcm_client")
					.post(requestBody)
					.build()
			);

			SyncRecorder.log(SyncRecorder.EventKind.gcm_send_attempt, null);
			final RegisterGcmClientResponseJson response = App.getDefaultGson().fromJson(call.execute().body().charStream(), RegisterGcmClientResponseJson.class);

			if (!response.success) {
				SyncRecorder.log(SyncRecorder.EventKind.gcm_send_not_success, null, "message", response.message);
				Log.d(TAG, "GCM registration id rejected by server: " + response.message);
				return false;
			}

			SyncRecorder.log(SyncRecorder.EventKind.gcm_send_success, null, "is_new_registration_id", response.is_new_registration_id);
			Log.d(TAG, "GCM registration id accepted by server: is_new_registration_id=" + response.is_new_registration_id);

			return true;

		} catch (IOException | JsonIOException e) {
			SyncRecorder.log(SyncRecorder.EventKind.gcm_send_error_io, null);
			Log.d(TAG, "Failed to send GCM registration id to server", e);
			return false;

		} catch (JsonSyntaxException e) {
			SyncRecorder.log(SyncRecorder.EventKind.gcm_send_error_json, null);
			Log.d(TAG, "Server response is not valid JSON", e);
			return false;
		}
	}

	static class InstallationInfoJson {
		public String installation_id;
		public String app_packageName;
		public int app_versionCode;
		public boolean app_debug;
		public String build_model;
		public String build_device;
		public String build_product;
		public int os_sdk_int;
		public String os_release;
	}

	/**
	 * Return a JSON string that contains information about the app installation on this particular device.
	 */
	public static String getInstallationInfoJson() {
		final InstallationInfoJson obj = new InstallationInfoJson();
		obj.installation_id = getInstallationId();
		obj.app_packageName = App.context.getPackageName();
		obj.app_versionCode = App.getVersionCode();
		obj.app_debug = BuildConfig.DEBUG;
		obj.build_model = Build.MODEL;
		obj.build_device = Build.DEVICE;
		obj.build_product = Build.PRODUCT;
		obj.os_sdk_int = Build.VERSION.SDK_INT;
		obj.os_release = Build.VERSION.RELEASE;
		return App.getDefaultGson().toJson(obj);
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
		public NotOkException(final String msg) {
			super(msg);
		}
	}

	/**
	 * Create an own user account.
	 * Must be called from a background thread.
	 */
	@NonNull public static LoginResponseJson register(@NonNull final RegisterForm form) throws NotOkException {
		final FormEncodingBuilder b = new FormEncodingBuilder();
		if (form.church != null) b.add("church", form.church);
		if (form.city != null) b.add("city", form.city);
		if (form.religion != null) b.add("religion", form.religion);

		final RequestBody requestBody = b
			.add("email", form.email)
			.add("password", form.password)
			.add("installation_info", getInstallationInfoJson())
			.build();

		try {
			final Call call = App.getOkHttpClient().newCall(
				new Request.Builder()
					.url(getEffectiveServerPrefix() + "/sync/api/create_own_user")
					.post(requestBody)
					.build()
			);

			final LoginResponseJson response = App.getDefaultGson().fromJson(call.execute().body().charStream(), LoginResponseJson.class);

			if (!response.success) {
				throw new NotOkException(response.message);
			}

			return response;

		} catch (IOException | JsonIOException e) {
			throw new NotOkException("Failed to send data");
		} catch (JsonSyntaxException e) {
			throw new NotOkException("Server response is not a valid JSON");
		}
	}

	/**
	 * Log in to own user account using email and password.
	 * Must be called from a background thread.
	 */
	@NonNull public static LoginResponseJson login(@NonNull final String email, @NonNull final String password) throws NotOkException {
		final RequestBody requestBody = new FormEncodingBuilder()
			.add("email", email)
			.add("password", password)
			.add("installation_info", getInstallationInfoJson())
			.build();

		try {
			final Call call = App.getOkHttpClient().newCall(
				new Request.Builder()
					.url(getEffectiveServerPrefix() + "/sync/api/login_own_user")
					.post(requestBody)
					.build()
			);

			final LoginResponseJson response = App.getDefaultGson().fromJson(call.execute().body().charStream(), LoginResponseJson.class);

			if (!response.success) {
				throw new NotOkException(response.message);
			}

			return response;

		} catch (IOException | JsonIOException e) {
			throw new NotOkException("Failed to send data");
		} catch (JsonSyntaxException e) {
			throw new NotOkException("Server response is not a valid JSON");
		}
	}

	/**
	 * Ask the server to allow user to reset password.
	 * Must be called from a background thread.
	 */
	public static void forgotPassword(@NonNull final String email) throws NotOkException {
		final RequestBody requestBody = new FormEncodingBuilder()
			.add("email", email)
			.build();

		try {
			final Call call = App.getOkHttpClient().newCall(
				new Request.Builder()
					.url(getEffectiveServerPrefix() + "/sync/api/forgot_password")
					.post(requestBody)
					.build()
			);

			final ResponseJson response = App.getDefaultGson().fromJson(call.execute().body().charStream(), ResponseJson.class);

			if (!response.success) {
				throw new NotOkException(response.message);
			}

		} catch (IOException | JsonIOException e) {
			throw new NotOkException("Failed to send data");
		} catch (JsonSyntaxException e) {
			throw new NotOkException("Server response is not a valid JSON");
		}
	}

	/**
	 * Ask the server to change password.
	 * Must be called from a background thread.
	 */
	public static void changePassword(@NonNull final String email, @NonNull final String password_old, @NonNull final String password_new) throws NotOkException {
		final RequestBody requestBody = new FormEncodingBuilder()
			.add("email", email)
			.add("password_old", password_old)
			.add("password_new", password_new)
			.build();

		try {
			final Call call = App.getOkHttpClient().newCall(
				new Request.Builder()
					.url(getEffectiveServerPrefix() + "/sync/api/change_password")
					.post(requestBody)
					.build()
			);

			final ResponseJson response = App.getDefaultGson().fromJson(call.execute().body().charStream(), ResponseJson.class);

			if (!response.success) {
				throw new NotOkException(response.message);
			}

		} catch (IOException | JsonIOException e) {
			throw new NotOkException("Failed to send data");
		} catch (JsonSyntaxException e) {
			throw new NotOkException("Server response is not a valid JSON");
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
		for (final String syncSetName : SyncShadow.ALL_SYNC_SETS) {
			final Bundle extras = new Bundle();
			extras.putString(SyncAdapter.EXTRA_SYNC_SET_NAME, syncSetName);
			extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
			extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
			ContentResolver.requestSync(account, authority, extras);
		}
	}
}
