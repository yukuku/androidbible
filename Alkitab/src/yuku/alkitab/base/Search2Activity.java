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

import yuku.alkitab.base.model.*;
import yuku.andoutil.*;
import yuku.androidsdk.searchbar.*;
import yuku.androidsdk.searchbar.SearchBar.OnSearchListener;

public class Search2Activity extends Activity {
	public static final String TAG = Search2Activity.class.getSimpleName();
	
	public static final String EXTRA_carian = "carian"; //$NON-NLS-1$
	public static final String EXTRA_filter_lama = "filter_lama"; //$NON-NLS-1$
	public static final String EXTRA_filter_baru = "filter_baru"; //$NON-NLS-1$
	public static final String EXTRA_hasilCari = "hasilCari"; //$NON-NLS-1$
	public static final String EXTRA_posisiTerpilih = "posisiTerpilih"; //$NON-NLS-1$
	public static final String EXTRA_ariTerpilih = "ariTerpilih"; //$NON-NLS-1$
	
	ListView lsHasilCari;
	SearchBar searchBar;
	View panelFilter;
	CheckBox cFilterLama;
	CheckBox cFilterBaru;
	TextView lTiadaHasil;
	
	int warnaStabilo;
	
	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		U.nyalakanTitleBarHanyaKalauTablet(this);
		
		S.siapinKitab();
		S.bacaPengaturan(this);
		S.siapinPengirimFidbek(this);
		
		setContentView(R.layout.activity_search2);

		lsHasilCari = U.getView(this, R.id.lsHasilCari);
		searchBar = U.getView(this, R.id.searchBar);
		panelFilter = U.getView(this, R.id.panelFilter);
		cFilterLama = U.getView(this, R.id.cFilterLama);
		cFilterBaru = U.getView(this, R.id.cFilterBaru);
		lTiadaHasil = U.getView(this, R.id.lTiadaHasil);
		
		((ViewGroup) panelFilter.getParent()).removeView(panelFilter);
		searchBar.setBottomView(panelFilter);
		
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
			@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				int ari = (int) parent.getItemIdAtPosition(position);
				
				Intent data = new Intent();
				data.putExtra(EXTRA_carian, searchBar.getText().toString());
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
		searchBar.setOnSearchListener(new OnSearchListener() {
			@Override public void onSearch(SearchBar searchBar, Editable text) {
				search(text.toString());
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
					searchBar.setText(carian);
					
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
	
	protected void search(String carian) {
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
		
		final ProgressDialog pd = new ProgressDialog(this);
		pd.setTitle(getString(R.string.mencari));
		pd.setMessage(Html.fromHtml(String.format(U.preprocessHtml(getString(R.string.sedang_mencari_ayat_yang_mengandung_kata_kata_xkata)), Arrays.toString(xkata))));
		pd.setCancelable(false);
		pd.setIndeterminate(true);
		pd.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override public void onDismiss(DialogInterface dialog) {
				// paksa muncul
				pd.show();
			}
		});
		pd.show();
		
		new AsyncTask<Void, Void, IntArrayList>() {
			@Override protected IntArrayList doInBackground(Void... params) {
				synchronized (Search2Activity.this) {
					return Search2Engine.cari(Search2Activity.this, xkata, filter_lama, filter_baru);
				}
			}
			
			@Override protected void onPostExecute(IntArrayList hasil) {
				lsHasilCari.setAdapter(new Search2Adapter(hasil, xkata));
				lsHasilCari.setFastScrollEnabled(true);
				Toast.makeText(Search2Activity.this, getString(R.string.size_hasil, hasil.size()), Toast.LENGTH_SHORT).show();
				
				if (hasil.size() > 0) {
					//# close soft keyboard 
					InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); 
					inputManager.hideSoftInputFromWindow(searchBar.getSearchField().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
				}
				
				pd.setOnDismissListener(null);
				pd.dismiss();
			};
		}.execute();
	}
	
	class Search2Adapter extends BaseAdapter {
		IntArrayList hasilCari;
		String[] xkata;
		
		public Search2Adapter(IntArrayList hasilCari, String[] xkata) {
			this.hasilCari = hasilCari;
			this.xkata = xkata;
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
			PengaturTampilan.aturTampilanTeksAlamatHasilCari(lAlamat, sb);
			
			String ayat = S.muatSatuAyat(S.edisiAktif, kitab, pasal_1, ayat_1);
			ayat = U.buangKodeKusus(ayat);
			lCuplikan.setText(Search2Engine.hilite(ayat, xkata, warnaStabilo));
			
			PengaturTampilan.aturTampilanTeksIsi(lCuplikan);
			
			return res;
		}
		
		IntArrayList getHasilCari() {
			return hasilCari;
		}
	}
}
