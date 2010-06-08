package yuku.alkitab;

import java.util.*;

import yuku.alkitab.model.*;
import yuku.andoutil.IntArrayList;
import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

public class Search2Activity extends Activity {
	ListView lsHasilCari;
	ImageButton bCari;
	EditText tCarian;
	TextView lTiadaHasil;

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
		String carian = tCarian.getText().toString();
		
		if (carian.trim().length() > 0) {
			cari(this.getResources(), carian);
		}
	}

	private static void cari(Resources r, String carian) {
		// pisah jadi kata-kata
		String[] xkata = carian.trim().toLowerCase().replaceAll("[\\s-]+", " ").split(" ");
		
		// urutkan berdasarkan panjang, lalu abjad
		Arrays.sort(xkata, new Comparator<String>() {
			@Override
			public int compare(String object1, String object2) {
				int len1 = object1.length();
				int len2 = object2.length();
				
				if (len1 > len2) return -1;
				if (len1 == len2) {
					return object1.compareTo(object2);
				}
				return 1;
			}
		});
		
		// buang ganda
		{
			ArrayList<String> akata = new ArrayList<String>();
			String terakhir = null;
			for (String kata: xkata) {
				if (!kata.equals(terakhir)) {
					akata.add(kata);
				}
				terakhir = kata;
			}
			xkata = akata.toArray(new String[akata.size()]);
			Log.d("alki", "xkata = " + Arrays.toString(xkata));
		}
		
		// cari betulan
		{
			int index = 0;
			
			while (true) {
				if (index >= xkata.length) {
					break;
				}
				
				String kata = xkata[index];
				
				//Debug.startMethodTracing();
				long ms = System.currentTimeMillis();
				cariDalam(r, kata, null, 10000);
				Log.w("alki", System.currentTimeMillis() - ms + " ms");
				//Debug.stopMethodTracing();
				
				index++;
			}
		}
	}

	private static IntArrayList cariDalam(Resources r, String kata, IntArrayList sumber, int max) {
		if (sumber == null) {
			IntArrayList res = new IntArrayList();
			
			for (Kitab k: S.xkitab) {
				int npasal = k.npasal;
				
				for (int i = 1; i <= npasal; i++) {
					String[] xayat = S.muatTeks(r, k, i);
					Log.d("alki", "cariDalam kitab " + k.nama + " pasal[1base] " + i + " mulai");
					
					int nayat = xayat.length;
					// ayat[0base]
					for (int a = 0; a < nayat; a++) {
						String ayat = xayat[a];
						
						if (ayat.indexOf(kata) >= 0) {
							res.add(Ari.encode(k.pos, i, a+1));
						}
					}
				}
				
				Log.d("alki", "cariDalam kitab " + k.nama + " selesai. res.size = " + res.size());
			}
			return res;
		}
		//FIXME
		return null;
	}
	
}
