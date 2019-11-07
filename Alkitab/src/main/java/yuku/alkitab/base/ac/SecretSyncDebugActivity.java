package yuku.alkitab.base.ac;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.base.sync.Sync_History;
import yuku.alkitab.base.sync.Sync_Mabel;
import yuku.alkitab.base.sync.Sync_Pins;
import yuku.alkitab.base.sync.Sync_Rp;
import yuku.alkitab.base.util.Background;
import yuku.alkitab.base.util.Highlights;
import yuku.alkitab.base.util.InstallationUtil;
import yuku.alkitab.base.util.LabelColorUtil;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.alkitab.model.Marker_Label;

public class SecretSyncDebugActivity extends BaseActivity {
	public static final String TAG = SecretSyncDebugActivity.class.getSimpleName();

	EditText tServer;
	EditText tUserEmail;
	CheckBox cMakeDirtyMarker;
	CheckBox cMakeDirtyLabel;
	CheckBox cMakeDirtyMarker_Label;
	Spinner cbSyncSetName;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_secret_sync_debug);

		tServer = findViewById(R.id.tServer);
		tUserEmail = findViewById(R.id.tUserEmail);
		cMakeDirtyMarker = findViewById(R.id.cMakeDirtyMarker);
		cMakeDirtyLabel = findViewById(R.id.cMakeDirtyLabel);
		cMakeDirtyMarker_Label = findViewById(R.id.cMakeDirtyMarker_Label);

		findViewById(R.id.bServerSave).setOnClickListener(v -> new MaterialDialog.Builder(this)
			.content("This will reset your synced shadow to revision 0.")
			.positiveText(R.string.ok)
			.onPositive((d, w) -> {
				Preferences.setString(Prefkey.sync_server_prefix, tServer.getText().toString().trim());

				// do the same as logging out
				bLogout_click.onClick(null);
			})
			.negativeText(R.string.cancel)
			.show());

		findViewById(R.id.bServerReset).setOnClickListener(v -> new MaterialDialog.Builder(this)
			.content("This will reset your synced shadow to revision 0.")
			.positiveText(R.string.ok)
			.onPositive((d, w) -> {
				Preferences.remove(Prefkey.sync_server_prefix);

				// do the same as logging out
				bLogout_click.onClick(null);

				tServer.setText("");
			})
			.negativeText(R.string.cancel)
			.show());

		findViewById(R.id.bMabelClientState).setOnClickListener(bMabelClientState_click);
		findViewById(R.id.bGenerateDummies).setOnClickListener(bGenerateDummies_click);
		findViewById(R.id.bGenerateDummies2).setOnClickListener(bGenerateDummies2_click);
		findViewById(R.id.bMabelMonkey).setOnClickListener(bMabelMonkey_click);
		findViewById(R.id.bLogout).setOnClickListener(bLogout_click);
		findViewById(R.id.bSync).setOnClickListener(bSync_click);

		cbSyncSetName = findViewById(R.id.cbSyncSetName);
		cbSyncSetName.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, SyncShadow.ALL_SYNC_SET_NAMES));

		findViewById(R.id.bCheckHash).setOnClickListener(bCheckHash_click);
	}

	View.OnClickListener bMabelClientState_click = v -> {
		final StringBuilder sb = new StringBuilder();
		final Sync.GetClientStateResult<Sync_Mabel.Content> pair = Sync_Mabel.getClientStateAndCurrentEntities();
		final Sync.ClientState<Sync_Mabel.Content> clientState = pair.clientState;

		sb.append("Base revno: ").append(clientState.base_revno).append('\n');
		sb.append("Delta operations (size ").append(clientState.delta.operations.size()).append("):\n");

		for (final Sync.Operation<Sync_Mabel.Content> operation : clientState.delta.operations) {
			sb.append("\u2022 ").append(operation).append('\n');
		}

		new MaterialDialog.Builder(this)
			.content(sb)
			.positiveText(R.string.ok)
			.show();
	};

	int rand(int n) {
		return (int) (Math.random() * n);
	}

	View.OnClickListener bGenerateDummies_click = v -> {
		final Label label1 = S.getDb().insertLabel(randomString("L1_", 1, 3, 8), LabelColorUtil.encodeBackground(rand(0xffffff)));
		final Label label2 = S.getDb().insertLabel(randomString("L2_", 1, 3, 8), LabelColorUtil.encodeBackground(rand(0xffffff)));

		for (int i = 0; i < 10; i++) {
			final Marker marker = S.getDb().insertMarker(0x000101 + rand(30), Marker.Kind.values()[rand(3)], randomString("M" + i + "_", rand(2) + 1, 4, 7), rand(2) + 1, new Date(), new Date());
			final Set<Label> labelSet = new HashSet<>();
			if (rand(10) < 5) {
				labelSet.add(label1);
			}
			if (rand(10) < 3) {
				labelSet.add(label2);
			}
			S.getDb().updateLabels(marker, labelSet);
		}

		new MaterialDialog.Builder(this)
			.content("10 markers, 2 labels generated.")
			.positiveText(R.string.ok)
			.show();
	};

	View.OnClickListener bGenerateDummies2_click = v -> {
		final Label label1 = S.getDb().insertLabel(randomString("LL1_", 1, 3, 8), LabelColorUtil.encodeBackground(rand(0xffffff)));
		final Label label2 = S.getDb().insertLabel(randomString("LL2_", 1, 3, 8), LabelColorUtil.encodeBackground(rand(0xffffff)));

		for (int i = 0; i < 1000; i++) {
			final Marker.Kind kind = Marker.Kind.values()[rand(3)];
			final Date now = new Date();
			final Marker marker = S.getDb().insertMarker(0x000101 + rand(30), kind, kind == Marker.Kind.highlight? Highlights.encode(rand(0xffffff)): randomString("MM" + i + "_", rand(10) < 5? rand(81): rand(400) + 4, 5, 15), rand(2) + 1, now, now);
			final Set<Label> labelSet = new HashSet<>();
			if (rand(10) < 1) {
				labelSet.add(label1);
			}
			if (rand(10) < 4) {
				labelSet.add(label2);
			}
			S.getDb().updateLabels(marker, labelSet);
		}

		new MaterialDialog.Builder(this)
			.content("1000 markers, 2 labels generated.")
			.positiveText(R.string.ok)
			.show();
	};

	static MonkeyThread monkey;
	Handler toastHandler = new Handler();

	class MonkeyThread extends Thread {
		final AtomicBoolean stopRequested = new AtomicBoolean();

		final Toast toast;

		@SuppressLint("ShowToast")
		MonkeyThread() {
			toast = Toast.makeText(SecretSyncDebugActivity.this, "none", Toast.LENGTH_SHORT);
		}

		void toast(String msg) {
			toastHandler.post(() -> {
				toast.setText(msg);
				toast.show();
			});
		}

		@Override
		public void run() {
			while (!stopRequested.get()) {
				toast("preparing");
				SystemClock.sleep(5000);

				{
					int nlabel = rand(5);
					toast("creating " + nlabel + " labels");
					for (int i = 0; i < nlabel; i++) {
						S.getDb().insertLabel(randomString("monkey L " + i + " ", 1, 3, 8), LabelColorUtil.encodeBackground(rand(0xffffff)));
					}
				}

				if (stopRequested.get()) return;
				toast("waiting for 10 secs");
				SystemClock.sleep(10000);

				final List<Label> labels = S.getDb().listAllLabels();

				{
					int nmarker = rand(500);
					toast("creating " + nmarker + " markers");
					for (int i = 0; i < nmarker; i++) {
						final Marker.Kind kind = Marker.Kind.values()[rand(3)];
						final Date now = new Date();
						final Marker marker = S.getDb().insertMarker(0x000101 + rand(30), kind, kind == Marker.Kind.highlight ? Highlights.encode(rand(0xffffff)) : randomString("monkey M " + i + " ", rand(8) + 2, 3, 5), rand(2) + 1, now, now);
						if (rand(10) < 1 && labels.size() > 0) {
							final Set<Label> labelSet = new HashSet<>();
							labelSet.add(labels.get(rand(labels.size())));
							S.getDb().updateLabels(marker, labelSet);
						}
					}
				}

				if (stopRequested.get()) return;
				toast("waiting for 10 secs");
				SystemClock.sleep(10000);

				final List<Marker> markers = S.getDb().listAllMarkers();
				if (markers.size() > 10) {
					int nmarker = rand(markers.size() / 10);
					toast("deleting up to 10% of markers: " + nmarker + " markers");
					for (int i = 0; i < nmarker; i++) {
						final Marker marker = markers.get(rand(markers.size()));
						markers.remove(marker);
						final List<Marker_Label> mls = S.getDb().listMarker_LabelsByMarker(marker);
						for (final Marker_Label ml : mls) {
							S.getDb().deleteMarker_LabelByGid(ml.gid);
						}
						S.getDb().deleteMarkerByGid(marker.gid);
					}
				}

				if (stopRequested.get()) return;
				toast("waiting for 10 secs");
				SystemClock.sleep(10000);

				if (labels.size() > 10) {
					int nlabel = rand(labels.size() / 10);
					toast("deleting up to 10% of label: " + nlabel + " labels");
					for (int i = 0; i < nlabel; i++) {
						final Label label = labels.get(rand(labels.size()));
						labels.remove(label);
						S.getDb().deleteLabelAndMarker_LabelsByLabelId(label._id);
					}
				}

				if (stopRequested.get()) return;
				toast("waiting for 10 secs");
				SystemClock.sleep(10000);

				{
					int nmarker = rand(markers.size() / 5);
					toast("editing up to 20% of markers: " + nmarker + " markers");
					for (int i = 0; i < nmarker; i++) {
						final Marker marker = markers.get(rand(markers.size()));
						marker.caption = randomString("monkey edit M " + i + " ", rand(8) + 2, 3, 5);
						S.getDb().insertOrUpdateMarker(marker);
					}
				}

				if (stopRequested.get()) return;
				toast("waiting for 40 secs");
				SystemClock.sleep(40000);
			}
		}

		void requestStop() {
			stopRequested.set(true);
		}
	}

	final View.OnClickListener bMabelMonkey_click = v -> {
		if (!BuildConfig.DEBUG) return;

		if (monkey != null) {
			monkey.requestStop();
			monkey = null;
			new MaterialDialog.Builder(this)
				.content("monkey stopped")
				.show();
			return;
		}

		new MaterialDialog.Builder(this)
			.content("This will MESS UP YOUR MARKERS. JANGAN TEKAN TOMBOL INI karena segala datamu akan rusak.")
			.positiveText("DO NOT PRESS")
			.negativeText("OK")
			.onPositive((dialog, which) -> {
				monkey = new MonkeyThread();
				monkey.start();
			})
			.show();
	};

	private String randomString(final String prefix, final int word_count, final int minwordlen, final int maxwordlen) {
		final StringBuilder sb = new StringBuilder(prefix);
		for (int i = 0; i < word_count; i++) {
			for (int j = 0, wordlen = rand(maxwordlen - minwordlen) + minwordlen; j < wordlen; j++) {
				if (j % 2 == 0) {
					sb.append("bcdfghjklmnpqrstvwyz".charAt(rand(20)));
				} else {
					sb.append("aeiou".charAt(rand(5)));
				}
			}
			if (i != word_count - 1) {
				sb.append(' ');
			}
		}
		return sb.toString();
	}

	View.OnClickListener bLogout_click = v -> {
		Preferences.hold();
		Preferences.remove(getString(R.string.pref_syncAccountName_key));
		Preferences.remove(Prefkey.sync_simpleToken);
		Preferences.remove(Prefkey.sync_token_obtained_time);
		Preferences.unhold();

		for (final String syncSetName : SyncShadow.ALL_SYNC_SET_NAMES) {
			S.getDb().deleteSyncShadowBySyncSetName(syncSetName);
		}
	};

	View.OnClickListener bSync_click = v -> {
		final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
		if (simpleToken == null) {
			new MaterialDialog.Builder(this)
				.content("not logged in")
				.positiveText(R.string.ok)
				.show();
			return;
		}

		final Sync.GetClientStateResult<Sync_Mabel.Content> pair = Sync_Mabel.getClientStateAndCurrentEntities();
		final Sync.ClientState<Sync_Mabel.Content> clientState = pair.clientState;
		final List<Sync.Entity<Sync_Mabel.Content>> entitiesBeforeSync = pair.currentEntities;

		final RequestBody requestBody = new FormBody.Builder()
			.add("simpleToken", simpleToken)
			.add("syncSetName", SyncShadow.SYNC_SET_MABEL)
			.add("installation_id", InstallationUtil.getInstallationId())
			.add("clientState", App.getDefaultGson().toJson(clientState))
			.build();

		final Call call = App.getLongTimeoutOkHttpClient().newCall(
			new Request.Builder()
				.url(Sync.getEffectiveServerPrefix() + "sync/api/sync")
				.post(requestBody)
				.build()
		);

		if (cMakeDirtyMarker.isChecked()) {
			S.getDb().insertMarker(0x000101 + rand(30), Marker.Kind.values()[rand(3)], randomString("MMD0_", rand(2) + 1, 4, 7), rand(2) + 1, new Date(), new Date());
		}

		if (cMakeDirtyLabel.isChecked()) {
			S.getDb().insertLabel(randomString("LMD_", 1, 3, 8), LabelColorUtil.encodeBackground(rand(0xffffff)));
		}

		if (cMakeDirtyMarker_Label.isChecked()) {
			final List<Label> labels = S.getDb().listAllLabels();
			final List<Marker> markers = S.getDb().listAllMarkers();
			if (labels.size() > 0 && markers.size() > 0) {
				final Marker_Label marker_label = Marker_Label.createNewMarker_Label(markers.get(0).gid, labels.get(0).gid);
				S.getDb().insertOrUpdateMarker_Label(marker_label);
			} else {
				new MaterialDialog.Builder(this)
					.content("not enough markers and labels to create marker_label")
					.positiveText(R.string.ok)
					.show();
				return;
			}
		}

		call.enqueue(new Callback() {
			@Override
			public void onFailure(final Call call, final IOException e) {
				runOnUiThread(() -> new MaterialDialog.Builder(SecretSyncDebugActivity.this)
					.content("Error: " + e.getMessage())
					.positiveText(R.string.ok)
					.show());
			}

			@Override
			public void onResponse(final Call call, final Response response) throws IOException {
				final Sync.SyncResponseJson<Sync_Mabel.Content> debugSyncResponse = App.getDefaultGson().fromJson(response.body().charStream(), new TypeToken<Sync.SyncResponseJson<Sync_Mabel.Content>>() {}.getType());
				runOnUiThread(() -> {
					if (debugSyncResponse.success) {
						final int final_revno = debugSyncResponse.final_revno;
						final Sync.Delta<Sync_Mabel.Content> append_delta = debugSyncResponse.append_delta;

						final Sync.ApplyAppendDeltaResult applyResult = S.getDb().applyMabelAppendDelta(final_revno, pair.shadowEntities, clientState, append_delta, entitiesBeforeSync, simpleToken);
						new MaterialDialog.Builder(SecretSyncDebugActivity.this)
							.content("Final revno: " + final_revno + "\nApply result: " + applyResult + "\nAppend delta: " + append_delta)
							.positiveText(R.string.ok)
							.show();

						if (applyResult == Sync.ApplyAppendDeltaResult.ok) {
							App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));
							App.getLbm().sendBroadcast(new Intent(MarkersActivity.ACTION_RELOAD));
							App.getLbm().sendBroadcast(new Intent(MarkerListActivity.ACTION_RELOAD));
						}
					} else {
						new MaterialDialog.Builder(SecretSyncDebugActivity.this)
							.content(debugSyncResponse.message)
							.positiveText(R.string.ok)
							.show();
					}
				});
			}
		});
	};

	View.OnClickListener bCheckHash_click = v -> {
		final String syncSetName = (String) cbSyncSetName.getSelectedItem();
		final List<Sync.Entity<?>> entities = new ArrayList<>();

		final MaterialDialog pd = new MaterialDialog.Builder(this)
			.progress(true, 0)
			.content("getting entities…")
			.show();

		Background.run(() -> {
			switch (syncSetName) {
				case SyncShadow.SYNC_SET_MABEL:
					entities.addAll(Sync_Mabel.getEntitiesFromCurrent());
					break;
				case SyncShadow.SYNC_SET_RP:
					entities.addAll(Sync_Rp.getEntitiesFromCurrent());
					break;
				case SyncShadow.SYNC_SET_PINS:
					entities.addAll(Sync_Pins.getEntitiesFromCurrent());
					break;
				case SyncShadow.SYNC_SET_HISTORY:
					entities.addAll(Sync_History.getEntitiesFromCurrent());
					break;
			}

			Collections.sort(entities, (lhs, rhs) -> lhs.gid.compareTo(rhs.gid));

			int hashCode = 1;
			for (final Sync.Entity<?> entity : entities) {
				int elementHashCode;

				if (entity == null) {
					elementHashCode = 0;
				} else {
					elementHashCode = (entity).hashCode();
				}
				hashCode = 31 * hashCode + elementHashCode;
			}

			pd.dismiss();

			final int finalHashCode = hashCode;

			runOnUiThread(() -> new MaterialDialog.Builder(this)
				.content("entities.size=" + entities.size() + " hash=" + String.format(Locale.US, "0x%08x", finalHashCode))
				.positiveText("OK")
				.show());
		});
	};
}
