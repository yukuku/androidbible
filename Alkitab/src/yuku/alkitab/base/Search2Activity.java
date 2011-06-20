package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;

import java.util.*;

import yuku.alkitab.*;
import yuku.alkitab.base.model.*;
import yuku.andoutil.*;

public class Search2Activity extends Activity {
	public static final String TAG = Search2Activity.class.getSimpleName();
	
	public static final String EXTRA_carian = "carian"; //$NON-NLS-1$
	public static final String EXTRA_filter_lama = "filter_lama"; //$NON-NLS-1$
	public static final String EXTRA_filter_baru = "filter_baru"; //$NON-NLS-1$
	public static final String EXTRA_hasilCari = "hasilCari"; //$NON-NLS-1$
	public static final String EXTRA_posisiTerpilih = "posisiTerpilih"; //$NON-NLS-1$
	public static final String EXTRA_ariTerpilih = "ariTerpilih"; //$NON-NLS-1$
	
	ListView lsHasilCari;
	ImageButton bCari;
	EditText tCarian;
	CheckBox cFilterLama;
	CheckBox cFilterBaru;
	TextView lTiadaHasil;
	
	private int warnaStabilo;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		S.siapinKitab();
		S.bacaPengaturan(this);
		S.siapinPengirimFidbek(this);
		
		setContentView(R.layout.activity_search2);

		lsHasilCari = (ListView) findViewById(R.id.lsHasilCari);
		bCari = (ImageButton) findViewById(R.id.bCari);
		tCarian = (EditText) findViewById(R.id.tCarian);
		cFilterLama = (CheckBox) findViewById(R.id.cFilterLama);
		cFilterBaru = (CheckBox) findViewById(R.id.cFilterBaru);
		lTiadaHasil = (TextView) findViewById(R.id.lTiadaHasil);
		
		lsHasilCari.setBackgroundColor(S.penerapan.warnaLatar);
		lsHasilCari.setCacheColorHint(S.penerapan.warnaLatar);
		
		{
			int warnaLatar = S.penerapan.warnaLatar;
			float keterangan = 0.30f * Color.red(warnaLatar) + 0.59f * Color.green(warnaLatar) + 0.11f * Color.blue(warnaLatar);
			if (keterangan < 0.5f) {
				warnaStabilo = 0xff66ff66;
			} else {
				warnaStabilo = 0xff990099;
			}
		}
		
		lsHasilCari.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				int ari = (int) parent.getItemIdAtPosition(position);
				
				Intent data = new Intent();
				data.putExtra(EXTRA_carian, tCarian.getText().toString());
				data.putExtra(EXTRA_filter_lama, cFilterLama.isChecked());
				data.putExtra(EXTRA_filter_baru, cFilterBaru.isChecked());
				
				Search2Adapter adapter = (Search2Adapter) parent.getAdapter();
				if (adapter != null) {
					data.putExtra(EXTRA_hasilCari, adapter.getHasilCari());
				}
				
				data.putExtra(EXTRA_posisiTerpilih, position);
				data.putExtra(EXTRA_ariTerpilih, ari);
				
