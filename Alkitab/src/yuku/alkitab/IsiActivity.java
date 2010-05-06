package yuku.alkitab;

import java.util.*;
import java.util.regex.*;

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
import android.text.method.LinkMovementMethod;
import android.text.style.*;
import android.text.util.Linkify;
import android.util.*;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.TextView.BufferType;

public class IsiActivity extends Activity {
	private static final int WARNA_NOMER_AYAT = 0xff8080ff;
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
	
	int pasal = 0;
	int ayatContextMenu = -1;
	String isiAyatContextMenu = null;
	SharedPreferences preferences;
	SharedPreferences pengaturan;
	Float ukuranAsalHurufIsi;
	Handler handler = new Handler();
	DisplayMetrics displayMetrics;
	ProgressDialog dialogBikinIndex;
	boolean lagiBikinIndex = false;
	
	private AyatAdapter ayatAdapter_ = new AyatAdapter();
	private boolean tombolAlamatLoncat_;
	
	CallbackSpan.OnClickListener paralelOnClickListener = new CallbackSpan.OnClickListener() {
		@Override
		public void onClick(View widget, Object data) {
			loncatKe((String)data);
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d("alki", "IsiActivity (fase 0) onCreate dipanggil icicle=" + savedInstanceState);
		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.isi);
		
		Log.d("alki", "IsiActivity (fase 5) sebelum siapin macem2");

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
		
		Log.d("alki", "IsiActivity (fase 10) sebelum terap pengaturan");

		pengaturan = PreferenceManager.getDefaultSharedPreferences(this);
		terapkanPengaturan();
		
		Log.d("alki", "IsiActivity (fase 20) sesudah terap pengaturan");

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
		
		Log.d("alki", "IsiActivity (fase 30) sebelum baca preferences");

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
		
		Log.d("alki", "Akan menuju kitab " + kitabTerakhir + " pasal " + kitabTerakhir + " ayat " + ayatTerakhir);
		
		// muat kitab
		if (kitabTerakhir < S.xkitab.length) {
			S.kitab = S.xkitab[kitabTerakhir];
		}
		// muat pasal dan ayat
		tampil(pasalTerakhir, ayatTerakhir); 
		
		final long terakhirMintaFidbek = preferences.getLong(NAMAPREF_terakhirMintaFidbek, 0);
		
		if (terakhirMintaFidbek == 0) {
			Editor editor = preferences.edit();
			editor.putLong(NAMAPREF_terakhirMintaFidbek, System.currentTimeMillis());
			editor.commit();
		} else {
			final long sekarang = System.currentTimeMillis();
			if (sekarang - terakhirMintaFidbek > 2000*60*60*24) { // 2 hari ato belom pernah
				handler.post(new Runnable() {
					
					@Override
					public void run() {
						popupMintaFidbek();
					}
				});
			}
		}

		Log.d("alki", "IsiActivity onCreate selesai");
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
			this.ayatContextMenu = (int) (menuInfo2.id) + 1;
			this.isiAyatContextMenu = xayat[(int) menuInfo2.id];
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
		
		//# atur terang teks
		{
			String key = getString(R.string.pref_cerahTeks_key);
			int cerahTeks = pengaturan.getInt(key, 100);
			
			S.penerapan.warnaHuruf = 0xff000000 | ((255 * cerahTeks / 100) * 0x010101);
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
		
		lsIsi.invalidateViews();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		Editor editor = preferences.edit();
		editor.putInt(NAMAPREF_kitabTerakhir, S.kitab.pos);
		editor.putInt(NAMAPREF_pasalTerakhir, pasal);
		editor.putInt(NAMAPREF_ayatTerakhir, getAyatBerdasarSkrol());
		editor.putString(NAMAPREF_renungan_nama, S.penampungan.renungan_nama);
		editor.commit();
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

	/**
	 * @param ayat mulai dari 1
	 */
	private static SpannableStringBuilder tampilanAyatSederhana(int ayat, String isi) {
		SpannableStringBuilder seayat = new SpannableStringBuilder();
		
		String ayat_s = String.valueOf(ayat);
		
		seayat.append(ayat_s).append(" ").append(isi);
		seayat.setSpan(new ForegroundColorSpan(WARNA_NOMER_AYAT), 0, ayat_s.length(), 0);
		
		seayat.setSpan(new LeadingMarginSpan.Standard(0, S.penerapan.indenParagraf), 0, ayat_s.length() + 1 + isi.length(), 0);
		
		return seayat;
	}

	public void tampilanAyatTehel(RelativeLayout res, int ayat, String isi) {
		// @@ = mulai ayat dengan tehel
		// @0 = mulai menjorok 0
		// @1 = mulai menjorok 1
		// @2 = mulai menjorok 2
		// @8 = tanda kasi jarak ke ayat berikutnya
		int posParse = 2; // mulai setelah @@
		int menjorok = 0;
		char[] isi_cc = isi.toCharArray();
		TextView tehelTerakhir = null;
		boolean keluarlah = false;
		boolean belumAdaTehel = true;
		boolean nomerAyatUdaDitulis = false;
		
		LinearLayout tempatTehel = (LinearLayout) res.findViewById(R.id.tempatTehel);
		tempatTehel.removeAllViews();
		
		while (true) {
			int posSampe = isi.indexOf('@', posParse);

			if (posSampe == -1) {
				// abis
				posSampe = isi.length();
				keluarlah = true;
			}
			
			if (posParse == posSampe) {
				// di awal, belum ada apa2!
			} else {
				Log.d("alki", "akan masukinTehel menjorok=" + menjorok + " " + isi.substring(posParse, posSampe));
				
				// bikin tehel
				{
					TextView tehel = new TextView(this);
					if (menjorok == 1) {
						tehel.setPadding(S.penerapan.menjorokSatu, 0, 0, 0);
					} else if (menjorok == 2) {
						tehel.setPadding(S.penerapan.menjorokDua, 0, 0, 0);
					}
					
					// kasus: belum ada tehel dan tehel pertama menjorok 0
					if (belumAdaTehel && menjorok == 0) {
						//# kasih no ayat di depannya
						SpannableStringBuilder s = new SpannableStringBuilder();
						String ayat_s = String.valueOf(ayat);
						s.append(ayat_s).append(" ").append(isi, posParse, posSampe);
						s.setSpan(new ForegroundColorSpan(WARNA_NOMER_AYAT), 0, ayat_s.length(), 0);
						tehel.setText(s, BufferType.SPANNABLE);
						
						// kasi tanda biar nanti ga tulis nomer ayat lagi
						nomerAyatUdaDitulis = true;
					} else {
						tehel.setText(isi_cc, posParse, posSampe - posParse);
					}
					
					aturTampilanTeksIsi(tehel);
					tempatTehel.addView(tehel);
					
					tehelTerakhir = tehel;
				}
				
				belumAdaTehel = false;
			}
			
			if (keluarlah) break;

			char jenisTanda = isi_cc[posSampe + 1];
			if (jenisTanda == '1') {
				menjorok = 1;
			} else if (jenisTanda == '2') {
				menjorok = 2;
			} else if (jenisTanda == '8') {
				if (tehelTerakhir != null) {
					tehelTerakhir.append("\n");
				}
			}
			
			posParse = posSampe+2;
		}
		
		TextView lAyat = (TextView) res.findViewById(R.id.lAyat);
		if (nomerAyatUdaDitulis) {
			lAyat.setText("");
		} else {
			lAyat.setText(String.valueOf(ayat));
			aturTampilanTeksNomerAyat(lAyat);
		}
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
		} else if (item.getItemId() == R.id.menuSearch) {
			menuSearch_click();
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
			Log.i("alki", "semua pref segera muncul di bawah ini:::");
			{
				Map<String, ?> all = preferences.getAll();
				for (Map.Entry<String, ?> entry: all.entrySet()) {
					Log.i("alki", String.format("%s = %s", entry.getKey(), entry.getValue()));
				}
			}
			Log.i("alki", "dan pengaturan:::");
			{
				Map<String, ?> all = pengaturan.getAll();
				for (Map.Entry<String, ?> entry: all.entrySet()) {
					Log.i("alki", String.format("%s = %s", entry.getKey(), entry.getValue()));
				}
			}
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
				String alamat = data.getStringExtra("terpilih.alamat");
				if (alamat != null) {
					loncatKe(alamat);
				}
			}
		} else if (requestCode == R.id.menuSearch) {
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

	private void tampil(int pasal, int ayat) {
		if (pasal < 1) pasal = 1;
		if (pasal > S.kitab.npasal) pasal = S.kitab.npasal;
		
		if (ayat < 1) ayat = 1;
		if (ayat > S.kitab.nayat[pasal-1]) ayat = S.kitab.nayat[pasal-1];
		
		// muat data GA USAH pake async dong. // diapdet 20100417 biar ga usa async, ga guna.
		{
			int[] perikop_xari;
			Blok[] perikop_xblok;
			int nblok;
			
			xayat = S.muatTeks(getResources(), S.kitab, pasal);
			
			//# max dibikin pol 30 aja (1 pasal max 30 blok, cukup mustahil)
			int max = 30;
			perikop_xari = new int[max];
			perikop_xblok = new Blok[max];
			nblok = S.muatPerikop(getResources(), S.kitab.pos, pasal, perikop_xari, perikop_xblok, max); 
			
			//# tadinya onPostExecute
			ayatAdapter_.setData(xayat, perikop_xari, perikop_xblok, nblok);
			lsIsi.setAdapter(ayatAdapter_);
			ayatAdapter_.notifyDataSetChanged();
			
			String judul = S.kitab.judul + " " + pasal;
			bTuju.setText(judul);
			
			this.pasal = pasal;
			
			int position = ayatAdapter_.getPositionDariAyat(ayat);
			
			if (position == -1) {
				Log.w("alki", "ga bisa ketemu ayat " + ayat + ", ANEH!");
			} else {
				lsIsi.setSelectionFromTop(position, lsIsi.getVerticalFadingEdgeLength());
			}
		}
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
		// hanya tampilin ini kalo ga lagi bikin index.
		if (! lagiBikinIndex) {
			menuSearch_click();
		}
		
		return true;
	}
	
	private static void aturTampilanTeksIsi(TextView t) {
		t.setTypeface(S.penerapan.jenisHuruf, S.penerapan.tebalHuruf);
		t.setTextSize(TypedValue.COMPLEX_UNIT_PX, S.penerapan.ukuranTeksPx);
		t.setTextColor(S.penerapan.warnaHuruf);
	}

	private static void aturTampilanTeksNomerAyat(TextView t) {
		t.setTypeface(S.penerapan.jenisHuruf, S.penerapan.tebalHuruf);
		t.setTextSize(TypedValue.COMPLEX_UNIT_PX, S.penerapan.ukuranTeksPx);
		t.setTextColor(WARNA_NOMER_AYAT);
	}

	private class AyatAdapter extends BaseAdapter {
		private String[] dataAyat_;
		private int[] perikop_xari_;
		private Blok[] perikop_xblok_;
		private int nblok_;
		
		/**
		 * Tiap elemen, kalo 0 sampe positif, berarti menunjuk ke AYAT di rendered_
		 * kalo negatif, -1 berarti index 0 di perikop_*, -2 (a) berarti index 1 (b) di perikop_*
		 * 
		 * Konvert a ke b: -(a+1); // -a-1 juga sih sebetulnya. gubrak.
		 * Konvert b ke a: -b-1;
		 */
		private int[] penunjukKotak_;
		
		synchronized void setData(String[] xayat, int[] perikop_xari, Blok[] perikop_xblok, int nblok) {
			dataAyat_ = xayat.clone();
			perikop_xari_ = perikop_xari;
			perikop_xblok_ = perikop_xblok;
			nblok_ = nblok;
			
			bikinPenunjukKotak();
		}
		
		private void bikinPenunjukKotak() {
			penunjukKotak_ = new int[dataAyat_.length + nblok_];
			
			int posBlok = 0;
			int posAyat = 0;
			int posPK = 0;
			
			int nayat = dataAyat_.length;
			while (true) {
				// cek apakah judul perikop, DAN perikop masih ada
				if (posBlok < nblok_) {
					// masih memungkinkan
					if (Ari.toAyat(perikop_xari_[posBlok]) - 1 == posAyat) {
						// ADA PERIKOP.
						penunjukKotak_[posPK++] = -posBlok-1;
						posBlok++;
						continue;
					}
				}
				
				// cek apakah ga ada ayat lagi
				if (posAyat >= nayat) {
					break;
				}
				
				// uda ga ada perikop, ATAU belom saatnya perikop. Maka masukin ayat.
				penunjukKotak_[posPK++] = posAyat;
				posAyat++;
				continue;
			}
			
			if (penunjukKotak_.length != posPK) {
				// ada yang ngaco! di algo di atas
				throw new RuntimeException("Algo selip2an perikop salah! posPK=" + posPK + " posAyat=" + posAyat + " posBlok=" + posBlok + " nayat=" + nayat + " nblok_=" + nblok_ + " xari:" + Arrays.toString(perikop_xari_) + " xblok:" + Arrays.toString(perikop_xblok_));
			}
		}

		@Override
		public synchronized int getCount() {
			if (dataAyat_ == null) return 0;

			return penunjukKotak_.length;
		}

		@Override
		public synchronized String getItem(int position) {
			int id = penunjukKotak_[position];
			
			if (id >= 0) {
				return dataAyat_[position].toString();
			} else {
				return perikop_xblok_[-id-1].toString();
			}
		}

		@Override
		public synchronized long getItemId(int position) {
			 return penunjukKotak_[position];
		}

		@Override
		public synchronized View getView(int position, View convertView, ViewGroup parent) {
			// Harus tentukan apakah ini perikop ato ayat.
			int id = penunjukKotak_[position];
			
			if (id >= 0) {
				// AYAT. bukan judul perikop.
				
				String isi = dataAyat_[id];
				// Udah ditentukan bahwa ini ayat dan bukan perikop, sekarang tinggal tentukan
				// apakah ayat ini pake formating biasa (tanpa menjorok dsb) atau ada formating
				if (isi.charAt(0) == '@') {
					// karakter kedua harus '@' juga, kalo bukan ada ngaco
					if (isi.charAt(1) != '@') {
						throw new RuntimeException("Karakter kedua bukan @. Isi ayat: " + isi);
					}
					
					RelativeLayout res;
					
					if (convertView == null || convertView.getId() != R.layout.satu_ayat_tehel) {
						res = (RelativeLayout) LayoutInflater.from(IsiActivity.this).inflate(R.layout.satu_ayat_tehel, null);
						res.setId(R.layout.satu_ayat_tehel);
					} else {
						res = (RelativeLayout) convertView;
					}
					
					tampilanAyatTehel(res, id + 1, isi);
					
					return res;
				} else {
					TextView res;
					
					if (convertView == null || convertView.getId() != R.layout.satu_ayat_sederhana) {
						res = (TextView) LayoutInflater.from(IsiActivity.this).inflate(R.layout.satu_ayat_sederhana, null);
						res.setId(R.layout.satu_ayat_sederhana);
					} else {
						res = (TextView) convertView;
					}
					
					res.setText(tampilanAyatSederhana(id + 1, isi), BufferType.SPANNABLE);
					
					aturTampilanTeksIsi(res);
					
					return res;
				}
			} else {
				// JUDUL PERIKOP. bukan ayat.
				View res;
				
				if (convertView == null || convertView.getId() != R.layout.header_perikop) {
					res = LayoutInflater.from(IsiActivity.this).inflate(R.layout.header_perikop, null);
					res.setId(R.layout.header_perikop);
				} else {
					res = convertView;
				}
				
				Blok blok = perikop_xblok_[-id-1];
				
				TextView lJudul = (TextView) res.findViewById(R.id.lJudul);
				TextView lXparalel = (TextView) res.findViewById(R.id.lXparalel);
				
				lJudul.setTypeface(S.penerapan.jenisHuruf, Typeface.BOLD);
				lJudul.setTextSize(TypedValue.COMPLEX_UNIT_PX, S.penerapan.ukuranTeksPx);
				lJudul.setText(blok.judul);
				lJudul.setTextColor(S.penerapan.warnaHuruf);
				
				// matikan padding atas kalau position == 0 ATAU sebelum ini juga judul perikop
				if (position == 0 || penunjukKotak_[position-1] < 0) {
					lJudul.setPadding(0, 0, 0, 0);
				} else {
					lJudul.setPadding(0, (int)(displayMetrics.density * 18.f), 0, 0);
				}
				
				// gonekan paralel kalo ga ada
				if (blok.xparalel.length == 0) {
					lXparalel.setVisibility(View.GONE);
				} else {
					lXparalel.setVisibility(View.VISIBLE);
					
					SpannableStringBuilder sb = new SpannableStringBuilder("(");
					int len = 1;

					int total = blok.xparalel.length;
					for (int i = 0; i < total; i++) {
						String paralel = blok.xparalel[i];
						
						if (i > 0) {
							if ( (total == 6 && i == 3) || (total == 4 && i == 2) || (total == 5 && i == 3) ) {
								sb.append("; \n");
								len += 3;
							} else {
								sb.append("; ");
								len += 2;
							}
						}
						
						sb.append(paralel);
						sb.setSpan(new CallbackSpan(paralel, paralelOnClickListener), len, len + paralel.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
						len += paralel.length();
					}
					sb.append(")");
					len += 1;
					
					lXparalel.setText(sb, BufferType.SPANNABLE);
					lXparalel.setMovementMethod(LinkMovementMethod.getInstance());
					lXparalel.setTextColor(S.penerapan.warnaHuruf);
					lXparalel.setLinkTextColor(S.penerapan.warnaHuruf);
				}
				
				return res;
			}
		}
		
		@Override
		public boolean areAllItemsEnabled() {
			return false;
		}
		
		@Override
		public boolean isEnabled(int position) {
			return false;
		}
		
		/**
		 * @param ayat mulai dari 1
		 * @return position di adapter ini atau -1 kalo ga ketemu
		 */
		public int getPositionDariAyat(int ayat) {
			if (penunjukKotak_ == null) return -1;
			
			int ayat0 = ayat - 1;
			
			for (int i = 0; i < penunjukKotak_.length; i++) {
				if (penunjukKotak_[i] == ayat0) {
					// ketemu, tapi kalo ada judul perikop, akan lebih baik. Coba cek mundur dari sini
					for (int j = i-1; j >= 0; j--) {
						if (penunjukKotak_[j] < 0) {
							// masih perikop, yey, kita lanjutkan
							i = j;
						} else {
							// uda bukan perikop. (Berarti uda ayat sebelumnya)
							break;
						}
					}
					
					return i;
				}
			}
			
			return -1;
		}
		
		/**
		 * @return ayat (mulai dari 1). atau 0 kalo ga masuk akal
		 */
		public int getAyatDariPosition(int position) {
			if (penunjukKotak_ == null) return 0;
			
			if (position >= penunjukKotak_.length) {
				position = penunjukKotak_.length - 1;
			}
			
			int id = penunjukKotak_[position];
			
			if (id >= 0) {
				return id + 1;
			}
			
			// perikop nih. Susuri sampe abis
			for (int i = position + 1; i < penunjukKotak_.length; i++) {
				id = penunjukKotak_[i];
				
				if (id >= 0) {
					return id + 1;
				}
			}
			
			Log.w("alki", "masa judul perikop di paling bawah? Ga masuk akal.");
			return 0; 
		}
	}
}
