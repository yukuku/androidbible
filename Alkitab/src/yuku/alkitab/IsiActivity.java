package yuku.alkitab;

import java.util.*;
import java.util.regex.*;

import yuku.alkitab.S.Peloncat;
import yuku.alkitab.model.*;
import android.app.*;
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.os.*;
import android.preference.PreferenceManager;
import android.text.*;
import android.text.style.*;
import android.text.util.Linkify;
import android.util.*;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.TextView.BufferType;

public class IsiActivity extends Activity {
	private static final String NAMAPREF_kitabTerakhir = "kitabTerakhir";
	private static final String NAMAPREF_pasalTerakhir = "pasalTerakhir";
	private static final String NAMAPREF_ayatTerakhir = "ayatTerakhir";
	private static final String NAMAPREF_terakhirMintaFidbek = "terakhirMintaFidbek";

	public static final int RESULT_pindahCara = RESULT_FIRST_USER + 1;

	String[] xayat;
	ListView lsIsi;
	Button bTuju;
	ImageButton bKiri;
	ImageButton bKanan;
	int pasal = 0;
	int ayatContextMenu = -1;
	String isiAyatContextMenu = null;
	SharedPreferences preferences;
	SharedPreferences pengaturan;
	Float ukuranAsalHurufIsi;
	Handler handler = new Handler();
	Integer cerahTeks; // null kalo ga diset, pake yang aslinya aja
	DisplayMetrics displayMetrics;
	