				setResult(RESULT_OK, data);
				finish();
			}
		});
		bCari.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) { bCari_click(); }
		});
		tCarian.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEARCH) {
					bCari_click();
					return true;
				}
				return false;
			}
		});
		
		{
			Intent intent = getIntent();
			if (intent != null) {
				String carian = intent.getStringExtra(EXTRA_carian);
				boolean filter_lama = intent.getBooleanExtra(EXTRA_filter_lama, true);
				boolean filter_baru = intent.getBooleanExtra(EXTRA_filter_baru, true);
				IntArrayList hasilCari = intent.getParcelableExtra(EXTRA_hasilCari);
				int posisiTerpilih = intent.getIntExtra(EXTRA_posisiTerpilih, -1);
				
				if (carian != null) {
					tCarian.setText(carian);
					
					if (hasilCari != null) {
						String[] xkata = Search2Engine.tokenkan(carian);
						lsHasilCari.setAdapter(new Search2Adapter(hasilCari, xkata));
						lsHasilCari.setFastScrollEnabled(true);
					}
				}
				
				cFilterLama.setChecked(filter_lama);
				cFilterBaru.setChecked(filter_baru);
				
				if (posisiTerpilih != -1) {
					lsHasilCari.setSelection(posisiTerpilih);
				}
				
				//Log.d("alki", "masuk search2 dengan carian=" + carian + ", posisiTerpilih=" + posisiTerpilih + " hasilCari=" + hasilCari);
			}
		}
	}
	
	protected void bCari_click() {
		final String carian = tCarian.getText().toString();
		final boolean filter_lama = cFilterLama.isChecked();
		final boolean filter_baru = cFilterBaru.isChecked();
		
		if (!filter_lama && !filter_baru) {
			Toast.makeText(Search2Activity.this, R.string.pilih_perjanjian_lama_dan_atau_baru, Toast.LENGTH_SHORT).show();
			return;
		}
		
		if (carian.trim().length() == 0) {
			return;
		}
		
		final String[] xkata = Search2Engine.tokenkan(carian);
		
		final ProgressDialog progress = new ProgressDialog(this);
		progress.setTitle(getString(R.string.mencari));
		progress.setMessage(Html.fromHtml(String.format(U.preprocessHtml(getString(R.string.sedang_mencari_ayat_yang_mengandung_kata_kata_xkata)), Arrays.toString(xkata))));
		progress.setCancelable(false);
		progress.setIndeterminate(true);
		progress.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				// paksa muncul
				progress.show();
			}
		});
		progress.show();
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				synchronized (Search2Activity.this) {
					final IntArrayList hasil = Search2Engine.cari(Search2Activity.this, xkata, filter_lama, filter_baru);
					Search2Activity.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							lsHasilCari.setAdapter(new Search2Adapter(hasil, xkata));
							lsHasilCari.setFastScrollEnabled(true);
							Toast.makeText(Search2Activity.this, getString(R.string.size_hasil, hasil.size()), Toast.LENGTH_SHORT).show();
							
							if (hasil.size() > 0) {
								//# close soft keyboard 
								InputMethodManager inputManager = (InputMethodManager) Search2Activity.this.getSystemService(Context.INPUT_METHOD_SERVICE); 
								inputManager.hideSoftInputFromWindow(tCarian.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
							}
						}
					});
					
					progress.setOnDismissListener(null);
					progress.dismiss();
				}
			}
		}, "search2engine").start(); //$NON-NLS-1$
	}
	
	class Search2Adapter extends BaseAdapter /*implements SectionIndexer*/ {
		IntArrayList hasilCari;
		String[] xkata;
//		private int[] seksiKitab;
//		private int[] seksiPos;
//		private int nseksi;
//		private String[] seksiLabel;
		
		public Search2Adapter(IntArrayList hasilCari, String[] xkata) {
			this.hasilCari = hasilCari;
			this.xkata = xkata;
//			bikinSeksi();
		}

		@Override
		public int getCount() {
			return hasilCari.size();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return hasilCari.get(position);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View res = convertView != null? convertView: LayoutInflater.from(Search2Activity.this).inflate(R.layout.item_search2, null);

			TextView lAlamat = (TextView) res.findViewById(R.id.lAlamat);
			TextView lCuplikan = (TextView) res.findViewById(R.id.lCuplikan);
			
			int ari = hasilCari.get(position);
			Kitab kitab = S.edisiAktif.getKitab(Ari.toKitab(ari));
			int pasal_1 = Ari.toPasal(ari);
			int ayat_1 = Ari.toAyat(ari);
			SpannableStringBuilder sb = new SpannableStringBuilder(S.alamat(kitab, pasal_1, ayat_1));
			IsiActivity.aturTampilanTeksAlamatHasilCari(lAlamat, sb);
			
			String ayat = S.muatSatuAyat(S.edisiAktif, kitab, pasal_1, ayat_1);
			ayat = U.buangKodeKusus(ayat);
			lCuplikan.setText(Search2Engine.hilite(ayat, xkata, warnaStabilo));
			
			IsiActivity.aturTampilanTeksIsi(lCuplikan);
			
			return res;
		}
		
		IntArrayList getHasilCari() {
			return hasilCari;
		}

//		private void bikinSeksi() {
//			int[] seksiKitab = new int[256]; // max
//			int[] seksiAwal = new int[256];
//			int nseksi = 0;
//			int maxSeksiKini = 0x000000;
//			
//			int[] tmpHasilCari = hasilCari.buffer();
//			for (int i = 0, len = hasilCari.size(); i < len; i++) {
//				int ari = tmpHasilCari[i];
//				if (ari >= maxSeksiKini) { // seksi baru diperlukan
//					int kitab = Ari.toKitab(ari);
//					seksiKitab[nseksi] = kitab;
//					seksiAwal[nseksi] = i;
//					nseksi++;
//					maxSeksiKini = Ari.encode(kitab+1, 0);
//				}
//			}
//			
//			this.seksiKitab = seksiKitab;
//			this.seksiPos = seksiAwal;
//			this.nseksi = nseksi;
//			
//			Log.d(TAG, "hasilCari = " + hasilCari); //$NON-NLS-1$
//			Log.d(TAG, "nseksi = " + nseksi); //$NON-NLS-1$
//			Log.d(TAG, "seksiKitab = " + Arrays.toString(seksiKitab)); //$NON-NLS-1$
//			Log.d(TAG, "seksiAwal = " + Arrays.toString(seksiAwal)); //$NON-NLS-1$
//		}
//
//		@Override
//		public int getPositionForSection(int section) {
//			Log.d(TAG, "getPositionForSection " + section); //$NON-NLS-1$
//			if (section >= nseksi) section = nseksi-1;
//			if (section < 0) section = 0;
//			return seksiPos[section];
//		}
//
//		@Override
//		public int getSectionForPosition(int position) {
//			Log.d(TAG, "getSectionForPosition " + position); //$NON-NLS-1$
//			int pos = Arrays.binarySearch(seksiPos, position);
//			int res = pos >= 0? pos: -pos-1;
//			if (res >= nseksi) return nseksi-1;
//			if (res < 0) return 0;
//			return res;
//		}
//
//		@Override
//		public Object[] getSections() {
//			if (seksiLabel == null) {
//				seksiLabel = new String[nseksi];
//				for (int i = 0; i < nseksi; i++) {
//					seksiLabel[i] = S.edisiAktif.volatile_xkitab[seksiKitab[i]].judul;
//				}
//			}
//			
//			Log.d(TAG, "getSections -> " + Arrays.toString(seksiLabel)); //$NON-NLS-1$
//			return seksiLabel;
//		}
	}
	
}
