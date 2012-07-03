package yuku.alkitab.base.ac;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

import yuku.alkitab.R;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Kitab;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.PengaturTampilan;
import yuku.alkitab.base.util.QueryTokenizer;
import yuku.alkitab.base.util.Search2Engine;
import yuku.alkitab.base.util.Search2Engine.Query;
import yuku.androidsdk.searchbar.SearchBar;
import yuku.androidsdk.searchbar.SearchBar.OnSearchListener;

public class Search2Activity extends BaseActivity {
	public static final String TAG = Search2Activity.class.getSimpleName();
	
	private static final String EXTRA_query = "query"; //$NON-NLS-1$
	private static final String EXTRA_hasilCari = "hasilCari"; //$NON-NLS-1$
	private static final String EXTRA_posisiTerpilih = "posisiTerpilih"; //$NON-NLS-1$
	private static final String EXTRA_kitabPosTerbuka = "kitabPosTerbuka"; //$NON-NLS-1$
	private static final String EXTRA_ariTerpilih = "ariTerpilih"; //$NON-NLS-1$
	
	ListView lsHasilCari;
	SearchBar searchBar;
	View panelFilter;
	CheckBox cFilterLama;
	CheckBox cFilterBaru;
	CheckBox cFilterKitabSaja;
	TextView tFilterRumit;
	View bEditFilter;
	
	int warnaHilite;
	SparseBooleanArray xkitabPosTerpilih = new SparseBooleanArray();
	int kitabPosTerbuka;
	int filterUserAction = 0; // when it's not user action, set to nonzero 
	
	public static class Result {
		public Query query;
		public IntArrayList hasilCari;
		public int posisiTerpilih;
		public int ariTerpilih;
	}
	
	public static Intent createIntent(Query query, IntArrayList hasilCari, int posisiTerpilih, int kitabPosTerbuka) {
		Intent res = new Intent(App.context, Search2Activity.class);
		res.putExtra(EXTRA_query, query);
		res.putExtra(EXTRA_hasilCari, hasilCari);
		res.putExtra(EXTRA_posisiTerpilih, posisiTerpilih);
		res.putExtra(EXTRA_kitabPosTerbuka, kitabPosTerbuka);
		return res;
	}
	
	public static Result obtainResult(Intent data) {
		if (data == null) return null;
		Result res = new Result();
		res.query = data.getParcelableExtra(EXTRA_query);
		res.hasilCari = data.getParcelableExtra(EXTRA_hasilCari);
		res.posisiTerpilih = data.getIntExtra(EXTRA_posisiTerpilih, -1);
		res.ariTerpilih = data.getIntExtra(EXTRA_ariTerpilih, -1);		
		return res;
	}

	@TargetApi(11) class Api11_compat {
		SearchView searchView;

		public void configureSearchView() {
			searchView = U.getView(Search2Activity.this, R.id.searchView);
			searchView.setSubmitButtonEnabled(true);
			searchView.setOnQueryTextListener(new OnQueryTextListener() {
				@Override public boolean onQueryTextSubmit(String query) {
					search(query);
					return true;
				}
				
				@Override public boolean onQueryTextChange(String newText) {
					return false;
				}
			});
		}

		public void setSearchViewQuery(String carian) {
			searchView.setQuery(carian, false);
		}

		public String getSearchViewQuery() {
			return searchView.getQuery().toString();
		}

		public void hideSoftInputFromSearchView(InputMethodManager inputManager) {
			inputManager.hideSoftInputFromWindow(searchView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
			searchView.clearFocus();
		}
	}
	
	Api11_compat api11_compat;
	
	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		U.nyalakanTitleBarHanyaKalauTablet(this);
		
		S.siapinKitab();
		S.hitungPenerapanBerdasarkanPengaturan();
		
		setContentView(R.layout.activity_search2);

		lsHasilCari = U.getView(this, R.id.lsHasilCari);
		panelFilter = U.getView(this, R.id.panelFilter);
		cFilterLama = U.getView(this, R.id.cFilterLama);
		cFilterBaru = U.getView(this, R.id.cFilterBaru);
		cFilterKitabSaja = U.getView(this, R.id.cFilterKitabSaja);
		tFilterRumit = U.getView(this, R.id.tFilterRumit);
		bEditFilter = U.getView(this, R.id.bEditFilter);
		
