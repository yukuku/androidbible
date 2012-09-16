package yuku.alkitab.base.model;

import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

import yuku.bintex.BintexReader;

public class PericopeIndex {
	public static final String TAG = PericopeIndex.class.getSimpleName();
	
	private int[] xari;
	private int[] xofset;
	
	public static PericopeIndex baca(BintexReader reader) throws IOException {
		PericopeIndex ip = new PericopeIndex();
		
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
	
	public PericopeBlock getBlok(BintexReader in, int index) {
		try {
			int ofset = xofset[index];
			int posKini = in.getPos();

			if (posKini > ofset) {
				throw new RuntimeException("posKini " + posKini + " > ofset " + ofset + ", ngaco!!!!"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			in.skip(ofset - posKini);
			
			return PericopeBlock.baca(in);
		} catch (IOException e) {
			Log.e(TAG, "getBlok ngaco", e); //$NON-NLS-1$
			
			return null;
		}
	}
}
