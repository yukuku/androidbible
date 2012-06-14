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
import yuku.alkitab.base.ac.EdisiActivity;
import yuku.alkitab.base.ac.EdisiActivity.MEdisi;
import yuku.alkitab.base.ac.EdisiActivity.MEdisiInternal;
import yuku.alkitab.base.ac.EdisiActivity.MEdisiPreset;
import yuku.alkitab.base.ac.EdisiActivity.MEdisiYes;
import yuku.alkitab.base.ac.MenujuActivity;
import yuku.alkitab.base.ac.PengaturanActivity;
import yuku.alkitab.base.ac.RenunganActivity;
import yuku.alkitab.base.ac.Search2Activity;
import yuku.alkitab.base.ac.ShareActivity;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.compat.Api8;
import yuku.alkitab.base.config.BuildConfig;
import yuku.alkitab.base.config.D;
import yuku.alkitab.base.dialog.JenisBukmakDialog;
import yuku.alkitab.base.dialog.JenisCatatanDialog;
import yuku.alkitab.base.dialog.JenisCatatanDialog.RefreshCallback;
import yuku.alkitab.base.dialog.JenisStabiloDialog;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Blok;
import yuku.alkitab.base.model.Edisi;
import yuku.alkitab.base.model.Kitab;
import yuku.alkitab.base.storage.Db.Bukmak2;
import yuku.alkitab.base.storage.Preferences;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.Peloncat;
import yuku.alkitab.base.util.Search2Engine.Query;
import yuku.alkitab.base.util.Sejarah;
import yuku.alkitab.base.widget.AyatAdapter;
import yuku.alkitab.base.widget.CallbackSpan;

public class IsiActivity extends BaseActivity {
	public static final String TAG = IsiActivity.class.getSimpleName();
	
	// Berikut ini buat preferences_instan.
	private static final String NAMAPREF_kitabTerakhir = "kitabTerakhir"; //$NON-NLS-1$
	private static final String NAMAPREF_pasalTerakhir = "pasalTerakhir"; //$NON-NLS-1$
	private static final String NAMAPREF_ayatTerakhir = "ayatTerakhir"; //$NON-NLS-1$
	private static final String NAMAPREF_edisiTerakhir = "edisiTerakhir"; //$NON-NLS-1$
	private static final String NAMAPREF_renungan_nama = "renungan_nama"; //$NON-NLS-1$

	public static final int RESULT_pindahCara = RESULT_FIRST_USER + 1;

	private static final int REQCODE_tuju = 1;
	private static final int REQCODE_bukmak = 2;
	private static final int REQCODE_renungan = 3;
	private static final int REQCODE_pengaturan = 4;
	private static final int REQCODE_edisi = 5;
	private static final int REQCODE_search = 6;
	private static final int REQCODE_bagikan = 7;

	private static final String EXTRA_urlAyat = "urlAyat"; //$NON-NLS-1$

	ListView lsIsi;
	Button bTuju;
	ImageButton bKiri;
	ImageButton bKanan;
	View tempatJudul;
	TextView lJudul;
	View bContext;
	View root;
	
	int pasal_1 = 0;
	SharedPreferences preferences_instan;
	
	AyatAdapter ayatAdapter_;
	Sejarah sejarah;
	NfcAdapter nfcAdapter;

	//# penyimpanan state buat search2
	Query search2_query = null;
	IntArrayList search2_hasilCari = null;
	int search2_posisiTerpilih = -1;
	
	// temporary states
	Animation fadeInAnimation;
	Animation fadeOutAnimation;
	boolean showingContextButton = false;
	Boolean hasEsvsbAsal;
	
	CallbackSpan.OnClickListener paralelOnClickListener = new CallbackSpan.OnClickListener() {
		@Override
		public void onClick(View widget, Object data) {
			int ari = loncatKe((String)data);
			if (ari != 0) {
				sejarah.tambah(ari);
			}
		}
	};

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		U.nyalakanTitleBarHanyaKalauTablet(this);
		
		S.siapinKitab();
		S.hitungPenerapanBerdasarkanPengaturan();
		
		setContentView(R.layout.activity_isi);
		
		lsIsi = U.getView(this, R.id.lsIsi);
		bTuju = U.getView(this, R.id.bTuju);
		bKiri = U.getView(this, R.id.bKiri);
		bKanan = U.getView(this, R.id.bKanan);
		tempatJudul = U.getView(this, R.id.tempatJudul);
		lJudul = U.getView(this, R.id.lJudul);
		bContext = U.getView(this, R.id.bContext);
		root = U.getView(this, R.id.root);
		
		terapkanPengaturan(false);

