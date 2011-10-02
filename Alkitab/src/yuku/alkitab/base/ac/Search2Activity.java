package yuku.alkitab.base.ac;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.*;
import android.util.*;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.inputmethod.*;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

import java.util.*;

import yuku.alkitab.R;
import yuku.alkitab.base.*;
import yuku.alkitab.base.Search2Engine.Query;
import yuku.alkitab.base.model.*;
import yuku.andoutil.*;
import yuku.androidsdk.searchbar.*;
import yuku.androidsdk.searchbar.SearchBar.OnSearchListener;

public class Search2Activity extends Activity {
	public static final String TAG = Search2Activity.class.getSimpleName();
	
	public static final String EXTRA_query = "query"; //$NON-NLS-1$
	public static final String EXTRA_hasilCari = "hasilCari"; //$NON-NLS-1$
	public static final String EXTRA_posisiTerpilih = "posisiTerpilih"; //$NON-NLS-1$
	public static final String EXTRA_ariTerpilih = "ariTerpilih"; //$NON-NLS-1$
	
	ListView lsHasilCari;
	SearchBar searchBar;
	View panelFilter;
	CheckBox cFilterLama;
	CheckBox cFilterBaru;
	TextView tFilterRumit;
	View bEditFilter;
	
	int warnaHilite;
	SparseBooleanArray xkitabPosTerpilih = new SparseBooleanArray();

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		U.nyalakanTitleBarHanyaKalauTablet(this);
		
		S.siapinKitab();
		S.bacaPengaturan();
		
		setContentView(R.layout.activity_search2);

		lsHasilCari = U.getView(this, R.id.lsHasilCari);
		searchBar = U.getView(this, R.id.searchBar);
		panelFilter = U.getView(this, R.id.panelFilter);
		cFilterLama = U.getView(this, R.id.cFilterLama);
		cFilterBaru = U.getView(this, R.id.cFilterBaru);
		tFilterRumit = U.getView(this, R.id.tFilterRumit);
		bEditFilter = U.getView(this, R.id.bEditFilter);
		
		((ViewGroup) panelFilter.getParent()).removeView(panelFilter);
		searchBar.setBottomView(panelFilter);
		
		lsHasilCari.setBackgroundColor(S.penerapan.warnaLatar);
		lsHasilCari.setCacheColorHint(S.penerapan.warnaLatar);
		
		warnaHilite = U.getWarnaHiliteKontrasDengan(S.penerapan.warnaLatar);
		
		lsHasilCari.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				int ari = (int) parent.getItemIdAtPosition(position);
				
