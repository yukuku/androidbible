package yuku.alkitab;

import yuku.alkitab.model.*;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.pm.PackageManager.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.widget.*;

public class IsiActivity extends Activity {
	private static final String UDAH_DIPERINGATKAN_BETA = "udahDiperingatkanBeta";
	
	String[] xayat;
	int[] ayat_offset;
	TextView tIsi;
	ScrollView scrollIsi;
	Button bTuju;
	ImageButton bKiri;
	ImageButton bKanan;
	int pasal = 0;
	private SharedPreferences preferences;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.isi);
		
		S.siapinEdisi(getResources());
		S.siapinKitab(getResources());
		
		tIsi = (TextView) findViewById(R.id.tIsi);
		scrollIsi = (ScrollView) findViewById(R.id.scrollIsi);
		
		bTuju = (Button) findViewById(R.id.bTuju);
		bKiri = (ImageButton) findViewById(R.id.bKiri);
		bKanan = (ImageButton) findViewById(R.id.bKanan);
		
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
		boolean udahDiperingatkanBeta = preferences.getBoolean(UDAH_DIPERINGATKAN_BETA, false);
		
		tampil(0, 0); // TODO tampilin yang terakhir dong!
		
		if (! udahDiperingatkanBeta) {
			new AlertDialog.Builder(this).setMessage(R.string.peringatanBeta_s).setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					//Editor editor = preferences.edit();
					//editor.putBoolean(UDAH_DIPERINGATKAN_BETA, true);
					//editor.commit();
				}
			}).setNegativeButton("No!", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}
			}).show();
		}
	}

	protected void bTuju_click() {
		Intent intent = new Intent(this, MenujuActivity.class);
		intent.putExtra("pasal", pasal);
		
		int line = tIsi.getLayout().getLineForVertical(scrollIsi.getScrollY());
		int offset = tIsi.getLayout().getOffsetForHorizontal(line, 0);
		int ayat = 0;
		for (int i = 1; i < ayat_offset.length; i++) {
			if (ayat_offset[i] > offset) {
				ayat = i-1;
				break;
			}
		}
		intent.putExtra("ayat", ayat+1);
		
		startActivityForResult(intent, R.id.menuTuju);
	}

	private int getAyatTop(int ayat) {
		Layout layout = tIsi.getLayout();
		int line = layout.getLineForOffset(ayat_offset[ayat-1]);
		return layout.getLineTop(line);
	}

	private SpannableStringBuilder siapinTampilanAyat() {
		SpannableStringBuilder res = new SpannableStringBuilder();
		
		int c = 0;
		String pengawal = "";
		String pengakhir = "\n";
		
		ayat_offset = new int[xayat.length];
		for (int i = 0; i < xayat.length; i++) {
			ayat_offset[i] = c;
			
			pengawal = (i+1) + " ";
			res.append(pengawal).append(xayat[i]).append(pengakhir);
			res.setSpan(new ForegroundColorSpan(0xff8080ff), c, c + pengawal.length() - 1, 0);
			
			c += pengawal.length() + xayat[i].length() + pengakhir.length();
		}
		
		return res;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		new MenuInflater(this).inflate(R.menu.isi, menu);
		
		menu.add(0, 0x985801, 0, "gebug 1");
		menu.add(0, 0x985802, 0, "gebug 2");
		
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
		} else if (item.getItemId() == 0x985801) { // debug 1
			CharSequence t = tIsi.getText();
			SpannableStringBuilder builder = new SpannableStringBuilder(t);
			builder.append("n");
			tIsi.setText(builder);
		} else if (item.getItemId() == 0x985802) { // debug 2
			CharSequence t = tIsi.getText();
			Log.w("disyuh", t.subSequence(t.length()-10, t.length()).toString());
			tIsi.setText(t.subSequence(0, t.length() - 10));
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
		}
	}

	private void tampil(int pasal, int ayat) {
		if (pasal < 1) pasal = 1;
		if (pasal > S.kitab.npasal) pasal = S.kitab.npasal;
		
		if (ayat < 1) ayat = 1;
		if (ayat > S.kitab.nayat[pasal-1]) ayat = S.kitab.nayat[pasal-1];
		
		// muat data pake async dong.
		new AsyncTask<Integer, Void, SpannableStringBuilder>() {
			int pasal;
			int ayat;
			
			@Override
			protected SpannableStringBuilder doInBackground(Integer... params) {
				pasal = params[0];
				ayat = params[1];
				
				xayat = S.muatTeks(getResources(), pasal);
				return siapinTampilanAyat();
			}
		
			@Override
			protected void onPostExecute(SpannableStringBuilder result) {
				tIsi.setText(result);
				setTitle(getString(R.string.judulIsi, S.kitab.judul, pasal, ayat));
				IsiActivity.this.pasal = pasal;
				
				scrollIsi.post(new Runnable() {
					@Override
					public void run() {
						int y = getAyatTop(ayat);
						scrollIsi.scrollTo(0, y - ViewConfiguration.get(IsiActivity.this).getScaledFadingEdgeLength());
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
}
