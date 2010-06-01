package yuku.alkitab.test;

import yuku.alkitab.S;
import yuku.alkitab.model.*;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.util.Log;

public class SelipPerikopTest extends AndroidTestCase {
	public void test1() {
		Resources res = getContext().getResources();
		S.siapinEdisi(res);
		S.siapinKitab(res);
		
		for (Kitab k: S.xkitab) {
			S.kitab = k;
			int[] perikop_xari;
			Blok[] perikop_xblok;
			int nblok;
			
			for (int pasal = 1; pasal <= S.kitab.npasal; pasal++) {
				String[] xayat = S.muatTeks(res, S.kitab, pasal);
				
				//# max dibikin pol 30 aja (1 pasal max 30 blok, cukup mustahil)
				int max = 30;
				perikop_xari = new int[max];
				perikop_xblok = new Blok[max];
				nblok = S.muatPerikop(res, S.kitab.pos, pasal, perikop_xari, perikop_xblok, max); 
				
				String judul = S.kitab.judul + " " + pasal;
				Log.i("AlkitabTest", judul);
				
				//# tadinya onPostExecute
				int[] penunjukKotak = S.bikinPenunjukKotak(xayat.length, perikop_xari, perikop_xblok, nblok);
				Log.i("AlkitabTest", " ayat=" + xayat.length + " blok=" + nblok + " penunjukKotak=" + penunjukKotak.length);
			}
		}
	}
}