				Intent data = new Intent();
				data.putExtra(EXTRA_query, getQuery());
				
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
		bEditFilter.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				bEditFilter_click();
			}
		});
		cFilterLama.setOnCheckedChangeListener(cFilterLama_checkedChange);
		cFilterBaru.setOnCheckedChangeListener(cFilterBaru_checkedChange);
		
		{
			Intent intent = getIntent();
			Query query = intent.getParcelableExtra(EXTRA_query);
			IntArrayList hasilCari = intent.getParcelableExtra(EXTRA_hasilCari);
			int posisiTerpilih = intent.getIntExtra(EXTRA_posisiTerpilih, -1);
			
			if (query != null) {
				searchBar.setText(query.carian);
				
				if (hasilCari != null) {
					String[] xkata = Search2Engine.tokenkan(query.carian);
					lsHasilCari.setAdapter(new Search2Adapter(hasilCari, xkata));
				}
			}
			
			if (query == null) { // default: semua kitab
				for (Kitab k: S.edisiAktif.getConsecutiveXkitab()) {
					xkitabPosTerpilih.put(k.pos, true);
				}
			} else if (query.xkitabPos != null) {
				xkitabPosTerpilih = query.xkitabPos;
			}
			
			aturTampilanFilterLamaBaru();
			
			if (posisiTerpilih != -1) {
				lsHasilCari.setSelection(posisiTerpilih);
			}
		}
	}
	
	private void aturTampilanFilterLamaBaru() {
		// kondisi: kalau sebagian nyala sebagian ga, null.
		// semua nyala: true.
		// semua ga nyala: false.
		Boolean lama = null;
		Boolean baru = null;
		
		{
			int c_nyala = 0, c_mati = 0;
			for (int i = 0; i < 39; i++) {
				boolean nyala = xkitabPosTerpilih.get(i, false);
				if (nyala) c_nyala++; else c_mati++;
			}
			if (c_nyala == 39) lama = true;
			if (c_mati == 39) lama = false;
		}
		
		{
			int c_nyala = 0, c_mati = 0;
			for (int i = 39; i < 66; i++) {
				boolean nyala = xkitabPosTerpilih.get(i, false);
				if (nyala) c_nyala++; else c_mati++;
			}
			if (c_nyala == 27) baru = true;
			if (c_mati == 27) baru = false;
		}
		
		// 22nya true atau false
		if (lama != null && baru != null) {
			cFilterLama.setVisibility(View.VISIBLE);
			cFilterLama.setChecked(lama);
			cFilterBaru.setVisibility(View.VISIBLE);
			cFilterBaru.setChecked(baru);
			tFilterRumit.setVisibility(View.GONE);
		} else {
			// tidak demikian, kita tulis label saja.
			cFilterLama.setVisibility(View.GONE);
			cFilterBaru.setVisibility(View.GONE);
			tFilterRumit.setVisibility(View.VISIBLE);
			StringBuilder sb = new StringBuilder();
			for (int i = 0, len = xkitabPosTerpilih.size(); i < len; i++) {
				if (xkitabPosTerpilih.valueAt(i) == true) {
					int kitabPos = xkitabPosTerpilih.keyAt(i);
					Kitab kitab = S.edisiAktif.getKitab(kitabPos);
					if (kitab != null) {
						if (sb.length() != 0) sb.append(", "); //$NON-NLS-1$
						sb.append(kitab.judul);
					}
				}
			}
			tFilterRumit.setText(sb);
		}
	}

	private OnCheckedChangeListener cFilterLama_checkedChange = new OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			for (int i = 0; i < 39; i++) {
				xkitabPosTerpilih.put(i, isChecked);
			}
		}
	};
	private OnCheckedChangeListener cFilterBaru_checkedChange = new OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			for (int i = 39; i < 66; i++) {
				xkitabPosTerpilih.put(i, isChecked);
			}
		}
	};
	
	protected Query getQuery() {
		Query res = new Query();
		res.carian = searchBar.getText().toString();
		res.xkitabPos = xkitabPosTerpilih;
		return res;
	}

	public void bEditFilter_click() {
		final SearchFilterAdapter adapter = new SearchFilterAdapter();
		
		final AlertDialog[] dialog = new AlertDialog[1]; 
		dialog[0] = new AlertDialog.Builder(this)
		.setTitle(R.string.select_books_to_search)
		.setAdapter(adapter, null)
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface _unused_, int which) {
				ListView lv = dialog[0].getListView();
				xkitabPosTerpilih.clear();
				SparseBooleanArray xpos = lv.getCheckedItemPositions();
				for (int i = 0, len = xpos.size(); i < len; i++) {
					if (xpos.valueAt(i) == true) {
						int position = xpos.keyAt(i);
						Kitab k = adapter.getItem(position);
						if (k != null) {
							xkitabPosTerpilih.put(k.pos, true);
						}
					}
				}
				aturTampilanFilterLamaBaru();
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
		
		final ListView lv = dialog[0].getListView();
		
		// Enable automatic support for multi choice, and also prevent dismissing the dialog because of
		// the click handler set by the alertdialog builder.
		lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		lv.setOnItemClickListener(null);
		
		// set checked items
		for (int position = 0, count = adapter.getCount(); position < count; position++) {
			Kitab k = adapter.getItem(position);
			if (k != null && xkitabPosTerpilih.get(k.pos, false) == true) {
				lv.setItemChecked(position, true);
			}
		}
	}
	
	class SearchFilterAdapter extends BaseAdapter {
		private Kitab[] xkitab;

		public SearchFilterAdapter() {
			xkitab = S.edisiAktif.getConsecutiveXkitab();
		}
		
		@Override public int getCount() {
			return xkitab.length;
		}

		@Override public Kitab getItem(int position) {
			return xkitab[position];
		}

		@Override public long getItemId(int position) {
			return position;
		}

		@Override public View getView(int position, View convertView, ViewGroup parent) {
			CheckedTextView res = (CheckedTextView) (convertView != null? convertView: getLayoutInflater().inflate(android.R.layout.select_dialog_multichoice, null));
			
			Kitab k = getItem(position);
			res.setText(k.judul);
			res.setTextColor(U.getWarnaBerdasarkanKitabPos(k.pos));
			
			return res;
		}
	}

	protected void search(String carian) {
		if (carian.trim().length() == 0) {
			return;
		}
		
		// cek apakah ga ada yang terpilih
		{
			int terpilihPertama = xkitabPosTerpilih.indexOfValue(true);
			if (terpilihPertama < 0) {
				new AlertDialog.Builder(this)
				.setMessage(R.string.pilih_setidaknya_satu_kitab)
				.setPositiveButton(R.string.ok, null)
				.show();
				return;
			}
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
					return Search2Engine.cari(Search2Activity.this, getQuery());
				}
			}
			
			@Override protected void onPostExecute(IntArrayList hasil) {
				if (hasil == null) {
					hasil = new IntArrayList(); // empty result
				}
				
				lsHasilCari.setAdapter(new Search2Adapter(hasil, xkata));
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
			lCuplikan.setText(Search2Engine.hilite(ayat, xkata, warnaHilite));
			PengaturTampilan.aturTampilanTeksIsi(lCuplikan);
			
			return res;
		}
		
		IntArrayList getHasilCari() {
			return hasilCari;
		}
	}
}