		lsIsi.setOnItemClickListener(lsIsi_itemClick);
		lsIsi.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		
		bTuju.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { bTuju_click(); }
		});
		bTuju.setOnLongClickListener(new View.OnLongClickListener() {
			@Override public boolean onLongClick(View v) { bTuju_longClick(); return true; }
		});
		
		lJudul.setOnClickListener(new View.OnClickListener() { // pinjem bTuju
			@Override public void onClick(View v) { bTuju_click(); }
		});
		lJudul.setOnLongClickListener(new View.OnLongClickListener() { // pinjem bTuju
			@Override public boolean onLongClick(View v) { bTuju_longClick(); return true; }
		});
		
		bKiri.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { bKiri_click(); }
		});
		bKanan.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) { bKanan_click(); }
		});
		
		bContext.setOnClickListener(bContext_click);
		
		lsIsi.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				int action = event.getAction();
				if (action == KeyEvent.ACTION_DOWN) {
					return tekan(keyCode);
				} else if (action == KeyEvent.ACTION_MULTIPLE) {
					return tekan(keyCode);
				}
				return false;
			}
		});
		
		// adapter
		ayatAdapter_ = new AyatAdapter(this, paralelOnClickListener, new AtributListener());
		lsIsi.setAdapter(ayatAdapter_);
		
		// muat preferences_instan, dan atur renungan
		preferences_instan = App.getPreferencesInstan();
		sejarah = new Sejarah(preferences_instan);
		{
			String renungan_nama = preferences_instan.getString(NAMAPREF_renungan_nama, null);
			if (renungan_nama != null) {
				for (String nama: RenunganActivity.ADA_NAMA) {
					if (renungan_nama.equals(nama)) {
						S.penampungan.renungan_nama = renungan_nama;
					}
				}
			}
		}
		
		// kembalikan (edisi; kitab; pasal dan ayat) terakhir.
		String edisiTerakhir = preferences_instan.getString(NAMAPREF_edisiTerakhir, null);
		int kitabTerakhir = preferences_instan.getInt(NAMAPREF_kitabTerakhir, 0);
		int pasalTerakhir = preferences_instan.getInt(NAMAPREF_pasalTerakhir, 0);
		int ayatTerakhir = preferences_instan.getInt(NAMAPREF_ayatTerakhir, 0);
		Log.d(TAG, "Akan ke posisi terakhir: edisi " + edisiTerakhir + " kitab " + kitabTerakhir + " pasal " + kitabTerakhir + " ayat " + ayatTerakhir); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		muatEdisiTerakhir(edisiTerakhir);
		
		{ // muat kitab
			Kitab k = S.edisiAktif.getKitab(kitabTerakhir);
			if (k != null) {
				S.kitabAktif = k;
			}
		}
		
		// muat pasal dan ayat
		tampil(pasalTerakhir, ayatTerakhir);

		if (D.EBUG) { // supaya ga lupa matiin pas release
			new AlertDialog.Builder(this)
			.setMessage("D.EBUG nyala!") //$NON-NLS-1$
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
						obj.put("ari", Ari.encode(S.kitabAktif.pos, IsiActivity.this.pasal_1, IsiActivity.this.getAyatBerdasarSkrol())); //$NON-NLS-1$
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
		}	}

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
							IsiActivity.this.loncatKeAri(ari);
						}
					} catch (JSONException e) {
						Log.e(TAG, "Malformed json from nfc", e); //$NON-NLS-1$
					}
				}
			}
		}
	}

	private void muatEdisiTerakhir(String edisiTerakhir) {
		if (edisiTerakhir == null || MEdisiInternal.getEdisiInternalId().equals(edisiTerakhir)) {
			return; // udah di internal, ga usah ngapa2in lagi!
		}
		
		BuildConfig c = BuildConfig.get(this);
		
		// coba preset dulu!
		for (MEdisiPreset preset: c.xpreset) { // 2. preset
			if (preset.getEdisiId().equals(edisiTerakhir)) {
				if (preset.adaFileDatanya()) {
					muatEdisi(preset);
				} else { 
					return; // ini harusnya yang dipilih, tapi ternyata filenya ga ada, mari fallback aja
				}
			}
		}
		
		// masih belum cocok, mari kita cari di daftar yes
		List<MEdisiYes> xyes = S.getDb().listSemuaEdisi();
		for (MEdisiYes yes: xyes) {
			if (yes.getEdisiId().equals(edisiTerakhir)) {
				if (yes.adaFileDatanya()) {
					muatEdisi(yes);
				} else { 
					return; // ini harusnya yang dipilih, tapi ternyata filenya ga ada, mari fallback aja
				}
			}
		}
	}
	
	protected void muatEdisi(final MEdisi me) {
		// buat rollback
		Edisi edisiAktifLama = S.edisiAktif;
		String edisiIdLama = S.edisiId;
		
		boolean sukses = false;
		try {
			Edisi edisi = me.getEdisi(getApplicationContext());
			
			if (edisi != null) {
				S.edisiAktif = edisi;
				S.edisiId = me.getEdisiId();
				S.siapinKitab();
				
				Kitab k = S.edisiAktif.getKitab(S.kitabAktif.pos);
				if (k != null) {
					// assign kitab aktif dengan yang baru, ga usa perhatiin pos
					S.kitabAktif = k;
				} else {
					S.kitabAktif = S.edisiAktif.getKitabPertama(); // apa boleh buat, ga ketemu...
				}
				
				tampil(pasal_1, getAyatBerdasarSkrol());
			} else {
				new AlertDialog.Builder(IsiActivity.this)
				.setMessage(getString(R.string.ada_kegagalan_membuka_edisiid, me.getEdisiId()))
				.setPositiveButton(R.string.ok, null)
				.show();
			}
			sukses = true;
		} catch (Throwable e) { // supaya ga kres pas mulai jalanin prog
			Log.e(TAG, "gagal di muatEdisi", e);  //$NON-NLS-1$
		} finally {
			if (!sukses) {
				S.edisiAktif = edisiAktifLama;
				S.edisiId = edisiIdLama;
				S.siapinKitab();
			}
		}
	}
	
	@Override protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		
		hideOrShowContextButton();
	}
	
	void showContextButton() {
		if (! showingContextButton) {
			if (fadeInAnimation == null) {
				fadeInAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
			}
			bContext.setVisibility(View.VISIBLE);
			bContext.startAnimation(fadeInAnimation);
			bContext.setEnabled(true);
			showingContextButton = true;
		}
	}
	
	void hideContextButton() {
		if (showingContextButton) {
			if (fadeOutAnimation == null) {
				fadeOutAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
			}
			fadeOutAnimation.setAnimationListener(fadeOutAnimation_animation);
			bContext.startAnimation(fadeOutAnimation);
			bContext.setEnabled(false);
			showingContextButton = false;
		}
	}

	private AnimationListener fadeOutAnimation_animation = new AnimationListener() {
		@Override public void onAnimationStart(Animation animation) {}
		@Override public void onAnimationRepeat(Animation animation) {}
		@Override public void onAnimationEnd(Animation animation) {
			bContext.setVisibility(View.INVISIBLE);
		}
	};
	
	protected boolean tekan(int keyCode) {
		String tombolVolumeBuatPindah = Preferences.getString(R.string.pref_tombolVolumeBuatPindah_key, R.string.pref_tombolVolumeBuatPindah_default);
		if (U.equals(tombolVolumeBuatPindah, "pasal")) { //$NON-NLS-1$
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
		} else if (U.equals(tombolVolumeBuatPindah, "ayat")) { //$NON-NLS-1$
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) keyCode = KeyEvent.KEYCODE_DPAD_UP;
		}

		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
			bKiri_click();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
			bKanan_click();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
			int posLama = getPosisiBerdasarSkrol();
			if (posLama < ayatAdapter_.getCount() - 1) {
				lsIsi.setSelectionFromTop(posLama+1, lsIsi.getVerticalFadingEdgeLength());
			}
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
			int posLama = getPosisiBerdasarSkrol();
			if (posLama >= 1) {
				int posBaru = posLama - 1;
				while (posBaru > 0) { // cek disabled, kalo iya, mundurin lagi
					if (ayatAdapter_.isEnabled(posBaru)) break;
					posBaru--;
				}
				lsIsi.setSelectionFromTop(posBaru, lsIsi.getVerticalFadingEdgeLength());
			} else {
				lsIsi.setSelectionFromTop(0, lsIsi.getVerticalFadingEdgeLength());
			}
			return true;
		}
		return false;
	}

	private synchronized void nyalakanTerusLayarKalauDiminta() {
		if (Preferences.getBoolean(R.string.pref_nyalakanTerusLayar_key, R.bool.pref_nyalakanTerusLayar_default)) {
			lsIsi.setKeepScreenOn(true);
		}
	}

	private synchronized void matikanLayarKalauSudahBolehMati() {
		lsIsi.setKeepScreenOn(false);
	}
	
	int loncatKe(String alamat) {
		if (alamat.trim().length() == 0) {
			return 0;
		}
		
		Log.d(TAG, "akan loncat ke " + alamat); //$NON-NLS-1$
		
		Peloncat peloncat = new Peloncat();
		boolean sukses = peloncat.parse(alamat);
		if (! sukses) {
			Toast.makeText(this, getString(R.string.alamat_tidak_sah_alamat, alamat), Toast.LENGTH_SHORT).show();
			return 0;
		}
		
		int kitabPos = peloncat.getKitab(S.edisiAktif.getConsecutiveXkitab());
		Kitab terpilih;
		if (kitabPos != -1) {
			Kitab k = S.edisiAktif.getKitab(kitabPos);
			if (k != null) {
				terpilih = k;
			} else {
				// not avail, just fallback
				terpilih = S.kitabAktif;
			}
		} else {
			terpilih = S.kitabAktif;
		}
		
		// set kitab
		S.kitabAktif = terpilih;
		
		int pasal = peloncat.getPasal();
		int ayat = peloncat.getAyat();
		int ari_pa;
		if (pasal == -1 && ayat == -1) {
			ari_pa = tampil(1, 1);
		} else {
			ari_pa = tampil(pasal, ayat);
		}
		
		return Ari.encode(terpilih.pos, ari_pa);
	}
	
	void loncatKeAri(int ari) {
		if (ari == 0) return;
		
		Log.d(TAG, "akan loncat ke ari 0x" + Integer.toHexString(ari)); //$NON-NLS-1$
		
		int kitabPos = Ari.toKitab(ari);
		Kitab k = S.edisiAktif.getKitab(kitabPos);
		
		if (k != null) {
			S.kitabAktif = k;
		} else {
			Log.w(TAG, "mana ada kitabPos " + kitabPos + " dari ari " + ari); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		
		tampil(Ari.toPasal(ari), Ari.toAyat(ari));
	}

	private OnItemClickListener lsIsi_itemClick = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			hideOrShowContextButton();
		}
	};
	
	private void hideOrShowContextButton() {
		SparseBooleanArray checkedPositions = lsIsi.getCheckedItemPositions();
		boolean anyChecked = false;
		for (int i = 0; i < checkedPositions.size(); i++) if (checkedPositions.valueAt(i)) {
			anyChecked = true; 
			break;
		}
		
		if (anyChecked) {
			showContextButton();
		} else {
			hideContextButton();
		}
	}

	private IntArrayList getAyatTerpilih_1() {
		// hitung ada berapa yang terpilih
		SparseBooleanArray positions = lsIsi.getCheckedItemPositions();
		if (positions == null) {
			return new IntArrayList(0);
		}
		
		IntArrayList res = new IntArrayList(positions.size());
		for (int i = 0, len = positions.size(); i < len; i++) {
			if (positions.valueAt(i)) {
				int position = positions.keyAt(i);
				int ayat_1 = ayatAdapter_.getAyatDariPosition(position);
				if (ayat_1 >= 1) res.add(ayat_1);
			}
		}
		return res;
	}
	
	private CharSequence alamatDariAyatTerpilih(IntArrayList ayatTerpilih) {
		if (ayatTerpilih.size() == 0) {
			// harusnya mustahil. Maka ga usa ngapa2in deh.
			return S.alamat(S.kitabAktif, this.pasal_1);
		} else if (ayatTerpilih.size() == 1) {
			return S.alamat(S.kitabAktif, this.pasal_1, ayatTerpilih.get(0));
		} else {
			return S.alamat(S.kitabAktif, this.pasal_1, ayatTerpilih);
		}
	}
	
	static class FakeContextMenu {
		static class Item {
			String label;
			Item(String label) {
				this.label = label;
			}
		}
		
		Item menuSalinAyat;
		Item menuTambahBukmak;
		Item menuTambahCatatan;
		Item menuTambahStabilo;
		Item menuBagikan;
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
		res.menuSalinAyat = new Item(getString(R.string.salin_ayat));
		res.menuTambahBukmak = new Item(getString(R.string.tambah_pembatas_buku));
		res.menuTambahCatatan = new Item(getString(R.string.tulis_catatan));
		res.menuTambahStabilo = new Item(getString(R.string.highlight_stabilo));
		res.menuBagikan = new Item(getString(R.string.bagikan));
		
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
		items.add(res.menuSalinAyat);
		items.add(res.menuTambahBukmak);
		items.add(res.menuTambahCatatan);
		items.add(res.menuTambahStabilo);
		items.add(res.menuBagikan);
		
		if (hasEsvsbAsal) {
			items.add(res.menuEsvsbasal);
		}
		res.items = items;
		
		return res;
	};
	

	public void showFakeContextMenu() {
		IntArrayList terpilih = getAyatTerpilih_1();
		if (terpilih.size() == 0) return;

		// pembuatan menu manual
		final FakeContextMenu menu = getFakeContextMenu();
		
		new AlertDialog.Builder(this)
		.setTitle(alamatDariAyatTerpilih(terpilih))
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
		IntArrayList terpilih = getAyatTerpilih_1();
		if (terpilih.size() == 0) return;
		
		CharSequence alamat = alamatDariAyatTerpilih(terpilih);
		
		// ayat utama (0 kalo ga ada), yaitu kalau cuma kepilih satu.
		int ayatUtama_1 = 0;
		if (terpilih.size() == 1) { // ga ada yang di bawah pencetan jari, tapi cuma 1 ayat yang di terpilih, maka pasang ayat itu aja.
			ayatUtama_1 = terpilih.get(0);
		}
		
		if (item == menu.menuSalinAyat) { // salin, bisa multiple
			StringBuilder salinan = new StringBuilder();
			salinan.append(alamat).append("  "); //$NON-NLS-1$
			
			// append tiap ayat terpilih
			for (int i = 0; i < terpilih.size(); i++) {
				int ayat_1 = terpilih.get(i);
				if (i != 0) salinan.append('\n');
				salinan.append(U.buangKodeKusus(ayatAdapter_.getAyat(ayat_1)));
			}
			
			U.salin(salinan);
			uncheckAll();
			
			Toast.makeText(this, getString(R.string.alamat_sudah_disalin, alamat), Toast.LENGTH_SHORT).show();
		} else if (item == menu.menuTambahBukmak) {
			if (ayatUtama_1 == 0) {
				// ga ada ayat utama, fokuskan ke yang relevan!
				ayatUtama_1 = terpilih.get(0);
				
				skrolSupayaAyatKeliatan(ayatUtama_1);
			}
			
			final int ari = Ari.encode(S.kitabAktif.pos, this.pasal_1, ayatUtama_1);
			
			JenisBukmakDialog dialog = new JenisBukmakDialog(this, S.alamat(S.kitabAktif, this.pasal_1, ayatUtama_1), ari);
			dialog.setListener(new JenisBukmakDialog.Listener() {
				@Override public void onOk() {
					uncheckAll();
					ayatAdapter_.muatAtributMap();
				}
			});
			dialog.bukaDialog();
		} else if (item == menu.menuTambahCatatan) {
			if (ayatUtama_1 == 0) {
				// ga ada ayat utama, fokuskan ke yang relevan!
				ayatUtama_1 = terpilih.get(0);
				
				skrolSupayaAyatKeliatan(ayatUtama_1);
			}
			
			JenisCatatanDialog dialog = new JenisCatatanDialog(IsiActivity.this, S.kitabAktif, this.pasal_1, ayatUtama_1, new RefreshCallback() {
				@Override public void udahan() {
					uncheckAll();
					ayatAdapter_.muatAtributMap();
				}
			});
			dialog.bukaDialog();
		} else if (item == menu.menuTambahStabilo) {
			final int ariKp = Ari.encode(S.kitabAktif.pos, this.pasal_1, 0);
			int warnaRgb = S.getDb().getWarnaRgbStabilo(ariKp, terpilih);
			
			new JenisStabiloDialog(this, ariKp, terpilih, new JenisStabiloDialog.JenisStabiloCallback() {
				@Override public void onOk(int warnaRgb) {
					uncheckAll();
					ayatAdapter_.muatAtributMap();
				}
			}, warnaRgb, alamat).bukaDialog();
		} else if (item == menu.menuBagikan) {
			StringBuilder sb = new StringBuilder();
			sb.append(alamat).append("  "); //$NON-NLS-1$
			
			// append tiap ayat terpilih
			for (int i = 0; i < terpilih.size(); i++) {
				int ayat_1 = terpilih.get(i);
				if (i != 0) sb.append('\n');
				sb.append(U.buangKodeKusus(ayatAdapter_.getAyat(ayat_1)));
			}
			
			String urlAyat;
			if (terpilih.size() == 1) {
				urlAyat = S.bikinUrlAyat(S.kitabAktif, this.pasal_1, String.valueOf(terpilih.get(0)));
			} else {
				StringBuilder sb2 = new StringBuilder();
				S.tulisAyatRange(terpilih, sb2);
				urlAyat = S.bikinUrlAyat(S.kitabAktif, this.pasal_1, sb2.toString()); // pake ayat range
			}
			
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("text/plain"); //$NON-NLS-1$
			intent.putExtra(Intent.EXTRA_SUBJECT, alamat); 
			intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
			intent.putExtra(EXTRA_urlAyat, urlAyat);
			startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.bagikan_alamat, alamat)), REQCODE_bagikan);

			uncheckAll();
		} else if (item == menu.menuEsvsbasal) {
			final int ari = Ari.encode(S.kitabAktif.pos, this.pasal_1, ayatUtama_1);

			try {
				Intent intent = new Intent("yuku.esvsbasal.action.GOTO"); //$NON-NLS-1$
				intent.putExtra("ari", ari); //$NON-NLS-1$
				startActivity(intent);
			} catch (Exception e) {
				Log.e(TAG, "ESVSB starting", e); //$NON-NLS-1$
			}
		}
	}

	private void skrolSupayaAyatKeliatan(int ayatUtama_1) {
		int position = ayatAdapter_.getPositionAwalPerikopDariAyat(ayatUtama_1);
		if (Build.VERSION.SDK_INT >= 8) {
			Api8.ListView_smoothScrollToPosition(lsIsi, position);
		} else {
			lsIsi.setSelectionFromTop(position, lsIsi.getVerticalFadingEdgeLength());
		}
	}

	private void terapkanPengaturan(boolean bahasaJuga) {
		// penerapan langsung warnaLatar
		{
			root.setBackgroundColor(S.penerapan.warnaLatar);
			lsIsi.setCacheColorHint(S.penerapan.warnaLatar);
		}
		
		// penerapan langsung sembunyi navigasi
		{
			View panelNavigasi = findViewById(R.id.panelNavigasi);
			if (Preferences.getBoolean(R.string.pref_tanpaNavigasi_key, R.bool.pref_tanpaNavigasi_default)) {
				panelNavigasi.setVisibility(View.GONE);
				tempatJudul.setVisibility(View.VISIBLE);
			} else {
				panelNavigasi.setVisibility(View.VISIBLE);
				tempatJudul.setVisibility(View.GONE);
			}
		}
		
		if (bahasaJuga) {
			S.terapkanPengaturanBahasa(null, 0);
		}
		
		// wajib
		lsIsi.invalidateViews();
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		
		Editor editor = preferences_instan.edit();
		editor.putInt(NAMAPREF_kitabTerakhir, S.kitabAktif.pos);
		editor.putInt(NAMAPREF_pasalTerakhir, pasal_1);
		editor.putInt(NAMAPREF_ayatTerakhir, getAyatBerdasarSkrol());
		editor.putString(NAMAPREF_renungan_nama, S.penampungan.renungan_nama);
		editor.putString(NAMAPREF_edisiTerakhir, S.edisiId);
		sejarah.simpan(editor);
		editor.commit();
		
		matikanLayarKalauSudahBolehMati();
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		//# nyala terus layar
		nyalakanTerusLayarKalauDiminta();
	}
	
	/**
	 * @return ayat mulai dari 1
	 */
	int getAyatBerdasarSkrol() {
		return ayatAdapter_.getAyatDariPosition(getPosisiBerdasarSkrol());
	}
	
	int getPosisiBerdasarSkrol() {
		int pos = lsIsi.getFirstVisiblePosition();

		// cek apakah paling atas uda keskrol
		View child = lsIsi.getChildAt(0); 
		if (child != null) {
			int top = child.getTop();
			if (top == 0) {
				return pos;
			}
			int bottom = child.getBottom();
			if (bottom > lsIsi.getVerticalFadingEdgeLength()) {
				return pos;
			} else {
				return pos+1;
			}
		}
		
		return pos;
	}

	void bTuju_click() {
		if (Preferences.getBoolean(R.string.pref_tombolAlamatLoncat_key, R.bool.pref_tombolAlamatLoncat_default)) {
			bukaDialogLoncat();
		} else {
			bukaDialogTuju();
		}
	}
	
	void bTuju_longClick() {
		if (sejarah.getN() > 0) {
			new AlertDialog.Builder(this)
			.setAdapter(sejarahAdapter, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					int ari = sejarah.getAri(which);
					loncatKeAri(ari);
					sejarah.tambah(ari);
				}
			})
			.setNegativeButton(R.string.cancel, null)
			.show();
		} else {
			Toast.makeText(this, R.string.belum_ada_sejarah, Toast.LENGTH_SHORT).show();
		}
	}
	
	private ListAdapter sejarahAdapter = new BaseAdapter() {
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView res = (TextView) convertView;
			if (res == null) {
				res = (TextView) LayoutInflater.from(IsiActivity.this).inflate(android.R.layout.select_dialog_item, null);
			}
			int ari = sejarah.getAri(position);
			res.setText(S.alamat(S.edisiAktif, ari));
			return res;
		}
		
		@Override
		public long getItemId(int position) {
			return position;
		}
		
		@Override
		public Integer getItem(int position) {
			return sejarah.getAri(position);
		}
		
		@Override
		public int getCount() {
			return sejarah.getN();
		}
	};
	
	void bukaDialogTuju() {
		Intent intent = new Intent(this, MenujuActivity.class);
		intent.putExtra(MenujuActivity.EXTRA_pasal, pasal_1);
		
		int ayat = getAyatBerdasarSkrol();
		intent.putExtra(MenujuActivity.EXTRA_ayat, ayat);
		
		startActivityForResult(intent, REQCODE_tuju);
	}
	
	void bukaDialogLoncat() {
		final View loncat = LayoutInflater.from(this).inflate(R.layout.dialog_loncat, null);
		final TextView lContohLoncat = (TextView) loncat.findViewById(R.id.lContohLoncat);
		final EditText tAlamatLoncat = (EditText) loncat.findViewById(R.id.tAlamatLoncat);
		final ImageButton bKeTuju = (ImageButton) loncat.findViewById(R.id.bKeTuju);

		{
			String alamatContoh = S.alamat(S.kitabAktif, IsiActivity.this.pasal_1, getAyatBerdasarSkrol());
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
				int ari = loncatKe(tAlamatLoncat.getText().toString());
				if (ari != 0) {
					sejarah.tambah(ari);
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
				bukaDialogTuju();
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
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		bikinMenu(menu);
		
		return true;
	}
	
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		if (menu != null) {
			bikinMenu(menu);
			
			MenuItem menuTuju = menu.findItem(R.id.menuTuju);
			if (menuTuju != null) {
				if (Preferences.getBoolean(R.string.pref_tombolAlamatLoncat_key, R.bool.pref_tombolAlamatLoncat_default)) {
					menuTuju.setIcon(R.drawable.ic_menu_loncat);
					menuTuju.setTitle(R.string.loncat_v);
				} else {
					menuTuju.setIcon(R.drawable.ic_menu_tuju);
					menuTuju.setTitle(R.string.tuju_v);
				}
			}
		}
		
		return true;
	}
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuTuju:
			bTuju_click();
			return true;
		case R.id.menuBukmak:
			startActivityForResult(new Intent(this, BukmakActivity.class), REQCODE_bukmak);
			return true;
		case R.id.menuSearch2:
			menuSearch2_click();
			return true;
		case R.id.menuEdisi:
			bukaDialogEdisi();
			return true;
		case R.id.menuRenungan: 
			startActivityForResult(new Intent(this, RenunganActivity.class), REQCODE_renungan);
			return true;
		case R.id.menuTentang:
			startActivity(new Intent(this, AboutActivity.class));
			return true;
		case R.id.menuPengaturan:
			startActivityForResult(new Intent(this, PengaturanActivity.class), REQCODE_pengaturan);
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
		final List<MEdisi> data = new ArrayList<MEdisi>();  // harus bareng2 sama atas
		
		pilihan.add(c.internalJudul); // 1. internal
		data.add(new MEdisiInternal());
		
		for (MEdisiPreset preset: c.xpreset) { // 2. preset
			if (preset.adaFileDatanya() && preset.getAktif()) {
				pilihan.add(preset.judul);
				data.add(preset);
			}
		}
		
		// 3. yes yang aktif
		List<MEdisiYes> xyes = S.getDb().listSemuaEdisi();
		for (MEdisiYes yes: xyes) {
			if (yes.adaFileDatanya() && yes.getAktif()) {
				pilihan.add(yes.judul);
				data.add(yes);
			}
		}
		
		int terpilih = -1;
		if (S.edisiId == null) {
			terpilih = 0;
		} else {
			for (int i = 0; i < data.size(); i++) {
				MEdisi me = data.get(i);
				if (me.getEdisiId().equals(S.edisiId)) {
					terpilih = i;
					break;
				}
			}
		}

		new AlertDialog.Builder(this)
		.setTitle(R.string.pilih_edisi)
		.setSingleChoiceItems(pilihan.toArray(new String[pilihan.size()]), terpilih, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				final MEdisi me = data.get(which);
				
				muatEdisi(me);
				dialog.dismiss();
			}
		})
		.setPositiveButton(R.string.versi_lainnya, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				Intent intent = new Intent(getApplicationContext(), EdisiActivity.class);
				startActivityForResult(intent, REQCODE_edisi);
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
	}

	private void menuSearch2_click() {
		startActivityForResult(Search2Activity.createIntent(search2_query, search2_hasilCari, search2_posisiTerpilih, S.kitabAktif.pos), REQCODE_search);
	}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "onActivityResult reqCode=0x" + Integer.toHexString(requestCode) + " resCode=" + resultCode + " data=" + data); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		
		if (requestCode == REQCODE_tuju) {
			if (resultCode == RESULT_OK) {
				int pasal = data.getIntExtra(MenujuActivity.EXTRA_pasal, 0);
				int ayat = data.getIntExtra(MenujuActivity.EXTRA_ayat, 0);
				int kitabPos = data.getIntExtra(MenujuActivity.EXTRA_kitab, AdapterView.INVALID_POSITION);
				
				if (kitabPos != AdapterView.INVALID_POSITION) {
					// ganti kitab
					Kitab k = S.edisiAktif.getKitab(kitabPos);
					if (k != null) {
						S.kitabAktif = k;
					}
				}
				
				int ari_pa = tampil(pasal, ayat);
				sejarah.tambah(Ari.encode(kitabPos, ari_pa));
			} else if (resultCode == RESULT_pindahCara) {
				bukaDialogLoncat();
			}
		} else if (requestCode == REQCODE_bukmak) {
			ayatAdapter_.muatAtributMap();

			if (resultCode == RESULT_OK) {
				int ari = data.getIntExtra(BukmakActivity.EXTRA_ariTerpilih, 0);
				if (ari != 0) { // 0 berarti ga ada apa2, karena ga ada pasal 0 ayat 0
					loncatKeAri(ari);
					sejarah.tambah(ari);
				}
			}
		} else if (requestCode == REQCODE_search) {
			if (resultCode == RESULT_OK) {
				Search2Activity.Result result = Search2Activity.obtainResult(data);
				if (result != null) {
					if (result.ariTerpilih != -1) {
						loncatKeAri(result.ariTerpilih);
						sejarah.tambah(result.ariTerpilih);
					}
					
					search2_query = result.query;
					search2_hasilCari = result.hasilCari;
					search2_posisiTerpilih = result.posisiTerpilih;
				}
			}
		} else if (requestCode == REQCODE_renungan) {
			if (data != null) {
				String alamat = data.getStringExtra(RenunganActivity.EXTRA_alamat);
				if (alamat != null) {
					int ari = loncatKe(alamat);
					if (ari != 0) {
						sejarah.tambah(ari);
					}
				}
			}
		} else if (requestCode == REQCODE_pengaturan) {
			// HARUS rilod pengaturan.
			S.hitungPenerapanBerdasarkanPengaturan();
			
			terapkanPengaturan(true);
		} else if (requestCode == REQCODE_bagikan) {
			if (resultCode == RESULT_OK) {
				ShareActivity.Result result = ShareActivity.obtainResult(data);
				if (result != null && result.chosenIntent != null) {
					Intent chosenIntent = result.chosenIntent;
					if (U.equals(chosenIntent.getComponent().getPackageName(), "com.facebook.katana")) { //$NON-NLS-1$
						String urlAyat = chosenIntent.getStringExtra(EXTRA_urlAyat);
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
	int tampil(int pasal_1, int ayat_1) {
		if (pasal_1 < 1) pasal_1 = 1;
		if (pasal_1 > S.kitabAktif.npasal) pasal_1 = S.kitabAktif.npasal;
		
		if (ayat_1 < 1) ayat_1 = 1;
		if (ayat_1 > S.kitabAktif.nayat[pasal_1 - 1]) ayat_1 = S.kitabAktif.nayat[pasal_1 - 1];
		
		// muat data GA USAH pake async dong. // diapdet 20100417 biar ga usa async, ga guna.
		{
			int[] perikop_xari;
			Blok[] perikop_xblok;
			int nblok;
			
			String[] xayat = S.muatTeks(S.edisiAktif, S.kitabAktif, pasal_1);
			
			//# max dibikin pol 30 aja (1 pasal max 30 blok, cukup mustahil)
			int max = 30;
			perikop_xari = new int[max];
			perikop_xblok = new Blok[max];
			nblok = S.edisiAktif.pembaca.muatPerikop(S.edisiAktif, S.kitabAktif.pos, pasal_1, perikop_xari, perikop_xblok, max); 
			
			//# isi adapter dengan data baru, pastikan semua checked state direset dulu
			uncheckAll();
			ayatAdapter_.setData(S.kitabAktif, pasal_1, xayat, perikop_xari, perikop_xblok, nblok);
			ayatAdapter_.muatAtributMap();
			
			// kasi tau activity
			this.pasal_1 = pasal_1;
			
			final int position = ayatAdapter_.getPositionAwalPerikopDariAyat(ayat_1);
			
			if (position == -1) {
				Log.w(TAG, "ga bisa ketemu ayat " + ayat_1 + ", ANEH!"); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				lsIsi.setSelectionFromTop(position, lsIsi.getVerticalFadingEdgeLength());
			}
		}
		
		String judul = S.alamat(S.kitabAktif, pasal_1);
		lJudul.setText(judul);
		bTuju.setText(judul);
		
		return Ari.encode(0, pasal_1, ayat_1);
	}

	private void uncheckAll() {
		SparseBooleanArray checkedPositions = lsIsi.getCheckedItemPositions();
		if (checkedPositions != null && checkedPositions.size() > 0) {
			for (int i = checkedPositions.size() - 1; i >= 0; i--) {
				if (checkedPositions.valueAt(i)) {
					lsIsi.setItemChecked(checkedPositions.keyAt(i), false);
				}
			}
		}
		hideContextButton();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (tekan(keyCode)) return true;
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		if (tekan(keyCode)) return true;
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
	
	void bKiri_click() {
		Kitab kitabKini = S.kitabAktif;
		if (pasal_1 == 1) {
			// uda di awal pasal, masuk ke kitab sebelum
			int cobaKitabPos = kitabKini.pos - 1;
			while (cobaKitabPos >= 0) {
				Kitab kitabBaru = S.edisiAktif.getKitab(cobaKitabPos);
				if (kitabBaru != null) {
					S.kitabAktif = kitabBaru;
					int pasalBaru_1 = kitabBaru.npasal; // ke pasal terakhir
					tampil(pasalBaru_1, 1);
					break;
				}
				cobaKitabPos--;
			}
			// whileelse: sekarang sudah Kejadian 1. Ga usa ngapa2in
		} else {
			int pasalBaru = pasal_1 - 1;
			tampil(pasalBaru, 1);
		}
	}
	
	void bKanan_click() {
		Kitab kitabKini = S.kitabAktif;
		if (pasal_1 >= kitabKini.npasal) {
			int maxKitabPos = S.edisiAktif.getMaxKitabPosTambahSatu();
			int cobaKitabPos = kitabKini.pos + 1;
			while (cobaKitabPos < maxKitabPos) {
				Kitab kitabBaru = S.edisiAktif.getKitab(cobaKitabPos);
				if (kitabBaru != null) {
					S.kitabAktif = kitabBaru;
					tampil(1, 1);
					break;
				}
				cobaKitabPos++;
			}
			// whileelse: uda di Wahyu (atau kitab terakhir) pasal terakhir. Ga usa ngapa2in
		} else {
			int pasalBaru = pasal_1 + 1;
			tampil(pasalBaru, 1);
		}
	}

	private OnClickListener bContext_click = new OnClickListener() {
		@Override public void onClick(View v) {
			showFakeContextMenu();
		}
	};
	
	@Override
	public boolean onSearchRequested() {
		menuSearch2_click();
		
		return true;
	}

	public class AtributListener {
		public void onClick(Kitab kitab_, int pasal_1, int ayat_1, int jenis) {
			if (jenis == Bukmak2.jenis_bukmak) {
				final int ari = Ari.encode(kitab_.pos, pasal_1, ayat_1);
				String alamat = S.alamat(S.edisiAktif, ari);
				JenisBukmakDialog dialog = new JenisBukmakDialog(IsiActivity.this, alamat, ari);
				dialog.setListener(new JenisBukmakDialog.Listener() {
					@Override public void onOk() {
						ayatAdapter_.muatAtributMap();
					}
				});
				dialog.bukaDialog();
			} else if (jenis == Bukmak2.jenis_catatan) {
				JenisCatatanDialog dialog = new JenisCatatanDialog(IsiActivity.this, kitab_, pasal_1, ayat_1, new RefreshCallback() {
					@Override public void udahan() {
						ayatAdapter_.muatAtributMap();
					}
				});
				dialog.bukaDialog();
			}
		}
	}
}
