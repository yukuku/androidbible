package yuku.alkitab;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;

public class IsiActivity extends Activity {
	String[] xayat;
	int[] ayat_offset;
	TextView tIsi;
	ScrollView scrollIsi;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.isi);
		
		S.siapinEdisi(getResources());
		S.siapinKitab(getResources());
		
		tIsi = (TextView) findViewById(R.id.tIsi);
		scrollIsi = (ScrollView) findViewById(R.id.scrollIsi);
	}

	private int getAyatTop(int ayat) {
		Layout layout = tIsi.getLayout();
		int line = layout.getLineForOffset(ayat_offset[ayat-1]);
		return layout.getLineTop(line);
	}

	private String siapinTampilanAyat() {
		StringBuilder res = new StringBuilder(5000);
		
		int c = 0;
		String pengawal = "";
		String pengakhir = "\n";
		
		ayat_offset = new int[xayat.length];
		for (int i = 0; i < xayat.length; i++) {
			ayat_offset[i] = c;
			
			pengawal = (i+1) + " ";
			res.append(pengawal).append(xayat[i]).append(pengakhir);
			
			c += pengawal.length() + xayat[i].length() + pengakhir.length();
		}
		
		return res.toString();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		new MenuInflater(this).inflate(R.menu.isi, menu);
		
		return true;
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		if (item.getItemId() == R.id.menuTuju) {
			Intent intent = new Intent(this, MenujuActivity.class);
			startActivityForResult(intent, R.id.menuTuju);
		}
		
		return super.onMenuItemSelected(featureId, item);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == R.id.menuTuju) {
			if (resultCode == RESULT_OK) {
				int pasal = data.getIntExtra("pasal", 0);
				int ayat = data.getIntExtra("ayat", 0);
				
				tampil(pasal, ayat);
			}
		}
	}

	private void tampil(int pasal, int ayat) {
		if (pasal < 1) pasal = 1;
		if (pasal > S.kitab.npasal) pasal = S.kitab.npasal;
		
		if (ayat < 1) ayat = 1;
		if (ayat > S.kitab.nayat[pasal-1]) ayat = S.kitab.nayat[pasal-1];
		
		// muat data
		xayat = S.muatTeks(getResources(), pasal);

		String semua = siapinTampilanAyat();
		
		tIsi.setText(semua);
		tIsi.requestLayout();
		
		scrollIsi.scrollTo(0, getAyatTop(ayat));
	}
}
