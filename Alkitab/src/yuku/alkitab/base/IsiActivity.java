package yuku.alkitab.base;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.ac.AboutActivity;
import yuku.alkitab.base.ac.BookmarkActivity;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.ac.GotoActivity;
import yuku.alkitab.base.ac.HelpActivity;
import yuku.alkitab.base.ac.Search2Activity;
import yuku.alkitab.base.ac.SettingsActivity;
import yuku.alkitab.base.ac.ShareActivity;
import yuku.alkitab.base.ac.SongViewActivity;
import yuku.alkitab.base.ac.VersionsActivity;
import yuku.alkitab.base.ac.VersionsActivity.MVersion;
import yuku.alkitab.base.ac.VersionsActivity.MVersionInternal;
import yuku.alkitab.base.ac.VersionsActivity.MVersionPreset;
import yuku.alkitab.base.ac.VersionsActivity.MVersionYes;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.dialog.TypeBookmarkDialog;
import yuku.alkitab.base.dialog.TypeHighlightDialog;
import yuku.alkitab.base.dialog.TypeNoteDialog;
import yuku.alkitab.base.dialog.XrefDialog;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.SingleChapterVerses;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.util.History;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.Jumper;
import yuku.alkitab.base.util.LidToAri;
import yuku.alkitab.base.util.Search2Engine.Query;
import yuku.alkitab.base.widget.CallbackSpan;
import yuku.alkitab.base.widget.VerseAdapter;
import yuku.alkitab.base.widget.VersesView;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class IsiActivity extends BaseActivity implements XrefDialog.XrefDialogListener {
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
	private static final int REQCODE_songs = 8;

	private static final String EXTRA_verseUrl = "urlAyat"; //$NON-NLS-1$

	VersesView lsText;
	VersesView lsSplit1;
	Button bGoto;
	ImageButton bLeft;
	ImageButton bRight;
	View root;
	
	Book activeBook;
	int chapter_1 = 0;
	SharedPreferences instant_pref;
	
	History history;
	NfcAdapter nfcAdapter;
	ActionMode actionMode;

	//# state storage for search2
	Query search2_query = null;
	IntArrayList search2_results = null;
	int search2_selectedPosition = -1;
	
	// temporary states
	Boolean hasEsvsbAsal;
	Version activeSplitVersion;
	String activeSplitVersionId;
	Book activeSplitBook;
	
	CallbackSpan.OnClickListener parallelListener = new CallbackSpan.OnClickListener() {
		@Override public void onClick(View widget, Object data) {
            if (data instanceof String) {
                int ari = jumpTo((String) data);
                if (ari != 0) {
                    history.add(ari);
                }
            } else if (data instanceof VerseAdapter.ParallelTypeAri) {
                int ari = ((VerseAdapter.ParallelTypeAri) data).ariStart;
                jumpToAri(ari);
                history.add(ari);
            } else if (data instanceof VerseAdapter.ParallelTypeLid) {
                int ari = LidToAri.lidToAri(((VerseAdapter.ParallelTypeLid) data).lidStart);
                if (ari != 0) {
                    jumpToAri(ari);
                    history.add(ari);
                }
            } else if (data instanceof VerseAdapter.ParallelTypeOsis) {
                String osis = ((VerseAdapter.ParallelTypeOsis) data).osisStart;
                int ari = jumpTo(osis); // jumpTo handles osis well
                if (ari != 0) {
                    history.add(ari);
                }
            }
		}
	};

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_isi);
		
		lsText = V.get(this, R.id.lsSplit0);
		lsSplit1 = V.get(this, R.id.lsSplit1);
		bGoto = V.get(this, R.id.bTuju);
		bLeft = V.get(this, R.id.bKiri);
		bRight = V.get(this, R.id.bKanan);
		root = V.get(this, R.id.root);
		
		applyPreferences(false);
		
		bGoto.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { bGoto_click(); }
		});
		bGoto.setOnLongClickListener(new View.OnLongClickListener() {
			@Override public boolean onLongClick(View v) { bGoto_longClick(); return true; }
		});
		
		bLeft.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { bLeft_click(); }
		});
		bRight.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { bRight_click(); }
		});
		
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
		lsText.setXrefListener(xrefListener);
		lsText.setSelectedVersesListener(lsText_selectedVerses);
		lsText.setOnVerseScrollListener(lsText_verseScroll);
		
		// additional setup for split1
		lsSplit1.setVerseSelectionMode(VersesView.VerseSelectionMode.none);
		lsSplit1.setOnVerseScrollListener(lsSplit1_verseScroll);
		
		// muat preferences_instan, dan atur renungan
		instant_pref = App.getPreferencesInstan();
		history = new History(instant_pref);
		{
			String devotion_name = instant_pref.getString(PREFKEY_devotion_name, null);
			if (devotion_name != null) {
				for (String nama: DevotionActivity.AVAILABLE_NAMES) {
					if (devotion_name.equals(nama)) {
						S.temporary.devotion_name = devotion_name;
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
			Book book = S.activeVersion.getBook(lastBook);
			if (book != null) {
				this.activeBook = book;
			}
		}
		
		// load chapter and verse
		display(lastChapter, lastVerse);
		
		if (Build.VERSION.SDK_INT >= 14) {
			initNfcIfAvailable();
		}
		
		processIntent(getIntent(), "onCreate");
	}
	
	@Override protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		
		processIntent(intent, "onNewIntent");
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
		
		if (Build.VERSION.SDK_INT >= 14) {
			checkAndProcessBeamIntent(intent);
		}
		
		checkAndProcessViewIntent(intent);
	}
	
	/** did we get here from VIEW intent? */
	private void checkAndProcessViewIntent(Intent intent) {
		if (!U.equals(intent.getAction(), "yuku.alkitab.action.VIEW")) return;

		if (intent.hasExtra("ari")) {
			int ari = intent.getIntExtra("ari", 0);
			if (ari != 0) {
				jumpToAri(ari);
				return;
			} else {
				new AlertDialog.Builder(this)
				.setMessage("Invalid ari: " + ari)
				.setPositiveButton(R.string.ok, null)
				.show();
			}
		} 
		
		if (intent.hasExtra("lid")) {
			int lid = intent.getIntExtra("lid", 0);
			int ari = LidToAri.lidToAri(lid);
			if (ari != 0) {
				jumpToAri(ari);
				return;
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
			nfcAdapter.setNdefPushMessageCallback(new CreateNdefMessageCallback() {
				@Override public NdefMessage createNdefMessage(NfcEvent event) {
					JSONObject obj = new JSONObject();
					try {
						obj.put("ari", Ari.encode(IsiActivity.this.activeBook.bookId, IsiActivity.this.chapter_1, lsText.getVerseBasedOnScroll())); //$NON-NLS-1$
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
		if (lastVersion == null || MVersionInternal.getVersionInternalId().equals(lastVersion)) {
			return; // we are now already on internal, no need to do anything!
		}
		
		AppConfig c = AppConfig.get();
		
		// coba preset dulu!
		for (MVersionPreset preset: c.presets) { // 2. preset
			if (preset.getVersionId().equals(lastVersion)) {
				if (preset.hasDataFile()) {
					if (loadVersion(preset, false)) return;
				} else { 
					return; // this is the one that should have been chosen, but the data file is not available, so let's fallback.
				}
			}
		}
		
		// masih belum cocok, mari kita cari di daftar yes
		List<MVersionYes> yeses = S.getDb().getAllVersions();
		for (MVersionYes yes: yeses) {
			if (yes.getVersionId().equals(lastVersion)) {
				if (yes.hasDataFile()) {
					if (loadVersion(yes, false)) return;
				} else { 
					return; // this is the one that should have been chosen, but the data file is not available, so let's fallback.
				}
			}
		}
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
				
				if (display) {
					display(chapter_1, lsText.getVerseBasedOnScroll(), false);
				}
				
				return true;
			} else {
				throw new RuntimeException(getString(R.string.ada_kegagalan_membuka_edisiid, mv.getVersionId()));
			}
		} catch (Throwable e) { // so we don't crash on the beginning of the app
			new AlertDialog.Builder(IsiActivity.this)
			.setMessage(getString(R.string.ada_kegagalan_membuka_edisiid, mv.getVersionId()))
			.setPositiveButton(R.string.ok, null)
			.show();
			
			return false;
		}
	}
	
	void loadSplitVersion(final MVersion mv, boolean display) {
		// for rollback
		Version oldActiveVersion = activeSplitVersion;
		String oldActiveVersionId = activeSplitVersionId;
		
		boolean success = false;
		try {
			Version version = mv.getVersion();
			
			if (version != null) {
				activeSplitVersion = version;
				activeSplitVersionId = mv.getVersionId();
				
				if (display) {
					display(chapter_1, lsText.getVerseBasedOnScroll(), false);
				}
			}
			success = true;
		} catch (Throwable e) { // so we don't crash on the beginning of the app
			Log.e(TAG, "failed in loadVersion", e);  //$NON-NLS-1$
		} finally {
			if (!success) {
				activeSplitVersion = oldActiveVersion;
				activeSplitVersionId = oldActiveVersionId;
			}
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
		
		if (lsText.press(keyCode)) return true;
		
		return false;
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
	
	private CharSequence referenceFromSelectedVerses(IntArrayList selectedVerses) {
		if (selectedVerses.size() == 0) {
			// should not be possible. So we don't do anything.
			return this.activeBook.reference(this.chapter_1);
		} else if (selectedVerses.size() == 1) {
			return this.activeBook.reference(this.chapter_1, selectedVerses.get(0));
		} else {
			return this.activeBook.reference(this.chapter_1, selectedVerses);
		}
	}
	
	CharSequence prepareTextForCopyShare(IntArrayList selectedVerses_1, CharSequence reference) {
		StringBuilder res = new StringBuilder();
		res.append(reference);
		
		if (Preferences.getBoolean(getString(R.string.pref_copyWithVerseNumbers_key), false) && selectedVerses_1.size() > 1) {
			res.append('\n');

			// append each selected verse with verse number prepended
			for (int i = 0; i < selectedVerses_1.size(); i++) {
				int verse_1 = selectedVerses_1.get(i);
				res.append(verse_1);
				res.append(' ');
				res.append(U.removeSpecialCodes(lsText.getVerse(verse_1)));
				res.append('\n');
			}
		} else {
			res.append("  "); //$NON-NLS-1$
			
			// append each selected verse without verse number prepended
			for (int i = 0; i < selectedVerses_1.size(); i++) {
				int verse_1 = selectedVerses_1.get(i);
				if (i != 0) res.append('\n');
				res.append(U.removeSpecialCodes(lsText.getVerse(verse_1)));
			}
		}
		return res;
	}

	private void applyPreferences(boolean languageToo) {
		// appliance of background color
		{
			root.setBackgroundColor(S.applied.backgroundColor);
			lsText.setCacheColorHint(S.applied.backgroundColor);
			lsSplit1.setCacheColorHint(S.applied.backgroundColor);
		}
		
		if (languageToo) {
			S.applyLanguagePreference(null, 0);
		}
		
		// necessary
		lsText.invalidateViews();
		lsSplit1.invalidateViews();
	}
	
	@Override protected void onStop() {
		super.onStop();
		
		Editor editor = instant_pref.edit();
		editor.putInt(PREFKEY_lastBook, this.activeBook.bookId);
		editor.putInt(PREFKEY_lastChapter, chapter_1);
		editor.putInt(PREFKEY_lastVerse, lsText.getVerseBasedOnScroll());
		editor.putString(PREFKEY_devotion_name, S.temporary.devotion_name);
		editor.putString(PREFKEY_lastVersion, S.activeVersionId);
		history.simpan(editor);
		editor.commit();
		
		lsText.setKeepScreenOn(false);
	}
	
	@Override protected void onStart() {
		super.onStart();
		
		if (Preferences.getBoolean(getString(R.string.pref_nyalakanTerusLayar_key), getResources().getBoolean(R.bool.pref_nyalakanTerusLayar_default))) {
			lsText.setKeepScreenOn(true);
		}
	}
	
	void bGoto_click() {
		startActivityForResult(GotoActivity.createIntent(this.activeBook.bookId, this.chapter_1, lsText.getVerseBasedOnScroll()), REQCODE_goto);
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
				res = (TextView) getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
			}
			int ari = history.getAri(position);
			res.setText(S.activeVersion.reference(ari));
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
	
	public void openDonationDialog() {
		new AlertDialog.Builder(this)
		.setTitle(R.string.donasi_judul)
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

	public void buildMenu(Menu menu) {
		menu.clear();
		getSupportMenuInflater().inflate(R.menu.activity_isi, menu);
		
		AppConfig c = AppConfig.get();

		if (c.menuGebug) {
			// SubMenu menuGebug = menu.addSubMenu(R.string.gebug);
			// menuGebug.add(0, 0x985801, 0, "gebug 1: dump p+p"); //$NON-NLS-1$
		}
		
		//# build config
		menu.findItem(R.id.menuRenungan).setVisible(c.menuDevotion);
		menu.findItem(R.id.menuEdisi).setVisible(c.menuVersions);
		menu.findItem(R.id.menuBantuan).setVisible(c.menuHelp);
		menu.findItem(R.id.menuDonasi).setVisible(c.menuDonation);
		menu.findItem(R.id.menuSongs).setVisible(c.menuSongs);
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
		switch (item.getItemId()) {
		case R.id.menuBukmak:
			startActivityForResult(new Intent(this, BookmarkActivity.class), REQCODE_bookmark);
			return true;
		case R.id.menuSearch2:
			menuSearch2_click();
			return true;
		case R.id.menuEdisi:
			openVersionDialog();
			return true;
		case R.id.menuSplitVersion:
			openSplitVersionDialog();
			return true;
		case R.id.menuRenungan: 
			startActivityForResult(new Intent(this, DevotionActivity.class), REQCODE_devotion);
			return true;
		case R.id.menuSongs: 
			startActivityForResult(SongViewActivity.createIntent(), REQCODE_songs);
			return true;
		case R.id.menuTentang:
			startActivity(new Intent(this, AboutActivity.class));
			return true;
		case R.id.menuPengaturan:
			startActivityForResult(new Intent(this, SettingsActivity.class), REQCODE_settings);
			return true;
		case R.id.menuBantuan:
			startActivity(HelpActivity.createIntent(false));
			return true;
		case R.id.menuSendMessage:
			startActivity(HelpActivity.createIntent(true));
			return true;
		case R.id.menuDonasi:
			openDonationDialog();
			return true;
		}
		
		return super.onOptionsItemSelected(item); 
	}

	private Pair<List<String>, List<MVersion>> getAvailableVersions() {
		// populate with 
		// 1. internal
		// 2. presets that have been DOWNLOADED and ACTIVE
		// 3. yeses that are ACTIVE
		
		AppConfig c = AppConfig.get();
		final List<String> options = new ArrayList<String>(); // sync with below line
		final List<MVersion> data = new ArrayList<MVersion>();  // sync with above line
		
		options.add(c.internalLongName); // 1. internal
		data.add(new MVersionInternal());
		
		for (MVersionPreset preset: c.presets) { // 2. preset
			if (preset.hasDataFile() && preset.getActive()) {
				options.add(preset.longName);
				data.add(preset);
			}
		}
		
		// 3. active yeses
		List<MVersionYes> yeses = S.getDb().getAllVersions();
		for (MVersionYes yes: yeses) {
			if (yes.hasDataFile() && yes.getActive()) {
				options.add(yes.longName);
				data.add(yes);
			}
		}
		
		return Pair.create(options, data);
	}
	
	void openVersionDialog() {
		Pair<List<String>, List<MVersion>> versions = getAvailableVersions();
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
		.setTitle(R.string.pilih_edisi)
		.setSingleChoiceItems(options.toArray(new String[options.size()]), selected, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				final MVersion mv = data.get(which);
				
				loadVersion(mv, true);
				dialog.dismiss();
			}
		})
		.setPositiveButton(R.string.versi_lainnya, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				Intent intent = new Intent(getApplicationContext(), VersionsActivity.class);
				startActivityForResult(intent, REQCODE_version);
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
	}
	
	void openSplitVersionDialog() {
		Pair<List<String>, List<MVersion>> versions = getAvailableVersions();
		final List<String> options = versions.first;
		final List<MVersion> data = versions.second;
		
		new AlertDialog.Builder(this)
		.setTitle(R.string.pilih_edisi)
		.setItems(options.toArray(new String[options.size()]), new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				final MVersion mv = data.get(which);
				
				loadSplitVersion(mv, true);
				dialog.dismiss();
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
	}

	private void menuSearch2_click() {
		startActivityForResult(Search2Activity.createIntent(search2_query, search2_results, search2_selectedPosition, this.activeBook.bookId), REQCODE_search);
	}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
		} else if (requestCode == REQCODE_bookmark) {
			lsText.loadAttributeMap();

			if (resultCode == RESULT_OK) {
				int ari = data.getIntExtra(BookmarkActivity.EXTRA_ariTerpilih, 0);
				if (ari != 0) { // 0 means nothing, because we don't have chapter 0 verse 0
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
			if (resultCode == RESULT_OK) {
				DevotionActivity.Result result = DevotionActivity.obtainResult(data);
				if (result != null && result.ari != 0) {
					jumpToAri(result.ari);
					history.add(result.ari);
				}
			}
		} else if (requestCode == REQCODE_songs) {
			if (resultCode == SongViewActivity.RESULT_gotoScripture && data != null) {
				String ref = data.getStringExtra(SongViewActivity.EXTRA_ref);
				if (ref != null) { // TODO
					int ari = jumpTo(ref);
					if (ari != 0) {
						history.add(ari);
					}
				}
			}
		} else if (requestCode == REQCODE_settings) {
			// MUST reload preferences
			S.calculateAppliedValuesBasedOnPreferences();
			
			applyPreferences(true);
		} else if (requestCode == REQCODE_share) {
			if (resultCode == RESULT_OK) {
				ShareActivity.Result result = ShareActivity.obtainResult(data);
				if (result != null && result.chosenIntent != null) {
					Intent chosenIntent = result.chosenIntent;
					if (U.equals(chosenIntent.getComponent().getPackageName(), "com.facebook.katana")) { //$NON-NLS-1$
						String verseUrl = chosenIntent.getStringExtra(EXTRA_verseUrl);
						if (verseUrl != null) {
							chosenIntent.putExtra(Intent.EXTRA_TEXT, verseUrl); // change text to url
						}
					}
					startActivity(chosenIntent);
				}
			}
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
		
		// loading data no need to use async. // 20100417 updated to not use async, it's not useful.
		{
			int[] pericope_aris;
			PericopeBlock[] pericope_blocks;
			int nblock;
			
			SingleChapterVerses verses = S.activeVersion.loadChapterText(this.activeBook, chapter_1);
			if (verses == null) {
				return 0;
			}
			
			//# max is set to 30 (one chapter has max of 30 blocks. Already almost impossible)
			int max = 30;
			pericope_aris = new int[max];
			pericope_blocks = new PericopeBlock[max];
			nblock = S.activeVersion.loadPericope(this.activeBook.bookId, chapter_1, pericope_aris, pericope_blocks, max); 
			
			// load xref
			int[] xrefEntryCounts = new int[256];
			S.activeVersion.getXrefEntryCounts(xrefEntryCounts, this.activeBook.bookId, chapter_1);
			
			boolean retainSelectedVerses = (!uncheckAllVerses && chapter_1 == current_chapter_1);
			
			// tell activity
			this.chapter_1 = chapter_1;
			
			lsText.setDataWithRetainSelectedVerses(retainSelectedVerses, this.activeBook, chapter_1, pericope_aris, pericope_blocks, nblock, verses, xrefEntryCounts);
			lsText.scrollToVerse(verse_1);
		}
			
		if (activeSplitVersion != null) {
			int[] pericope_aris;
			PericopeBlock[] pericope_blocks;
			int nblock;
			
			activeSplitBook = activeSplitVersion.getBook(this.activeBook.bookId);
			SingleChapterVerses verses = activeSplitVersion.loadChapterText(activeSplitBook, chapter_1);
			if (verses == null) {
				return 0;
			}
			
			//# max is set to 30 (one chapter has max of 30 blocks. Already almost impossible)
			int max = 30;
			pericope_aris = new int[max];
			pericope_blocks = new PericopeBlock[max];
			nblock = activeSplitVersion.loadPericope(activeSplitBook.bookId, chapter_1, pericope_aris, pericope_blocks, max); 
			
			// load xref
			int[] xrefEntryCounts = new int[256];
			activeSplitVersion.getXrefEntryCounts(xrefEntryCounts, activeSplitBook.bookId, chapter_1);
			
			boolean retainSelectedVerses = (!uncheckAllVerses && chapter_1 == current_chapter_1);
			
			lsSplit1.setDataWithRetainSelectedVerses(retainSelectedVerses, this.activeBook, chapter_1, pericope_aris, pericope_blocks, nblock, verses, xrefEntryCounts);
			lsSplit1.scrollToVerse(verse_1);
		}
		
		bGoto.setText(this.activeBook.reference(chapter_1));
		
		return Ari.encode(0, chapter_1, verse_1);
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
		String volumeButtonsForNavigation = Preferences.getString(getString(R.string.pref_tombolVolumeBuatPindah_key), getString(R.string.pref_tombolVolumeBuatPindah_default));
		if (! U.equals(volumeButtonsForNavigation, "default")) { // consume here //$NON-NLS-1$
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
	
	@Override public boolean onSearchRequested() {
		menuSearch2_click();
		
		return true;
	}

	@Override public void onVerseSelected(XrefDialog dialog, int ari_source, int ari_target) {
		dialog.dismiss();
		jumpToAri(ari_target);
		
		// add both xref source and target, so user can go back to source easily
		history.add(ari_source);
		history.add(ari_target);
	}

	/**
	 * If verse_1_ranges is null, verses will be ignored.
	 */
	public static String createVerseUrl(Book book, int chapter_1, String verse_1_ranges) {
		AppConfig c = AppConfig.get();
		if (book.bookId >= c.url_standardBookNames.length) {
			return null;
		}
		String tobeBook = c.url_standardBookNames[book.bookId], tobeChapter = String.valueOf(chapter_1), tobeVerse = verse_1_ranges;
		for (String format: c.url_format.split(" ")) { //$NON-NLS-1$
			if ("slash1".equals(format)) tobeChapter = "/" + tobeChapter; //$NON-NLS-1$ //$NON-NLS-2$
			if ("slash2".equals(format)) tobeVerse = "/" + tobeVerse; //$NON-NLS-1$ //$NON-NLS-2$
			if ("dot1".equals(format)) tobeChapter = "." + tobeChapter; //$NON-NLS-1$ //$NON-NLS-2$
			if ("dot2".equals(format)) tobeVerse = "." + tobeVerse; //$NON-NLS-1$ //$NON-NLS-2$
			if ("nospace0".equals(format)) tobeBook = tobeBook.replaceAll("\\s+", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		return c.url_prefix + tobeBook + tobeChapter + (verse_1_ranges == null? "": tobeVerse); //$NON-NLS-1$
	}

	VersesView.AttributeListener attributeListener = new VersesView.AttributeListener() {
		public void onAttributeClick(Book book, int chapter_1, int verse_1, int kind) {
			if (kind == Db.Bookmark2.kind_bookmark) {
				final int ari = Ari.encode(book.bookId, chapter_1, verse_1);
				String alamat = S.activeVersion.reference(ari);
				TypeBookmarkDialog dialog = new TypeBookmarkDialog(IsiActivity.this, alamat, ari);
				dialog.setListener(new TypeBookmarkDialog.Listener() {
					@Override public void onOk() {
						lsText.loadAttributeMap();
					}
				});
				dialog.show();
			} else if (kind == Db.Bookmark2.kind_note) {
				TypeNoteDialog dialog = new TypeNoteDialog(IsiActivity.this, book, chapter_1, verse_1, new TypeNoteDialog.RefreshCallback() {
					@Override public void onDone() {
						lsText.loadAttributeMap();
					}
				});
				dialog.show();
			}
		}
	};
	
	VersesView.XrefListener xrefListener = new VersesView.XrefListener() {
		@Override public void onXrefClick(int ari, int which) {
			FragmentManager fm = getSupportFragmentManager();
			XrefDialog dialog = XrefDialog.newInstance(ari, which);
			dialog.show(fm, XrefDialog.class.getSimpleName());
		}
	};
	
	VersesView.SelectedVersesListener lsText_selectedVerses = new VersesView.SelectedVersesListener() {
		@Override public void onSomeVersesSelected(VersesView v) {
			if (actionMode == null) {
				actionMode = startActionMode(actionMode_callback);
			} 
		}

		@Override public void onNoVersesSelected(VersesView v) {
			if (actionMode != null) {
				actionMode.finish();
			}
		}

		@Override public void onVerseSingleClick(VersesView v, int verse_1) {}
	};
	
	VersesView.OnVerseScrollListener lsText_verseScroll = new VersesView.OnVerseScrollListener() {
		@Override public void onVerseScroll(VersesView v, boolean isPericope, int verse_1, float prop) {
			if (!isPericope) {
				Log.d(TAG, "verse=" + verse_1 + " prop=" + prop);
				lsSplit1.scrollToVerse(verse_1, prop);
			}
		}
	};
	
	VersesView.OnVerseScrollListener lsSplit1_verseScroll = new VersesView.OnVerseScrollListener() {
		@Override public void onVerseScroll(VersesView v, boolean isPericope, int verse_1, float prop) {
			if (!isPericope) {
				lsText.scrollToVerse(verse_1, prop);
			}
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
			
			return true;
		}

		@Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			IntArrayList selected = lsText.getSelectedVerses_1();
			if (selected.size() == 0) return true;
			
			CharSequence reference = referenceFromSelectedVerses(selected);
			
			// the main verse (0 if not exist), which is only when only one verse is selected
			int mainVerse_1 = 0;
			if (selected.size() == 1) { 
				mainVerse_1 = selected.get(0);
			}
			
			int itemId = item.getItemId();
			switch (itemId) {
			case R.id.menuCopy: { // copy, can be multiple
				CharSequence textToCopy = prepareTextForCopyShare(selected, reference);
				
				U.copyToClipboard(textToCopy);
				lsText.uncheckAll();
				
				Toast.makeText(App.context, getString(R.string.alamat_sudah_disalin, reference), Toast.LENGTH_SHORT).show();
				mode.finish();
			} return true;
			case R.id.menuShare: {
				CharSequence textToShare = prepareTextForCopyShare(selected, reference);
				
				String verseUrl;
				if (selected.size() == 1) {
					verseUrl = IsiActivity.createVerseUrl(IsiActivity.this.activeBook, IsiActivity.this.chapter_1, String.valueOf(selected.get(0)));
				} else {
					StringBuilder sb = new StringBuilder();
					Book.writeVerseRange(selected, sb);
					verseUrl = IsiActivity.createVerseUrl(IsiActivity.this.activeBook, IsiActivity.this.chapter_1, sb.toString()); // use verse range
				}
				
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain"); //$NON-NLS-1$
				intent.putExtra(Intent.EXTRA_SUBJECT, reference); 
				intent.putExtra(Intent.EXTRA_TEXT, textToShare.toString());
				intent.putExtra(EXTRA_verseUrl, verseUrl);
				startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.bagikan_alamat, reference)), REQCODE_share);

				lsText.uncheckAll();
				mode.finish();
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
						lsText.uncheckAll();
						lsText.loadAttributeMap();
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
				
				TypeNoteDialog dialog = new TypeNoteDialog(IsiActivity.this, IsiActivity.this.activeBook, IsiActivity.this.chapter_1, mainVerse_1, new TypeNoteDialog.RefreshCallback() {
					@Override public void onDone() {
						lsText.uncheckAll();
						lsText.loadAttributeMap();
					}
				});
				dialog.show();
				mode.finish();
			} return true;
			case R.id.menuAddHighlight: {
				final int ariKp = Ari.encode(IsiActivity.this.activeBook.bookId, IsiActivity.this.chapter_1, 0);
				int warnaRgb = S.getDb().getWarnaRgbStabilo(ariKp, selected);
				
				new TypeHighlightDialog(IsiActivity.this, ariKp, selected, new TypeHighlightDialog.JenisStabiloCallback() {
					@Override public void onOk(int warnaRgb) {
						lsText.uncheckAll();
						lsText.loadAttributeMap();
					}
				}, warnaRgb, reference).bukaDialog();
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
			}
			return false;
		}

		@Override public void onDestroyActionMode(ActionMode mode) {
			actionMode = null;
			lsText.uncheckAll();
		}
	};
}
