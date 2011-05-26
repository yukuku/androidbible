package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.*;
import android.graphics.drawable.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.text.util.*;
import android.util.*;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;

import java.util.*;

import yuku.alkitab.*;
import yuku.alkitab.base.BukmakEditor.Listener;
import yuku.alkitab.base.EdisiActivity.MEdisi;
import yuku.alkitab.base.EdisiActivity.MEdisiInternal;
import yuku.alkitab.base.EdisiActivity.MEdisiPreset;
import yuku.alkitab.base.EdisiActivity.MEdisiYes;
import yuku.alkitab.base.GelembungDialog.RefreshCallback;
import yuku.alkitab.base.config.*;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.storage.Db.Bukmak2;
import yuku.alkitab.base.storage.*;
import yuku.andoutil.*;

public class IsiActivity extends Activity {
	public static final String TAG = IsiActivity.class.getSimpleName();
	
	private static final String NAMAPREF_kitabTerakhir = "kitabTerakhir"; //$NON-NLS-1$
	private static final String NAMAPREF_pasalTerakhir = "pasalTerakhir"; //$NON-NLS-1$
	private static final String NAMAPREF_ayatTerakhir = "ayatTerakhir"; //$NON-NLS-1$
	private static final String NAMAPREF_terakhirMintaFidbek = "terakhirMintaFidbek"; //$NON-NLS-1$
	private static final String NAMAPREF_renungan_nama = "renungan_nama"; //$NON-NLS-1$

	public static final int RESULT_pindahCara = RESULT_FIRST_USER + 1;

	String[] xayat;
	ListView lsIsi;
	Button bTuju;
	ImageButton bKiri;
	ImageButton bKanan;
	View tempatJudul;
	TextView lJudul;
	
	int pasal_1 = 0;
	int ayatContextMenu_1 = -1;
	String isiAyatContextMenu = null;
	SharedPreferences preferences_instan;
	Float ukuranAsalHurufIsi;
	Handler handler = new Handler();
	DisplayMetrics displayMetrics;
	ProgressDialog dialogBikinIndex;
	boolean lagiBikinIndex = false;
	boolean perluReloadMenuWaktuOnMenuOpened = false;
	
	private AyatAdapter ayatAdapter_;
	private Sejarah sejarah;

	//# penyimpanan state buat search2
	String search2_carian = null;
	boolean search2_filter_lama = true;
	boolean search2_filter_baru = true;
	IntArrayList search2_hasilCari = null;
	int search2_posisiTerpilih = -1;

	
	CallbackSpan.OnClickListener paralelOnClickListener = new CallbackSpan.OnClickListener() {
		@Override
		public void onClick(View widget, Object data) {
			loncatKe((String)data);
		}
	};
	
	AtributListener gelembungListener = new AtributListener();
	
