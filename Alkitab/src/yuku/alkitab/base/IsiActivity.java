package yuku.alkitab.base;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ShareCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.widget.DrawerLayout;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.Pair;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.ac.BookmarkActivity;
import yuku.alkitab.base.ac.GotoActivity;
import yuku.alkitab.base.ac.Search2Activity;
import yuku.alkitab.base.ac.ShareActivity;
import yuku.alkitab.base.ac.VersionsActivity;
import yuku.alkitab.base.ac.VersionsActivity.MVersion;
import yuku.alkitab.base.ac.VersionsActivity.MVersionInternal;
import yuku.alkitab.base.ac.VersionsActivity.MVersionPreset;
import yuku.alkitab.base.ac.VersionsActivity.MVersionYes;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.config.VersionConfig;
import yuku.alkitab.base.dialog.ProgressMarkDialog;
import yuku.alkitab.base.dialog.TypeBookmarkDialog;
import yuku.alkitab.base.dialog.TypeHighlightDialog;
import yuku.alkitab.base.dialog.TypeNoteDialog;
import yuku.alkitab.base.dialog.VersesDialog;
import yuku.alkitab.base.dialog.XrefDialog;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.BackupManager;
import yuku.alkitab.base.util.History;
import yuku.alkitab.base.util.Jumper;
import yuku.alkitab.base.util.LidToAri;
import yuku.alkitab.base.util.OsisBookNames;
import yuku.alkitab.base.widget.AttributeView;
import yuku.alkitab.base.widget.CallbackSpan;
import yuku.alkitab.base.widget.Floater;
import yuku.alkitab.base.widget.FormattedTextRenderer;
import yuku.alkitab.base.widget.GotoButton;
import yuku.alkitab.base.widget.LabeledSplitHandleButton;
import yuku.alkitab.base.widget.LeftDrawer;
import yuku.alkitab.base.widget.SplitHandleButton;
import yuku.alkitab.base.widget.TextAppearancePanel;
import yuku.alkitab.base.widget.TouchInterceptLinearLayout;
import yuku.alkitab.base.widget.VerseInlineLinkSpan;
import yuku.alkitab.base.widget.VerseRenderer;
import yuku.alkitab.base.widget.VersesView;
import yuku.alkitab.base.widget.VersesView.PressResult;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.FootnoteEntry;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.ProgressMark;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

public class IsiActivity extends BaseActivity implements XrefDialog.XrefDialogListener, LeftDrawer.Text.Listener {
	public static final String TAG = IsiActivity.class.getSimpleName();

	public static final String ACTION_ATTRIBUTE_MAP_CHANGED = "yuku.alkitab.action.ATTRIBUTE_MAP_CHANGED";
	public static final String EXTRA_CLOSE_DRAWER = "close_drawer";

	public static final String ACTION_SETTINGS_CHANGED = "yuku.alkitab.action.SETTINGS_CHANGED";

	// The followings are for instant_pref
	private static final String PREFKEY_lastBookId = "kitabTerakhir";
	private static final String PREFKEY_lastChapter = "pasalTerakhir";
	private static final String PREFKEY_lastVerse = "ayatTerakhir";
	private static final String PREFKEY_lastVersionId = "edisiTerakhir";

	private static final String PREFKEY_lastSplitVersionId = "lastSplitVersionId";
	private static final int REQCODE_goto = 1;
	private static final int REQCODE_share = 7;
	private static final int REQCODE_textAppearanceGetFonts = 9;
	private static final int REQCODE_textAppearanceCustomColors = 10;
	private static final int REQCODE_readingPlan = 11;

	private static final String EXTRA_verseUrl = "verseUrl";
	private boolean uncheckVersesWhenActionModeDestroyed = true;

	private GotoButton.FloaterDragListener bGoto_floaterDrag = new GotoButton.FloaterDragListener() {
		final int[] floaterLocationOnScreen = {0, 0};

		@Override
		public void onFloaterDragStart(final float screenX, final float screenY) {
			floater.show(activeBook.bookId, chapter_1);
			floater.onDragStart(S.activeVersion);
		}

		@Override
		public void onFloaterDragMove(final float screenX, final float screenY) {
			floater.getLocationOnScreen(floaterLocationOnScreen);
			floater.onDragMove(screenX - floaterLocationOnScreen[0], screenY - floaterLocationOnScreen[1]);
		}

		@Override
		public void onFloaterDragComplete(final float screenX, final float screenY) {
			floater.hide();
			floater.onDragComplete(screenX - floaterLocationOnScreen[0], screenY - floaterLocationOnScreen[1]);
		}
	};

	private Floater.Listener floater_listener = new Floater.Listener() {
		@Override
		public void onSelectComplete(final int ari) {
			jumpToAri(ari);
			history.add(ari);
		}
	};

	private View.OnTouchListener splitRoot_interceptTouch = new View.OnTouchListener() {
		long lastDownTime;
		float lastDownX;
		float lastDownY;
		long lastUpTime;
		float lastUpX;
		float lastUpY;

		int maxDoubleTapDelay = ViewConfiguration.getDoubleTapTimeout();
		int maxDoubleTapDistance = -1;

		final int[] locationOnScreen = new int[2];

		float distSquared(float dx, float dy) {
			return dx * dx + dy * dy;
		}

		@Override
		public boolean onTouch(final View v, final MotionEvent event) {
			final int action = MotionEventCompat.getActionMasked(event);

			// lazy
			if (maxDoubleTapDistance == -1) {
				maxDoubleTapDistance = ViewConfiguration.get(IsiActivity.this).getScaledDoubleTapSlop();
			}

			v.getLocationOnScreen(locationOnScreen);

			if (action == MotionEvent.ACTION_DOWN) { // check for double click
				if (lastUpTime - lastDownTime < maxDoubleTapDelay) { // first tap down to up must be fast enough
					long thisDownTime = event.getEventTime();
					if (thisDownTime - lastUpTime < maxDoubleTapDelay) { // second tap must be fast enough
						if (distSquared(lastUpX - lastDownX, lastUpY - lastDownY) < maxDoubleTapDistance) { // first tap down to up must be within distance
							float thisDownX = event.getX() + locationOnScreen[0];
							float thisDownY = event.getY() + locationOnScreen[1];
							if (distSquared(thisDownX - lastUpX, thisDownY - lastUpY) < maxDoubleTapDistance) { // second tap must be fast enough
								// double tap success!
								setFullScreen(!fullScreen);
								return true;
							}
						}
					}
				}
			}

			if (action == MotionEvent.ACTION_DOWN) {
				lastDownTime = event.getEventTime();
				lastDownX = event.getX() + locationOnScreen[0];
				lastDownY = event.getY() + locationOnScreen[1];
			} else if (action == MotionEvent.ACTION_UP) {
				lastUpTime = event.getEventTime();
				lastUpX = event.getX() + locationOnScreen[0];
				lastUpY = event.getY() + locationOnScreen[1];
			}

			return false;
		}
	};

	DrawerLayout drawerLayout;
	ActionBarDrawerToggle drawerToggle;
	LeftDrawer.Text leftDrawer;

	FrameLayout overlayContainer;
	View root;
	VersesView lsText;
	VersesView lsSplit1;
	TextView tSplitEmpty;
	TouchInterceptLinearLayout splitRoot;
	View splitHandle;
	LabeledSplitHandleButton splitHandleButton;
	GotoButton bGoto;
	ImageButton bLeft;
	ImageButton bRight;
	Button bVersion;
	Floater floater;

	Book activeBook;
	int chapter_1 = 0;
	SharedPreferences instant_pref;
	boolean fullScreen;

	History history;
	NfcAdapter nfcAdapter;
	ActionMode actionMode;
	TextAppearancePanel textAppearancePanel;

	// temporary states
	Boolean hasEsvsbAsal;
	Version activeSplitVersion;
	String activeSplitVersionId;
	
	CallbackSpan.OnClickListener parallelListener = new CallbackSpan.OnClickListener() {
		@Override public void onClick(View widget, Object data) {
            if (data instanceof String) {
                final int ari = jumpTo((String) data);
                if (ari != 0) {
                    history.add(ari);
                }
            } else if (data instanceof Integer) {
	            final int ari = (Integer) data;
	            jumpToAri(ari);
	            history.add(ari);
            }
		}
	};

	static class IntentResult {
		public int ari;
		public boolean selectVerse;
		public IntentResult(final int ari) {
			this.ari = ari;
		}
	}

