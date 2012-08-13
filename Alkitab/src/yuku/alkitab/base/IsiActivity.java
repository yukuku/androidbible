package yuku.alkitab.base;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Typeface;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import yuku.alkitab.R;
import yuku.alkitab.base.IsiActivity.FakeContextMenu.Item;
import yuku.alkitab.base.ac.AboutActivity;
import yuku.alkitab.base.ac.BantuanActivity;
import yuku.alkitab.base.ac.BukmakActivity;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.ac.EdisiActivity;
import yuku.alkitab.base.ac.EdisiActivity.MEdisiInternal;
import yuku.alkitab.base.ac.EdisiActivity.MEdisiPreset;
import yuku.alkitab.base.ac.EdisiActivity.MEdisiYes;
import yuku.alkitab.base.ac.EdisiActivity.MVersion;
import yuku.alkitab.base.ac.MenujuActivity;
import yuku.alkitab.base.ac.PengaturanActivity;
import yuku.alkitab.base.ac.Search2Activity;
import yuku.alkitab.base.ac.ShareActivity;
import yuku.alkitab.base.ac.SongViewActivity;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.compat.Api8;
import yuku.alkitab.base.config.BuildConfig;
import yuku.alkitab.base.config.D;
import yuku.alkitab.base.dialog.JenisStabiloDialog;
import yuku.alkitab.base.dialog.TypeBookmarkDialog;
import yuku.alkitab.base.dialog.TypeNoteDialog;
import yuku.alkitab.base.dialog.TypeNoteDialog.RefreshCallback;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Blok;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.storage.Db.Bukmak2;
import yuku.alkitab.base.storage.Preferences;
import yuku.alkitab.base.util.History;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.Jumper;
import yuku.alkitab.base.util.Search2Engine.Query;
import yuku.alkitab.base.widget.CallbackSpan;
import yuku.alkitab.base.widget.VerseAdapter;

public class IsiActivity extends BaseActivity {
	public static final String TAG = IsiActivity.class.getSimpleName();
	
	// The followings are for instant_pref
	private static final String PREFKEY_lastBook = "kitabTerakhir"; //$NON-NLS-1$
	private static final String PREFKEY_lastChapter = "pasalTerakhir"; //$NON-NLS-1$
	private static final String PREFKEY_lastVerse = "ayatTerakhir"; //$NON-NLS-1$
	private static final String PREFKEY_lastVersion = "edisiTerakhir"; //$NON-NLS-1$
	private static final String PREFKEY_devotion_name = "renungan_nama"; //$NON-NLS-1$

	public static final int RESULT_pindahCara = RESULT_FIRST_USER + 1;

	private static final int REQCODE_goto = 1;
	private static final int REQCODE_bookmark = 2;
	private static final int REQCODE_devotion = 3;
	private static final int REQCODE_settings = 4;
	private static final int REQCODE_version = 5;
	private static final int REQCODE_search = 6;
	private static final int REQCODE_share = 7;

	private static final String EXTRA_verseUrl = "urlAyat"; //$NON-NLS-1$

	ListView lsText;
	Button bGoto;
	ImageButton bLeft;
	ImageButton bRight;
	View titleContainer;
	TextView lTitle;
	View bContextMenu;
	View root;
	
	int chapter_1 = 0;
	SharedPreferences instant_pref;
	
	VerseAdapter verseAdapter_;
	History history;
	NfcAdapter nfcAdapter;

	//# state storage for search2
	Query search2_query = null;
	IntArrayList search2_results = null;
	int search2_selectedPosition = -1;
	
	// temporary states
	Animation fadeInAnimation;
	Animation fadeOutAnimation;
	boolean showingContextMenuButton = false;
	Boolean hasEsvsbAsal;
	
	CallbackSpan.OnClickListener parallel_click = new CallbackSpan.OnClickListener() {
		@Override public void onClick(View widget, Object data) {
			int ari = jumpTo((String)data);
			if (ari != 0) {
				history.add(ari);
			}
		}
	};

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		U.enableTitleBarOnlyForHolo(this);
		
		S.prepareBook();
		S.calculateAppliedValuesBasedOnPreferences();
		
		setContentView(R.layout.activity_isi);
		
		lsText = U.getView(this, R.id.lsIsi);
		bGoto = U.getView(this, R.id.bTuju);
		bLeft = U.getView(this, R.id.bKiri);
		bRight = U.getView(this, R.id.bKanan);
		titleContainer = U.getView(this, R.id.tempatJudul);
		lTitle = U.getView(this, R.id.lJudul);
		bContextMenu = U.getView(this, R.id.bContext);
		root = U.getView(this, R.id.root);
		
		applyPreferences(false);