	private Float ukuranTeksSesuaiPengaturan_;
	private Typeface jenisHurufSesuaiPengaturan_;
	private Integer tebalHurufSesuaiPengaturan_;
	private AyatAdapter ayatAdapter_ = new AyatAdapter();
	private int indenParagraf_;
	private boolean tombolAlamatLoncat_;
	private String carianTerakhir_;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.isi);
		
		S.siapinEdisi(getResources());
		S.siapinKitab(getResources());
		S.siapinPengirimFidbek(this);
		
		displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		
		lsIsi = (ListView) findViewById(R.id.lsIsi);
		
		bTuju = (Button) findViewById(R.id.bTuju);
		bKiri = (ImageButton) findViewById(R.id.bKiri);
		bKanan = (ImageButton) findViewById(R.id.bKanan);
		
		pengaturan = PreferenceManager.getDefaultSharedPreferences(this);
		terapkanPengaturan();
		
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
		
		preferences = getSharedPreferences(S.NAMA_PREFERENCES, 0);
		int kitabTerakhir = preferences.getInt(NAMAPREF_kitabTerakhir, 0);
		int pasalTerakhir = preferences.getInt(NAMAPREF_pasalTerakhir, 0);
		int ayatTerakhir = preferences.getInt(NAMAPREF_ayatTerakhir, 0);
		
		Log.d("alki", String.format("Akan menuju kitab %d pasal %d ayat %d", kitabTerakhir, pasalTerakhir, ayatTerakhir));
		
		// muat kitab
		if (kitabTerakhir < S.xkitab.length) {
			S.kitab = S.xkitab[kitabTerakhir];
		}
		// muat pasal dan ayat
		tampil(pasalTerakhir, ayatTerakhir); 
		
		final long terakhirMintaFidbek = preferences.getLong(NAMAPREF_terakhirMintaFidbek, 0);
		final long sekarang = System.currentTimeMillis();
		if (terakhirMintaFidbek == 0 || (sekarang - terakhirMintaFidbek > 1000*60*60*24)) { // 1 hari ato belom pernah
			handler.post(new Runnable() {
				
				@Override
				public void run() {
					popupMintaFidbek();
				}
			});
		}
		
		SearchDb.setPath("/sdcard/coba.db");
	}

	private void loncatKe(String alamat) {
		if (alamat.trim().length() == 0) {
			return;
		}
		
		Log.d("alki", "akan loncat ke " + alamat);
		
		Peloncat peloncat = new S.Peloncat();
		boolean sukses = peloncat.parse(alamat);
		if (! sukses) {
			Toast.makeText(this, "Alamat salah: " + alamat, Toast.LENGTH_SHORT).show();
			return;
		}
		
		int kitab = peloncat.getKitab();
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
		
		return false;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		
		new MenuInflater(this).inflate(R.menu.context_ayat, menu);
		
		
		// simpen ayat yang dipilih untuk dipake sama menu tambah bukmak
		{
			AdapterContextMenuInfo menuInfo2 = (AdapterContextMenuInfo) menuInfo;
			this.ayatContextMenu = (menuInfo2.position + 1);
			this.isiAyatContextMenu = xayat[menuInfo2.position];
		}
		
		//# pasang header
		String alamat = S.kitab.judul + " " + this.pasal + ":" + this.ayatContextMenu;
		menu.setHeaderTitle(alamat);
	}

	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menuSalinAyat) {
			ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboardManager.setText(this.isiAyatContextMenu);
			
			return true;
		} else if (item.getItemId() == R.id.menuTambahBukmak) {
			final SQLiteDatabase db = AlkitabDb.getInstance(this).getDatabase();
			
			//# bikin bukmak
			String alamat = S.kitab.judul + " " + this.pasal + ":" + this.ayatContextMenu;
			final Bukmak bukmak = new Bukmak(alamat, this.isiAyatContextMenu, new Date(), S.kitab.pos, this.pasal, this.ayatContextMenu);
			
			Cursor cursor = db.query(AlkitabDb.TABEL_Bukmak, null, String.format("%s=? and %s=? and %s=?", AlkitabDb.KOLOM_kitab, AlkitabDb.KOLOM_pasal, AlkitabDb.KOLOM_ayat), new String[] {"" + S.kitab.pos, "" + this.pasal, "" + this.ayatContextMenu}, null, null, null);
			cursor.moveToNext();
			
			if (cursor.isAfterLast()) {
				// belum ada
				cursor.close();
				
				// tambah baru
				db.insertOrThrow(AlkitabDb.TABEL_Bukmak, null, bukmak.toContentValues());
			} else {
				// udah ada, tanya user
				final int id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
				cursor.close();
				
				new AlertDialog.Builder(this)
				.setMessage("Pembatas buku " + alamat + " sudah ditambahkan sebelumnya. Perbarui tanggal pembatas buku?")
				.setPositiveButton("Ya", new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						db.update(AlkitabDb.TABEL_Bukmak, bukmak.toContentValues(), "_id=?", new String[] {"" + id});
					}
				})
				.setNegativeButton("Tidak", null)
				.create()
				.show();
			}
			
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
			ukuranTeksSesuaiPengaturan_ = ukuranAsalHurufIsi * ukuranHuruf / 100.f;
			Log.d("alki", "ukuran baru px = " + ukuranTeksSesuaiPengaturan_);
			
			indenParagraf_ = (int) (getResources().getDimension(R.dimen.indenParagraf) * ukuranHuruf / 100.f);
			Log.d("alki", "indenParagraf_ = " + indenParagraf_);
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
			
			jenisHurufSesuaiPengaturan_ = typeface;
			tebalHurufSesuaiPengaturan_ = boldHuruf_b? Typeface.BOLD: Typeface.NORMAL;
		}
		
		//# atur terang teks
		{
			String key = getString(R.string.pref_cerahTeks_key);
			cerahTeks = pengaturan.getInt(key, 100);
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
		
		
		lsIsi.invalidateViews();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		Editor editor = preferences.edit();
		editor.putInt(NAMAPREF_kitabTerakhir, S.kitab.pos);
		editor.putInt(NAMAPREF_pasalTerakhir, pasal);
		editor.putInt(NAMAPREF_ayatTerakhir, getAyatBerdasarSkrol());
		editor.commit();
	}
	
	/**
	 * @return ayat mulai dari 1
	 */
	private int getAyatBerdasarSkrol() {
		int firstPos = lsIsi.getFirstVisiblePosition();

		// TODO bikin lebih tepat.
		if (firstPos == 0) {
			return 1;
		}
		
		firstPos += 2;
		
		if (firstPos > xayat.length) {
			firstPos = xayat.length;
		}
		
		return firstPos;
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
		intent.putExtra("pasal", pasal);
		
		int ayat = getAyatBerdasarSkrol();
		intent.putExtra("ayat", ayat);
		
		startActivityForResult(intent, R.id.menuTuju);
	}
	
	private void bukaDialogLoncat() {
		final View loncat = LayoutInflater.from(IsiActivity.this).inflate(R.layout.loncat, null);
		final TextView lAlamatKini = (TextView) loncat.findViewById(R.id.lAlamatKini);
		final EditText tAlamatLoncat = (EditText) loncat.findViewById(R.id.tAlamatLoncat);
		final ImageButton bKeTuju = (ImageButton) loncat.findViewById(R.id.bKeTuju);

		// set lAlamatKini
		{
			int ayat = getAyatBerdasarSkrol();
			String alamat = S.kitab.judul + " " + IsiActivity.this.pasal + ":" + ayat;
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
					Log.d("alki", "setSoftInputMode panggil");
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

	private SpannableStringBuilder[] siapinTampilanAyat() {
		SpannableStringBuilder[] res = new SpannableStringBuilder[xayat.length];
		
		String pengawal = "";
		
		for (int i = 0; i < xayat.length; i++) {
			SpannableStringBuilder seayat = new SpannableStringBuilder();
			
			pengawal = (i+1) + " ";
			seayat.append(pengawal).append(xayat[i]);
			seayat.setSpan(new ForegroundColorSpan(0xff8080ff), 0, pengawal.length() - 1, 0);
			seayat.setSpan(new LeadingMarginSpan.Standard(0, indenParagraf_), 0, pengawal.length() + xayat[i].length(), 0);
			
			res[i] = seayat;
		}
		
		return res;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		new MenuInflater(this).inflate(R.menu.isi, menu);
		
		menu.add(0, 0x985801, 0, "gebug 1 (dump p+p)");
		menu.add(0, 0x985802, 0, "gebug 2 (create index)");
		menu.add(0, 0x985803, 0, "gebug 3 (cari)");
		menu.add(0, 0x985804, 0, "gebug 4 (reset p+p)");
		
		return true;
	}
	
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		MenuItem menuTuju = menu.findItem(R.id.menuTuju);
		if (menuTuju != null) {
			if (tombolAlamatLoncat_) {
				menuTuju.setIcon(R.drawable.menu_loncat);
				menuTuju.setTitle("Loncat");
			} else {
				menuTuju.setIcon(R.drawable.menu_tuju);
				menuTuju.setTitle("Tuju");
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
		} else if (item.getItemId() == R.id.menuSearch) {
			menuSearch_click();
			return true;
		} else if (item.getItemId() == R.id.menuKitab) {
			Intent intent = new Intent(this, KitabActivity.class);
			startActivityForResult(intent, R.id.menuKitab);
			return true;
		} else if (item.getItemId() == R.id.menuEdisi) {
			Intent intent = new Intent(this, EdisiActivity.class);
			startActivityForResult(intent, R.id.menuEdisi);
			return true;
		} else if (item.getItemId() == R.id.menuTentang) {
			String verName = "null";
	    	int verCode = -1;
	    	
			try {
				PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				verName = packageInfo.versionName;
				verCode = packageInfo.versionCode;
			} catch (NameNotFoundException e) {
				Log.e("alki-isi", "PackageInfo ngaco", e);
			}
	    	
			new AlertDialog.Builder(this).setTitle(R.string.tentang_title).setMessage(
					Html.fromHtml(
							getString(R.string.tentang_message, verName, verCode) 
							+ "<br/><br/>Rev: " + "$LastChangedRevision$".substring(22)
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
		} else if (item.getItemId() == 0x985801) { // debug 1
			// dump pref
			Log.i("alki.gebug1", "semua pref segera muncul di bawah ini");
			{
				Map<String, ?> all = preferences.getAll();
				for (Map.Entry<String, ?> entry: all.entrySet()) {
					Log.i("alki.gebug1", String.format("%s = %s", entry.getKey(), entry.getValue()));
				}
			}
			Log.i("alki.gebug1", "dan pengaturan");
			{
				Map<String, ?> all = pengaturan.getAll();
				for (Map.Entry<String, ?> entry: all.entrySet()) {
					Log.i("alki.gebug1", String.format("%s = %s", entry.getKey(), entry.getValue()));
				}
			}
			return true;
		} else if (item.getItemId() == 0x985802) { // debug 2
			final PembuatIndex pembuatIndex = new PembuatIndex();
			final Handler handler = new Handler();
			final ProgressDialog dialog = ProgressDialog.show(this, "Membuat indeks...", "Mempersiapkan indeks...", true, false);
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					Log.d("alki", "tred 2 jalan");
					pembuatIndex.buatIndex(IsiActivity.this, new PembuatIndex.OnProgressListener() {
						@Override
						public void onProgress(final String msg) {
							handler.post(new Runnable() {
								@Override
								public void run() {
									Log.d("alki", "handler dipanggil msg: " + msg);
									if (msg == null) {
										dialog.dismiss();
									} else {
										dialog.setMessage(msg);
									}
								}
							});
						}
					});
				}
			}).start();
			
			Log.d("alki", "sebelum dialog.show()");
			dialog.show();
			Log.d("alki", "sesudah dialog.show()");
			return true;
		} else if (item.getItemId() == 0x985803) { // debug 3
			return true;
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
		}
		
		return super.onMenuItemSelected(featureId, item);
	}

	private void menuSearch_click() {
		Intent intent = new Intent(this, SearchActivity.class);
		intent.putExtra("carian", carianTerakhir_);
		startActivityForResult(intent, R.id.menuSearch);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
				String alamat = data.getStringExtra("terpilih.alamat");
				if (alamat != null) {
					loncatKe(alamat);
				}
			}
		} else if (requestCode == R.id.menuSearch) {
			// Apapun hasilnya, simpan carian terakhir
			String carian = data.getStringExtra("carian");
			carianTerakhir_ = carian;
			
			if (resultCode == RESULT_OK) {
				int ari = data.getIntExtra("terpilih.ari", 0);
				if (ari != 0) { // 0 berarti ga ada apa2, karena ga ada pasal 0 ayat 0
					loncatKeAri(ari);
				}
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
		} else if (requestCode == R.id.menuKitab) {
			if (resultCode == RESULT_OK) {
				String nama = data.getStringExtra("nama");
				
				for (Kitab k : S.xkitab) {
					if (k.nama.equals(nama)) {
						S.kitab = k;
						tampil(pasal, 0);
						break;
					}
				}
			}
		} else if (requestCode == R.id.menuPengaturan) {
			terapkanPengaturan();
		}
	}

	private void tampil(int pasal, int ayat) {
		if (pasal < 1) pasal = 1;
		if (pasal > S.kitab.npasal) pasal = S.kitab.npasal;
		
		if (ayat < 1) ayat = 1;
		if (ayat > S.kitab.nayat[pasal-1]) ayat = S.kitab.nayat[pasal-1];
		
		// muat data pake async dong.
		new AsyncTask<Integer, Void, SpannableStringBuilder[]>() {
			int pasal;
			int ayat;
			
			@Override
			protected SpannableStringBuilder[] doInBackground(Integer... params) {
				pasal = params[0];
				ayat = params[1];
				
				xayat = S.muatTeks(getResources(), pasal);
				return siapinTampilanAyat();
			}
		
			@Override
			protected void onPostExecute(SpannableStringBuilder[] result) {
				ayatAdapter_.setRendered(result);
				lsIsi.setAdapter(ayatAdapter_);
				ayatAdapter_.notifyDataSetChanged();
				
				String judul = getString(R.string.judulIsi, S.kitab.judul, pasal, ayat);
				setTitle(judul);
				bTuju.setText(judul);
				
				IsiActivity.this.pasal = pasal;
				
				lsIsi.post(new Runnable() {
					@Override
					public void run() {
						// ayat mulai dari 1. setSelectionFromTop mulai dari 0.
						lsIsi.setSelectionFromTop(ayat - 1, lsIsi.getVerticalFadingEdgeLength());
					}
				});
			}
		}.execute(pasal, ayat);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
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
		tampil(pasal-1, 1);
	}
	
	private void bKanan_click() {
		tampil(pasal+1, 1);
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
		menuSearch_click();
		
		return true;
	}
	
	private class AyatAdapter extends BaseAdapter {
		private SpannableStringBuilder[] rendered_;
		
		synchronized void setRendered(SpannableStringBuilder[] baru) {
			rendered_ = baru;
		}
		
		@Override
		public synchronized int getCount() {
			if (rendered_ == null) return 0;
			return rendered_.length;
		}

		@Override
		public synchronized String getItem(int position) {
			return rendered_[position].toString();
		}

		@Override
		public synchronized long getItemId(int position) {
			 return position;
		}

		@Override
		public synchronized View getView(int position, View convertView, ViewGroup parent) {
			TextView res;
			
			if (convertView == null) {
				res = (TextView) LayoutInflater.from(IsiActivity.this).inflate(R.layout.satu_ayat, null);
			} else {
				res = (TextView) convertView;
			}
			
			res.setTypeface(jenisHurufSesuaiPengaturan_, tebalHurufSesuaiPengaturan_);
			res.setTextSize(TypedValue.COMPLEX_UNIT_PX, ukuranTeksSesuaiPengaturan_);
			res.setText(rendered_[position], BufferType.SPANNABLE);
			
			if (cerahTeks != null) {
				int color = ((255 * cerahTeks / 100) * 0x010101) | 0xff000000;
				res.setTextColor(color);
			}
			
			return res;
		}
		
		@Override
		public boolean areAllItemsEnabled() {
			return false;
		}
		
		@Override
		public boolean isEnabled(int position) {
			return false;
		}
	}
	
}