	public static Intent createIntent() {
		return new Intent(App.context, IsiActivity.class);
	}

	public static Intent createIntent(int ari) {
		Intent res = new Intent(App.context, IsiActivity.class);
		res.setAction("yuku.alkitab.action.VIEW");
		res.putExtra("ari", ari);
		return res;
	}

	final BroadcastReceiver reloadAttributeMapReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			reloadBothAttributeMaps();

			if (intent.getBooleanExtra(EXTRA_CLOSE_DRAWER, false)) {
				leftDrawer.closeDrawer();
			}
		}
	};

	final BroadcastReceiver applyPreferencesReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			S.calculateAppliedValuesBasedOnPreferences();
			applyPreferences(true);
		}
	};

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, false);
		
		setContentView(R.layout.activity_isi);

		drawerLayout = V.get(this, R.id.drawerLayout);
		leftDrawer = V.get(this, R.id.left_drawer);
		leftDrawer.configure(this, drawerLayout);

		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.drawable.ic_navigation_drawer, R.string.drawer_open, R.string.drawer_close);
		drawerLayout.setDrawerListener(drawerToggle);

		final ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowCustomEnabled(true);
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setDisplayShowHomeEnabled(false);
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);

		final View actionCustomView = getLayoutInflater().cloneInContext(actionBar.getThemedContext()).inflate(R.layout.activity_isi_action_custom_view, null);
		bGoto = V.get(actionCustomView, R.id.bGoto);
		bLeft = V.get(actionCustomView, R.id.bLeft);
		bRight = V.get(actionCustomView, R.id.bRight);
		bVersion = V.get(actionCustomView, R.id.bVersion);
		actionBar.setCustomView(actionCustomView);

		overlayContainer = V.get(this, R.id.overlayContainer);
		root = V.get(this, R.id.root);
		lsText = V.get(this, R.id.lsSplit0);
		lsSplit1 = V.get(this, R.id.lsSplit1);
		tSplitEmpty = V.get(this, R.id.tSplitEmpty);
		splitRoot = V.get(this, R.id.splitRoot);
		splitHandle = V.get(this, R.id.splitHandle);
		splitHandleButton = V.get(this, R.id.splitHandleButton);
		floater = V.get(this, R.id.floater);

		applyPreferences(false);

		lsText.setName("lsText");
		lsSplit1.setName("lsSplit1");

		splitRoot.setInterceptTouchEventListener(splitRoot_interceptTouch);
		
		bGoto.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) { bGoto_click(); }
		});
		bGoto.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				bGoto_longClick();
				return true;
			}
		});
		bGoto.setFloaterDragListener(bGoto_floaterDrag);

		bLeft.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { bLeft_click(); }
		});
		bRight.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { bRight_click(); }
		});
		bVersion.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { bVersion_click(); }
		});

		floater.setListener(floater_listener);

		lsText.setOnKeyListener(new View.OnKeyListener() {
			@Override public boolean onKey(View v, int keyCode, KeyEvent event) {
				int action = event.getAction();
				if (action == KeyEvent.ACTION_DOWN) {
					return press(keyCode);
				} else if (action == KeyEvent.ACTION_MULTIPLE) {
					return press(keyCode);
				}
				return false;
			}
		});

		// listeners
		lsText.setParallelListener(parallelListener);
		lsText.setAttributeListener(attributeListener);
		lsText.setInlineLinkSpanFactory(new VerseInlineLinkSpanFactory(lsText));
		lsText.setSelectedVersesListener(lsText_selectedVerses);
		lsText.setOnVerseScrollListener(lsText_verseScroll);

		// additional setup for split1
		lsSplit1.setVerseSelectionMode(VersesView.VerseSelectionMode.multiple);
		lsSplit1.setEmptyView(tSplitEmpty);
		lsSplit1.setParallelListener(parallelListener);
		lsSplit1.setAttributeListener(attributeListener);
		lsSplit1.setInlineLinkSpanFactory(new VerseInlineLinkSpanFactory(lsSplit1));
		lsSplit1.setSelectedVersesListener(lsSplit1_selectedVerses);
		lsSplit1.setOnVerseScrollListener(lsSplit1_verseScroll);

		// for splitting
		splitHandleButton.setListener(splitHandleButton_listener);
		splitHandleButton.setOnLabelPressed(splitHandleButton_labelPressed);

		history = History.getInstance();

		initNfcIfAvailable();

		if (S.getDb().countAllBookmarks() != 0) {
			BackupManager.startAutoBackup();
		}

		final IntentResult intentResult = processIntent(getIntent(), "onCreate");
		final int openingAri;
		final boolean selectVerse;

		instant_pref = App.getInstantPreferences();
		if (intentResult == null) {
			// restore the last (version; book; chapter and verse).
			final int lastBookId = instant_pref.getInt(PREFKEY_lastBookId, 0);
			final int lastChapter = instant_pref.getInt(PREFKEY_lastChapter, 0);
			final int lastVerse = instant_pref.getInt(PREFKEY_lastVerse, 0);
			openingAri = Ari.encode(lastBookId, lastChapter, lastVerse);
			selectVerse = false;
			Log.d(TAG, "Going to the last: bookId=" + lastBookId + " chapter=" + lastChapter + " verse=" + lastVerse);
		} else {
			openingAri = intentResult.ari;
			selectVerse = intentResult.selectVerse;
		}

		final String lastVersionId = instant_pref.getString(PREFKEY_lastVersionId, null);
		final MVersion mv = getVersionFromVersionId(lastVersionId);

		if (mv != null) {
			loadVersion(mv, false);
		} else {
			loadVersion(new MVersionInternal(), false);
		}

		{ // load book
			final Book book = S.activeVersion.getBook(Ari.toBook(openingAri));
			if (book != null) {
				this.activeBook = book;
			} else { // can't load last book or bookId 0
				this.activeBook = S.activeVersion.getFirstBook();
			}
		}

		// load chapter and verse
		display(Ari.toChapter(openingAri), Ari.toVerse(openingAri));

		if (intentResult != null) { // also add to history if not opening the last seen verse
			history.add(openingAri);
		}

		{ // load last split version. This must be after load book, chapter, and verse.
			final String lastSplitVersionId = instant_pref.getString(PREFKEY_lastSplitVersionId, null);
			if (lastSplitVersionId != null) {
				final MVersion splitMv = getVersionFromVersionId(lastSplitVersionId);
				final MVersion splitMvActual = splitMv == null? new MVersionInternal(): splitMv;

				if (loadSplitVersion(splitMvActual)) {
					openSplitDisplay();
					displaySplitFollowingMaster(Ari.toVerse(openingAri));
				}
			}
		}

		if (selectVerse) {
			lsText.setVerseSelected(Ari.toVerse(openingAri), true);
		}

		App.getLbm().registerReceiver(reloadAttributeMapReceiver, new IntentFilter(ACTION_ATTRIBUTE_MAP_CHANGED));
		App.getLbm().registerReceiver(applyPreferencesReceiver, new IntentFilter(ACTION_SETTINGS_CHANGED));
	}

	@Override
	protected void onPostCreate(final Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(final Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		drawerToggle.onConfigurationChanged(newConfig);
	}

	@Override protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		processIntent(intent, "onNewIntent");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		App.getLbm().unregisterReceiver(reloadAttributeMapReceiver);
		App.getLbm().unregisterReceiver(applyPreferencesReceiver);
	}

	/**
	 * @return non-null if the intent is handled by any of the intent handler (e.g. nfc or VIEW)
	 */
	private IntentResult processIntent(Intent intent, String via) {
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
		
		if (Build.VERSION.SDK_INT >= 14) {
			final IntentResult result = tryGetIntentResultFromBeam(intent);
			if (result != null) return result;
		}

		final IntentResult result = tryGetIntentResultFromView(intent);
		if (result != null) return result;

		return null;
	}
	
	/** did we get here from VIEW intent? */
	private IntentResult tryGetIntentResultFromView(Intent intent) {
		if (!U.equals(intent.getAction(), "yuku.alkitab.action.VIEW")) return null;

		final boolean selectVerse = intent.getBooleanExtra("selectVerse", false);

		if (intent.hasExtra("ari")) {
			int ari = intent.getIntExtra("ari", 0);
			if (ari != 0) {
				final IntentResult res = new IntentResult(ari);
				res.selectVerse = selectVerse;
				return res;
			} else {
				new AlertDialog.Builder(this)
				.setMessage("Invalid ari: " + ari)
				.setPositiveButton(R.string.ok, null)
				.show();
				return null;
			}
		} else if (intent.hasExtra("lid")) {
			int lid = intent.getIntExtra("lid", 0);
			int ari = LidToAri.lidToAri(lid);
			if (ari != 0) {
				jumpToAri(ari);
				history.add(ari);
				final IntentResult res = new IntentResult(ari);
				res.selectVerse = selectVerse;
				return res;
			} else {
				new AlertDialog.Builder(this)
				.setMessage("Invalid lid: " + lid)
				.setPositiveButton(R.string.ok, null)
				.show();
				return null;
			}
		} else {
			return null;
		}
	}

	private void initNfcIfAvailable() {
		nfcAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
		if (nfcAdapter != null) {
			nfcAdapter.setNdefPushMessageCallback(new CreateNdefMessageCallback() {
				@Override public NdefMessage createNdefMessage(NfcEvent event) {
					JSONObject obj = new JSONObject();
					try {
						obj.put("ari", Ari.encode(IsiActivity.this.activeBook.bookId, IsiActivity.this.chapter_1, lsText.getVerseBasedOnScroll())); //$NON-NLS-1$
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

	@Override protected void onPause() {
		super.onPause();
		disableNfcForegroundDispatchIfAvailable();
	}

	private void disableNfcForegroundDispatchIfAvailable() {
		if (nfcAdapter != null) nfcAdapter.disableForegroundDispatch(this);
	}
	
	@Override protected void onResume() {
		super.onResume();
		enableNfcForegroundDispatchIfAvailable();
	}

	private void enableNfcForegroundDispatchIfAvailable() {
		if (nfcAdapter != null) {
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, IsiActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
			IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
			try {
			    ndef.addDataType("application/vnd.yuku.alkitab.nfc.beam"); //$NON-NLS-1$
			} catch (MalformedMimeTypeException e) {
			    throw new RuntimeException("fail mime type", e); //$NON-NLS-1$
			}
			IntentFilter[] intentFiltersArray = new IntentFilter[] {ndef, };
			nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, null);
		}
	}

	private IntentResult tryGetIntentResultFromBeam(Intent intent) {
		String action = intent.getAction();
		if (!U.equals(action, NfcAdapter.ACTION_NDEF_DISCOVERED)) return null;

		Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
		// only one message sent during the beam
		if (rawMsgs == null || rawMsgs.length <= 0) return null;

		NdefMessage msg = (NdefMessage) rawMsgs[0];
		// record 0 contains the MIME type, record 1 is the AAR, if present
		NdefRecord[] records = msg.getRecords();
		if (records == null || records.length <= 0) return null;

		String json = new String(records[0].getPayload());
		try {
			JSONObject obj = new JSONObject(json);
			final int ari = obj.optInt("ari", -1); //$NON-NLS-1$
			if (ari == -1) return null;

			return new IntentResult(ari);
		} catch (JSONException e) {
			Log.e(TAG, "Malformed json from nfc", e); //$NON-NLS-1$
			return null;
		}
	}

	MVersion getVersionFromVersionId(String versionId) {
		if (versionId == null || MVersionInternal.getVersionInternalId().equals(versionId)) {
			return null; // internal is made the same as null
		}

		// try preset versions first
		final VersionConfig c = VersionConfig.get();
		for (MVersionPreset preset: c.presets) { // 2. preset
			if (preset.getVersionId().equals(versionId)) {
				if (preset.hasDataFile()) {
					return preset;
				} else {
					return null; // this is the one that should have been chosen, but the data file is not available, so let's fallback.
				}
			}
		}

		// still no match, let's look at yes versions
		for (MVersionYes yes: S.getDb().listAllVersions()) {
			if (yes.getVersionId().equals(versionId)) {
				if (yes.hasDataFile()) {
					return yes;
				} else {
					return null; // this is the one that should have been chosen, but the data file is not available, so let's fallback.
				}
			}
		}

		return null; // not known
	}

	boolean loadVersion(final MVersion mv, boolean display) {
		try {
			Version version = mv.getVersion();
			
			if (version != null) {
				if (this.activeBook != null) { // we already have some other version loaded, so make the new version open the same book
					int bookId = this.activeBook.bookId;
					Book book = version.getBook(bookId);
					if (book != null) { // we load the new book succesfully
						this.activeBook = book;
					} else { // too bad, this book was not found, get any book
						this.activeBook = version.getFirstBook();
					}
				}
				S.activeVersion = version;
				S.activeVersionId = mv.getVersionId();
				{
					final String shortName = version.getShortName();
					if (shortName != null) {
						bVersion.setText(shortName);
					} else {
						bVersion.setText(version.getLongName());
					}
				}
				splitHandleButton.setLabel1("\u25b2 " + getSplitHandleVersionName(mv, version));
				
				if (display) {
					display(chapter_1, lsText.getVerseBasedOnScroll(), false);
				}
				
				return true;
			} else {
				throw new RuntimeException(getString(R.string.ada_kegagalan_membuka_edisiid, mv.getVersionId()));
			}
		} catch (Throwable e) { // so we don't crash on the beginning of the app
			Log.e(TAG, "Error opening main version", e);

			new AlertDialog.Builder(IsiActivity.this)
			.setMessage(getString(R.string.ada_kegagalan_membuka_edisiid, mv.getVersionId()))
			.setPositiveButton(R.string.ok, null)
			.show();
			
			return false;
		}
	}
	
	boolean loadSplitVersion(final MVersion mv) {
		try {
			Version version = mv.getVersion();
			
			if (version != null) {
				activeSplitVersion = version;
				activeSplitVersionId = mv.getVersionId();
				splitHandleButton.setLabel2(getSplitHandleVersionName(mv, version) + " \u25bc");
				
				return true;
			} else {
				throw new RuntimeException(getString(R.string.ada_kegagalan_membuka_edisiid, mv.getVersionId()));
			}
		} catch (Throwable e) { // so we don't crash on the beginning of the app
			Log.e(TAG, "Error opening split version", e);

			new AlertDialog.Builder(IsiActivity.this)
			.setMessage(getString(R.string.ada_kegagalan_membuka_edisiid, mv.getVersionId()))
			.setPositiveButton(R.string.ok, null)
			.show();
			
			return false;
		}
	}
	
	String getSplitHandleVersionName(MVersion mv, Version version) {
		String shortName = version.getShortName();
		if (shortName != null) {
			return shortName;
		} else {
			// try to get it from the model
			if (mv.shortName != null) {
				return mv.shortName;
			}
			
			return version.getLongName(); // this will not be null
		}
	}
	
	boolean press(int keyCode) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
			bLeft_click();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
			bRight_click();
			return true;
		}
		
		PressResult pressResult = lsText.press(keyCode);
		switch (pressResult.kind) {
		case left:
			bLeft_click();
			return true;
		case right:
			bRight_click();
			return true;
		case consumed:
			if (activeSplitVersion != null) {
				lsSplit1.scrollToVerse(pressResult.targetVerse_1);
			}
			return true;
		default:
			return false;
		}
	}
	
	/**
	 * Jump to a given verse reference in string format.
	 * @return ari of the parsed reference
	 */
	int jumpTo(String reference) {
		if (reference.trim().length() == 0) {
			return 0;
		}
		
		Log.d(TAG, "going to jump to " + reference); //$NON-NLS-1$
		
		Jumper jumper = new Jumper(reference);
		if (! jumper.getParseSucceeded()) {
			Toast.makeText(this, getString(R.string.alamat_tidak_sah_alamat, reference), Toast.LENGTH_SHORT).show();
			return 0;
		}
		
		int bookId = jumper.getBookId(S.activeVersion.getConsecutiveBooks());
		Book selected;
		if (bookId != -1) {
			Book book = S.activeVersion.getBook(bookId);
			if (book != null) {
				selected = book;
			} else {
				// not avail, just fallback
				selected = this.activeBook;
			}
		} else {
			selected = this.activeBook;
		}
		
		// set book
		this.activeBook = selected;
		
		int chapter = jumper.getChapter();
		int verse = jumper.getVerse();
		int ari_cv;
		if (chapter == -1 && verse == -1) {
			ari_cv = display(1, 1);
		} else {
			ari_cv = display(chapter, verse);
		}
		
		return Ari.encode(selected.bookId, ari_cv);
	}
	
	/**
	 * Jump to a given ari
	 */
	void jumpToAri(int ari) {
		if (ari == 0) return;
		
		Log.d(TAG, "will jump to ari 0x" + Integer.toHexString(ari)); //$NON-NLS-1$
		
		int bookId = Ari.toBook(ari);
		Book book = S.activeVersion.getBook(bookId);
		
		if (book != null) {
			this.activeBook = book;
		} else {
			Log.w(TAG, "bookId=" + bookId + " not found for ari=" + ari); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		
		display(Ari.toChapter(ari), Ari.toVerse(ari));
	}
	
	private CharSequence referenceFromSelectedVerses(IntArrayList selectedVerses, Book book) {
		if (selectedVerses.size() == 0) {
			// should not be possible. So we don't do anything.
			return book.reference(this.chapter_1);
		} else if (selectedVerses.size() == 1) {
			return book.reference(this.chapter_1, selectedVerses.get(0));
		} else {
			return book.reference(this.chapter_1, selectedVerses);
		}
	}
	
	String prepareTextForCopyShare(IntArrayList selectedVerses_1, CharSequence reference, boolean isSplitVersion) {
		StringBuilder res = new StringBuilder();
		res.append(reference);
		
		if (Preferences.getBoolean(getString(R.string.pref_copyWithVerseNumbers_key), false) && selectedVerses_1.size() > 1) {
			res.append('\n');

			// append each selected verse with verse number prepended
			for (int i = 0; i < selectedVerses_1.size(); i++) {
				int verse_1 = selectedVerses_1.get(i);
				res.append(verse_1);
				res.append(' ');
				if (isSplitVersion) {
					res.append(U.removeSpecialCodes(lsSplit1.getVerse(verse_1)));
				} else {
					res.append(U.removeSpecialCodes(lsText.getVerse(verse_1)));
				}
				res.append('\n');
			}
		} else {
			res.append("  "); //$NON-NLS-1$
			
			// append each selected verse without verse number prepended
			for (int i = 0; i < selectedVerses_1.size(); i++) {
				int verse_1 = selectedVerses_1.get(i);
				if (i != 0) res.append('\n');
				if (isSplitVersion) {
					res.append(U.removeSpecialCodes(lsSplit1.getVerse(verse_1)));
				} else {
					res.append(U.removeSpecialCodes(lsText.getVerse(verse_1)));
				}
			}
		}
		return res.toString();
	}

	private void applyPreferences(boolean languageToo) {
		// appliance of background color
		{
			root.setBackgroundColor(S.applied.backgroundColor);
			lsText.setCacheColorHint(S.applied.backgroundColor);
			lsSplit1.setCacheColorHint(S.applied.backgroundColor);
		}
		
		if (languageToo) {
			App.updateConfigurationWithPreferencesLocale();
		}
		
		// necessary
		lsText.invalidateViews();
		lsSplit1.invalidateViews();
	}
	
	@Override protected void onStop() {
		super.onStop();
		
		final Editor editor = instant_pref.edit();
		editor.putInt(PREFKEY_lastBookId, this.activeBook.bookId);
		editor.putInt(PREFKEY_lastChapter, chapter_1);
		editor.putInt(PREFKEY_lastVerse, lsText.getVerseBasedOnScroll());
		editor.putString(PREFKEY_lastVersionId, S.activeVersionId);
		if (activeSplitVersion == null) {
			editor.putString(PREFKEY_lastSplitVersionId, null);
		} else {
			editor.putString(PREFKEY_lastSplitVersionId, activeSplitVersionId);
		}
		editor.apply();

		history.save();

		lsText.setKeepScreenOn(false);
	}
	
	@Override protected void onStart() {
		super.onStart();
		
		if (Preferences.getBoolean(getString(R.string.pref_keepScreenOn_key), getResources().getBoolean(R.bool.pref_keepScreenOn_default))) {
			lsText.setKeepScreenOn(true);
		}
	}
	
	@Override public void onBackPressed() {
		if (textAppearancePanel != null) {
			textAppearancePanel.hide();
			textAppearancePanel = null;
		} else if (fullScreen) {
			setFullScreen(false);
			leftDrawer.getHandle().setFullScreen(false);
		} else {
			super.onBackPressed();
		}
	}
	
	void bGoto_click() {
		startActivityForResult(GotoActivity.createIntent(this.activeBook.bookId, this.chapter_1, lsText.getVerseBasedOnScroll()), REQCODE_goto);
	}
	
	void bGoto_longClick() {
		if (history.getSize() > 0) {
			new AlertDialog.Builder(this)
			.setAdapter(historyAdapter, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					int ari = history.getAri(which);
					jumpToAri(ari);
					history.add(ari);
					Preferences.setBoolean(Prefkey.history_button_understood, true);
				}
			})
			.setNegativeButton(R.string.cancel, null)
			.show();
		} else {
			Toast.makeText(this, R.string.recentverses_not_available, Toast.LENGTH_SHORT).show();
		}
	}

	private ListAdapter historyAdapter = new EasyAdapter() {

		private final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(App.context);
		private final java.text.DateFormat mediumDateFormat = DateFormat.getMediumDateFormat(App.context);

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			TextView textView = (TextView) view;

			int ari = history.getAri(position);
			SpannableStringBuilder sb = new SpannableStringBuilder();
			sb.append(S.activeVersion.reference(ari));
			sb.append("  ");
			int sb_len = sb.length();
			sb.append(formatTimestamp(history.getTimestamp(position)));
			sb.setSpan(new ForegroundColorSpan(0xff666666), sb_len, sb.length(), 0);
			sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);

			textView.setText(sb);
		}

		private CharSequence formatTimestamp(final long timestamp) {
			{
				long now = System.currentTimeMillis();
				long delta = now - timestamp;
				if (delta <= 200000) {
					return getString(R.string.recentverses_just_now);
				} else if (delta <= 3600000) {
					return getString(R.string.recentverses_min_plural_ago, Math.round(delta / 60000.0));
				}
			}

			{
				Calendar now = GregorianCalendar.getInstance();
				Calendar that = GregorianCalendar.getInstance();
				that.setTimeInMillis(timestamp);
				if (now.get(Calendar.YEAR) == that.get(Calendar.YEAR)) {
					if (now.get(Calendar.DAY_OF_YEAR) == that.get(Calendar.DAY_OF_YEAR)) {
						return getString(R.string.recentverses_today_time, timeFormat.format(that.getTime()));
					} else if (now.get(Calendar.DAY_OF_YEAR) == that.get(Calendar.DAY_OF_YEAR) + 1) {
						return getString(R.string.recentverses_yesterday_time, timeFormat.format(that.getTime()));
					}
				}

				return mediumDateFormat.format(that.getTime());
			}
		}

		@Override
		public int getCount() {
			return history.getSize();
		}
	};

	public void buildMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.activity_isi, menu);
	}
	
	@Override public boolean onCreateOptionsMenu(Menu menu) {
		buildMenu(menu);
		return true;
	}
	
	@Override public boolean onPrepareOptionsMenu(Menu menu) {
		if (menu != null) {
			buildMenu(menu);
		}
		
		return true;
	}
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		// Pass the event to ActionBarDrawerToggle, if it returns true, then it has handled the app icon touch event
		if (drawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		switch (item.getItemId()) {
		case R.id.menuSearch:
			menuSearch_click();
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}

	@TargetApi(19)
	void setFullScreen(boolean yes) {
		if (yes) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			getActionBar().hide();
			if (Build.VERSION.SDK_INT >= 19) {
				root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
			}
			fullScreen = true;
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			getActionBar().show();
			if (Build.VERSION.SDK_INT >= 19) {
				root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
			}
			fullScreen = false;
		}

		if (textAppearancePanel != null) {
			textAppearancePanel.setFullScreen(yes);
		}
	}

	void setShowTextAppearancePanel(boolean yes) {
		if (yes) {
			if (textAppearancePanel == null) { // not showing yet
				textAppearancePanel = new TextAppearancePanel(this, LayoutInflater.from(new ContextThemeWrapper(this, android.R.style.Theme_Holo_Light)), overlayContainer, new TextAppearancePanel.Listener() {
					@Override public void onValueChanged(TextAppearancePanel.ValueGet valueGet) {
						S.calculateAppliedValuesBasedOnPreferences();
						applyPreferences(false);

						final boolean fullScreen = valueGet.fullScreenChecked();
						final boolean nightMode = valueGet.nightModeChecked();

						setFullScreen(fullScreen);
						setNightMode(nightMode);

						final LeftDrawer.Text.Handle handle = leftDrawer.getHandle();
						handle.setFullScreen(fullScreen);
						handle.setNightMode(nightMode);
					}

					@Override
					public void onCloseButtonClick() {
						textAppearancePanel.hide();
						textAppearancePanel = null;
					}
				}, REQCODE_textAppearanceGetFonts, REQCODE_textAppearanceCustomColors);
				textAppearancePanel.setFullScreen(fullScreen);
				textAppearancePanel.show();
			}
		} else {
			if (textAppearancePanel != null) {
				textAppearancePanel.hide();
				textAppearancePanel = null;
			}
		}
	}

	void setNightMode(boolean yes) {
		final boolean previousValue = Preferences.getBoolean(Prefkey.is_night_mode, false);
		if (previousValue == yes) return;

		Preferences.setBoolean(Prefkey.is_night_mode, yes);

		S.calculateAppliedValuesBasedOnPreferences();
		applyPreferences(false);

		if (textAppearancePanel != null) {
			textAppearancePanel.displayValues();
		}
	}

	void openVersionsDialog() {
		Pair<List<String>, List<MVersion>> versions = S.getAvailableVersions();
		final List<String> options = versions.first;
		final List<MVersion> data = versions.second;
		
		int selected = -1;
		if (S.activeVersionId == null) {
			selected = 0;
		} else {
			for (int i = 0; i < data.size(); i++) {
				MVersion mv = data.get(i);
				if (mv.getVersionId().equals(S.activeVersionId)) {
					selected = i;
					break;
				}
			}
		}
		
		new AlertDialog.Builder(this)
		.setSingleChoiceItems(options.toArray(new String[options.size()]), selected, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				final MVersion mv = data.get(which);
				
				loadVersion(mv, true);
				dialog.dismiss();
			}
		})
		.setPositiveButton(R.string.versi_lainnya, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				startActivity(VersionsActivity.createIntent());
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
	}

	void openSplitVersionsDialog() {
		final Pair<List<String>, List<MVersion>> versions = S.getAvailableVersions();
		final List<String> options = versions.first;
		final List<MVersion> data = versions.second;
		
		options.add(0, getString(R.string.split_version_none));
		data.add(0, null);
		
		int selected = -1;
		if (this.activeSplitVersionId == null) {
			selected = 0;
		} else {
			for (int i = 1 /* because 0 is null */; i < data.size(); i++) {
				MVersion mv = data.get(i);
				if (mv.getVersionId().equals(this.activeSplitVersionId)) {
					selected = i;
					break;
				}
			}
		}
		
		new AlertDialog.Builder(this)
		.setSingleChoiceItems(options.toArray(new String[options.size()]), selected, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				final MVersion mv = data.get(which);
				
				if (mv == null) { // closing split version
					activeSplitVersion = null;
					activeSplitVersionId = null;
					closeSplitDisplay();
				} else {
					boolean ok = loadSplitVersion(mv);
					if (ok) {
						openSplitDisplay();
						displaySplitFollowingMaster();
					} else {
						activeSplitVersion = null;
						activeSplitVersionId = null;
						closeSplitDisplay();
					}
				}
				
				dialog.dismiss();
			}
		})
		.setPositiveButton(R.string.versi_lainnya, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				startActivity(VersionsActivity.createIntent());
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
	}

	void openSplitDisplay() {
		if (splitHandle.getVisibility() == View.VISIBLE) {
			return; // it's already split, no need to do anything
		}

		// do it on after the layout pass
		overlayContainer.requestLayout();
		overlayContainer.post(new Runnable() {
			@Override
			public void run() {
				splitHandle.setVisibility(View.VISIBLE);
				int splitHandleHeight = getResources().getDimensionPixelSize(R.dimen.split_handle_height);
				int totalHeight = splitRoot.getHeight();
				int masterHeight = totalHeight / 2 - splitHandleHeight / 2;

				// divide by 2 the screen space
				ViewGroup.LayoutParams lp = lsText.getLayoutParams();
				lp.height = masterHeight;
				lsText.setLayoutParams(lp);

				// no need to set height, because it has been set to match_parent, so it takes
				// the remaining space.
				lsSplit1.setVisibility(View.VISIBLE);
			}
		});

		bVersion.setVisibility(View.GONE);
		leftDrawer.getHandle().setSplitVersion(true);
	}

	void closeSplitDisplay() {
		if (splitHandle.getVisibility() == View.GONE) {
			return; // it's already not split, no need to do anything
		}

		splitHandle.setVisibility(View.GONE);
		lsSplit1.setVisibility(View.GONE);
		ViewGroup.LayoutParams lp = lsText.getLayoutParams();
		lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
		lsText.setLayoutParams(lp);

		bVersion.setVisibility(View.VISIBLE);
		leftDrawer.getHandle().setSplitVersion(false);
	}

	private void menuSearch_click() {
		startActivity(Search2Activity.createIntent(this.activeBook.bookId));
	}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQCODE_goto) {
			if (resultCode == RESULT_OK) {
				GotoActivity.Result result = GotoActivity.obtainResult(data);
				if (result != null) {
					// change book
					Book book = S.activeVersion.getBook(result.bookId);
					if (book != null) {
						this.activeBook = book;
					} else { // no book, just chapter and verse.
						result.bookId = this.activeBook.bookId;
					}

					int ari_cv = display(result.chapter_1, result.verse_1);
					history.add(Ari.encode(result.bookId, ari_cv));
				}
			}
		} else if (requestCode == REQCODE_share) {
			if (resultCode == RESULT_OK) {
				ShareActivity.Result result = ShareActivity.obtainResult(data);
				if (result != null && result.chosenIntent != null) {
					Intent chosenIntent = result.chosenIntent;
					final String packageName = chosenIntent.getComponent().getPackageName();
					if (U.equals(packageName, "com.facebook.katana")) { //$NON-NLS-1$
						String verseUrl = chosenIntent.getStringExtra(EXTRA_verseUrl);
						if (verseUrl != null) {
							chosenIntent.putExtra(Intent.EXTRA_TEXT, verseUrl); // change text to url
						}
					} else if (U.equals(packageName, "com.whatsapp")) {
						chosenIntent.removeExtra(Intent.EXTRA_SUBJECT);
					}
					startActivity(chosenIntent);
				}
			}
		} else if (requestCode == REQCODE_textAppearanceGetFonts) {
			if (textAppearancePanel != null) textAppearancePanel.onActivityResult(requestCode, resultCode, data);
		} else if (requestCode == REQCODE_textAppearanceCustomColors) {
			if (textAppearancePanel != null) textAppearancePanel.onActivityResult(requestCode, resultCode, data);

			// MUST reload preferences
			S.calculateAppliedValuesBasedOnPreferences();

			applyPreferences(true);
		}
	}

	/**
	 * Display specified chapter and verse of the active book. By default all checked verses will be unchecked.
	 * @return Ari that contains only chapter and verse. Book always set to 0.
	 */
	int display(int chapter_1, int verse_1) {
		return display(chapter_1, verse_1, true);
	}

	/**
	 * Display specified chapter and verse of the active book.
	 * @param uncheckAllVerses whether we want to always make all verses unchecked after this operation.
	 * @return Ari that contains only chapter and verse. Book always set to 0.
	 */
	int display(int chapter_1, int verse_1, boolean uncheckAllVerses) {
		int current_chapter_1 = this.chapter_1;
		
		if (chapter_1 < 1) chapter_1 = 1;
		if (chapter_1 > this.activeBook.chapter_count) chapter_1 = this.activeBook.chapter_count;
		
		if (verse_1 < 1) verse_1 = 1;
		if (verse_1 > this.activeBook.verse_counts[chapter_1 - 1]) verse_1 = this.activeBook.verse_counts[chapter_1 - 1];
		
		{ // main
			this.uncheckVersesWhenActionModeDestroyed = false;
			try {
				boolean ok = loadChapterToVersesView(lsText, S.activeVersion, this.activeBook, chapter_1, current_chapter_1, uncheckAllVerses);
				if (!ok) return 0;
			} finally {
				this.uncheckVersesWhenActionModeDestroyed = true;
			}

			// tell activity
			this.chapter_1 = chapter_1;

			lsText.scrollToVerse(verse_1);
		}
		
		displaySplitFollowingMaster(verse_1);
		
		// set goto button text
		final String reference = this.activeBook.reference(chapter_1);
		if (Preferences.getBoolean(Prefkey.history_button_understood, false) || history.getSize() == 0) {
			bGoto.setText(reference);
		} else {
			// TODO show something to indicate user can long press on goto button
			bGoto.setText(reference);
		}
		
		return Ari.encode(0, chapter_1, verse_1);
	}

	void displaySplitFollowingMaster() {
		displaySplitFollowingMaster(lsText.getVerseBasedOnScroll());
	}

	private void displaySplitFollowingMaster(int verse_1) {
		if (activeSplitVersion != null) { // split1
			final Book splitBook = activeSplitVersion.getBook(this.activeBook.bookId);
			if (splitBook == null) {
				tSplitEmpty.setText(getString(R.string.split_version_cant_display_verse, this.activeBook.reference(this.chapter_1), activeSplitVersion.getLongName()));
				tSplitEmpty.setTextColor(S.applied.fontColor);
				lsSplit1.setDataEmpty();
			} else {
				this.uncheckVersesWhenActionModeDestroyed = false;
				try {
					loadChapterToVersesView(lsSplit1, activeSplitVersion, splitBook, this.chapter_1, this.chapter_1, true);
				} finally {
					this.uncheckVersesWhenActionModeDestroyed = true;
				}
				lsSplit1.scrollToVerse(verse_1);
			}
		}
	}

	static boolean loadChapterToVersesView(VersesView versesView, Version version, Book book, int chapter_1, int current_chapter_1, boolean uncheckAllVerses) {
		SingleChapterVerses verses = version.loadChapterText(book, chapter_1);
		if (verses == null) {
			return false;
		}
		
		//# max is set to 30 (one chapter has max of 30 blocks. Already almost impossible)
		int max = 30;
		int[] pericope_aris = new int[max];
		PericopeBlock[] pericope_blocks = new PericopeBlock[max];
		int nblock = version.loadPericope(book.bookId, chapter_1, pericope_aris, pericope_blocks, max);
		
		boolean retainSelectedVerses = (!uncheckAllVerses && chapter_1 == current_chapter_1);
		versesView.setDataWithRetainSelectedVerses(retainSelectedVerses, book, chapter_1, pericope_aris, pericope_blocks, nblock, verses);

		return true;
	}

	@Override public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (press(keyCode)) return true;
		return super.onKeyDown(keyCode, event);
	}
	
	@Override public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		if (press(keyCode)) return true;
		return super.onKeyMultiple(keyCode, repeatCount, event);
	}
	
	@Override public boolean onKeyUp(int keyCode, KeyEvent event) {
		final String volumeButtonsForNavigation = Preferences.getString(getString(R.string.pref_volumeButtonNavigation_key), getString(R.string.pref_volumeButtonNavigation_default));
		if (! U.equals(volumeButtonsForNavigation, "default")) { // consume here
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return true;
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) return true;
		}
		return super.onKeyUp(keyCode, event);
	}
	
	void bLeft_click() {
		Book currentBook = this.activeBook;
		if (chapter_1 == 1) {
			// we are in the beginning of the book, so go to prev book
			int tryBookId = currentBook.bookId - 1;
			while (tryBookId >= 0) {
				Book newBook = S.activeVersion.getBook(tryBookId);
				if (newBook != null) {
					this.activeBook = newBook;
					int newChapter_1 = newBook.chapter_count; // to the last chapter
					display(newChapter_1, 1);
					break;
				}
				tryBookId--;
			}
			// whileelse: now is already Genesis 1. No need to do anything
		} else {
			int newChapter = chapter_1 - 1;
			display(newChapter, 1);
		}
	}
	
	void bRight_click() {
		Book currentBook = this.activeBook;
		if (chapter_1 >= currentBook.chapter_count) {
			int maxBookId = S.activeVersion.getMaxBookIdPlusOne();
			int tryBookId = currentBook.bookId + 1;
			while (tryBookId < maxBookId) {
				Book newBook = S.activeVersion.getBook(tryBookId);
				if (newBook != null) {
					this.activeBook = newBook;
					display(1, 1);
					break;
				}
				tryBookId++;
			}
			// whileelse: now is already Revelation (or the last book) at the last chapter. No need to do anything
		} else {
			int newChapter = chapter_1 + 1;
			display(newChapter, 1);
		}
	}

	void bVersion_click() {
		openVersionsDialog();
	}
	
	@Override public boolean onSearchRequested() {
		menuSearch_click();
		
		return true;
	}

	@Override public void onVerseSelected(XrefDialog dialog, int arif_source, int ari_target) {
		final int ari_source = arif_source >>> 8;

		dialog.dismiss();
		jumpToAri(ari_target);

		// add both xref source and target, so user can go back to source easily
		history.add(ari_source);
		history.add(ari_target);
	}

	/**
	 * If verse_1_ranges is null, verses will be ignored.
	 */
	public static String createVerseUrl(final String versionShortName, Book book, int chapter_1, String verse_1_ranges) {
		final AppConfig c = AppConfig.get();
		String format = c.shareUrlFormat;

		String osisBookName = OsisBookNames.getBookName(book.bookId);
		if (osisBookName == null) {
			osisBookName = book.shortName; // fall back
		}
		format = format.replace("{book.osis}", osisBookName);
		format = format.replace("{chapter}", String.valueOf(chapter_1));
		format = format.replace("{verses}", verse_1_ranges == null? "": verse_1_ranges);

		String versionShortName2;
		if (versionShortName == null) {
			versionShortName2 = "";
		} else if ("DRA".equals(versionShortName)) {
			versionShortName2 = "DOUAYRHEIMS";
		} else {
			versionShortName2 = versionShortName;
		}

		format = format.replace("{version.shortName}", versionShortName2);

		return format;
	}
	
	VersesView.AttributeListener attributeListener = new VersesView.AttributeListener() {
		@Override
		public void onBookmarkAttributeClick(final Book book, final int chapter_1, final int verse_1) {
			final int ari = Ari.encode(book.bookId, chapter_1, verse_1);
			String reference = book.reference(chapter_1, verse_1);
			TypeBookmarkDialog dialog = new TypeBookmarkDialog(IsiActivity.this, reference, ari);
			dialog.setListener(new TypeBookmarkDialog.Listener() {
				@Override public void onOk() {
					lsText.reloadAttributeMap();

					if (activeSplitVersion != null) {
						lsSplit1.reloadAttributeMap();
					}
				}
			});
			dialog.show();
		}

		@Override
		public void onNoteAttributeClick(final Book book, final int chapter_1, final int verse_1) {
			TypeNoteDialog dialog = new TypeNoteDialog(IsiActivity.this, book, chapter_1, verse_1, new TypeNoteDialog.Listener() {
				@Override public void onDone() {
					lsText.reloadAttributeMap();

					if (activeSplitVersion != null) {
						lsSplit1.reloadAttributeMap();
					}
				}
			});
			dialog.show();
		}

		@Override
		public void onProgressMarkAttributeClick(final int preset_id) {
			final ProgressMark progressMark = S.getDb().getProgressMarkByPresetId(preset_id);

			ProgressMarkDialog.showRenameDialog(IsiActivity.this, progressMark, new ProgressMarkDialog.Listener() {
				@Override
				public void onOked() {
					lsText.uncheckAllVerses(true);
					reloadBothAttributeMaps();
				}

				@Override
				public void onDeleted() {
					lsText.uncheckAllVerses(true);
					reloadBothAttributeMaps();
				}
			});
		}
	};

	class VerseInlineLinkSpanFactory implements VerseInlineLinkSpan.Factory {
		private final Object source;

		VerseInlineLinkSpanFactory(final Object source) {
			this.source = source;
		}

		@Override
		public VerseInlineLinkSpan create(final VerseInlineLinkSpan.Type type, final int arif) {
			return new VerseInlineLinkSpan(type, arif, source) {
				@Override
				public void onClick(final Type type, final int arif, final Object source) {
					if (type == Type.xref) {
						final XrefDialog dialog = XrefDialog.newInstance(arif);

						// TODO setSourceVersion here is not restored when dialog is restored
						if (source == lsText) { // use activeVersion
							dialog.setSourceVersion(S.activeVersion);
						} else if (source == lsSplit1) { // use activeSplitVersion
							dialog.setSourceVersion(activeSplitVersion);
						}

						FragmentManager fm = getSupportFragmentManager();
						dialog.show(fm, XrefDialog.class.getSimpleName());
					} else if (type == Type.footnote) {
						FootnoteEntry fe = null;
						if (source == lsText) { // use activeVersion
							fe = S.activeVersion.getFootnoteEntry(arif);
						} else if (source == lsSplit1) { // use activeSplitVersion
							fe = activeSplitVersion.getFootnoteEntry(arif);
						}

						if (fe != null) {
							final SpannableStringBuilder footnoteText = new SpannableStringBuilder();
							VerseRenderer.appendSuperscriptNumber(footnoteText, arif & 0xff);
							footnoteText.append(" ");

							new AlertDialog.Builder(IsiActivity.this)
							.setMessage(FormattedTextRenderer.render(fe.content, footnoteText))
							.setPositiveButton("OK", null)
							.show();
						} else {
							new AlertDialog.Builder(IsiActivity.this)
							.setMessage(String.format(Locale.US, "Error: footnote arif 0x%08x couldn't be loaded", arif))
							.setPositiveButton("OK", null)
							.show();
						}
					} else {
						new AlertDialog.Builder(IsiActivity.this)
						.setMessage("Error: Unknown inline link type: " + type)
						.setPositiveButton("OK", null)
						.show();
					}
				}
			};
		}
	}

	VersesView.SelectedVersesListener lsText_selectedVerses = new VersesView.SelectedVersesListener() {
		@Override public void onSomeVersesSelected(VersesView v) {
			if (activeSplitVersion != null) {
				// synchronize the selection with the split view
				IntArrayList selectedVerses = v.getSelectedVerses_1();
				lsSplit1.checkVerses(selectedVerses, false);
			}
			
			if (actionMode == null) {
				actionMode = startActionMode(actionMode_callback);
			}
			
			if (actionMode != null) {
				actionMode.invalidate();
			}
		}
		
		@Override public void onNoVersesSelected(VersesView v) {
			if (activeSplitVersion != null) {
				// synchronize the selection with the split view
				lsSplit1.uncheckAllVerses(false);
			}

			if (actionMode != null) {
				actionMode.finish();
				actionMode = null;
			}
		}
		
		@Override public void onVerseSingleClick(VersesView v, int verse_1) {}
	};
	
	VersesView.SelectedVersesListener lsSplit1_selectedVerses = new VersesView.SelectedVersesListener() {
		@Override public void onSomeVersesSelected(VersesView v) {
			// synchronize the selection with the main view
			IntArrayList selectedVerses = v.getSelectedVerses_1();
			lsText.checkVerses(selectedVerses, true);
		}

		@Override public void onNoVersesSelected(VersesView v) {
			lsText.uncheckAllVerses(true);
		}

		@Override public void onVerseSingleClick(VersesView v, int verse_1) {}
	};

	VersesView.OnVerseScrollListener lsText_verseScroll = new VersesView.OnVerseScrollListener() {
		@Override public void onVerseScroll(VersesView v, boolean isPericope, int verse_1, float prop) {

			if (!isPericope && activeSplitVersion != null) {
				lsSplit1.scrollToVerse(verse_1, prop);
			}
		}

		@Override public void onScrollToTop(VersesView v) {
			if (activeSplitVersion != null) {
				lsSplit1.scrollToTop();
			}
		}
	};
	
	VersesView.OnVerseScrollListener lsSplit1_verseScroll = new VersesView.OnVerseScrollListener() {
		@Override public void onVerseScroll(VersesView v, boolean isPericope, int verse_1, float prop) {
			if (!isPericope) {
				lsText.scrollToVerse(verse_1, prop);
			}
		}

		@Override public void onScrollToTop(VersesView v) {
			lsText.scrollToTop();
		}
	};
	
	ActionMode.Callback actionMode_callback = new ActionMode.Callback() {
		@Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			mode.getMenuInflater().inflate(R.menu.context_isi, menu);
			
			/* The following "esvsbasal" thing is a personal thing by yuku that doesn't matter to anyone else.
			 * Please ignore it and leave it intact. */
			if (hasEsvsbAsal == null) {
				try {
					getPackageManager().getApplicationInfo("yuku.esvsbasal", 0); //$NON-NLS-1$
					hasEsvsbAsal = true;
				} catch (NameNotFoundException e) {
					hasEsvsbAsal = false;
				}
			}
			
			if (hasEsvsbAsal) {
				MenuItem esvsb = menu.findItem(R.id.menuEsvsb);
				if (esvsb != null) esvsb.setVisible(true);
			}

			List<ProgressMark> progressMarks = S.getDb().listAllProgressMarks();
			MenuItem item1 = menu.findItem(R.id.menuProgress1);
			setProgressMarkMenuItemTitle(progressMarks, item1, 0);
			MenuItem item2 = menu.findItem(R.id.menuProgress2);
			setProgressMarkMenuItemTitle(progressMarks, item2, 1);
			MenuItem item3 = menu.findItem(R.id.menuProgress3);
			setProgressMarkMenuItemTitle(progressMarks, item3, 2);
			MenuItem item4 = menu.findItem(R.id.menuProgress4);
			setProgressMarkMenuItemTitle(progressMarks, item4, 3);
			MenuItem item5 = menu.findItem(R.id.menuProgress5);
			setProgressMarkMenuItemTitle(progressMarks, item5, 4);

			return true;
		}

		@Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			MenuItem menuAddBookmark = menu.findItem(R.id.menuAddBookmark);
			MenuItem menuAddNote = menu.findItem(R.id.menuAddNote);
			MenuItem menuProgressMark = menu.findItem(R.id.menuProgressMark);
			MenuItem menuCompare = menu.findItem(R.id.menuCompare);

			IntArrayList selected = lsText.getSelectedVerses_1();
			boolean single = selected.size() == 1;
			
			boolean changed1 = menuAddBookmark.isVisible() != single;
			boolean changed2 = menuAddNote.isVisible() != single;
			boolean changed3 = menuProgressMark.isVisible() != single;
			boolean changed4 = menuCompare.isVisible() != single;
			boolean changed = changed1 || changed2 || changed3 || changed4;
			
			if (changed) {
				menuAddBookmark.setVisible(single);
				menuAddNote.setVisible(single);
				menuProgressMark.setVisible(single);
				menuCompare.setVisible(single);
			}

			return changed;
		}

		@Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			IntArrayList selected = lsText.getSelectedVerses_1();

			if (selected.size() == 0) return true;

			CharSequence reference = referenceFromSelectedVerses(selected, activeBook);

			// the main verse (0 if not exist), which is only when only one verse is selected
			int mainVerse_1 = 0;
			if (selected.size() == 1) {
				mainVerse_1 = selected.get(0);
			}
			
			int itemId = item.getItemId();
			switch (itemId) {
			case R.id.menuCopy: { // copy, can be multiple
				String textToCopy = prepareTextForCopyShare(selected, reference, false);
				if (activeSplitVersion != null) {
					textToCopy = appendSplitTextForCopyShare(textToCopy);
				}
				
				U.copyToClipboard(textToCopy);
				lsText.uncheckAllVerses(true);
				
				Toast.makeText(App.context, getString(R.string.alamat_sudah_disalin, reference), Toast.LENGTH_SHORT).show();
				mode.finish();
			} return true;
			case R.id.menuShare: {
				String textToShare = prepareTextForCopyShare(selected, reference, false);
				if (activeSplitVersion != null) {
					textToShare = appendSplitTextForCopyShare(textToShare);
				}

				String verseUrl;
				if (selected.size() == 1) {
					verseUrl = createVerseUrl(S.activeVersion.getShortName(), IsiActivity.this.activeBook, IsiActivity.this.chapter_1, String.valueOf(selected.get(0)));
				} else {
					StringBuilder sb = new StringBuilder();
					Book.writeVerseRange(selected, sb);
					verseUrl = createVerseUrl(S.activeVersion.getShortName(), IsiActivity.this.activeBook, IsiActivity.this.chapter_1, sb.toString()); // use verse range
				}
				
				Intent intent = ShareCompat.IntentBuilder.from(IsiActivity.this)
				.setType("text/plain") //$NON-NLS-1$
				.setSubject(reference.toString())
				.setText(textToShare.toString())
				.getIntent();
				intent.putExtra(EXTRA_verseUrl, verseUrl);
				startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.bagikan_alamat, reference)), REQCODE_share);

				lsText.uncheckAllVerses(true);
				mode.finish();
			} return true;
			case R.id.menuCompare: {
				final int ari = Ari.encode(IsiActivity.this.activeBook.bookId, IsiActivity.this.chapter_1, mainVerse_1);
				VersesDialog.newCompareInstance(ari).show(getSupportFragmentManager(), "compare_dialog");
			} return true;
			case R.id.menuVersions: {
				openVersionsDialog();
			} return true;
			case R.id.menuAddBookmark: {
				if (mainVerse_1 == 0) {
					// no main verse, scroll to show the relevant one!
					mainVerse_1 = selected.get(0);
					
					lsText.scrollToShowVerse(mainVerse_1);
				}
				
				final int ari = Ari.encode(IsiActivity.this.activeBook.bookId, IsiActivity.this.chapter_1, mainVerse_1);
				
				TypeBookmarkDialog dialog = new TypeBookmarkDialog(IsiActivity.this, IsiActivity.this.activeBook.reference(IsiActivity.this.chapter_1, mainVerse_1), ari);
				dialog.setListener(new TypeBookmarkDialog.Listener() {
					@Override public void onOk() {
						lsText.uncheckAllVerses(true);
						reloadBothAttributeMaps();
					}
				});
				dialog.show();

				mode.finish();
			} return true;
			case R.id.menuAddNote: {
				if (mainVerse_1 == 0) {
					// no main verse, scroll to show the relevant one!
					mainVerse_1 = selected.get(0);
					
					lsText.scrollToShowVerse(mainVerse_1);
				}
				
				TypeNoteDialog dialog = new TypeNoteDialog(IsiActivity.this, IsiActivity.this.activeBook, IsiActivity.this.chapter_1, mainVerse_1, new TypeNoteDialog.Listener() {
					@Override public void onDone() {
						lsText.uncheckAllVerses(true);
						reloadBothAttributeMaps();
					}
				});
				dialog.show();
				mode.finish();
			} return true;
			case R.id.menuAddHighlight: {
				final int ariKp = Ari.encode(IsiActivity.this.activeBook.bookId, IsiActivity.this.chapter_1, 0);
				int colorRgb = S.getDb().getHighlightColorRgb(ariKp, selected);
				
				new TypeHighlightDialog(IsiActivity.this, ariKp, selected, new TypeHighlightDialog.Listener() {
					@Override public void onOk(int colorRgb) {
						lsText.uncheckAllVerses(true);
						reloadBothAttributeMaps();
					}
				}, colorRgb, reference).show();
				mode.finish();
			} return true;
			case R.id.menuEsvsb: {
				final int ari = Ari.encode(IsiActivity.this.activeBook.bookId, IsiActivity.this.chapter_1, mainVerse_1);

				try {
					Intent intent = new Intent("yuku.esvsbasal.action.GOTO"); //$NON-NLS-1$
					intent.putExtra("ari", ari); //$NON-NLS-1$
					startActivity(intent);
				} catch (Exception e) {
					Log.e(TAG, "ESVSB starting", e); //$NON-NLS-1$
				}
			} return true;
			case R.id.menuProgress1: {
				updateProgressMark(mainVerse_1, 0);
			} return true;
			case R.id.menuProgress2: {
				updateProgressMark(mainVerse_1, 1);
			} return true;
			case R.id.menuProgress3: {
				updateProgressMark(mainVerse_1, 2);
			} return true;
			case R.id.menuProgress4: {
				updateProgressMark(mainVerse_1, 3);
			} return true;
			case R.id.menuProgress5: {
				updateProgressMark(mainVerse_1, 4);
			} return true;

			}
			return false;
		}

		String appendSplitTextForCopyShare(String textToCopy) {
			final Book splitBook = activeSplitVersion.getBook(activeBook.bookId);
			if (splitBook != null) {
				IntArrayList selectedSplit = lsSplit1.getSelectedVerses_1();
				CharSequence referenceSplit = referenceFromSelectedVerses(selectedSplit, splitBook);
				textToCopy += "\n\n" + prepareTextForCopyShare(selectedSplit, referenceSplit, true);
			}
			return textToCopy;
		}

		@Override public void onDestroyActionMode(ActionMode mode) {
			actionMode = null;

			// FIXME even with this guard, verses are still unchecked when switching version while both Fullscreen and Split is active.
			// This guard only fixes unchecking of verses when in fullscreen mode.
			if (uncheckVersesWhenActionModeDestroyed) {
				lsText.uncheckAllVerses(true);
			}
		}

		private void setProgressMarkMenuItemTitle(final List<ProgressMark> progressMarks, final MenuItem item, int position) {
			String title = (progressMarks.get(position).ari == 0 || TextUtils.isEmpty(progressMarks.get(position).caption)) ? getString(AttributeView.getDefaultProgressMarkStringResource(position)): progressMarks.get(position).caption;

			item.setTitle(getString(R.string.pm_menu_save_progress, title));
		}
	};

	void reloadBothAttributeMaps() {
		lsText.reloadAttributeMap();

		if (activeSplitVersion != null) {
			lsSplit1.reloadAttributeMap();
		}
	}

	private void updateProgressMark(final int mainVerse_1, final int position) {
		final int ari = Ari.encode(this.activeBook.bookId, this.chapter_1, mainVerse_1);
		final List<ProgressMark> progressMarks = S.getDb().listAllProgressMarks();
		final ProgressMark progressMark = progressMarks.get(position);

		if (progressMark.caption == null) {
				ProgressMarkDialog.showRenameDialog(this, progressMark, new ProgressMarkDialog.Listener() {
				@Override
				public void onOked() {
					saveProgress(progressMark, ari, position);
				}

				@Override
				public void onDeleted() {
					lsText.uncheckAllVerses(true);
					reloadBothAttributeMaps();
				}
			});
		} else {
			saveProgress(progressMark, ari, position);
		}
	}

	public void saveProgress(final ProgressMark progressMark, final int ari, final int position) {
		progressMark.ari = ari;
		progressMark.modifyTime = new Date();
		S.getDb().updateProgressMark(progressMark);
		AttributeView.startAnimationForProgressMark(position);
		lsText.uncheckAllVerses(true);
		reloadBothAttributeMaps();
	}

	SplitHandleButton.SplitHandleButtonListener splitHandleButton_listener = new SplitHandleButton.SplitHandleButtonListener() {
		int aboveH;
		int handleH;
		int rootH;
		
		@Override public void onHandleDragStart() {
			aboveH = lsText.getHeight();
			handleH = splitHandle.getHeight();
			rootH = splitRoot.getHeight();
		}
		
		@Override public void onHandleDragMove(float dySinceLast, float dySinceStart) {
			int newH = (int) (aboveH + dySinceStart);
			int maxH = rootH - handleH;
			ViewGroup.LayoutParams lp = lsText.getLayoutParams();
			lp.height = newH < 0? 0: newH > maxH? maxH: newH;
			lsText.setLayoutParams(lp);
		}
		
		@Override public void onHandleDragStop() {
		}
	};

	LabeledSplitHandleButton.OnLabelPressed splitHandleButton_labelPressed = new LabeledSplitHandleButton.OnLabelPressed() {
		@Override
		public void onLabelPressed(final int which) {
			if (which == 1) { // left
				openVersionsDialog();
			} else if (which == 2) { // right
				openSplitVersionsDialog();
			}
		}
	};

	@Override
	public void bMarkers_click() {
		startActivity(BookmarkActivity.createIntent());
	}

	@Override
	public void bDisplay_click() {
		setShowTextAppearancePanel(textAppearancePanel == null);
	}

	@Override
	public void cFullScreen_checkedChange(final boolean isChecked) {
		setFullScreen(isChecked);
		if (textAppearancePanel != null) {
			textAppearancePanel.setFullScreen(isChecked);
		}
	}

	@Override
	public void cNightMode_checkedChange(final boolean isChecked) {
		setNightMode(isChecked);
		if (textAppearancePanel != null) {
			textAppearancePanel.setNightMode(isChecked);
		}
	}

	@Override
	public void cSplitVersion_checkedChange(final Switch cSplitVersion, final boolean isChecked) {
		if (isChecked) {
			cSplitVersion.setChecked(false); // do it later, at the version chooser dialog
			openSplitVersionsDialog();
		} else {
			activeSplitVersion = null;
			activeSplitVersionId = null;
			closeSplitDisplay();
		}
	}

	@Override
	public void bProgress_click(final int preset_id) {
		final ProgressMark progressMark = S.getDb().getProgressMarkByPresetId(preset_id);
		final int ari = progressMark.ari;

		if (ari != 0) {
			jumpToAri(ari);
			history.add(ari);
		} else {
			new AlertDialog.Builder(this)
				.setMessage(R.string.pm_activate_tutorial)
				.setPositiveButton(R.string.ok, null)
				.show();
		}
	}
}