		lsText.setOnItemClickListener(lsText_itemClick);
		lsText.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		
		bGoto.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { bGoto_click(); }
		});
		bGoto.setOnLongClickListener(new View.OnLongClickListener() {
			@Override public boolean onLongClick(View v) { bGoto_longClick(); return true; }
		});
		
		lTitle.setOnClickListener(new View.OnClickListener() { // pinjem bTuju
			@Override public void onClick(View v) { bGoto_click(); }
		});
		lTitle.setOnLongClickListener(new View.OnLongClickListener() { // pinjem bTuju
			@Override public boolean onLongClick(View v) { bGoto_longClick(); return true; }
		});
		
		bLeft.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { bLeft_click(); }
		});
		bRight.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { bRight_click(); }
		});
		
		bContextMenu.setOnClickListener(bContextMenu_click);
		
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
		
		// adapter
		verseAdapter_ = new VerseAdapter(this, parallel_click, new AttributeListener());
		lsText.setAdapter(verseAdapter_);
		
		// muat preferences_instan, dan atur renungan
		instant_pref = App.getPreferencesInstan();
		history = new History(instant_pref);
		{
			String devotion_name = instant_pref.getString(PREFKEY_devotion_name, null);
			if (devotion_name != null) {
				for (String nama: DevotionActivity.AVAILABLE_NAMES) {
					if (devotion_name.equals(nama)) {
						S.penampungan.devotion_name = devotion_name;
					}
				}
			}
		}
		
		// restore the last (version; book; chapter and verse).
		String lastVersion = instant_pref.getString(PREFKEY_lastVersion, null);
		int lastBook = instant_pref.getInt(PREFKEY_lastBook, 0);
		int lastChapter = instant_pref.getInt(PREFKEY_lastChapter, 0);
		int lastVerse = instant_pref.getInt(PREFKEY_lastVerse, 0);
		Log.d(TAG, "Going to the last: version=" + lastVersion + " book=" + lastBook + " chapter=" + lastBook + " verse=" + lastVerse); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		loadLastVersion(lastVersion);
		
		{ // load book
			Book book = S.activeVersion.getKitab(lastBook);
			if (book != null) {
				S.activeBook = book;
			}
		}
		
		// load chapter and verse
		display(lastChapter, lastVerse);

		if (D.EBUG) { // so we get some warning if D.EBUG is on when released
			new AlertDialog.Builder(this)
			.setMessage("D.EBUG is on!") //$NON-NLS-1$
			.show();
		}
		
		if (Build.VERSION.SDK_INT >= 14) {
			initNfcIfAvailable();
			checkAndProcessBeamIntent(getIntent());
		}
	}
	
	@TargetApi(14) private void initNfcIfAvailable() {
		nfcAdapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
		if (nfcAdapter != null) {
			nfcAdapter.setNdefPushMessageCallback(new CreateNdefMessageCallback() {
				@Override public NdefMessage createNdefMessage(NfcEvent event) {
					JSONObject obj = new JSONObject();
					try {
						obj.put("ari", Ari.encode(S.activeBook.pos, IsiActivity.this.chapter_1, IsiActivity.this.getVerseBasedOnScroll())); //$NON-NLS-1$
					} catch (JSONException e) { // won't happen
					}
					byte[] payload = obj.toString().getBytes();
					NdefRecord record = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, "application/vnd.yuku.alkitab.nfc.beam".getBytes(), new byte[0], payload); //$NON-NLS-1$
					NdefMessage msg = new NdefMessage(new NdefRecord[] { 
						record,
						NdefRecord.createApplicationRecord(getPackageName()),
					});
					return msg;
				}
			}, this);
		}
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

	@TargetApi(14) private void enableNfcForegroundDispatchIfAvailable() {
		if (nfcAdapter != null) {
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
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
	
	@Override protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		
		if (Build.VERSION.SDK_INT >= 14) {
			checkAndProcessBeamIntent(intent);
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
							IsiActivity.this.jumpToAri(ari);
						}
					} catch (JSONException e) {
						Log.e(TAG, "Malformed json from nfc", e); //$NON-NLS-1$
					}
				}
			}
		}
	}

	private void loadLastVersion(String lastVersion) {
		if (lastVersion == null || MEdisiInternal.getVersionInternalId().equals(lastVersion)) {
			return; // we are now already on internal, no need to do anything!
		}
		
		BuildConfig c = BuildConfig.get(this);
		
		// coba preset dulu!
		for (MEdisiPreset preset: c.presets) { // 2. preset
			if (preset.getEdisiId().equals(lastVersion)) {
				if (preset.adaFileDatanya()) {
					loadVersion(preset);
				} else { 
					return; // this is the one that should have been chosen, but the data file is not available, so let's fallback.
				}
			}
		}
		
		// masih belum cocok, mari kita cari di daftar yes
		List<MEdisiYes> yeses = S.getDb().listAllVersions();
		for (MEdisiYes yes: yeses) {
			if (yes.getEdisiId().equals(lastVersion)) {
				if (yes.adaFileDatanya()) {
					loadVersion(yes);
				} else { 
					return; // this is the one that should have been chosen, but the data file is not available, so let's fallback.
				}
			}
		}
	}
	
	protected void loadVersion(final MVersion mv) {
		// for rollback
		Version oldActiveVersion = S.activeVersion;
		String oldActiveVersionId = S.activeVersionId;
		
		boolean success = false;
		try {
			Version version = mv.getVersion(getApplicationContext());
			
			if (version != null) {
				S.activeVersion = version;
				S.activeVersionId = mv.getEdisiId();
				S.prepareBook();
				
				Book book = S.activeVersion.getKitab(S.activeBook.pos);
				if (book != null) {
					// assign active book with the new one, no need to consider the pos
					S.activeBook = book;
				} else {
					S.activeBook = S.activeVersion.getKitabPertama(); // too bad, it was not found
				}
				
				display(chapter_1, getVerseBasedOnScroll());
			} else {
				new AlertDialog.Builder(IsiActivity.this)
				.setMessage(getString(R.string.ada_kegagalan_membuka_edisiid, mv.getEdisiId()))
				.setPositiveButton(R.string.ok, null)
				.show();
			}
			success = true;
		} catch (Throwable e) { // so we don't crash on the beginning of the app
			Log.e(TAG, "failed in loadVersion", e);  //$NON-NLS-1$
		} finally {
			if (!success) {
				S.activeVersion = oldActiveVersion;
				S.activeVersionId = oldActiveVersionId;
				S.prepareBook();
			}
		}
	}
	
	@Override protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		
		hideOrShowContextMenuButton();
	}
	
	void showContextMenuButton() {
		if (! showingContextMenuButton) {
			if (fadeInAnimation == null) {
				fadeInAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
			}
			bContextMenu.setVisibility(View.VISIBLE);
			bContextMenu.startAnimation(fadeInAnimation);
			bContextMenu.setEnabled(true);
			showingContextMenuButton = true;
		}
	}
	
	void hideContextButton() {
		if (showingContextMenuButton) {
			if (fadeOutAnimation == null) {
				fadeOutAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
			}
			fadeOutAnimation.setAnimationListener(fadeOutAnimation_animation);
			bContextMenu.startAnimation(fadeOutAnimation);
			bContextMenu.setEnabled(false);
			showingContextMenuButton = false;
		}
	}

	private AnimationListener fadeOutAnimation_animation = new AnimationListener() {
		@Override public void onAnimationStart(Animation animation) {}
		@Override public void onAnimationRepeat(Animation animation) {}
		@Override public void onAnimationEnd(Animation animation) {
			bContextMenu.setVisibility(View.INVISIBLE);
		}
	};
	
	protected boolean press(int keyCode) {
		String volumeButtonsForNavigation = Preferences.getString(R.string.pref_tombolVolumeBuatPindah_key, R.string.pref_tombolVolumeBuatPindah_default);
		if (U.equals(volumeButtonsForNavigation, "pasal" /* chapter */)) { //$NON-NLS-1$
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
		} else if (U.equals(volumeButtonsForNavigation, "ayat" /* verse */)) { //$NON-NLS-1$
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) keyCode = KeyEvent.KEYCODE_DPAD_UP;
		}

		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
			bLeft_click();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
			bRight_click();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
			int oldPos = getPositionBasedOnScroll();
			if (oldPos < verseAdapter_.getCount() - 1) {
				lsText.setSelectionFromTop(oldPos+1, lsText.getVerticalFadingEdgeLength());
			}
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
			int oldPos = getPositionBasedOnScroll();
			if (oldPos >= 1) {
				int newPos = oldPos - 1;
				while (newPos > 0) { // cek disabled, kalo iya, mundurin lagi
					if (verseAdapter_.isEnabled(newPos)) break;
					newPos--;
				}
				lsText.setSelectionFromTop(newPos, lsText.getVerticalFadingEdgeLength());
			} else {
				lsText.setSelectionFromTop(0, lsText.getVerticalFadingEdgeLength());
			}
			return true;
		}
		return false;
	}

	private synchronized void keepScreenOnWhenRequested() {
		if (Preferences.getBoolean(R.string.pref_nyalakanTerusLayar_key, R.bool.pref_nyalakanTerusLayar_default)) {
			lsText.setKeepScreenOn(true);
		}
	}

	private synchronized void turnScreenOffIfAllowedToTurnOff() {
		lsText.setKeepScreenOn(false);
	}
	
	int jumpTo(String reference) {
		if (reference.trim().length() == 0) {
			return 0;
		}
		
		Log.d(TAG, "going to jump to " + reference); //$NON-NLS-1$
		
		Jumper jumper = new Jumper();
		boolean success = jumper.parse(reference);
		if (! success) {
			Toast.makeText(this, getString(R.string.alamat_tidak_sah_alamat, reference), Toast.LENGTH_SHORT).show();
			return 0;
		}
		
		int bookPos = jumper.getKitab(S.activeVersion.getConsecutiveBooks());
		Book selected;
		if (bookPos != -1) {
			Book book = S.activeVersion.getKitab(bookPos);
			if (book != null) {
				selected = book;
			} else {
				// not avail, just fallback
				selected = S.activeBook;
			}
		} else {
			selected = S.activeBook;
		}
		
		// set book
		S.activeBook = selected;
		
		int chapter = jumper.getChapter();
		int verse = jumper.getVerse();
		int ari_cv;
		if (chapter == -1 && verse == -1) {
			ari_cv = display(1, 1);
		} else {
			ari_cv = display(chapter, verse);
		}
		
		return Ari.encode(selected.pos, ari_cv);
	}
	
	void jumpToAri(int ari) {
		if (ari == 0) return;
		
		Log.d(TAG, "will jump to ari 0x" + Integer.toHexString(ari)); //$NON-NLS-1$
		
		int bookPos = Ari.toKitab(ari);
		Book book = S.activeVersion.getKitab(bookPos);
		
		if (book != null) {
			S.activeBook = book;
		} else {
			Log.w(TAG, "bookPos=" + bookPos + " not found for ari=" + ari); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		
		display(Ari.toChapter(ari), Ari.toVerse(ari));
	}

	private OnItemClickListener lsText_itemClick = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			hideOrShowContextMenuButton();
		}
	};
	
	void hideOrShowContextMenuButton() {
		SparseBooleanArray checkedPositions = lsText.getCheckedItemPositions();
		boolean anyChecked = false;
		for (int i = 0; i < checkedPositions.size(); i++) if (checkedPositions.valueAt(i)) {
			anyChecked = true; 
			break;
		}
		
		if (anyChecked) {
			showContextMenuButton();
		} else {
			hideContextButton();
		}
	}

	private IntArrayList getSelectedVerses_1() {
		// count how many are selected
		SparseBooleanArray positions = lsText.getCheckedItemPositions();
		if (positions == null) {
			return new IntArrayList(0);
		}
		
		IntArrayList res = new IntArrayList(positions.size());
		for (int i = 0, len = positions.size(); i < len; i++) {
			if (positions.valueAt(i)) {
				int position = positions.keyAt(i);
				int verse_1 = verseAdapter_.getVerseFromPosition(position);
				if (verse_1 >= 1) res.add(verse_1);
			}
		}
		return res;
	}
	
	private CharSequence referenceFromSelectedVerses(IntArrayList selectedVerses) {
		if (selectedVerses.size() == 0) {
			// should not be possible. So we don't do anything.
			return S.alamat(S.activeBook, this.chapter_1);
		} else if (selectedVerses.size() == 1) {
			return S.alamat(S.activeBook, this.chapter_1, selectedVerses.get(0));
		} else {
			return S.alamat(S.activeBook, this.chapter_1, selectedVerses);
		}
	}
	
	static class FakeContextMenu {
		static class Item {
			String label;
			Item(String label) {
				this.label = label;
			}
		}
		
		Item menuCopyVerse;
		Item menuAddBookmark;
		Item menuAddNote;
		Item menuAddHighlight;
		Item menuShare;
		Item menuEsvsbasal;
		List<Item> items;
		
		String[] getLabels() {
			String[] res = new String[items.size()];
			for (int i = 0, len = items.size(); i < len; i++) {
				res[i] = items.get(i).label;
			}
			return res;
		}
	}
	
	FakeContextMenu getFakeContextMenu() {
		FakeContextMenu res = new FakeContextMenu();
		res.menuCopyVerse = new Item(getString(R.string.salin_ayat));
		res.menuAddBookmark = new Item(getString(R.string.tambah_pembatas_buku));
		res.menuAddNote = new Item(getString(R.string.tulis_catatan));
		res.menuAddHighlight = new Item(getString(R.string.highlight_stabilo));
		res.menuShare = new Item(getString(R.string.bagikan));
		
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
			res.menuEsvsbasal = new Item("ESV Study Bible"); //$NON-NLS-1$
		}
		
		ArrayList<Item> items = new ArrayList<Item>(6);
		items.add(res.menuCopyVerse);
		items.add(res.menuAddBookmark);
		items.add(res.menuAddNote);
		items.add(res.menuAddHighlight);
		items.add(res.menuShare);
		
		if (hasEsvsbAsal) {
			items.add(res.menuEsvsbasal);
		}
		res.items = items;
		
		return res;
	};
	

	public void showFakeContextMenu() {
		IntArrayList selected = getSelectedVerses_1();
		if (selected.size() == 0) return;

		// creating the menu manually
		final FakeContextMenu menu = getFakeContextMenu();
		
		new AlertDialog.Builder(this)
		.setTitle(referenceFromSelectedVerses(selected))
		.setItems(menu.getLabels(), new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				if (which >= 0 && which < menu.items.size()) {
					onFakeContextMenuSelected(menu, menu.items.get(which));
				}
			}
		})
		.show();
	}
	
	public void onFakeContextMenuSelected(FakeContextMenu menu, Item item) {
		IntArrayList selected = getSelectedVerses_1();
		if (selected.size() == 0) return;
		
		CharSequence reference = referenceFromSelectedVerses(selected);
		
		// the main verse (0 if not exist), which is only when only one verse is selected
		int mainVerse_1 = 0;
		if (selected.size() == 1) { 
			mainVerse_1 = selected.get(0);
		}
		
		if (item == menu.menuCopyVerse) { // copy, can be multiple
			StringBuilder textToCopy = new StringBuilder();
			textToCopy.append(reference).append("  "); //$NON-NLS-1$
			
			// append each selected verse
			for (int i = 0; i < selected.size(); i++) {
				int verse_1 = selected.get(i);
				if (i != 0) textToCopy.append('\n');
				textToCopy.append(U.removeSpecialCodes(verseAdapter_.getVerse(verse_1)));
			}
			
			U.copyToClipboard(textToCopy);
			uncheckAll();
			
			Toast.makeText(this, getString(R.string.alamat_sudah_disalin, reference), Toast.LENGTH_SHORT).show();
		} else if (item == menu.menuAddBookmark) {
			if (mainVerse_1 == 0) {
				// no main verse, scroll to show the relevant one!
				mainVerse_1 = selected.get(0);
				
				scrollToShowVerse(mainVerse_1);
			}
			
			final int ari = Ari.encode(S.activeBook.pos, this.chapter_1, mainVerse_1);
			
			TypeBookmarkDialog dialog = new TypeBookmarkDialog(this, S.alamat(S.activeBook, this.chapter_1, mainVerse_1), ari);
			dialog.setListener(new TypeBookmarkDialog.Listener() {
				@Override public void onOk() {
					uncheckAll();
					verseAdapter_.loadAttributeMap();
				}
			});
			dialog.bukaDialog();
		} else if (item == menu.menuAddNote) {
			if (mainVerse_1 == 0) {
				// no main verse, scroll to show the relevant one!
				mainVerse_1 = selected.get(0);
				
				scrollToShowVerse(mainVerse_1);
			}
			
			TypeNoteDialog dialog = new TypeNoteDialog(IsiActivity.this, S.activeBook, this.chapter_1, mainVerse_1, new RefreshCallback() {
				@Override public void onDone() {
					uncheckAll();
					verseAdapter_.loadAttributeMap();
				}
			});
			dialog.bukaDialog();
		} else if (item == menu.menuAddHighlight) {
			final int ariKp = Ari.encode(S.activeBook.pos, this.chapter_1, 0);
			int warnaRgb = S.getDb().getWarnaRgbStabilo(ariKp, selected);
			
			new JenisStabiloDialog(this, ariKp, selected, new JenisStabiloDialog.JenisStabiloCallback() {
				@Override public void onOk(int warnaRgb) {
					uncheckAll();
					verseAdapter_.loadAttributeMap();
				}
			}, warnaRgb, reference).bukaDialog();
		} else if (item == menu.menuShare) {
			StringBuilder sb = new StringBuilder();
			sb.append(reference).append("  "); //$NON-NLS-1$
			
			// append each selected verse
			for (int i = 0; i < selected.size(); i++) {
				int ayat_1 = selected.get(i);
				if (i != 0) sb.append('\n');
				sb.append(U.removeSpecialCodes(verseAdapter_.getVerse(ayat_1)));
			}
			
			String verseUrl;
			if (selected.size() == 1) {
				verseUrl = S.createVerseUrl(S.activeBook, this.chapter_1, String.valueOf(selected.get(0)));
			} else {
				StringBuilder sb2 = new StringBuilder();
				S.writeVerseRange(selected, sb2);
				verseUrl = S.createVerseUrl(S.activeBook, this.chapter_1, sb2.toString()); // pake ayat range
			}
			
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("text/plain"); //$NON-NLS-1$
			intent.putExtra(Intent.EXTRA_SUBJECT, reference); 
			intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
			intent.putExtra(EXTRA_verseUrl, verseUrl);
			startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.bagikan_alamat, reference)), REQCODE_share);

			uncheckAll();
		} else if (item == menu.menuEsvsbasal) {
			final int ari = Ari.encode(S.activeBook.pos, this.chapter_1, mainVerse_1);

			try {
				Intent intent = new Intent("yuku.esvsbasal.action.GOTO"); //$NON-NLS-1$
				intent.putExtra("ari", ari); //$NON-NLS-1$
				startActivity(intent);
			} catch (Exception e) {
				Log.e(TAG, "ESVSB starting", e); //$NON-NLS-1$
			}
		}
	}

	private void scrollToShowVerse(int ayatUtama_1) {
		int position = verseAdapter_.getPositionOfPericopeBeginningFromVerse(ayatUtama_1);
		if (Build.VERSION.SDK_INT >= 8) {
			Api8.ListView_smoothScrollToPosition(lsText, position);
		} else {
			lsText.setSelectionFromTop(position, lsText.getVerticalFadingEdgeLength());
		}
	}

	private void applyPreferences(boolean languageToo) {
		// appliance of background color
		{
			root.setBackgroundColor(S.penerapan.backgroundColor);
			lsText.setCacheColorHint(S.penerapan.backgroundColor);
		}
		
		// appliance of hide navigation
		{
			View navigationPanel = findViewById(R.id.panelNavigasi);
			if (Preferences.getBoolean(R.string.pref_tanpaNavigasi_key, R.bool.pref_tanpaNavigasi_default)) {
				navigationPanel.setVisibility(View.GONE);
				titleContainer.setVisibility(View.VISIBLE);
			} else {
				navigationPanel.setVisibility(View.VISIBLE);
				titleContainer.setVisibility(View.GONE);
			}
		}
		
		if (languageToo) {
			S.applyLanguagePreference(null, 0);
		}
		
		// necessary
		lsText.invalidateViews();
	}
	
	@Override protected void onStop() {
		super.onStop();
		
		Editor editor = instant_pref.edit();
		editor.putInt(PREFKEY_lastBook, S.activeBook.pos);
		editor.putInt(PREFKEY_lastChapter, chapter_1);
		editor.putInt(PREFKEY_lastVerse, getVerseBasedOnScroll());
		editor.putString(PREFKEY_devotion_name, S.penampungan.devotion_name);
		editor.putString(PREFKEY_lastVersion, S.activeVersionId);
		history.simpan(editor);
		editor.commit();
		
		turnScreenOffIfAllowedToTurnOff();
	}
	
	@Override protected void onStart() {
		super.onStart();
		
		keepScreenOnWhenRequested();
	}
	
	/**
	 * @return 1-based verse
	 */
	int getVerseBasedOnScroll() {
		return verseAdapter_.getVerseFromPosition(getPositionBasedOnScroll());
	}
	
	int getPositionBasedOnScroll() {
		int pos = lsText.getFirstVisiblePosition();

		// check if the top one has been scrolled 
		View child = lsText.getChildAt(0); 
		if (child != null) {
			int top = child.getTop();
			if (top == 0) {
				return pos;
			}
			int bottom = child.getBottom();
			if (bottom > lsText.getVerticalFadingEdgeLength()) {
				return pos;
			} else {
				return pos+1;
			}
		}
		
		return pos;
	}

	void bGoto_click() {
		if (Preferences.getBoolean(R.string.pref_tombolAlamatLoncat_key, R.bool.pref_tombolAlamatLoncat_default)) {
			openJumpToDialog();
		} else {
			openGotoDialog();
		}
	}
	
	void bGoto_longClick() {
		if (history.getN() > 0) {
			new AlertDialog.Builder(this)
			.setAdapter(historyAdapter, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					int ari = history.getAri(which);
					jumpToAri(ari);
					history.add(ari);
				}
			})
			.setNegativeButton(R.string.cancel, null)
			.show();
		} else {
			Toast.makeText(this, R.string.belum_ada_sejarah, Toast.LENGTH_SHORT).show();
		}
	}
	
	private ListAdapter historyAdapter = new BaseAdapter() {
		@Override public View getView(int position, View convertView, ViewGroup parent) {
			TextView res = (TextView) convertView;
			if (res == null) {
				res = (TextView) LayoutInflater.from(IsiActivity.this).inflate(android.R.layout.select_dialog_item, null);
			}
			int ari = history.getAri(position);
			res.setText(S.reference(S.activeVersion, ari));
			return res;
		}
		
		@Override
		public long getItemId(int position) {
			return position;
		}
		
		@Override
		public Integer getItem(int position) {
			return history.getAri(position);
		}
		
		@Override
		public int getCount() {
			return history.getN();
		}
	};
	
	void openGotoDialog() {
		Intent intent = new Intent(this, MenujuActivity.class);
		intent.putExtra(MenujuActivity.EXTRA_chapter, chapter_1);
		
		int verse = getVerseBasedOnScroll();
		intent.putExtra(MenujuActivity.EXTRA_verse, verse);
		
		startActivityForResult(intent, REQCODE_goto);
	}
	
	void openJumpToDialog() {
		final View loncat = LayoutInflater.from(this).inflate(R.layout.dialog_loncat, null);
		final TextView lContohLoncat = (TextView) loncat.findViewById(R.id.lContohLoncat);
		final EditText tAlamatLoncat = (EditText) loncat.findViewById(R.id.tAlamatLoncat);
		final ImageButton bKeTuju = (ImageButton) loncat.findViewById(R.id.bKeTuju);

		{
			String alamatContoh = S.alamat(S.activeBook, IsiActivity.this.chapter_1, getVerseBasedOnScroll());
			String text = getString(R.string.loncat_ke_alamat_titikdua);
			int pos = text.indexOf("%s"); //$NON-NLS-1$
			if (pos >= 0) {
				SpannableStringBuilder sb = new SpannableStringBuilder();
				sb.append(text.substring(0, pos));
				sb.append(alamatContoh);
				sb.append(text.substring(pos + 2));
				sb.setSpan(new StyleSpan(Typeface.BOLD), pos, pos + alamatContoh.length(), 0);
				lContohLoncat.setText(sb, BufferType.SPANNABLE);
			}
		}
		
		final DialogInterface.OnClickListener loncat_click = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				int ari = jumpTo(tAlamatLoncat.getText().toString());
				if (ari != 0) {
					history.add(ari);
				}
			}
		};
		
		final AlertDialog dialog = new AlertDialog.Builder(IsiActivity.this)
			.setView(loncat)
			.setPositiveButton(R.string.loncat, loncat_click)
			.create();
		
		tAlamatLoncat.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				loncat_click.onClick(dialog, 0);
				dialog.dismiss();
				
				return true;
			}
		});
		
		bKeTuju.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
				openGotoDialog();
			}
		});
		
		dialog.setOnDismissListener(new OnDismissListener() {
			@Override public void onDismiss(DialogInterface _) {
				dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
			}
		});
		
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		dialog.show();
	}
	
	public void bukaDialogDonasi() {
		new AlertDialog.Builder(this)
		.setTitle(R.string.donasi_judul)
		.setMessage(R.string.donasi_keterangan)
		.setPositiveButton(R.string.donasi_tombol_ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String alamat_donasi = getString(R.string.alamat_donasi);
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(alamat_donasi));
				startActivity(intent);
			}
		})
		.setNegativeButton(R.string.donasi_tombol_gamau, null)
		.show();
	}

	public void bikinMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.activity_isi, menu);
		
		BuildConfig c = BuildConfig.get(this);

		if (c.menuGebug) {
			SubMenu menuGebug = menu.addSubMenu(R.string.gebug);
			menuGebug.setHeaderTitle("Untuk percobaan dan cari kutu. Tidak penting."); //$NON-NLS-1$
			menuGebug.add(0, 0x985801, 0, "gebug 1: dump p+p"); //$NON-NLS-1$
			menuGebug.add(0, 0x985806, 0, "gebug 6: tehel bewarna"); //$NON-NLS-1$
			menuGebug.add(0, 0x985807, 0, "gebug 7: dump warna"); //$NON-NLS-1$
		}
		
		//# build config
		menu.findItem(R.id.menuRenungan).setVisible(c.menuRenungan);
		menu.findItem(R.id.menuEdisi).setVisible(c.menuEdisi);
		menu.findItem(R.id.menuBantuan).setVisible(c.menuBantuan);
		menu.findItem(R.id.menuDonasi).setVisible(c.menuDonasi);
		menu.findItem(R.id.menuSongs).setVisible(c.menuSongs);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		bikinMenu(menu);
		
		return true;
	}
	
	@Override public boolean onMenuOpened(int featureId, Menu menu) {
		if (menu != null) {
			bikinMenu(menu);
		}
		
		return true;
	}
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuBukmak:
			startActivityForResult(new Intent(this, BukmakActivity.class), REQCODE_bookmark);
			return true;
		case R.id.menuSearch2:
			menuSearch2_click();
			return true;
		case R.id.menuEdisi:
			bukaDialogEdisi();
			return true;
		case R.id.menuRenungan: 
			startActivityForResult(new Intent(this, DevotionActivity.class), REQCODE_devotion);
			return true;
		case R.id.menuSongs: 
			startActivity(SongViewActivity.createIntent());
			return true;
		case R.id.menuTentang:
			startActivity(new Intent(this, AboutActivity.class));
			return true;
		case R.id.menuPengaturan:
			startActivityForResult(new Intent(this, PengaturanActivity.class), REQCODE_settings);
			return true;
		case R.id.menuBantuan:
			startActivity(new Intent(this, BantuanActivity.class));
			return true;
		case R.id.menuDonasi:
			bukaDialogDonasi();
			return true;
		}
		
		return super.onOptionsItemSelected(item); 
	}

	private void bukaDialogEdisi() {
		// populate dengan 
		// 1. internal
		// 2. preset yang UDAH DIDONLOT dan AKTIF
		// 3. yes yang AKTIF
		
		BuildConfig c = BuildConfig.get(this);
		final List<String> pilihan = new ArrayList<String>(); // harus bareng2 sama bawah
		final List<MVersion> data = new ArrayList<MVersion>();  // harus bareng2 sama atas
		
		pilihan.add(c.internalJudul); // 1. internal
		data.add(new MEdisiInternal());
		
		for (MEdisiPreset preset: c.presets) { // 2. preset
			if (preset.adaFileDatanya() && preset.getAktif()) {
				pilihan.add(preset.judul);
				data.add(preset);
			}
		}
		
		// 3. yes yang aktif
		List<MEdisiYes> xyes = S.getDb().listAllVersions();
		for (MEdisiYes yes: xyes) {
			if (yes.adaFileDatanya() && yes.getAktif()) {
				pilihan.add(yes.judul);
				data.add(yes);
			}
		}
		
		int terpilih = -1;
		if (S.activeVersionId == null) {
			terpilih = 0;
		} else {
			for (int i = 0; i < data.size(); i++) {
				MVersion me = data.get(i);
				if (me.getEdisiId().equals(S.activeVersionId)) {
					terpilih = i;
					break;
				}
			}
		}

		new AlertDialog.Builder(this)
		.setTitle(R.string.pilih_edisi)
		.setSingleChoiceItems(pilihan.toArray(new String[pilihan.size()]), terpilih, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				final MVersion me = data.get(which);
				
				loadVersion(me);
				dialog.dismiss();
			}
		})
		.setPositiveButton(R.string.versi_lainnya, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				Intent intent = new Intent(getApplicationContext(), EdisiActivity.class);
				startActivityForResult(intent, REQCODE_version);
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
	}

	private void menuSearch2_click() {
		startActivityForResult(Search2Activity.createIntent(search2_query, search2_results, search2_selectedPosition, S.activeBook.pos), REQCODE_search);
	}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "onActivityResult reqCode=0x" + Integer.toHexString(requestCode) + " resCode=" + resultCode + " data=" + data); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		
		if (requestCode == REQCODE_goto) {
			if (resultCode == RESULT_OK) {
				int pasal = data.getIntExtra(MenujuActivity.EXTRA_chapter, 0);
				int ayat = data.getIntExtra(MenujuActivity.EXTRA_verse, 0);
				int kitabPos = data.getIntExtra(MenujuActivity.EXTRA_kitab, AdapterView.INVALID_POSITION);
				
				if (kitabPos != AdapterView.INVALID_POSITION) {
					// ganti kitab
					Book k = S.activeVersion.getKitab(kitabPos);
					if (k != null) {
						S.activeBook = k;
					}
				}
				
				int ari_pa = display(pasal, ayat);
				history.add(Ari.encode(kitabPos, ari_pa));
			} else if (resultCode == RESULT_pindahCara) {
				openJumpToDialog();
			}
		} else if (requestCode == REQCODE_bookmark) {
			verseAdapter_.loadAttributeMap();

			if (resultCode == RESULT_OK) {
				int ari = data.getIntExtra(BukmakActivity.EXTRA_ariTerpilih, 0);
				if (ari != 0) { // 0 berarti ga ada apa2, karena ga ada pasal 0 ayat 0
					jumpToAri(ari);
					history.add(ari);
				}
			}
		} else if (requestCode == REQCODE_search) {
			if (resultCode == RESULT_OK) {
				Search2Activity.Result result = Search2Activity.obtainResult(data);
				if (result != null) {
					if (result.ariTerpilih != -1) {
						jumpToAri(result.ariTerpilih);
						history.add(result.ariTerpilih);
					}
					
					search2_query = result.query;
					search2_results = result.hasilCari;
					search2_selectedPosition = result.posisiTerpilih;
				}
			}
		} else if (requestCode == REQCODE_devotion) {
			if (data != null) {
				String alamat = data.getStringExtra(DevotionActivity.EXTRA_alamat);
				if (alamat != null) {
					int ari = jumpTo(alamat);
					if (ari != 0) {
						history.add(ari);
					}
				}
			}
		} else if (requestCode == REQCODE_settings) {
			// HARUS rilod pengaturan.
			S.calculateAppliedValuesBasedOnPreferences();
			
			applyPreferences(true);
		} else if (requestCode == REQCODE_share) {
			if (resultCode == RESULT_OK) {
				ShareActivity.Result result = ShareActivity.obtainResult(data);
				if (result != null && result.chosenIntent != null) {
					Intent chosenIntent = result.chosenIntent;
					if (U.equals(chosenIntent.getComponent().getPackageName(), "com.facebook.katana")) { //$NON-NLS-1$
						String urlAyat = chosenIntent.getStringExtra(EXTRA_verseUrl);
						if (urlAyat != null) {
							chosenIntent.putExtra(Intent.EXTRA_TEXT, urlAyat); // change text to url
						}
					}
					startActivity(chosenIntent);
				}
			}
		}
	}

	/**
	 * @param pasal_1 basis-1
	 * @param ayat_1 basis-1
	 * @return Ari yang hanya terdiri dari pasal dan ayat. Kitab selalu 00
	 */
	int display(int pasal_1, int ayat_1) {
		if (pasal_1 < 1) pasal_1 = 1;
		if (pasal_1 > S.activeBook.npasal) pasal_1 = S.activeBook.npasal;
		
		if (ayat_1 < 1) ayat_1 = 1;
		if (ayat_1 > S.activeBook.nayat[pasal_1 - 1]) ayat_1 = S.activeBook.nayat[pasal_1 - 1];
		
		// muat data GA USAH pake async dong. // diapdet 20100417 biar ga usa async, ga guna.
		{
			int[] perikop_xari;
			Blok[] perikop_xblok;
			int nblok;
			
			String[] xayat = S.muatTeks(S.activeVersion, S.activeBook, pasal_1);
			
			//# max dibikin pol 30 aja (1 pasal max 30 blok, cukup mustahil)
			int max = 30;
			perikop_xari = new int[max];
			perikop_xblok = new Blok[max];
			nblok = S.activeVersion.pembaca.muatPerikop(S.activeVersion, S.activeBook.pos, pasal_1, perikop_xari, perikop_xblok, max); 
			
			//# isi adapter dengan data baru, pastikan semua checked state direset dulu
			uncheckAll();
			verseAdapter_.setData(S.activeBook, pasal_1, xayat, perikop_xari, perikop_xblok, nblok);
			verseAdapter_.loadAttributeMap();
			
			// kasi tau activity
			this.chapter_1 = pasal_1;
			
			final int position = verseAdapter_.getPositionOfPericopeBeginningFromVerse(ayat_1);
			
			if (position == -1) {
				Log.w(TAG, "ga bisa ketemu ayat " + ayat_1 + ", ANEH!"); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				lsText.setSelectionFromTop(position, lsText.getVerticalFadingEdgeLength());
			}
		}
		
		String judul = S.alamat(S.activeBook, pasal_1);
		lTitle.setText(judul);
		bGoto.setText(judul);
		
		return Ari.encode(0, pasal_1, ayat_1);
	}

	void uncheckAll() {
		SparseBooleanArray checkedPositions = lsText.getCheckedItemPositions();
		if (checkedPositions != null && checkedPositions.size() > 0) {
			for (int i = checkedPositions.size() - 1; i >= 0; i--) {
				if (checkedPositions.valueAt(i)) {
					lsText.setItemChecked(checkedPositions.keyAt(i), false);
				}
			}
		}
		hideContextButton();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (press(keyCode)) return true;
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		if (press(keyCode)) return true;
		return super.onKeyMultiple(keyCode, repeatCount, event);
	}
	
	@Override public boolean onKeyUp(int keyCode, KeyEvent event) {
		String tombolVolumeBuatPindah = Preferences.getString(R.string.pref_tombolVolumeBuatPindah_key, R.string.pref_tombolVolumeBuatPindah_default);
		if (! U.equals(tombolVolumeBuatPindah, "default")) { // consume here //$NON-NLS-1$
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return true;
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) return true;
		}
		return super.onKeyUp(keyCode, event);
	}
	
	void bLeft_click() {
		Book kitabKini = S.activeBook;
		if (chapter_1 == 1) {
			// uda di awal pasal, masuk ke kitab sebelum
			int cobaKitabPos = kitabKini.pos - 1;
			while (cobaKitabPos >= 0) {
				Book kitabBaru = S.activeVersion.getKitab(cobaKitabPos);
				if (kitabBaru != null) {
					S.activeBook = kitabBaru;
					int pasalBaru_1 = kitabBaru.npasal; // ke pasal terakhir
					display(pasalBaru_1, 1);
					break;
				}
				cobaKitabPos--;
			}
			// whileelse: sekarang sudah Kejadian 1. Ga usa ngapa2in
		} else {
			int pasalBaru = chapter_1 - 1;
			display(pasalBaru, 1);
		}
	}
	
	void bRight_click() {
		Book kitabKini = S.activeBook;
		if (chapter_1 >= kitabKini.npasal) {
			int maxKitabPos = S.activeVersion.getMaxKitabPosTambahSatu();
			int cobaKitabPos = kitabKini.pos + 1;
			while (cobaKitabPos < maxKitabPos) {
				Book kitabBaru = S.activeVersion.getKitab(cobaKitabPos);
				if (kitabBaru != null) {
					S.activeBook = kitabBaru;
					display(1, 1);
					break;
				}
				cobaKitabPos++;
			}
			// whileelse: uda di Wahyu (atau kitab terakhir) pasal terakhir. Ga usa ngapa2in
		} else {
			int pasalBaru = chapter_1 + 1;
			display(pasalBaru, 1);
		}
	}

	private OnClickListener bContextMenu_click = new OnClickListener() {
		@Override public void onClick(View v) {
			showFakeContextMenu();
		}
	};
	
	@Override
	public boolean onSearchRequested() {
		menuSearch2_click();
		
		return true;
	}

	public class AttributeListener {
		public void onClick(Book kitab_, int pasal_1, int ayat_1, int jenis) {
			if (jenis == Bukmak2.jenis_bukmak) {
				final int ari = Ari.encode(kitab_.pos, pasal_1, ayat_1);
				String alamat = S.reference(S.activeVersion, ari);
				TypeBookmarkDialog dialog = new TypeBookmarkDialog(IsiActivity.this, alamat, ari);
				dialog.setListener(new TypeBookmarkDialog.Listener() {
					@Override public void onOk() {
						verseAdapter_.loadAttributeMap();
					}
				});
				dialog.bukaDialog();
			} else if (jenis == Bukmak2.jenis_catatan) {
				TypeNoteDialog dialog = new TypeNoteDialog(IsiActivity.this, kitab_, pasal_1, ayat_1, new RefreshCallback() {
					@Override public void onDone() {
						verseAdapter_.loadAttributeMap();
					}
				});
				dialog.bukaDialog();
			}
		}
	}
}
