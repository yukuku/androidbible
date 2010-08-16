package yuku.alkitab.model;

import java.io.IOException;
import java.util.Arrays;

import yuku.bintex.BintexReader;
import android.util.Log;

public class IndexPerikop {
	private int[] xari;
	private int[] xofset;
	
	public static IndexPerikop baca(BintexReader reader) throws IOException {
		IndexPerikop ip = new IndexPerikop();
		
		int nentri = reader.readInt();
		
		ip.xari = new int[nentri];
		ip.xofset = new int[nentri];
		
		for (int i = 0; i < nentri; i++) {
			ip.xari[i] = reader.readInt();
			ip.xofset[i] = reader.readInt();
		}
		
		return ip;
	}

	public int cariPertama(int ariMin, int ariMax) {
		int x = Arrays.binarySearch(xari, ariMin);
		
		// x == -1 (-0-1) kalo ariMin == 0
		/*
		 * Contoh xari
		 * [0] 0x000101
		 * [1] 0x000125
		 * [2] 0x000201
		 * [3] 0x000308
		 * 
		 * Cari 0x000000 -> insert di 0 -> x == -1 -> pengennya 0, maka -(-1 +1)
		 * Cari 0x000100 -> insert di 0 -> x == -1 
		 * Cari 0x000200 -> insert di 2 -> x == -3 -> pengennya 2, maka -(-3 +1)
		 * Cari 0x000201 -> ketemu di 2 -> x == 2 -> pengennya 2, maka 2
		 * Cari 0x000300 -> insert di 3 -> x == -4
		 * Cari 0x000400 -> insert di 4 -> x == -5 -> pengennya 4, maka -(-5 +1)
		 */
		
		int res;
		
		if (x < 0) res = -(x + 1);
		else res = x;
		
		if (res >= xari.length) return -1;
		
		int ari = xari[res];
		if (ari < ariMax) {
			return res;
		}
		
		return -1;
	}
	
	public int getAri(int index) {
		if (index >= xari.length) {
			return 0x00ffffff; // EOF
		}
		
		return xari[index];
	}
	
	public Blok getBlok(BintexReader in, int index) {
		try {
			int ofset = xofset[index];
			int posKini = in.getPos();

			if (posKini > ofset) {
				throw new RuntimeException("posKini " + posKini + " > ofset " + ofset + ", ngaco!!!!");
			}

			in.skip(ofset - posKini);
			
			return Blok.baca(in);
		} catch (IOException e) {
			Log.e("alki", "getBlok ngaco", e);
			
			return null;
		}
	}
}
