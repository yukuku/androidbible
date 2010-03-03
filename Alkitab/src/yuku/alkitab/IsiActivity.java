package yuku.alkitab;

import java.util.Map;

import yuku.alkitab.model.*;
import android.app.*;
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Typeface;
import android.os.*;
import android.preference.PreferenceManager;
import android.text.*;
import android.text.style.*;
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

	String[] xayat;
	ListView lsIsi;
	Button bTuju;
	ImageButton bKiri;
	ImageButton bKanan;
	int pasal = 0;
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
	}

	private boolean lsIsi_itemLongClick(AdapterView<?> parent, View view, int position, long id) {
		Log.d("klik panjang", "di " + position + " id " + id);
		
		return false;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		
		new MenuInflater(this).inflate(R.menu.context_ayat, menu);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menuSalinAyat) {
			AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
			String isiAyat = xayat[menuInfo.position];
			ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboardManager.setText(isiAyat);
			
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
			String ukuranHuruf_s = pengaturan.getString(getString(R.string.pref_ukuranHuruf_key), "100");
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
			if (pengaturan.contains(key)) {
				cerahTeks = pengaturan.getInt(key, 80);
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

	protected void bTuju_click() {
		Intent intent = new Intent(this, MenujuActivity.class);
		intent.putExtra("pasal", pasal);
		
		int ayat = getAyatBerdasarSkrol();
		intent.putExtra("ayat", ayat);
		
		startActivityForResult(intent, R.id.menuTuju);
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
		menu.add(0, 0x985802, 0, "gebug 2");
		menu.add(0, 0x985803, 0, "gebug 3 (reset pref)");
		menu.add(0, 0x985804, 0, "gebug 4 (reset pengaturan)");
		
		return true;
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		if (item.getItemId() == R.id.menuTuju) {
			bTuju_click();
		} else if (item.getItemId() == R.id.menuKitab) {
			Intent intent = new Intent(this, KitabActivity.class);
			startActivityForResult(intent, R.id.menuKitab);
		} else if (item.getItemId() == R.id.menuEdisi) {
			Intent intent = new Intent(this, EdisiActivity.class);
			startActivityForResult(intent, R.id.menuEdisi);
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
					Html.fromHtml(getString(R.string.tentang_message, verName, verCode))).show();
		} else if (item.getItemId() == R.id.menuPengaturan) {
			Intent intent = new Intent(this, PengaturanActivity.class);
			startActivityForResult(intent, R.id.menuPengaturan);
		} else if (item.getItemId() == R.id.menuFidbek) {
			popupMintaFidbek();
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
		} else if (item.getItemId() == 0x985802) { // debug 2
			
			// kosong dulu
		} else if (item.getItemId() == 0x985803) { // debug 3
			Editor editor = preferences.edit();
			editor.clear();
			editor.commit();
		} else if (item.getItemId() == 0x985804) { // debug 4
			Editor editor = pengaturan.edit();
			editor.clear();
			editor.commit();
		}
		
		return super.onMenuItemSelected(featureId, item);
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

//	@Override
//	public boolean onTrackballEvent(MotionEvent event) {
//		float x = event.getX();
//		float y = event.getY();
//		float absx = Math.abs(x);
//		float absy = Math.abs(y);
//		
//		// hanya kalo y nya lebih dari x
//		if (absy > absx) {
//			float actualY = event.getY() * event.getYPrecision();
//			Log.d("trekbol skrol", String.format("actual y=%f", actualY));
//			Log.d("lsIsi skrol", String.format("%d %d", lsIsi.getScrollX(), lsIsi.getScrollY()));
//			
//			lsIsi.scrollBy(0, (int) (actualY * 10 * displayMetrics.density));
//			lsIsi.computeScroll();
//			
//			return true;
//		} else {
//			Log.d("trekbol abai", String.format("%f %f", event.getX(), event.getY()));
//			return false;
//		}
//		
//	}
	
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
