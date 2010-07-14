package yuku.alkitab;

import java.util.*;
import java.util.regex.*;

import yuku.alkitab.GelembungDialog.RefreshCallback;
import yuku.alkitab.model.*;
import yuku.andoutil.*;
import android.app.*;
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.os.PowerManager.WakeLock;
import android.preference.*;
import android.text.*;
import android.text.style.*;
import android.text.util.*;
import android.util.*;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class IsiActivity extends Activity {
	private static final String NAMAPREF_kitabTerakhir = "kitabTerakhir";
	private static final String NAMAPREF_pasalTerakhir = "pasalTerakhir";
	private static final String NAMAPREF_ayatTerakhir = "ayatTerakhir";
	private static final String NAMAPREF_terakhirMintaFidbek = "terakhirMintaFidbek";
	private static final String NAMAPREF_renungan_nama = "renungan_nama";

	public static final int RESULT_pindahCara = RESULT_FIRST_USER + 1;
	public static final int DIALOG_bikinIndex = 1;

	String[] xayat;
	ListView lsIsi;
	Button bTuju;
	ImageButton bKiri;
	ImageButton bKanan;
	
	int pasal_1 = 0;
	int ayatContextMenu_1 = -1;
	String isiAyatContextMenu = null;
	SharedPreferences preferences;
	SharedPreferences pengaturan;
	Float ukuranAsalHurufIsi;
	Handler handler = new Handler();
	DisplayMetrics displayMetrics;
	ProgressDialog dialogBikinIndex;
	boolean lagiBikinIndex = false;
	WakeLock wakeLock = null; // FIXME
	AlkitabDb alkitabDb;
	
	private AyatAdapter ayatAdapter_;
	private boolean tombolAlamatLoncat_;
	
	//# penyimpanan state buat search2
	String search2_carian = null;
	IntArrayList search2_hasilCari = null;
	int search2_posisiTerpilih = -1;

	
	CallbackSpan.OnClickListener paralelOnClickListener = new CallbackSpan.OnClickListener() {
		@Override
		public void onClick(View widget, Object data) {
			loncatKe((String)data);
		}
	};
	
	GelembungListener gelembungListener = new GelembungListener();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		TimingLogger tog = new TimingLogger("alki", "IsiActivity#onCreate");
		tog.addSplit("IsiActivity (fase 0) onCreate dipanggil");
		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.isi);
		tog.addSplit("IsiActivity (fase 5) sebelum siapin macem2");

		S.siapinEdisi(getResources());
		S.siapinKitab(getResources());
		S.siapinPengirimFidbek(this);
		S.pengirimFidbek.cobaKirim();
		
		displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		
		lsIsi = (ListView) findViewById(R.id.lsIsi);
		
		bTuju = (Button) findViewById(R.id.bTuju);
		bKiri = (ImageButton) findViewById(R.id.bKiri);
		bKanan = (ImageButton) findViewById(R.id.bKanan);
		tog.addSplit("IsiActivity (fase 10) sebelum terap pengaturan");

		pengaturan = PreferenceManager.getDefaultSharedPreferences(this);
		terapkanPengaturan();
		tog.addSplit("IsiActivity (fase 20) sesudah terap pengaturan");

		lsIsi.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				return lsIsi_itemLongClick(parent, view, position, id);
			}
		});
		
		registerForContextMenu(lsIsi);
		
		bTuju.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				bTuju_click();
			}
		});
		
		bTuju.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				bTuju_longClick();
				return true;
			}
		});
		
		bKiri.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				bKiri_click();
			}
		});
		
		bKanan.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				bKanan_click();
			}
		});
		tog.addSplit("IsiActivity (fase 30) sebelum baca preferences");

		preferences = S.getPreferences(this);
		int kitabTerakhir = preferences.getInt(NAMAPREF_kitabTerakhir, 0);
		int pasalTerakhir = preferences.getInt(NAMAPREF_pasalTerakhir, 0);
		int ayatTerakhir = preferences.getInt(NAMAPREF_ayatTerakhir, 0);
		
		{
			String renungan_nama = preferences.getString(NAMAPREF_renungan_nama, null);
			if (renungan_nama != null) {
				for (String nama: RenunganActivity.ADA_NAMA) {
					if (renungan_nama.equals(nama)) {
						S.penampungan.renungan_nama = renungan_nama;
					}
				}
			}
		}
		
		// siapin db
		alkitabDb = AlkitabDb.getInstance(getApplicationContext());
		
		ayatAdapter_ = new AyatAdapter(getApplicationContext(), alkitabDb, paralelOnClickListener, gelembungListener);
		lsIsi.setAdapter(ayatAdapter_);
		
		Log.d("alki", "Akan menuju kitab " + kitabTerakhir + " pasal " + kitabTerakhir + " ayat " + ayatTerakhir);
		
		// muat kitab
		if (kitabTerakhir < S.xkitab.length) {
			S.kitab = S.xkitab[kitabTerakhir];
		}
		// muat pasal dan ayat
		tampil(pasalTerakhir, ayatTerakhir); 
		
		//# minta fidbek kah?
		final long terakhirMintaFidbek = preferences.getLong(NAMAPREF_terakhirMintaFidbek, 0);
		if (terakhirMintaFidbek == 0) {
			Editor editor = preferences.edit();
			editor.putLong(NAMAPREF_terakhirMintaFidbek, System.currentTimeMillis());
			editor.commit();
		} else {
			final long sekarang = System.currentTimeMillis();
			if (sekarang - terakhirMintaFidbek > (long)30000*60*60*24) { // 1 BULAN ato belom pernah
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
		if (S.penerapan.nyalakanTerusLayar) {
			if (wakeLock == null) {
				PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
				wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "alki:nyalakanTerusLayar");
				wakeLock.setReferenceCounted(false);
			}
			if (wakeLock != null) { // jaga2 aja
				Log.i("alki", "wakeLock nyala");
				wakeLock.acquire();
			}
		}
	}

	private synchronized void matikanLayarKalauSudahBolehMati() {
		if (wakeLock != null) {
			Log.i("alki", "wakeLock mati");
			wakeLock.release();
		}
	}
	
	@Override
	protected Dialog onCreateDialog(final int id) {
		if (id == DIALOG_bikinIndex) {
			dialogBikinIndex = new ProgressDialog(this);
			dialogBikinIndex.setTitle("Membuat indeks...");
			dialogBikinIndex.setMessage("Mempersiapkan indeks...");
			dialogBikinIndex.setIndeterminate(true);
			dialogBikinIndex.setCancelable(false);
			
			dialogBikinIndex.setOnCancelListener(new DialogInterface.OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					Log.d("alki", "onCancel");
				}
			});
			
			dialogBikinIndex.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					Log.d("alki", "onDismiss");
					
					if (lagiBikinIndex) {
						showDialog(id);
					}
				}
			});
			
			return dialogBikinIndex;
		}
		
		return null;
	}
	
	private void loncatKe(String alamat) {
		if (alamat.trim().length() == 0) {
			return;
		}
		
		Log.d("alki", "akan loncat ke " + alamat);
		
		Peloncat peloncat = new Peloncat();
		boolean sukses = peloncat.parse(alamat);
		if (! sukses) {
			Toast.makeText(this, "Alamat salah: " + alamat, Toast.LENGTH_SHORT).show();
			return;
		}
		
		int kitab = peloncat.getKitab(S.xkitab);
		if (kitab != -1) {
			S.kitab = S.xkitab[kitab];
		}
		
		int pasal = peloncat.getPasal();
		int ayat = peloncat.getAyat();
		if (pasal == -1 && ayat == -1) {
			tampil(1, 1);
		} else {
			tampil(pasal, ayat);
		}
	}
	
	private void loncatKeAri(int ari) {
		if (ari == 0) return;
		
		Log.d("alki", "akan loncat ke ari 0x" + Integer.toHexString(ari));
		
		int kitab = Ari.toKitab(ari);
		if (kitab >= 0 && kitab < S.xkitab.length) {
			S.kitab = S.xkitab[kitab];
		} else {
			Log.w("alki", "mana ada kitab " + ari);
			return;
		}
		
		tampil(Ari.toPasal(ari), Ari.toAyat(ari));
	}
	
	private boolean lsIsi_itemLongClick(AdapterView<?> parent, View view, int position, long id) {
		Log.d("klik panjang", "di " + position + " id " + id);

		// kalo setingnya mati, anggap true aja (diconsume)
		if (S.penerapan.matikanTahanAyat) {
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
			this.isiAyatContextMenu = xayat[(int) menuInfo2.id];
		}
		
		//# pasang header
		String alamat = S.kitab.judul + " " + this.pasal_1 + ":" + this.ayatContextMenu_1;
		menu.setHeaderTitle(alamat);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		String alamat = S.kitab.judul + " " + this.pasal_1 + ":" + this.ayatContextMenu_1;
		
		int itemId = item.getItemId();
		if (itemId == R.id.menuSalinAyat) {
			ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboardManager.setText(this.isiAyatContextMenu);
			
			Toast.makeText(this, alamat + " sudah disalin ke clipboard", Toast.LENGTH_SHORT).show();
			
			return true;
		} else if (itemId == R.id.menuTambahBukmak) {
			final int ari = Ari.encode(S.kitab.pos, pasal_1, ayatContextMenu_1);
			final Bukmak2 bukmak = alkitabDb.getBukmak(ari, AlkitabDb.ENUM_Bukmak2_jenis_bukmak);
			
			View dialogView = LayoutInflater.from(this).inflate(R.layout.bukmak_ubah_dialog, null);
			final EditText tTulisan = (EditText) dialogView.findViewById(R.id.tTulisan);
			tTulisan.setText(bukmak != null? bukmak.tulisan: alamat);
			
			new AlertDialog.Builder(this)
			.setView(dialogView)
			.setTitle(alamat)
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String tulisan = tTulisan.getText().toString();
					
					if (bukmak != null) {
						bukmak.tulisan = tulisan;
						bukmak.waktuUbah = new Date();
						alkitabDb.updateBukmak(bukmak);
					} else {
						Bukmak2 bukmakBaru = new Bukmak2(ari, AlkitabDb.ENUM_Bukmak2_jenis_bukmak, tulisan, new Date(), new Date());
						alkitabDb.insertBukmak(bukmakBaru);
					}
				}
			})
			.setNegativeButton("Batal", null)
			.create()
			.show();
			
			return true;
		} else if (itemId == R.id.menuTambahCatatan) {
			tampilkanGelembung(S.kitab, pasal_1, ayatContextMenu_1);
		} else if (itemId == R.id.menuBagikan) {
			Intent i = new Intent(Intent.ACTION_SEND);
			i.setType("text/plain");
			i.putExtra(Intent.EXTRA_SUBJECT, alamat);
			i.putExtra(Intent.EXTRA_TEXT, U.buangKodeKusus(this.isiAyatContextMenu));
			startActivity(Intent.createChooser(i, "Bagikan " + alamat));

			return true;
		}
		
		return super.onContextItemSelected(item);
	}

	private void terapkanPengaturan() {
		//# atur ukuran huruf isi berdasarkan pengaturan
		{
			if (ukuranAsalHurufIsi == null) {
				ukuranAsalHurufIsi = new TextView(this).getTextSize();
			}
			Log.d("alki", "ukuran asal huruf px = " + ukuranAsalHurufIsi);
			String ukuranHuruf_s = pengaturan.getString(getString(R.string.pref_ukuranHuruf_key), "120");
			int ukuranHuruf = 100;
			try {
				ukuranHuruf = Integer.valueOf(ukuranHuruf_s);
			} catch (NumberFormatException e) {
			}
			Log.d("alki", "skala di pengaturan = " + ukuranHuruf);
			S.penerapan.ukuranTeksPx = ukuranAsalHurufIsi * ukuranHuruf / 100.f;
			Log.d("alki", "ukuran baru px = " + S.penerapan.ukuranTeksPx);
			
			S.penerapan.indenParagraf = (int) (getResources().getDimension(R.dimen.indenParagraf) * ukuranHuruf / 100.f);
			Log.d("alki", "indenParagraf = " + S.penerapan.indenParagraf);
			S.penerapan.menjorokSatu = (int) (getResources().getDimension(R.dimen.menjorokSatu) * ukuranHuruf / 100.f);
			Log.d("alki", "menjorokSatu = " + S.penerapan.menjorokSatu);
			S.penerapan.menjorokDua = (int) (getResources().getDimension(R.dimen.menjorokDua) * ukuranHuruf / 100.f);
			Log.d("alki", "menjorokDua = " + S.penerapan.menjorokDua);
		}
		
		//# atur jenis huruf, termasuk boldnya
		{
			String jenisHuruf_s = pengaturan.getString(getString(R.string.pref_jenisHuruf_key), null);
			Typeface typeface;
			
			if (jenisHuruf_s == null) typeface = Typeface.DEFAULT;
			else if (jenisHuruf_s.equals("SERIF")) typeface = Typeface.SERIF;
			else if (jenisHuruf_s.equals("SANS_SERIF")) typeface = Typeface.SANS_SERIF;
			else if (jenisHuruf_s.equals("MONOSPACE")) typeface = Typeface.MONOSPACE;
			else typeface = Typeface.DEFAULT;
			
			boolean boldHuruf_b = pengaturan.getBoolean(getString(R.string.pref_boldHuruf_key), false);
			
			S.penerapan.jenisHuruf = typeface;
			S.penerapan.tebalHuruf = boldHuruf_b? Typeface.BOLD: Typeface.NORMAL;
		}
		
		//# atur warna teks, latar, dan nomer ayat
		{
			int warnaHuruf = pengaturan.getInt(getString(R.string.pref_warnaHuruf_int_key), 0xfff0f0f0);
			S.penerapan.warnaHuruf = warnaHuruf;

			int warnaLatar = pengaturan.getInt(getString(R.string.pref_warnaLatar_int_key), 0xff000000);
			S.penerapan.warnaLatar = warnaLatar;
			lsIsi.setBackgroundColor(warnaLatar);
			lsIsi.setCacheColorHint(warnaLatar);
			Window window = getWindow();
			if (window != null) {
				ColorDrawable bg = new ColorDrawable(warnaLatar);
				window.setBackgroundDrawable(bg);
			}
			
			int warnaNomerAyat = pengaturan.getInt(getString(R.string.pref_warnaNomerAyat_int_key), 0xff8080ff);
			S.penerapan.warnaNomerAyat = warnaNomerAyat;
		}
		
		tombolAlamatLoncat_ = pengaturan.getBoolean(getString(R.string.pref_tombolAlamatLoncat_key), false);
		
		//# sembunyikan navigasi kalo perlu
		{
			String key = getString(R.string.pref_tanpaNavigasi_key);
			boolean sembunyiNavigasi = pengaturan.getBoolean(key, false);
			
			View panelNavigasi = findViewById(R.id.panelNavigasi);
			if (sembunyiNavigasi) {
				panelNavigasi.setVisibility(View.GONE);
			} else {
				panelNavigasi.setVisibility(View.VISIBLE);
			}
		}
		
		//# abjad kah?
		{
			boolean sortKitabAlfabet = pengaturan.getBoolean(getString(R.string.pref_sortKitabAlfabet_key), false);
			S.penerapan.sortKitabAlfabet = sortKitabAlfabet;
		}
		
		//# jangan nyalakan context menu kah?
		{
			boolean matikanTahanAyat = pengaturan.getBoolean(getString(R.string.pref_matikanTahanAyat_key), false);
			S.penerapan.matikanTahanAyat = matikanTahanAyat;
		}
		
		//# layar selalu nyala kah?
		{
			boolean nyalakanTerusLayar = pengaturan.getBoolean(getString(R.string.pref_nyalakanTerusLayar_key), false);
			S.penerapan.nyalakanTerusLayar = nyalakanTerusLayar;
		}
		
		lsIsi.invalidateViews();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		Editor editor = preferences.edit();
		editor.putInt(NAMAPREF_kitabTerakhir, S.kitab.pos);
		editor.putInt(NAMAPREF_pasalTerakhir, pasal_1);
		editor.putInt(NAMAPREF_ayatTerakhir, getAyatBerdasarSkrol());
		editor.putString(NAMAPREF_renungan_nama, S.penampungan.renungan_nama);
		editor.commit();
		
		matikanLayarKalauSudahBolehMati();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		//# nyala terus layar
		nyalakanTerusLayarKalauDiminta();
	}
	
	/**
	 * @return ayat mulai dari 1
	 */
	private int getAyatBerdasarSkrol() {
		int firstPos = lsIsi.getFirstVisiblePosition();

		if (firstPos != 0) {
			firstPos++;
		}
		
		int ayat = ayatAdapter_.getAyatDariPosition(firstPos);
		
		return ayat;
	}

	private void bTuju_click() {
		if (tombolAlamatLoncat_) {
			bukaDialogLoncat();
		} else {
			bukaDialogTuju();
		}
	}
	
	private void bTuju_longClick() {
		if (tombolAlamatLoncat_) {
			bukaDialogTuju();
		} else {
			bukaDialogLoncat();
		}
	}
	
	private void bukaDialogTuju() {
		Intent intent = new Intent(this, MenujuActivity.class);
		intent.putExtra("pasal", pasal_1);
		
		int ayat = getAyatBerdasarSkrol();
		intent.putExtra("ayat", ayat);
		
		startActivityForResult(intent, R.id.menuTuju);
	}
	
	private void bukaDialogLoncat() {
		final View loncat = LayoutInflater.from(IsiActivity.this).inflate(R.layout.loncat_dialog, null);
		final TextView lAlamatKini = (TextView) loncat.findViewById(R.id.lAlamatKini);
		final EditText tAlamatLoncat = (EditText) loncat.findViewById(R.id.tAlamatLoncat);
		final ImageButton bKeTuju = (ImageButton) loncat.findViewById(R.id.bKeTuju);

		// set lAlamatKini
		{
			int ayat = getAyatBerdasarSkrol();
			String alamat = S.kitab.judul + " " + IsiActivity.this.pasal_1 + ":" + ayat;
			lAlamatKini.setText(alamat);
		}
		
		DialogInterface.OnClickListener loncat_click = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				loncatKe(tAlamatLoncat.getText().toString());
			}
		};
		
		final AlertDialog dialog = new AlertDialog.Builder(IsiActivity.this)
			.setView(loncat)
			.setPositiveButton("Loncat", loncat_click)
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
				loncatKe(tAlamatLoncat.getText().toString());
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

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		new MenuInflater(this).inflate(R.menu.isi, menu);
		
		SubMenu menuGebug = menu.addSubMenu("Gebug");
		menuGebug.setHeaderTitle("Untuk percobaan dan cari kutu. Tidak penting.");
		menuGebug.add(0, 0x985801, 0, "gebug 1: dump p+p");
		menuGebug.add(0, 0x985802, 0, "gebug 2: bikin ulang index");
		menuGebug.add(0, 0x985803, 0, "gebug 3: crash!");
		menuGebug.add(0, 0x985804, 0, "gebug 4: reset p+p");
		menuGebug.add(0, 0x985805, 0, "gebug 5: search1 (lama)");
		menuGebug.add(0, 0x985806, 0, "gebug 6: tehel bewarna");
		
		return true;
	}
	
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		if (menu != null) {
			MenuItem menuTuju = menu.findItem(R.id.menuTuju);
			if (menuTuju != null) {
				if (tombolAlamatLoncat_) {
					menuTuju.setIcon(R.drawable.menu_loncat);
					menuTuju.setTitle("Loncat");
				} else {
					menuTuju.setIcon(R.drawable.ic_menu_forward);
					menuTuju.setTitle("Tuju");
				}
			}
		}
		
		return true;
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		if (item.getItemId() == R.id.menuTuju) {
			bTuju_click();
			return true;
		} else if (item.getItemId() == R.id.menuBukmak) {
			Intent intent = new Intent(this, BukmakActivity.class);
			startActivityForResult(intent, R.id.menuBukmak);
			return true;
		} else if (item.getItemId() == R.id.menuSearch2) {
			menuSearch2_click();
			return true;
		} else if (item.getItemId() == R.id.menuEdisi) {
			Intent intent = new Intent(this, EdisiActivity.class);
			startActivityForResult(intent, R.id.menuEdisi);
			return true;
		} else if (item.getItemId() == R.id.menuRenungan) { 
			Intent intent = new Intent(this, RenunganActivity.class);
			startActivityForResult(intent, R.id.menuRenungan);
			return true;
		} else if (item.getItemId() == R.id.menuTentang) {
			String verName = "null";
	    	int verCode = -1;
	    	
			try {
				PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				verName = packageInfo.versionName;
				verCode = packageInfo.versionCode;
			} catch (NameNotFoundException e) {
				Log.e("alki", "PackageInfo ngaco", e);
			}
	    	
			new AlertDialog.Builder(this).setTitle(R.string.tentang_title).setMessage(
					Html.fromHtml(
							getString(R.string.tentang_message, verName, verCode) 
							+ "<br/>Rev: " + "$LastChangedRevision$".replaceAll("\\$|LastChangedRevision|:| ", "")
							+ "<br/>Root: " + Environment.getRootDirectory()
							+ "<br/>Ext: " + Environment.getExternalStorageDirectory()
							+ "<br/>Data: " + Environment.getDataDirectory()
					))
					.show();
			return true;
		} else if (item.getItemId() == R.id.menuPengaturan) {
			Intent intent = new Intent(this, PengaturanActivity.class);
			startActivityForResult(intent, R.id.menuPengaturan);
			return true;
		} else if (item.getItemId() == R.id.menuFidbek) {
			popupMintaFidbek();
			return true;
		} else if (item.getItemId() == R.id.menuBantuan) {
			Intent intent = new Intent(this, BantuanActivity.class);
			startActivity(intent);
			return true;
		} else if (item.getItemId() == 0x985801) { // debug 1
			// dump pref
			Log.i("alki", "### semua pref segera muncul di bawah ini:::");
			{
				Map<String, ?> all = preferences.getAll();
				for (Map.Entry<String, ?> entry: all.entrySet()) {
					Log.i("alki", String.format("%s = %s", entry.getKey(), entry.getValue()));
				}
			}
			Log.i("alki", "### pengaturan:::");
			{
				Map<String, ?> all = pengaturan.getAll();
				for (Map.Entry<String, ?> entry: all.entrySet()) {
					Log.i("alki", String.format("%s = %s", entry.getKey(), entry.getValue()));
				}
			}
			Log.i("alki", "### db:::");
			alkitabDb.dump();
			return true;
		} else if (item.getItemId() == 0x985802) { // debug 2
			bikinIndex();
			return true;
		} else if (item.getItemId() == 0x985803) { // debug 3
			throw new RuntimeException("ini cuma lagi nyoba2 DUEH.");
			//return true;
		} else if (item.getItemId() == 0x985804) { // debug 4
			{
				Editor editor = preferences.edit();
				editor.clear();
				editor.commit();
			}
			{
				Editor editor = pengaturan.edit();
				editor.clear();
				editor.commit();
			}
			return true;
		} else if (item.getItemId() == 0x985805) { // debug 5
			menuSearch_click();
		} else if (item.getItemId() == 0x985806) { // debug 6
			if (S.penerapan.gebug_tehelBewarna) {
				Toast.makeText(this, "tehel bewarna mati", Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(this, "tehel bewarna nyala", Toast.LENGTH_SHORT).show();
			}
			S.penerapan.gebug_tehelBewarna = ! S.penerapan.gebug_tehelBewarna;
		}
		
		return super.onMenuItemSelected(featureId, item);
	}

	private void bikinIndex() {
		final PembuatIndex pembuatIndex = new PembuatIndex();
		final Handler handler = new Handler();
		
		showDialog(DIALOG_bikinIndex);
		lagiBikinIndex = true;
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				Log.d("alki", "tred 2 jalan");
				pembuatIndex.buatIndex(IsiActivity.this, S.edisi, S.xkitab, new PembuatIndex.OnProgressListener() {
					@Override
					public void onProgress(final String msg) {
						handler.post(new Runnable() {
							@Override
							public void run() {
								Log.d("alki", "handler dipanggil msg: " + msg);
								if (msg == null) {
									lagiBikinIndex = false;
									dismissDialog(DIALOG_bikinIndex);
								} else {
									dialogBikinIndex.setMessage(msg);
								}
							}
						});
					}
				});
			}
		}).start();
	}

	private void menuSearch_click() {
		// cek db. Harus ada dan jadi.
		long wmulai = System.currentTimeMillis();
		boolean adaDb = SearchDb.cekAdaDb(this);
		Log.i("alki", "cek ada db " + (System.currentTimeMillis() - wmulai) + " ms");
		
		boolean adaTabelEdisiIni = false;
		
		wmulai = System.currentTimeMillis();
		if (adaDb) {
			adaTabelEdisiIni = SearchDb.cekAdaTabelEdisi(this, S.edisi.nama);
		}
		Log.i("alki", "cek ada tabel edisi " + (System.currentTimeMillis() - wmulai) + " ms");
		
		if (! adaDb || ! adaTabelEdisiIni) {
			AlertDialog dialog = new AlertDialog.Builder(this).setMessage(
					Html.fromHtml("Untuk fungsi pencarian, perlu dibuat dulu <b>indeks</b>. Indeks memerlukan sekitar 9 MB memori dan akan disimpan di memori eksternal (SD card, dsb). " +
							"Dengan indeks, Anda dapat mencari gabungan kata seperti <b>kuperbuat hidup kekal</b> atau <b>\"benda penerang\"</b>. " +
							"Proses pembuatan indeks memerlukan 2-5 menit, dan selama itu Anda tidak bisa memakai program Alkitab. " +
							"Buat indeks sekarang?"))
					.setTitle("Pencarian " + S.edisi.judul)
					.setPositiveButton("OK", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							int ret = SearchDb.bikinDb(IsiActivity.this);
							
							if (ret == 0) {
								Toast.makeText(IsiActivity.this, "Pembuatan file database gagal! Pastikan memori eksternal terpasang dan bisa ditulisi.", Toast.LENGTH_LONG).show();
								return;
							} else {
								// mulai bikin! Hore.
								bikinIndex();
							}
						}
					})
					.setNegativeButton("Tidak", null)
					.create();
			
			dialog.show();
			
			return;
		} else {
			Intent intent = new Intent(this, SearchActivity.class);
			startActivityForResult(intent, R.id.menuSearch);
		}
	}
	
	private void menuSearch2_click() {
		Intent intent = new Intent(this, Search2Activity.class);
		intent.putExtra(Search2Activity.EXTRA_carian, search2_carian);
		intent.putExtra(Search2Activity.EXTRA_hasilCari, search2_hasilCari);
		intent.putExtra(Search2Activity.EXTRA_posisiTerpilih, search2_posisiTerpilih);
		
		startActivityForResult(intent, R.id.menuSearch2);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d("alki", "onActivityResult reqCode=0x" + Integer.toHexString(requestCode) + " resCode=" + resultCode + " data=" + data);
		
		if (requestCode == R.id.menuTuju) {
			if (resultCode == RESULT_OK) {
				int pasal = data.getIntExtra("pasal", 0);
				int ayat = data.getIntExtra("ayat", 0);
				int kitab = data.getIntExtra("kitab", AdapterView.INVALID_POSITION);
				
				if (kitab == AdapterView.INVALID_POSITION || kitab >= S.xkitab.length || kitab < 0) {
				} else {
					// ganti kitab
					S.kitab = S.xkitab[kitab];
				}
				
				tampil(pasal, ayat);
			} else if (resultCode == RESULT_pindahCara) {
				bukaDialogLoncat();
			}
		} else if (requestCode == R.id.menuBukmak) {
			if (resultCode == RESULT_OK) {
				int ari = data.getIntExtra(BukmakActivity.EXTRA_ariTerpilih, 0);
				if (ari != 0) { // 0 berarti ga ada apa2, karena ga ada pasal 0 ayat 0
					loncatKeAri(ari);
				}
			}
		} else if (requestCode == R.id.menuSearch) {
			if (resultCode == RESULT_OK) {
				int ari = data.getIntExtra("terpilih.ari", 0);
				if (ari != 0) { // 0 berarti ga ada apa2, karena ga ada pasal 0 ayat 0
					loncatKeAri(ari);
				}
			}
		} else if (requestCode == R.id.menuSearch2) {
			if (resultCode == RESULT_OK) {
				int ari = data.getIntExtra(Search2Activity.EXTRA_ariTerpilih, 0);
				if (ari != 0) { // 0 berarti ga ada apa2, karena ga ada pasal 0 ayat 0
					loncatKeAri(ari);
				}
				
				search2_carian = data.getStringExtra(Search2Activity.EXTRA_carian);
				search2_hasilCari = data.getParcelableExtra(Search2Activity.EXTRA_hasilCari);
				search2_posisiTerpilih = data.getIntExtra(Search2Activity.EXTRA_posisiTerpilih, -1);
				
				//Log.d("alki", "kembali dari search2. Carian=" + search2_carian + ", posisiTerpilih=" + search2_posisiTerpilih + ", hasilCari=" + search2_hasilCari);
			}
		} else if (requestCode == R.id.menuEdisi) {
			if (resultCode == RESULT_OK) {
				String nama = data.getStringExtra("nama");
				
				for (Edisi e : S.xedisi) {
					if (e.nama.equals(nama)) {
						S.edisi = e;
						//# buang kitab2 yang uda kelod
						S.xkitab = null;
						S.kitab = null;
						break;
					}
				}
				
				S.siapinKitab(getResources());
			}
		} else if (requestCode == R.id.menuRenungan) {
			if (data != null) {
				String alamat = data.getStringExtra("alamat");
				if (alamat != null) {
					loncatKe(alamat);
				}
			}
		} else if (requestCode == R.id.menuPengaturan) {
			terapkanPengaturan();
		}
	}

	/**
	 * @param pasal_1 basis-1
	 * @param ayat_1 basis-1
	 */
	private void tampil(int pasal_1, int ayat_1) {
		if (pasal_1 < 1) pasal_1 = 1;
		if (pasal_1 > S.kitab.npasal) pasal_1 = S.kitab.npasal;
		
		if (ayat_1 < 1) ayat_1 = 1;
		if (ayat_1 > S.kitab.nayat[pasal_1 - 1]) ayat_1 = S.kitab.nayat[pasal_1 - 1];
		
		// muat data GA USAH pake async dong. // diapdet 20100417 biar ga usa async, ga guna.
		{
			int[] perikop_xari;
			Blok[] perikop_xblok;
			int nblok;
			
			xayat = S.muatTeks(getResources(), S.kitab, pasal_1);
			
			//# max dibikin pol 30 aja (1 pasal max 30 blok, cukup mustahil)
			int max = 30;
			perikop_xari = new int[max];
			perikop_xblok = new Blok[max];
			nblok = S.muatPerikop(getResources(), S.kitab.pos, pasal_1, perikop_xari, perikop_xblok, max); 
			
			//# tadinya onPostExecute
			ayatAdapter_.setData(S.kitab, pasal_1, xayat, perikop_xari, perikop_xblok, nblok);
			ayatAdapter_.muatCatatanMap();
			ayatAdapter_.notifyDataSetChanged();
			
			String judul = S.kitab.judul + " " + pasal_1;
			bTuju.setText(judul);
			
			// kasi tau activity
			this.pasal_1 = pasal_1;
			
			final int position = ayatAdapter_.getPositionDariAyat(ayat_1);
			
			if (position == -1) {
				Log.w("alki", "ga bisa ketemu ayat " + ayat_1 + ", ANEH!");
			} else {
				lsIsi.setSelectionFromTop(position, lsIsi.getVerticalFadingEdgeLength());
			}
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		Log.d("alki", "onKeyDown: " + event);
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
			bKiri_click();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
			bKanan_click();
			return true;
		}
		
		return super.onKeyDown(keyCode, event);
	}

	private void bKiri_click() {
		if (pasal_1 == 1) {
			// uda di awal pasal, masuk ke kitab sebelum
			Kitab kitabKini = S.kitab;
			if (kitabKini.pos > 0) {
				Kitab kitabBaru = S.xkitab[kitabKini.pos - 1];
				S.kitab = kitabBaru;
				int pasalBaru_1 = kitabBaru.npasal;
				tampil(pasalBaru_1, 1);
			} else {
				// Kejadian 1. Ga usa ngapa2in
			}
			return;
		}
		tampil(pasal_1 - 1, 1);
	}
	
	private void bKanan_click() {
		Kitab kitabKini = S.kitab;
		if (pasal_1 >= kitabKini.npasal) {
			if (kitabKini.pos < S.xkitab.length - 1) {
				Kitab kitabBaru = S.xkitab[kitabKini.pos + 1];
				S.kitab = kitabBaru;
				tampil(1, 1);
			} else {
				// uda di Wahyu pasal terakhir. Ga usa ngapa2in
			}
			return;
		}
		tampil(pasal_1 + 1, 1);
	}
	
	private void popupMintaFidbek() {
		final View feedback = getLayoutInflater().inflate(R.layout.feedback, null);
		TextView lVersi = (TextView) feedback.findViewById(R.id.lVersi);
		
		try {
			PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
			lVersi.setText(String.format("Alkitab v%s (%d)", info.versionName, info.versionCode));
		} catch (NameNotFoundException e) {
			Log.w("alki", e);
		}
		
		TextView lTautKeMarket = (TextView) feedback.findViewById(R.id.lTautKeMarket);
		Linkify.addLinks(lTautKeMarket, Pattern.compile(".+"), "http://market.android.com/", new Linkify.MatchFilter() {
			@Override
			public boolean acceptMatch(CharSequence s, int start, int end) {
				return true;
			}
		}, new Linkify.TransformFilter() {
			@Override
			public String transformUrl(Matcher match, String url) {
				return "details?id=" + getPackageName();
			}
		});
		
		new AlertDialog.Builder(IsiActivity.this).setView(feedback)
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				EditText tFeedback = (EditText) feedback.findViewById(R.id.tFeedback);
				String isi = tFeedback.getText().toString();
				
				if (isi.length() > 0) {
					S.pengirimFidbek.tambah(isi);
				}
				
				S.pengirimFidbek.cobaKirim();
				
				Editor editor = preferences.edit();
				editor.putLong(NAMAPREF_terakhirMintaFidbek, System.currentTimeMillis());
				editor.commit();
			}
		}).show();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		Log.d("alki", "onConfigurationChanged");
	}
	
	@Override
	public boolean onSearchRequested() {
		menuSearch2_click();
		
		return true;
	}
	
	static void aturTampilanTeksIsi(TextView t) {
		t.setTypeface(S.penerapan.jenisHuruf, S.penerapan.tebalHuruf);
		t.setTextSize(TypedValue.COMPLEX_UNIT_PX, S.penerapan.ukuranTeksPx);
		t.setIncludeFontPadding(false);
		t.setTextColor(S.penerapan.warnaHuruf);
	}
	
	static void aturTampilanTeksAlamatHasilCari(TextView t, SpannableStringBuilder sb) {
		t.setTypeface(S.penerapan.jenisHuruf, S.penerapan.tebalHuruf);
		t.setTextSize(TypedValue.COMPLEX_UNIT_PX, S.penerapan.ukuranTeksPx * 1.2f);
		t.setTextColor(S.penerapan.warnaHuruf);
		sb.setSpan(new UnderlineSpan(), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		t.setText(sb);
	}

	static void aturTampilanTeksNomerAyat(TextView t) {
		t.setTypeface(S.penerapan.jenisHuruf, S.penerapan.tebalHuruf);
		t.setTextSize(TypedValue.COMPLEX_UNIT_PX, S.penerapan.ukuranTeksPx);
		t.setIncludeFontPadding(false);
		t.setTextColor(S.penerapan.warnaNomerAyat);
	}
	
	void tampilkanGelembung(Kitab kitab_, int pasal_1, int ayat_1) {
		GelembungDialog dialog = new GelembungDialog(IsiActivity.this, new RefreshCallback() {
			@Override
			public void udahan() {
				ayatAdapter_.muatCatatanMap();
				ayatAdapter_.notifyDataSetChanged();
			}
		});
		dialog.setDbKitabPasalAyat(alkitabDb, kitab_, pasal_1, ayat_1);
		dialog.tampilkan();
	}
	
	public class GelembungListener {
		public void onClick(Kitab kitab_, int pasal_1, int ayat_1) {
			tampilkanGelembung(kitab_, pasal_1, ayat_1);
		}
	}
}
