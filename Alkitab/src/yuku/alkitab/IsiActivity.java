package yuku.alkitab;

import java.util.*;
import java.util.regex.*;

import yuku.alkitab.BukmakEditor.Listener;
import yuku.alkitab.GelembungDialog.RefreshCallback;
import yuku.alkitab.model.*;
import yuku.andoutil.*;
import android.app.*;
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.*;
import android.graphics.drawable.*;
import android.os.*;
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
	View tempatJudul;
	TextView lJudul;
	
	int pasal_1 = 0;
	int ayatContextMenu_1 = -1;
	String isiAyatContextMenu = null;
	SharedPreferences preferences;
	Float ukuranAsalHurufIsi;
	Handler handler = new Handler();
	DisplayMetrics displayMetrics;
	ProgressDialog dialogBikinIndex;
	boolean lagiBikinIndex = false;
	AlkitabDb alkitabDb;
	
	private AyatAdapter ayatAdapter_;
	private boolean tombolAlamatLoncat_;
	
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
		TimingLogger tog = new TimingLogger("alki", "IsiActivity#onCreate");
		tog.addSplit("IsiActivity (fase 0) onCreate dipanggil");
		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.isi);
		tog.addSplit("IsiActivity (fase 5) sebelum siapin macem2");

		S.siapinEdisi(getResources());
		S.siapinKitab(getResources());
		S.bacaPengaturan(this);
		S.siapinPengirimFidbek(this);
		S.pengirimFidbek.cobaKirim();
		
		displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		
		lsIsi = (ListView) findViewById(R.id.lsIsi);
		bTuju = (Button) findViewById(R.id.bTuju);
		bKiri = (ImageButton) findViewById(R.id.bKiri);
		bKanan = (ImageButton) findViewById(R.id.bKanan);
		tempatJudul = findViewById(R.id.tempatJudul);
		lJudul = (TextView) findViewById(R.id.lJudul);
		
		tog.addSplit("IsiActivity (fase 10) sebelum terap pengaturan");

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
		alkitabDb = AlkitabDb.getInstance(this);
		
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
			lsIsi.setKeepScreenOn(true);
		}
	}

	private synchronized void matikanLayarKalauSudahBolehMati() {
		lsIsi.setKeepScreenOn(false);
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
			
			BukmakEditor editor = new BukmakEditor(this, alkitabDb, alamat, ari);
			editor.setListener(muatUlangAtributMapListener);
			editor.bukaDialog();
			
			return true;
		} else if (itemId == R.id.menuTambahCatatan) {
			tampilkanCatatan(S.kitab, pasal_1, ayatContextMenu_1);
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
			if (S.penerapan.sembunyiNavigasi) {
				panelNavigasi.setVisibility(View.GONE);
				tempatJudul.setVisibility(View.VISIBLE);
			} else {
				panelNavigasi.setVisibility(View.VISIBLE);
				tempatJudul.setVisibility(View.GONE);
			}
		}
		
		// wajib
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
	    	
			new AlertDialog.Builder(this)
			.setTitle(R.string.tentang_title)
			.setMessage(
				Html.fromHtml(
						"Alkitab untuk Android<br/>versi " + verName + " (" + verCode + ") rev " + "$LastChangedRevision$".replaceAll("\\$|LastChangedRevision|:| ", "")
						+ "<br/><br/>Terjemahan Baru (c) LAI<br/><br/><b>yuku</b> 2009-2010<br/>www.kejut.com<br/><br/>"
						+ "<b>Bantuan:</b><br/>"
						+ "- <b>Roy Krisnadi</b> salinan indentasi Mazmur<br/>"
						+ "- <b>Oluss</b> ikon program<br/>"
						+ "- <b>Jaya Satrio</b> ikon bukmak<br/>"
						+ "- dsb.<br/>"
				))
			.setPositiveButton("OK", null)
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
					Log.i("alki", entry.getKey() + ": " + entry.getValue());
				}
			}
			Log.i("alki", "### pengaturan:::");
			{
				Map<String, ?> all = PreferenceManager.getDefaultSharedPreferences(this).getAll();
				for (Map.Entry<String, ?> entry: all.entrySet()) {
					Log.i("alki", entry.getKey() + ": " + entry.getValue());
				}
			}
			Log.i("alki", "### db:::");
			alkitabDb.dump();
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
		intent.putExtra(Search2Activity.EXTRA_filter_lama, search2_filter_lama);
		intent.putExtra(Search2Activity.EXTRA_filter_baru, search2_filter_baru);
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
			ayatAdapter_.muatAtributMap();
			ayatAdapter_.notifyDataSetChanged();
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
				search2_filter_lama = data.getBooleanExtra(Search2Activity.EXTRA_filter_lama, true);
				search2_filter_baru = data.getBooleanExtra(Search2Activity.EXTRA_filter_baru, true);
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
			ayatAdapter_.muatAtributMap();
			ayatAdapter_.notifyDataSetChanged();
			
			// kasi tau activity
			this.pasal_1 = pasal_1;
			
			final int position = ayatAdapter_.getPositionDariAyat(ayat_1);
			
			if (position == -1) {
				Log.w("alki", "ga bisa ketemu ayat " + ayat_1 + ", ANEH!");
			} else {
				lsIsi.setSelectionFromTop(position, lsIsi.getVerticalFadingEdgeLength());
			}
		}
		
		String judul = S.alamat(S.kitab, pasal_1);
		lJudul.setText(judul);
		bTuju.setText(judul);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
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

	static void aturIsiDanTampilanCuplikanBukmak(TextView t, String alamat, String isi) {
		SpannableStringBuilder sb = new SpannableStringBuilder(alamat);
		sb.setSpan(new UnderlineSpan(), 0, alamat.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		sb.append(" ").append(isi);
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
		dialog.setDbKitabPasalAyat(alkitabDb, kitab_, pasal_1, ayat_1);
		dialog.tampilkan();
	}
	
	public class AtributListener {
		public void onClick(Kitab kitab_, int pasal_1, int ayat_1, int jenis) {
			if (jenis == AlkitabDb.ENUM_Bukmak2_jenis_bukmak) {
				final int ari = Ari.encode(kitab_.pos, pasal_1, ayat_1);
				String alamat = S.alamat(ari);
				BukmakEditor editor = new BukmakEditor(IsiActivity.this, alkitabDb, alamat, ari);
				editor.setListener(muatUlangAtributMapListener);
				editor.bukaDialog();
			} else if (jenis == AlkitabDb.ENUM_Bukmak2_jenis_catatan) {
				tampilkanCatatan(kitab_, pasal_1, ayat_1);
			}
		}
	}
}
