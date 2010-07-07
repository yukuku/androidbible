package yuku.alkitab;

import java.util.*;

import yuku.alkitab.model.*;
import yuku.andoutil.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;

public class Search2Activity extends Activity {
	public static final String EXTRA_carian = "carian";
	public static final String EXTRA_hasilCari = "hasilCari";
	public static final String EXTRA_posisiTerpilih = "posisiTerpilih";
	public static final String EXTRA_ariTerpilih = "ariTerpilih";
	
	ListView lsHasilCari;
	ImageButton bCari;
	EditText tCarian;
	TextView lTiadaHasil;
	
	private int warnaStabilo;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search2);
		
		lsHasilCari = (ListView) findViewById(R.id.lsHasilCari);
		bCari = (ImageButton) findViewById(R.id.bCari);
		tCarian = (EditText) findViewById(R.id.tCarian);
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
					//# close soft keyboard 
					InputMethodManager inputManager = (InputMethodManager) Search2Activity.this.getSystemService(Context.INPUT_METHOD_SERVICE); 
					inputManager.hideSoftInputFromWindow(tCarian.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
					
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
				IntArrayList hasilCari = intent.getParcelableExtra(EXTRA_hasilCari);
				int posisiTerpilih = intent.getIntExtra(EXTRA_posisiTerpilih, -1);
				
				if (carian != null) {
					tCarian.setText(carian);
					
					if (hasilCari != null) {
						String[] xkata = Search2Engine.tokenkan(carian);
						lsHasilCari.setAdapter(new Search2Adapter(hasilCari, xkata));
					}
				}
				
				if (posisiTerpilih != -1) {
					lsHasilCari.setSelection(posisiTerpilih);
				}
				
				//Log.d("alki", "masuk search2 dengan carian=" + carian + ", posisiTerpilih=" + posisiTerpilih + " hasilCari=" + hasilCari);
			}
		}
	}
	
	protected void bCari_click() {
		final String carian = tCarian.getText().toString();
		
		if (carian.trim().length() > 0) {
			final String[] xkata = Search2Engine.tokenkan(carian);
			
			final ProgressDialog progress = new ProgressDialog(this);
			progress.setTitle("Mencari");
			progress.setMessage(Html.fromHtml("Sedang mencari ayat yang mengandung kata-kata: <b>" + Arrays.toString(xkata)));
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
						final IntArrayList hasil = Search2Engine.cari(Search2Activity.this.getResources(), xkata);
						Search2Activity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								lsHasilCari.setAdapter(new Search2Adapter(hasil, xkata));
								Toast.makeText(Search2Activity.this, hasil.size() + " hasil", Toast.LENGTH_SHORT).show();
							}
						});
						
						progress.setOnDismissListener(null);
						progress.dismiss();
					}
				}
			}).start();
		}
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
			Kitab kitab = S.xkitab[Ari.toKitab(ari)];
			int pasal_1 = Ari.toPasal(ari);
			int ayat_1 = Ari.toAyat(ari);
			SpannableStringBuilder sb = new SpannableStringBuilder(kitab.judul).append(" " + pasal_1 + ":" + ayat_1);
			IsiActivity.aturTampilanTeksAlamatHasilCari(lAlamat, sb);
			
			String[] xayat = S.muatTeks(getResources(), kitab, pasal_1);
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
