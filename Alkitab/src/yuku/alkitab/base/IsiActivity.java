package yuku.alkitab.base;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import com.actionbarsherlock.internal.nineoldandroids.animation.Animator;
import com.actionbarsherlock.internal.nineoldandroids.animation.AnimatorListenerAdapter;
import com.actionbarsherlock.internal.nineoldandroids.animation.AnimatorSet;
import com.actionbarsherlock.internal.nineoldandroids.animation.ObjectAnimator;
import com.actionbarsherlock.internal.nineoldandroids.widget.NineFrameLayout;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import org.json.JSONException;
import org.json.JSONObject;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.ac.AboutActivity;
import yuku.alkitab.base.ac.HelpActivity;
import yuku.alkitab.base.ac.SettingsActivity;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.fr.DevotionFragment;
import yuku.alkitab.base.fr.MarkersFragment;
import yuku.alkitab.base.fr.SongViewFragment;
import yuku.alkitab.base.fr.TextFragment;
import yuku.alkitab.base.util.LidToAri;

public class IsiActivity extends BaseActivity {
	public static final String TAG = IsiActivity.class.getSimpleName();

	private static final int REQCODE_settings = 4;
	public static final String ACTION_SETTINGS_UPDATED = "yuku.alkitab.action.settingsUpdated";

	final String[] names = {"Alkitab", "Marka", "Kidung", "Renungan"};
	final Class<?>[] classes = {TextFragment.class, MarkersFragment.class, SongViewFragment.class, DevotionFragment.class};
	DrawerLayout drawer;
	ActionBarDrawerToggle drawerToggle;
	ListView navList;
	private int drawerSelection = 0;
	private int drawerOldSelection = -1;

	FullScreenController fullScreenController;
	NfcAdapter nfcAdapter;
	boolean fullScreen;
	Toast fullScreenDismissHint;

	TextFragment textFragment;
	MarkersFragment markersFragment;

