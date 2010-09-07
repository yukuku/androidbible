package yuku.alkitab.base;

import java.util.Arrays;

import yuku.alkitab.R;
import yuku.alkitab.base.model.*;
import yuku.andoutil.IntArrayList;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;

public class Search2Activity extends Activity {
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
		setContentView(R.layout.search2);
		
		S.siapinEdisi(getApplicationContext());
		S.siapinKitab(getApplicationContext());
		S.bacaPengaturan(this);
		S.siapinPengirimFidbek(this);

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
		progress.setMessage(Html.fromHtml(getString(R.string.sedang_mencari_ayat_yang_mengandung_kata_kata_xkata, Arrays.toString(xkata))));
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
		}).start();
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
			View res = null;
			
			if (convertView == null) {
				res = LayoutInflater.from(Search2Activity.this).inflate(R.layout.search2_item, null);
				res.setId(R.layout.search2_item);
			} else {
				res = convertView;
			}
			
			TextView lAlamat = (TextView) res.findViewById(R.id.lAlamat);
			TextView lCuplikan = (TextView) res.findViewById(R.id.lCuplikan);
			
			int ari = hasilCari.get(position);
			Kitab kitab = S.edisiAktif.volatile_xkitab[Ari.toKitab(ari)];
			int pasal_1 = Ari.toPasal(ari);
			int ayat_1 = Ari.toAyat(ari);
			SpannableStringBuilder sb = new SpannableStringBuilder(kitab.judul).append(" " + pasal_1 + ":" + ayat_1); //$NON-NLS-1$ //$NON-NLS-2$
			IsiActivity.aturTampilanTeksAlamatHasilCari(lAlamat, sb);
			
			String[] xayat = S.muatTeks(Search2Activity.this.getApplicationContext(), S.edisiAktif, kitab, pasal_1);
			String ayat = xayat[ayat_1 - 1];
			ayat = U.buangKodeKusus(ayat);
			lCuplikan.setText(Search2Engine.hilite(ayat, xkata, warnaStabilo));
			
			IsiActivity.aturTampilanTeksIsi(lCuplikan);
			
			return res;
		}
		
		IntArrayList getHasilCari() {
			return hasilCari;
		}
	}
	
}