	Listener muatUlangAtributMapListener = new Listener() {
		@Override
		public void onOk() {
			ayatAdapter_.muatAtributMap();
			ayatAdapter_.notifyDataSetChanged();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		TimingLogger tog = new TimingLogger(TAG, "IsiActivity#onCreate"); //$NON-NLS-1$
		tog.addSplit("IsiActivity (fase 0) onCreate dipanggil"); //$NON-NLS-1$
		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		S.siapinKitab();
		S.bacaPengaturan(this);
		S.terapkanPengaturanBahasa(this, handler, 2);
		
		displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		
		setContentView(R.layout.activity_isi);
		tog.addSplit("IsiActivity (fase 5) sebelum siapin macem2"); //$NON-NLS-1$
		
		S.siapinPengirimFidbek(this);
		S.pengirimFidbek.cobaKirim();
		
		lsIsi = (ListView) findViewById(R.id.lsIsi);
		bTuju = (Button) findViewById(R.id.bTuju);
		bKiri = (ImageButton) findViewById(R.id.bKiri);
		bKanan = (ImageButton) findViewById(R.id.bKanan);
		tempatJudul = findViewById(R.id.tempatJudul);
		lJudul = (TextView) findViewById(R.id.lJudul);
		
		tog.addSplit("IsiActivity (fase 10) sebelum terap pengaturan"); //$NON-NLS-1$

		terapkanPengaturan(false);
		tog.addSplit("IsiActivity (fase 20) sesudah terap pengaturan"); //$NON-NLS-1$

		lsIsi.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				return lsIsi_itemLongClick(parent, view, position, id);
			}
		});
		
		registerForContextMenu(lsIsi);
		
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
		
		lsIsi.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				int action = event.getAction();
				if (action == KeyEvent.ACTION_DOWN) {
					return onKeyDown(keyCode, event);
				} else if (action == KeyEvent.ACTION_MULTIPLE) {
					return onKeyMultiple(keyCode, event.getRepeatCount(), event);
				}
				return false;
			}
		});
		
		// adapter
		ayatAdapter_ = new AyatAdapter(getApplicationContext(), paralelOnClickListener, gelembungListener);
		lsIsi.setAdapter(ayatAdapter_);
		
		// muat preferences
		preferences_instan = S.getPreferences(this);
		int kitabTerakhir = preferences_instan.getInt(NAMAPREF_kitabTerakhir, 0);
		int pasalTerakhir = preferences_instan.getInt(NAMAPREF_pasalTerakhir, 0);
		int ayatTerakhir = preferences_instan.getInt(NAMAPREF_ayatTerakhir, 0);
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
		sejarah = new Sejarah(preferences_instan);
		Log.d(TAG, "Akan menuju kitab " + kitabTerakhir + " pasal " + kitabTerakhir + " ayat " + ayatTerakhir); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		
		// muat kitab
		{
			Kitab k = S.edisiAktif.getKitab(kitabTerakhir);
			if (k != null) {
				S.kitabAktif = k;
			}
		}
		
		// muat pasal dan ayat
		tampil(pasalTerakhir, ayatTerakhir);
		//sejarah.tambah(Ari.encode(kitabTerakhir, pasalTerakhir, ayatTerakhir));
		
		//# minta fidbek kah?
		final long terakhirMintaFidbek = preferences_instan.getLong(NAMAPREF_terakhirMintaFidbek, 0);
		if (terakhirMintaFidbek == 0) {
			Editor editor = preferences_instan.edit();
			editor.putLong(NAMAPREF_terakhirMintaFidbek, System.currentTimeMillis());
			editor.commit();
		} else {
			final long sekarang = System.currentTimeMillis();
			if (sekarang - terakhirMintaFidbek > (long)90000*24*3600) { // 3 BULAN ato belom pernah
				handler.post(new Runnable() {
					@Override
					public void run() {
						popupMintaFidbek();
					}
				});
			}
		}

		tog.dumpToLog();
	}
	
	private synchronized void nyalakanTerusLayarKalauDiminta() {
		if (Preferences.getBoolean(R.string.pref_nyalakanTerusLayar_key, R.bool.pref_nyalakanTerusLayar_default)) {
			lsIsi.setKeepScreenOn(true);
		}
	}

	private synchronized void matikanLayarKalauSudahBolehMati() {
		lsIsi.setKeepScreenOn(false);
	}
	
	private int loncatKe(String alamat) {
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
	
	private void loncatKeAri(int ari) {
		if (ari == 0) return;
		
		Log.d(TAG, "akan loncat ke ari 0x" + Integer.toHexString(ari)); //$NON-NLS-1$
		
		int kitabPos = Ari.toKitab(ari);
		Kitab k = S.edisiAktif.getKitab(kitabPos);
		
		if (k != null) {
			S.kitabAktif = k;
		} else {
			Log.w(TAG, "mana ada kitabPos " + kitabPos + " dari ari " + ari); //$NON-NLS-1$
			return;
		}
		
		tampil(Ari.toPasal(ari), Ari.toAyat(ari));
	}
	
	private boolean lsIsi_itemLongClick(AdapterView<?> parent, View view, int position, long id) {
		// kalo setingnya mati, anggap true aja (diconsume)
		if (Preferences.getBoolean(R.string.pref_matikanTahanAyat_key, R.bool.pref_matikanTahanAyat_default)) {
			return true;
		}
		
		// hanya untuk ayat! bukan perikop. Maka kalo perikop, anggap aja uda diconsume.
		if (id < 0) {
			return true;
		}
		
		return false;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		
		new MenuInflater(this).inflate(R.menu.context_ayat, menu);
		
		// simpen ayat yang dipilih untuk dipake sama menu tambah bukmak
		{
			AdapterContextMenuInfo menuInfo2 = (AdapterContextMenuInfo) menuInfo;
			this.ayatContextMenu_1 = (int) (menuInfo2.id) + 1;
			this.isiAyatContextMenu = U.buangKodeKusus(xayat[(int) menuInfo2.id]);
		}
		
		//# pasang header
		String alamat = S.alamat(S.kitabAktif, this.pasal_1, this.ayatContextMenu_1);
		menu.setHeaderTitle(alamat);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		String alamat = S.alamat(S.kitabAktif, this.pasal_1, this.ayatContextMenu_1);
		
		int itemId = item.getItemId();
		if (itemId == R.id.menuSalinAyat) {
			ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboardManager.setText(alamat + "  " + this.isiAyatContextMenu); //$NON-NLS-1$
			
			Toast.makeText(this, getString(R.string.alamat_sudah_disalin, alamat), Toast.LENGTH_SHORT).show();
			
			return true;
		} else if (itemId == R.id.menuTambahBukmak) {
			final int ari = Ari.encode(S.kitabAktif.pos, pasal_1, ayatContextMenu_1);
			
			BukmakEditor editor = new BukmakEditor(this, alamat, ari);
			editor.setListener(muatUlangAtributMapListener);
			editor.bukaDialog();
			
			return true;
		} else if (itemId == R.id.menuTambahCatatan) {
			tampilkanCatatan(S.kitabAktif, pasal_1, ayatContextMenu_1);

			return true;
		} else if (itemId == R.id.menuTambahStabilo) {
			final int ari = Ari.encode(S.kitabAktif.pos, pasal_1, ayatContextMenu_1);
			
			PemilihStabiloDialog dialog = new PemilihStabiloDialog(this, new PemilihStabiloDialog.PemilihStabiloCallback() {
				@Override public void dipilih(int warnaRgb) {
					S.getDb().updateAtauInsertStabilo(ari, warnaRgb);
					ayatAdapter_.muatAtributMap();
					ayatAdapter_.notifyDataSetChanged();
				}
				
				@Override public void batal() {
				}
			}, S.getDb().getWarnaRgbStabilo(ari));
			
			dialog.show();
			
			return true;
		} else if (itemId == R.id.menuBagikan) {
			Intent i = new Intent(Intent.ACTION_SEND);
			i.setType("text/plain"); //$NON-NLS-1$
			i.putExtra(Intent.EXTRA_SUBJECT, alamat);
			i.putExtra(Intent.EXTRA_TEXT, alamat + "  " + this.isiAyatContextMenu); //$NON-NLS-1$
			startActivity(Intent.createChooser(i, getString(R.string.bagikan_alamat, alamat)));

			return true;
		}
		
		return super.onContextItemSelected(item);
	}

	private void terapkanPengaturan(boolean bahasaJuga) {
		// penerapan langsung warnaLatar
		{
			lsIsi.setBackgroundColor(S.penerapan.warnaLatar);
			lsIsi.setCacheColorHint(S.penerapan.warnaLatar);
			Window window = getWindow();
			if (window != null) {
				ColorDrawable bg = new ColorDrawable(S.penerapan.warnaLatar);
				window.setBackgroundDrawable(bg);
			}
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
			S.terapkanPengaturanBahasa(this, null, 0);
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
	private int getAyatBerdasarSkrol() {
		return ayatAdapter_.getAyatDariPosition(getPosisiBerdasarSkrol());
	}
	
	private int getPosisiBerdasarSkrol() {
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

	private void bTuju_click() {
		if (Preferences.getBoolean(R.string.pref_tombolAlamatLoncat_key, R.bool.pref_tombolAlamatLoncat_default)) {
			bukaDialogLoncat();
		} else {
			bukaDialogTuju();
		}
	}
	
	private void bTuju_longClick() {
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
		public Object getItem(int position) {
			return sejarah.getAri(position);
		}
		
		@Override
		public int getCount() {
			return sejarah.getN();
		}
	};
	
	private void bukaDialogTuju() {
		Intent intent = new Intent(this, MenujuActivity.class);
		intent.putExtra(MenujuActivity.EXTRA_pasal, pasal_1);
		
		int ayat = getAyatBerdasarSkrol();
		intent.putExtra(MenujuActivity.EXTRA_ayat, ayat);
		
		startActivityForResult(intent, R.id.menuTuju);
	}
	
	private void bukaDialogLoncat() {
		final View loncat = LayoutInflater.from(this).inflate(R.layout.dialog_loncat, null);
		final TextView lAlamatKini = (TextView) loncat.findViewById(R.id.lAlamatKini);
		final EditText tAlamatLoncat = (EditText) loncat.findViewById(R.id.tAlamatLoncat);
		final ImageButton bKeTuju = (ImageButton) loncat.findViewById(R.id.bKeTuju);

		// set lAlamatKini
		{
			int ayatKini = getAyatBerdasarSkrol();
			String alamat = S.alamat(S.kitabAktif, IsiActivity.this.pasal_1, ayatKini);
			lAlamatKini.setText(alamat);
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
		
		tAlamatLoncat.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
				}
			}
		});
		
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

		new MenuInflater(this).inflate(R.menu.activity_isi, menu);
		
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
			if (perluReloadMenuWaktuOnMenuOpened) {
				bikinMenu(menu);
				perluReloadMenuWaktuOnMenuOpened = false;
			}
			
			MenuItem menuTuju = menu.findItem(R.id.menuTuju);
			if (menuTuju != null) {
				if (Preferences.getBoolean(R.string.pref_tombolAlamatLoncat_key, R.bool.pref_tombolAlamatLoncat_default)) {
					menuTuju.setIcon(R.drawable.menu_loncat);
					menuTuju.setTitle(R.string.loncat_v);
				} else {
					menuTuju.setIcon(R.drawable.ic_menu_forward);
					menuTuju.setTitle(R.string.tuju_v);
				}
			}
		}
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuTuju:
			bTuju_click();
			return true;
		case R.id.menuBukmak:
			startActivityForResult(new Intent(this, BukmakActivity.class), R.id.menuBukmak);
			return true;
		case R.id.menuSearch2:
			menuSearch2_click();
			return true;
		case R.id.menuEdisi:
			pilihEdisi();
			return true;
		case R.id.menuRenungan: 
			startActivityForResult(new Intent(this, RenunganActivity.class), R.id.menuRenungan);
			return true;
		case R.id.menuTentang:
			tampilDialogTentang();
			return true;
		case R.id.menuPengaturan:
			startActivityForResult(new Intent(this, PengaturanActivity.class), R.id.menuPengaturan);
			return true;
		case R.id.menuFidbek:
			popupMintaFidbek();
			return true;
		case R.id.menuBantuan:
			startActivity(new Intent(this, BantuanActivity.class));
			return true;
		case R.id.menuDonasi:
			bukaDialogDonasi();
			return true;
		}
		
		return false; 
	}

	private void tampilDialogTentang() {
		String verName = "null"; //$NON-NLS-1$
		int verCode = -1;
		
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			verName = packageInfo.versionName;
			verCode = packageInfo.versionCode;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "PackageInfo ngaco", e); //$NON-NLS-1$
		}
		
		TextView isi = new TextView(this);
		isi.setText(Html.fromHtml(getString(R.string.teks_about, verName, verCode)));
		isi.setTextColor(0xffffffff);
		isi.setLinkTextColor(0xff8080ff);
		Linkify.addLinks(isi, Linkify.WEB_URLS);

		int pad = (int) (getResources().getDisplayMetrics().density * 6.f);
		isi.setPadding(pad, pad, pad, pad);
		
		new AlertDialog.Builder(this)
		.setTitle(R.string.tentang_title)
		.setView(isi)
		.setPositiveButton(R.string.ok, null)
		.show();
	}

	private void pilihEdisi() {
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
			if (AddonManager.cekAdaEdisi(preset.namafile_preset) && preset.getAktif()) {
				pilihan.add(preset.judul);
				data.add(preset);
			}
		}
		
		List<MEdisiYes> xyes = S.getDb().listSemuaEdisi();
		for (MEdisiYes yes: xyes) {
			if (yes.getAktif()) {
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
			@Override
			public void onClick(DialogInterface dialog, int which) {
				final MEdisi me = data.get(which);
				
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
					
					dialog.dismiss();
					tampil(pasal_1, getAyatBerdasarSkrol());
				} else {
					new AlertDialog.Builder(IsiActivity.this)
					.setMessage("Ada kegagalan membuka: " + me.getEdisiId())
					.setPositiveButton(R.string.ok, null)
					.show();
				}
			}
		})
		.setPositiveButton("Versi lainnya...", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Intent intent = new Intent(getApplicationContext(), EdisiActivity.class);
				startActivityForResult(intent, R.id.menuEdisi);
			}
		})
		.setNegativeButton("Cancel", null)
		.show();
	}

	private void menuSearch2_click() {
		Intent intent = new Intent(this, Search2Activity.class);
		intent.putExtra(Search2Activity.EXTRA_carian, search2_carian);
		intent.putExtra(Search2Activity.EXTRA_filter_lama, search2_filter_lama);
		intent.putExtra(Search2Activity.EXTRA_filter_baru, search2_filter_baru);
		intent.putExtra(Search2Activity.EXTRA_hasilCari, search2_hasilCari);
		intent.putExtra(Search2Activity.EXTRA_posisiTerpilih, search2_posisiTerpilih);
		
		startActivityForResult(intent, R.id.menuSearch2);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "onActivityResult reqCode=0x" + Integer.toHexString(requestCode) + " resCode=" + resultCode + " data=" + data); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		
		if (requestCode == R.id.menuTuju) {
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
		} else if (requestCode == R.id.menuBukmak) {
			ayatAdapter_.muatAtributMap();
			ayatAdapter_.notifyDataSetChanged();

			if (resultCode == RESULT_OK) {
				int ari = data.getIntExtra(BukmakActivity.EXTRA_ariTerpilih, 0);
				if (ari != 0) { // 0 berarti ga ada apa2, karena ga ada pasal 0 ayat 0
					loncatKeAri(ari);
					sejarah.tambah(ari);
				}
			}
		} else if (requestCode == R.id.menuSearch2) {
			if (resultCode == RESULT_OK) {
				int ari = data.getIntExtra(Search2Activity.EXTRA_ariTerpilih, 0);
				if (ari != 0) { // 0 berarti ga ada apa2, karena ga ada pasal 0 ayat 0
					loncatKeAri(ari);
					sejarah.tambah(ari);
				}
				
				search2_carian = data.getStringExtra(Search2Activity.EXTRA_carian);
				search2_filter_lama = data.getBooleanExtra(Search2Activity.EXTRA_filter_lama, true);
				search2_filter_baru = data.getBooleanExtra(Search2Activity.EXTRA_filter_baru, true);
				search2_hasilCari = data.getParcelableExtra(Search2Activity.EXTRA_hasilCari);
				search2_posisiTerpilih = data.getIntExtra(Search2Activity.EXTRA_posisiTerpilih, -1);
				
				//Log.d("alki", "kembali dari search2. Carian=" + search2_carian + ", posisiTerpilih=" + search2_posisiTerpilih + ", hasilCari=" + search2_hasilCari);
			}
		} else if (requestCode == R.id.menuRenungan) {
			if (data != null) {
				String alamat = data.getStringExtra(RenunganActivity.EXTRA_alamat);
				if (alamat != null) {
					int ari = loncatKe(alamat);
					if (ari != 0) {
						sejarah.tambah(ari);
					}
				}
			}
		} else if (requestCode == R.id.menuPengaturan) {
			// HARUS rilod pengaturan.
			S.bacaPengaturan(this);
			
			terapkanPengaturan(true);
			perluReloadMenuWaktuOnMenuOpened = true;
		}
	}

	/**
	 * @param pasal_1 basis-1
	 * @param ayat_1 basis-1
	 * @return Ari yang hanya terdiri dari pasal dan ayat. Kitab selalu 00
	 */
	private int tampil(int pasal_1, int ayat_1) {
		if (pasal_1 < 1) pasal_1 = 1;
		if (pasal_1 > S.kitabAktif.npasal) pasal_1 = S.kitabAktif.npasal;
		
		if (ayat_1 < 1) ayat_1 = 1;
		if (ayat_1 > S.kitabAktif.nayat[pasal_1 - 1]) ayat_1 = S.kitabAktif.nayat[pasal_1 - 1];
		
		// muat data GA USAH pake async dong. // diapdet 20100417 biar ga usa async, ga guna.
		{
			int[] perikop_xari;
			Blok[] perikop_xblok;
			int nblok;
			
			xayat = S.muatTeks(S.edisiAktif, S.kitabAktif, pasal_1);
			
			//# max dibikin pol 30 aja (1 pasal max 30 blok, cukup mustahil)
			int max = 30;
			perikop_xari = new int[max];
			perikop_xblok = new Blok[max];
			nblok = S.edisiAktif.pembaca.muatPerikop(S.edisiAktif, S.kitabAktif.pos, pasal_1, perikop_xari, perikop_xblok, max); 
			
			//# tadinya onPostExecute
			ayatAdapter_.setData(S.kitabAktif, pasal_1, xayat, perikop_xari, perikop_xblok, nblok);
			ayatAdapter_.muatAtributMap();
			ayatAdapter_.notifyDataSetChanged();
			
			// kasi tau activity
			this.pasal_1 = pasal_1;
			
			final int position = ayatAdapter_.getPositionDariAyat(ayat_1);
			
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
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (Preferences.getBoolean(R.string.pref_tombolVolumeNaikTurun_key, R.bool.pref_tombolVolumeNaikTurun_default)) {
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
				lsIsi.setSelectionFromTop(posLama-1, lsIsi.getVerticalFadingEdgeLength());
			} else {
				lsIsi.setSelectionFromTop(0, lsIsi.getVerticalFadingEdgeLength());
			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
			return onKeyDown(keyCode, event);
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
			return onKeyDown(keyCode, event);
		}
		return super.onKeyMultiple(keyCode, repeatCount, event);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (Preferences.getBoolean(R.string.pref_tombolVolumeNaikTurun_key, R.bool.pref_tombolVolumeNaikTurun_default)) {
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return true;
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) return true;
		}
		
		return super.onKeyUp(keyCode, event);
	}
	
	private void bKiri_click() {
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
			}
			// whileelse: sekarang sudah Kejadian 1. Ga usa ngapa2in
		} else {
			int pasalBaru = pasal_1 - 1;
			tampil(pasalBaru, 1);
		}
	}
	
	private void bKanan_click() {
		Kitab kitabKini = S.kitabAktif;
		if (pasal_1 >= kitabKini.npasal) {
			int maxKitabPos = S.edisiAktif.getMaxKitabPos();
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
	
	private void popupMintaFidbek() {
		final View feedback = getLayoutInflater().inflate(R.layout.feedback, null);
		TextView lVersi = (TextView) feedback.findViewById(R.id.lVersi);
		
		try {
			PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
			lVersi.setText(getString(R.string.namaprog_versi_build, info.versionName, info.versionCode));
		} catch (NameNotFoundException e) {
			Log.w(TAG, e);
		}
		
		new AlertDialog.Builder(IsiActivity.this).setView(feedback)
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				EditText tFeedback = (EditText) feedback.findViewById(R.id.tFeedback);
				String isi = tFeedback.getText().toString();
				
				if (isi.length() > 0) {
					S.pengirimFidbek.tambah(isi);
				}
				
				S.pengirimFidbek.cobaKirim();
				
				Editor editor = preferences_instan.edit();
				editor.putLong(NAMAPREF_terakhirMintaFidbek, System.currentTimeMillis());
				editor.commit();
			}
		}).show();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		S.terapkanPengaturanBahasa(this, handler, 2);
		perluReloadMenuWaktuOnMenuOpened = true;

		super.onConfigurationChanged(newConfig);
	}
	
	@Override
	public boolean onSearchRequested() {
		menuSearch2_click();
		
		return true;
	}

	static void aturIsiDanTampilanCuplikanBukmak(TextView t, String alamat, String isi) {
		SpannableStringBuilder sb = new SpannableStringBuilder(alamat);
		sb.setSpan(new UnderlineSpan(), 0, alamat.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		sb.append(' ').append(isi);
		t.setText(sb);
		aturTampilanTeksIsi(t);
	}
	
	static void aturTampilanTeksIsi(TextView t) {
		t.setTypeface(S.penerapan.jenisHuruf, S.penerapan.tebalHuruf);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.penerapan.ukuranHuruf2dp);
		t.setIncludeFontPadding(false);
		t.setTextColor(S.penerapan.warnaHuruf);
	}
	
	static void aturTampilanTeksAlamatHasilCari(TextView t, SpannableStringBuilder sb) {
		aturTampilanTeksJudulBukmak(t);
		sb.setSpan(new UnderlineSpan(), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		t.setText(sb);
	}
	
	static void aturTampilanTeksJudulBukmak(TextView t) {
		t.setTypeface(S.penerapan.jenisHuruf, S.penerapan.tebalHuruf);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.penerapan.ukuranHuruf2dp * 1.2f);
		t.setTextColor(S.penerapan.warnaHuruf);
	}
	
	static void aturTampilanTeksTanggalBukmak(TextView t) {
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.penerapan.ukuranHuruf2dp * 0.8f);
		t.setTextColor(S.penerapan.warnaHuruf);
	}

	static void aturTampilanTeksNomerAyat(TextView t) {
		t.setTypeface(S.penerapan.jenisHuruf, S.penerapan.tebalHuruf);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.penerapan.ukuranHuruf2dp);
		t.setIncludeFontPadding(false);
		t.setTextColor(S.penerapan.warnaNomerAyat);
	}
	
	void tampilkanCatatan(Kitab kitab_, int pasal_1, int ayat_1) {
		GelembungDialog dialog = new GelembungDialog(IsiActivity.this, new RefreshCallback() {
			@Override
			public void udahan() {
				ayatAdapter_.muatAtributMap();
				ayatAdapter_.notifyDataSetChanged();
			}
		});
		dialog.setDbKitabPasalAyat(kitab_, pasal_1, ayat_1);
		dialog.tampilkan();
	}
	
	public class AtributListener {
		public void onClick(Kitab kitab_, int pasal_1, int ayat_1, int jenis) {
			if (jenis == Bukmak2.jenis_bukmak) {
				final int ari = Ari.encode(kitab_.pos, pasal_1, ayat_1);
				String alamat = S.alamat(S.edisiAktif, ari);
				BukmakEditor editor = new BukmakEditor(IsiActivity.this, alamat, ari);
				editor.setListener(muatUlangAtributMapListener);
				editor.bukaDialog();
			} else if (jenis == Bukmak2.jenis_catatan) {
				tampilkanCatatan(kitab_, pasal_1, ayat_1);
			}
		}
	}
}