	public static Intent createIntent(int ari) {
		Intent res = new Intent(App.context, IsiActivity.class);
		res.setAction("yuku.alkitab.action.VIEW");
		res.putExtra("ari", ari);
		return res;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, false);
		setContentView(R.layout.activity_isi);

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names);

		drawer = V.get(this, R.id.drawer_layout);

		navList = V.get(this, R.id.drawer);
		navList.setAdapter(adapter);
		navList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, final int pos, long id) {
				drawerSelection = pos;
				drawer.closeDrawer(navList);
			}
		});

		drawerToggle = new ActionBarDrawerToggle(this, drawer, R.drawable.ic_drawer, R.string.desc_drawer_open, R.string.desc_drawer_close) {
			@Override
			public void onDrawerOpened(final View drawerView) {
				super.onDrawerOpened(drawerView);
				getSupportActionBar().setTitle(R.string.app_name);
				getSupportActionBar().setSubtitle(null);
			}

			@Override
			public void onDrawerClosed(final View drawerView) {
				super.onDrawerClosed(drawerView);
				updateContent();
			}
		};
		drawer.setDrawerListener(drawerToggle);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		updateContent();

		if (Build.VERSION.SDK_INT >= 14) {
			initNfcIfAvailable();
		}

		processIntent(getIntent(), "onCreate");
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getSupportMenuInflater().inflate(R.menu.activity_isi, menu);

		AppConfig c = AppConfig.get();

		//# build config
		menu.findItem(R.id.menuHelp).setVisible(c.menuHelp);
		menu.findItem(R.id.menuDonation).setVisible(c.menuDonation);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == android.R.id.home && drawerToggle.isDrawerIndicatorEnabled()) {
			if (drawer.isDrawerVisible(GravityCompat.START)) {
				drawer.closeDrawer(GravityCompat.START);
			} else {
				drawer.openDrawer(GravityCompat.START);
			}
			return true;
		}

		switch (item.getItemId()) {
			case R.id.menuSettings:
				startActivityForResult(new Intent(App.context, SettingsActivity.class), REQCODE_settings);
				return true;
			case R.id.menuDonation:
				openDonationDialog();
				return true;
			case R.id.menuHelp:
				startActivity(HelpActivity.createIntent(false));
				return true;
			case R.id.menuSendMessage:
				startActivity(HelpActivity.createIntent(true));
				return true;
			case R.id.menuAbout:
				startActivity(new Intent(App.context, AboutActivity.class));
				return true;
		}

		return super.onOptionsItemSelected(item);
	}


	public void openDonationDialog() {
		new AlertDialog.Builder(this)
		.setMessage(R.string.donasi_keterangan)
		.setPositiveButton(R.string.donasi_tombol_ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String donation_url = getString(R.string.alamat_donasi);
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(donation_url));
				startActivity(intent);
			}
		})
		.setNegativeButton(R.string.donasi_tombol_gamau, null)
		.show();
	}

	private void updateContent() {
		getSupportActionBar().setTitle(names[drawerSelection]);
		getSupportActionBar().setSubtitle(null);

		if (drawerSelection != drawerOldSelection) {
			FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
			final Fragment fragment = Fragment.instantiate(this, classes[drawerSelection].getName());
			tx.replace(R.id.main, fragment);
			tx.commit();

			if (fragment instanceof TextFragment) {
				textFragment = (TextFragment) fragment;
			} else if (fragment instanceof MarkersFragment) {
				markersFragment = (MarkersFragment) fragment;
			}

			drawerOldSelection = drawerSelection;
		}
	}

	@Override protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		processIntent(intent, "onNewIntent");
	}

	@Override protected void onPause() {
		super.onPause();
		if (Build.VERSION.SDK_INT >= 14) {
			disableNfcForegroundDispatchIfAvailable();
		}
	}

	@TargetApi(14) private void disableNfcForegroundDispatchIfAvailable() {
		if (nfcAdapter != null) nfcAdapter.disableForegroundDispatch(this);
	}

	@Override protected void onResume() {
		super.onResume();
		if (Build.VERSION.SDK_INT >= 14) {
			enableNfcForegroundDispatchIfAvailable();
		}
	}

	@Override public void onBackPressed() {
		if (fullScreen && fullScreenController != null) {
			setFullScreen(null, false);
			return;
		}
		if (textFragment != null) {
			if (textFragment.handleBackPressed()) {
				return;
			}
		}
		super.onBackPressed();
	}

	@Override public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (textFragment != null) {
			if (textFragment.press(keyCode)) return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		if (textFragment != null) {
			if (textFragment.press(keyCode)) return true;
		}
		return super.onKeyMultiple(keyCode, repeatCount, event);
	}

	// prevent the default sound effect from emitting
	@Override public boolean onKeyUp(int keyCode, KeyEvent event) {
		String volumeButtonsForNavigation = Preferences.getString(getString(R.string.pref_tombolVolumeBuatPindah_key), getString(R.string.pref_tombolVolumeBuatPindah_default));
		if (! U.equals(volumeButtonsForNavigation, "default")) { // consume here //$NON-NLS-1$
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return true;
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (requestCode == REQCODE_settings) {
			LocalBroadcastManager.getInstance(App.context).sendBroadcast(new Intent(ACTION_SETTINGS_UPDATED));
		}
	}

	private void processIntent(Intent intent, String via) {
		Log.d(TAG, "Got intent via " + via);
		Log.d(TAG, "  action: " + intent.getAction());
		Log.d(TAG, "  data uri: " + intent.getData());
		Log.d(TAG, "  component: " + intent.getComponent());
		Log.d(TAG, "  flags: 0x" + Integer.toHexString(intent.getFlags()));
		Log.d(TAG, "  mime: " + intent.getType());
		Bundle extras = intent.getExtras();
		Log.d(TAG, "  extras: " + (extras == null? "null": extras.size()));
		if (extras != null) {
			for (String key: extras.keySet()) {
				Log.d(TAG, "    " + key + " = " + extras.get(key));
			}
		}

		if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
			if (Build.VERSION.SDK_INT >= 14) {
				checkAndProcessBeamIntent(intent);
			}

			checkAndProcessAlkitabViewIntent(intent);

			checkAndProcessOpenBackupIntent(intent);
		}
	}

	private void checkAndProcessOpenBackupIntent(final Intent intent) {
		if (U.equals(intent.getAction(), Intent.ACTION_VIEW)) {
			Uri data = intent.getData();
			if (data != null && (U.equals(data.getScheme(), "content") || U.equals(data.getScheme(), "file"))) { //$NON-NLS-1$ //$NON-NLS-2$
				if (markersFragment != null) { // TODO activate this
					markersFragment.handleOpenBackupIntent(intent);
				}
			}
		}

	}

	/** did we get here from yuku.alkitab.action.VIEW intent? */
	private void checkAndProcessAlkitabViewIntent(Intent intent) {
		if (!U.equals(intent.getAction(), "yuku.alkitab.action.VIEW")) return;

		if (intent.hasExtra("ari")) {
			int ari = intent.getIntExtra("ari", 0);
			if (ari != 0) {
				// TODO make sure text fragment is visible
				if (textFragment != null) {
					textFragment.handleViewIntent(ari);
				}
			} else {
				new AlertDialog.Builder(this)
				.setMessage("Invalid ari: " + ari)
				.setPositiveButton(R.string.ok, null)
				.show();
			}
		} else if (intent.hasExtra("lid")) {
			int lid = intent.getIntExtra("lid", 0);
			int ari = LidToAri.lidToAri(lid);
			if (ari != 0) {
				// TODO make sure text fragment is visible
				if (textFragment != null) {
					textFragment.handleViewIntent(ari);
				}
			} else {
				new AlertDialog.Builder(this)
				.setMessage("Invalid lid: " + lid)
				.setPositiveButton(R.string.ok, null)
				.show();
			}
		}
	}

	@TargetApi(14) private void initNfcIfAvailable() {
		nfcAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
		if (nfcAdapter != null) {
			if (textFragment == null) {
				return;  // TODO make sure textFragment is visible
			}

			nfcAdapter.setNdefPushMessageCallback(new NfcAdapter.CreateNdefMessageCallback() {
				@Override
				public NdefMessage createNdefMessage(NfcEvent event) {
					JSONObject obj = new JSONObject();
					try {
						obj.put("ari", textFragment.getCurrentDisplayedAri()); //$NON-NLS-1$
					} catch (JSONException e) { // won't happen
					}
					byte[] payload = obj.toString().getBytes();
					NdefRecord record = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, "application/vnd.yuku.alkitab.nfc.beam".getBytes(), new byte[0], payload); //$NON-NLS-1$
					return new NdefMessage(new NdefRecord[] {
					record,
					NdefRecord.createApplicationRecord(getPackageName()),
					});
				}
			}, this);
		}
	}


	@TargetApi(14) private void enableNfcForegroundDispatchIfAvailable() {
		if (nfcAdapter != null) {
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, IsiActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
			IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
			try {
				ndef.addDataType("application/vnd.yuku.alkitab.nfc.beam"); //$NON-NLS-1$
			} catch (IntentFilter.MalformedMimeTypeException e) {
				throw new RuntimeException("fail mime type", e); //$NON-NLS-1$
			}
			IntentFilter[] intentFiltersArray = new IntentFilter[] {ndef, };
			nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, null);
		}
	}

	@TargetApi(14) private void checkAndProcessBeamIntent(Intent intent) {
		String action = intent.getAction();
		if (U.equals(action, NfcAdapter.ACTION_NDEF_DISCOVERED)) {
			Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			// only one message sent during the beam
			if (rawMsgs != null && rawMsgs.length > 0) {
				NdefMessage msg = (NdefMessage) rawMsgs[0];
				// record 0 contains the MIME type, record 1 is the AAR, if present
				NdefRecord[] records = msg.getRecords();
				if (records != null && records.length > 0) {
					String json = new String(records[0].getPayload());
					try {
						JSONObject obj = new JSONObject(json);
						int ari = obj.optInt("ari", -1); //$NON-NLS-1$
						if (ari != -1) {
							// TODO make sure textFragment is ready
							if (textFragment != null) {
								textFragment.handleViewIntent(ari);
							}
						}
					} catch (JSONException e) {
						Log.e(TAG, "Malformed json from nfc", e); //$NON-NLS-1$
					}
				}
			}
		}
	}

	public void setFullScreen(NineFrameLayout panelNavigation, boolean yes) {
		if (fullScreenController == null) {
			fullScreenController = new FullScreenController(panelNavigation);
		} else if (panelNavigation != null) {
			fullScreenController.panelNavigation = panelNavigation;
		}

		if (yes) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			fullScreenController.hidePermanently();
			fullScreen = true;

			if (fullScreenDismissHint == null) {
				fullScreenDismissHint = Toast.makeText(this, R.string.full_screen_dismiss_hint, Toast.LENGTH_SHORT);
			}
			fullScreenDismissHint.show();
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			fullScreenController.showPermanently();
			fullScreen = false;
		}
	}

	class FullScreenController {
		private NineFrameLayout panelNavigation;
		private Animator mCurrentShowAnim;
		private boolean mShowHideAnimationEnabled = true;

		final Animator.AnimatorListener mHideListener = new AnimatorListenerAdapter() {
			@TargetApi(11) @Override public void onAnimationEnd(Animator animation) {
				if (panelNavigation != null) {
					panelNavigation.setTranslationY(0);
					panelNavigation.setVisibility(View.GONE);
				}
				mCurrentShowAnim = null;
			}
		};

		final Animator.AnimatorListener mShowListener = new AnimatorListenerAdapter() {
			@TargetApi(11) @Override public void onAnimationEnd(Animator animation) {
				mCurrentShowAnim = null;
				panelNavigation.requestLayout();
			}
		};

		public FullScreenController(final NineFrameLayout panelNavigation) {
			this.panelNavigation = panelNavigation;
		}

		@TargetApi(11) void hidePanelNavigation() {
			if (mCurrentShowAnim != null) {
				mCurrentShowAnim.end();
			}

			if (panelNavigation.getVisibility() == View.GONE) {
				return;
			}

			if (mShowHideAnimationEnabled) {
				panelNavigation.setAlpha(1);
				AnimatorSet anim = new AnimatorSet();
				AnimatorSet.Builder b = anim.play(ObjectAnimator.ofFloat(panelNavigation, "alpha", 0));
				if (panelNavigation != null) {
					b.with(ObjectAnimator.ofFloat(panelNavigation, "translationY", 0, +panelNavigation.getHeight()));
				}
				anim.addListener(mHideListener);
				mCurrentShowAnim = anim;
				anim.start();
			} else {
				mHideListener.onAnimationEnd(null);
			}
		}

		@TargetApi(11) void showPanelNavigation() {
			if (mCurrentShowAnim != null) {
				mCurrentShowAnim.end();
			}
			if (panelNavigation.getVisibility() == View.VISIBLE) {
				return;
			}
			panelNavigation.setVisibility(View.VISIBLE);

			if (mShowHideAnimationEnabled) {
				panelNavigation.setAlpha(0);
				AnimatorSet anim = new AnimatorSet();
				AnimatorSet.Builder b = anim.play(ObjectAnimator.ofFloat(panelNavigation, "alpha", 1));
				b.with(ObjectAnimator.ofFloat(panelNavigation, "translationY", +panelNavigation.getHeight(), 0));
				anim.addListener(mShowListener);
				mCurrentShowAnim = anim;
				anim.start();
			} else {
				panelNavigation.setAlpha(1);
				panelNavigation.setTranslationY(0);
				mShowListener.onAnimationEnd(null);
			}
		}

		public void hidePermanently() {
			getSupportActionBar().hide();
			hidePanelNavigation();
		}

		public void showPermanently() {
			getSupportActionBar().show();
			showPanelNavigation();
		}
	}

	public boolean isFullScreen() {
		return fullScreen;
	}
}
