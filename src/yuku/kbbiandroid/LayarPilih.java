package yuku.kbbiandroid;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.*;
import android.text.TextUtils.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;
import android.widget.TextView.*;

public class LayarPilih extends ListActivity {
	private String cari_;
	private Handler handler = new Handler();

	public LayarPilih() {
		S.layarPilih = this;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.pilih);

		S.kamus = new KamusLuringAndroid(getResources());

		cari_ = getIntent().getStringExtra("cari");
		TextView lEmpty = (TextView) findViewById(android.R.id.empty);
		lEmpty.setText(getResources().getString(R.string.kataYangDicariGaKetemu_s, cari_));
		
		setTitle(getResources().getString(R.string.hasilPencarian_s, cari_));

		tampilkanKandidat();
	}

	private void tampilkanKandidat() {
		String[] kandidat = S.kamus.kandidat(cari_, 50);
		String[] xarti = new String[kandidat.length];
		
		DuaBarisAdapter adapter = new DuaBarisAdapter(this, android.R.layout.simple_list_item_2, android.R.id.text1, android.R.id.text2, kandidat, xarti);
				
		setListAdapter(adapter);
		
		// preload arti kata dengan thread rendah
		if (kandidat != null && kandidat.length >= 1) {
			PemberiHint pemberiHint = new PemberiHint(kandidat, xarti, kandidat[0], adapter);
			pemberiHint.setPriority(Thread.MIN_PRIORITY);
			pemberiHint.start();
		}
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		TextView lKata = (TextView) v.findViewById(android.R.id.text1);
		String kata = lKata.getText().toString();

		{
			final Dialog dialog = new Dialog(this);
			dialog.setContentView(R.layout.arti);
			dialog.setTitle(kata);
			dialog.setCanceledOnTouchOutside(true);

			TextView lArti = (TextView) dialog.findViewById(R.id.lArti);
			lArti.setText(Html.fromHtml(S.kamus.arti(kata)), BufferType.SPANNABLE);

			Button bTutup = (Button) dialog.findViewById(R.id.bTutup);
			bTutup.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					dialog.dismiss();
				}
			});

			dialog.show();
		}
	}
	
	private class PemberiHint extends Thread {
		private final String[] kandidat;
		private final String[] xarti;
		private final String kataPertama;
		private final DuaBarisAdapter adapter;

		public PemberiHint(String[] kandidat, String[] xarti, String kataPertama, DuaBarisAdapter adapter) {
			this.kandidat = kandidat;
			this.xarti = xarti;
			this.kataPertama = kataPertama;
			this.adapter = adapter;
		}
		
		@Override
		public void run() {
			// preload arti dulu
			S.kamus.arti(kataPertama);
			
			// udah beres, baru tulisin
			for (int i = 0; i < kandidat.length; i++) {
				String arti = S.kamus.arti(kandidat[i]);
				xarti[i] = arti;
			}
			handler.post(new Runnable() {
				@Override
				public void run() {
					adapter.notifyDataSetChanged();
				}
			});
		}
	}
	
	private class DuaBarisAdapter extends BaseAdapter {
		private final Context context;
		private final int itemResource;
		private final int text1Resource;
		private final int text2Resource;
		private final String[] data1;
		private final String[] data2;

		public DuaBarisAdapter(Context context, int itemResource, int text1Resource, int text2Resource, String[] data1, String[] data2) {
			this.context = context;
			this.itemResource = itemResource;
			this.text1Resource = text1Resource;
			this.text2Resource = text2Resource;
			this.data1 = data1;
			this.data2 = data2;
		}
		
		@Override
		public int getCount() {
			return data1.length;
		}

		@Override
		public Object getItem(int position) {
			return position;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view;
			
			if (convertView != null) {
				view = convertView;
			} else {
				view = LayoutInflater.from(context).inflate(itemResource, null);
			}
			
			// set data1
			if (data1[position] != null) {
				TextView text1 = (TextView) view.findViewById(text1Resource);
				text1.setText(data1[position]);
			}
			
			// set data2
			final String arti = data2[position];
			if (arti != null) {
				TextView text2 = (TextView) view.findViewById(text2Resource);
				text2.setSingleLine();
				text2.setEllipsize(TruncateAt.END);
				
				int indexArtiKata = getIndexArtiKata(arti);
				int end = indexArtiKata + 150; 
				if (end > arti.length()) {
					end = arti.length();
				}
				
				text2.setText(Html.fromHtml(arti.substring(indexArtiKata, end)));
			}
			
			return view;
		}
		
		private int getIndexArtiKata(String arti) {
			// sampe non<b> pertama saja
			int pos = arti.indexOf("</b>");
			
			if (pos == -1) {
				int pos2 = arti.indexOf("→");
				
				if (pos2 != -1) {
					return pos2; // sertakan "→"nya
				}
			} else {
				// cek apakah diikuti ", "
				if (arti.length() > pos + 5) {
					if (arti.charAt(pos + 4) == ',') {
						if (arti.charAt(pos + 5) == ' ') {
							return pos + 6;
						}
						return pos + 5;
					}
				}
				
				return pos + 4; // jangan sertakan "</b>"nya
			}
			
			return 0;
		}
		
	}
}
