package yuku.alkitab;

import yuku.alkitab.model.*;
import yuku.andoutil.*;
import android.app.*;
import android.content.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;

public class Search2Activity extends Activity {
	ListView lsHasilCari;
	ImageButton bCari;
	EditText tCarian;
	TextView lTiadaHasil;
	ProgressDialog progress;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search2);
		
		lsHasilCari = (ListView) findViewById(R.id.lsHasilCari);
		bCari = (ImageButton) findViewById(R.id.bCari);
		tCarian = (EditText) findViewById(R.id.tCarian);
		lTiadaHasil = (TextView) findViewById(R.id.lTiadaHasil);
		
		bCari.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) { bCari_click(); }
		});
	}
	
	protected void bCari_click() {
		final String carian = tCarian.getText().toString();
		
		if (carian.trim().length() > 0) {
			progress = ProgressDialog.show(this, "Mencari", Html.fromHtml("Sedang mencari ayat yang mengandung kata-kata: <b>" + carian), true, false, new DialogInterface.OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					progress.show();
				}
			});
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					synchronized (Search2Activity.this) {
						final String[] xkata = Search2Engine.tokenkan(carian);
						final IntArrayList hasil = Search2Engine.cari(Search2Activity.this.getResources(), xkata);
						Search2Activity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								lsHasilCari.setAdapter(new Search2Adapter(hasil, xkata));
							}
						});
						
						progress.dismiss();
					}
				}
			}).start();
		}
	}
	
	class Search2Adapter extends BaseAdapter {
		IntArrayList hasil;
		String[] xkata;
		
		public Search2Adapter(IntArrayList hasil, String[] xkata) {
			this.hasil = hasil;
			this.xkata = xkata;
		}
		
		@Override
		public int getCount() {
			return hasil.size();
		}

		@Override
		public Integer getItem(int position) {
			return hasil.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
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
			
			int ari = hasil.get(position);
			Kitab kitab = S.xkitab[Ari.toKitab(ari)];
			int pasal_1 = Ari.toPasal(ari);
			int ayat_1 = Ari.toAyat(ari);
			SpannableStringBuilder sb = new SpannableStringBuilder(kitab.judul).append(" " + pasal_1 + ":" + ayat_1);
			sb.setSpan(new UnderlineSpan(), 0, sb.length(), 0);
			lAlamat.setText(sb);
			
			String[] xayat = S.muatTeks(getResources(), kitab, pasal_1);
			String ayat = xayat[ayat_1 - 1];
			ayat = S.buangKodeKusus(ayat);
			lCuplikan.setText(Search2Engine.hilite(ayat, xkata));
			
			IsiActivity.aturTampilanTeksIsi(lCuplikan);
			
			return res;
		}
		
	}
	
}