		if (useSearchView()) {
			api11_compat = new Api11_compat();
			api11_compat.configureSearchView();
		} else {
			searchBar = U.getView(this, R.id.searchBar);
			((ViewGroup) panelFilter.getParent()).removeView(panelFilter);
			searchBar.setBottomView(panelFilter);
			searchBar.setOnSearchListener(new OnSearchListener() {
				@Override public void onSearch(SearchBar searchBar, Editable text) {
					search(text.toString());
				}
			});
			// the background of the search bar is bright, so let's make all text black
			cFilterLama.setTextColor(0xff000000);
			cFilterBaru.setTextColor(0xff000000);
			cFilterKitabSaja.setTextColor(0xff000000);
			tFilterRumit.setTextColor(0xff000000);
		}
		
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
		bEditFilter.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				bEditFilter_click();
			}
		});
		cFilterLama.setOnCheckedChangeListener(cFilterLama_checkedChange);
		cFilterBaru.setOnCheckedChangeListener(cFilterBaru_checkedChange);
		cFilterKitabSaja.setOnCheckedChangeListener(cFilterKitabSaja_checkedChange);
		
		{
			Intent intent = getIntent();
			Query query = intent.getParcelableExtra(EXTRA_query);
			IntArrayList hasilCari = intent.getParcelableExtra(EXTRA_hasilCari);
			int posisiTerpilih = intent.getIntExtra(EXTRA_posisiTerpilih, -1);
			
			kitabPosTerbuka = intent.getIntExtra(EXTRA_kitabPosTerbuka, -1);
			Kitab kitab = S.edisiAktif.getKitab(kitabPosTerbuka);
			cFilterKitabSaja.setText(getString(R.string.search_bookname_only, kitab.judul));
			
			if (query != null) {
				if (!useSearchView()) {
					searchBar.setText(query.carian);
				} else {
					api11_compat.setSearchViewQuery(query.carian);
				}
				
				if (hasilCari != null) {
					String[] xkata = QueryTokenizer.tokenize(query.carian);
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
	
	boolean useSearchView() {
		return VERSION.SDK_INT >= 11;
	}
	
	void aturTampilanFilterLamaBaru() {
		// kondisi: kalau sebagian nyala sebagian ga, null.
		// semua nyala: true.
		// semua ga nyala: false.
		Boolean lama = null;
		Boolean baru = null;
		int satuSatunyaNyala = -1;
		
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
		
		{
			int c = 0;
			int k = 0;
			for (int i = 0, len = xkitabPosTerpilih.size(); i < len; i++) {
				if (xkitabPosTerpilih.valueAt(i)) {
					k = xkitabPosTerpilih.keyAt(i);
					c++;
					if (c > 1) break;
				}
			}
			if (c == 1) {
				satuSatunyaNyala = k;
			}
		}
		
		filterUserAction++; {
			if (lama != null && baru != null) {	// 22nya true atau false
				cFilterLama.setVisibility(View.VISIBLE);
				cFilterLama.setChecked(lama);
				cFilterBaru.setVisibility(View.VISIBLE);
				cFilterBaru.setChecked(baru);
				cFilterKitabSaja.setVisibility(View.VISIBLE);
				cFilterKitabSaja.setChecked(false);
				tFilterRumit.setVisibility(View.GONE);
			} else {
				if (satuSatunyaNyala != -1 && satuSatunyaNyala == kitabPosTerbuka) {
					cFilterLama.setVisibility(View.VISIBLE);
					cFilterLama.setChecked(false);
					cFilterBaru.setVisibility(View.VISIBLE);
					cFilterBaru.setChecked(false);
					cFilterKitabSaja.setVisibility(View.VISIBLE);
					cFilterKitabSaja.setChecked(true);
					tFilterRumit.setVisibility(View.GONE);
				} else {
					// tidak demikian, kita tulis label saja.
					cFilterLama.setVisibility(View.VISIBLE);
					cFilterLama.setChecked(false);
					cFilterBaru.setVisibility(View.VISIBLE);
					cFilterBaru.setChecked(false);
					cFilterKitabSaja.setVisibility(View.GONE);
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
			} filterUserAction--;
		}
	}

	private OnCheckedChangeListener cFilterLama_checkedChange = new OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (filterUserAction != 0) return;
			
			filterUserAction++; {
				if (isChecked) {
					cFilterKitabSaja.setVisibility(View.VISIBLE);
					cFilterKitabSaja.setChecked(false);
					tFilterRumit.setVisibility(View.GONE);
				}
				
				setXkitabPosTerpilihBerdasarkanFilter();
			} filterUserAction--;
		}
	};
	
	private OnCheckedChangeListener cFilterBaru_checkedChange = new OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (filterUserAction != 0) return;
			
			filterUserAction++; {
				if (isChecked) {
					cFilterKitabSaja.setVisibility(View.VISIBLE);
					cFilterKitabSaja.setChecked(false);
					tFilterRumit.setVisibility(View.GONE);
				}
				
				setXkitabPosTerpilihBerdasarkanFilter();
			} filterUserAction--;
		}
	};
	
	private OnCheckedChangeListener cFilterKitabSaja_checkedChange = new OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (filterUserAction != 0) return;
			
			filterUserAction++; {
				if (isChecked) {
					cFilterLama.setChecked(false);
					cFilterBaru.setChecked(false);
				}
				
				setXkitabPosTerpilihBerdasarkanFilter();
			} filterUserAction--;
		}
	};
	
	protected void setXkitabPosTerpilihBerdasarkanFilter() {
		xkitabPosTerpilih.clear();
		if (cFilterLama.isChecked()) for (int i = 0; i < 39; i++) xkitabPosTerpilih.put(i, true);
		if (cFilterBaru.isChecked()) for (int i = 39; i < 66; i++) xkitabPosTerpilih.put(i, true);
		if (cFilterKitabSaja.isChecked()) xkitabPosTerpilih.put(kitabPosTerbuka, true);
	}
	
	protected Query getQuery() {
		Query res = new Query();
		if (!useSearchView()) {
			res.carian = searchBar.getText().toString();
		} else {
			res.carian = api11_compat.getSearchViewQuery();
		}
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
		
		final String[] xkata = QueryTokenizer.tokenize(carian);
		
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
					if (!useSearchView()) {
						inputManager.hideSoftInputFromWindow(searchBar.getSearchField().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
					} else {
						api11_compat.hideSoftInputFromSearchView(inputManager);
						lsHasilCari.requestFocus();
					}
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
