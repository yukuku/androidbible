package yuku.alkitab.base.model;

import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

import yuku.bintex.BintexReader;

public class PericopeIndex {
	public static final String TAG = PericopeIndex.class.getSimpleName();
	
	private int[] aris;
	private int[] offsets;
	
	public static PericopeIndex read(BintexReader in) throws IOException {
		PericopeIndex pi = new PericopeIndex();
		
		int nentri = in.readInt();
		
		pi.aris = new int[nentri];
		pi.offsets = new int[nentri];
		
		for (int i = 0; i < nentri; i++) {
			pi.aris[i] = in.readInt();
			pi.offsets[i] = in.readInt();
		}
		
		return pi;
	}

	public int findFirst(int ariMin, int ariMax) {
		int x = Arrays.binarySearch(aris, ariMin);
		
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
		
		if (res >= aris.length) return -1;
		
		int ari = aris[res];
		if (ari < ariMax) {
			return res;
		}
		
		return -1;
	}
	
	public int getAri(int index) {
		if (index >= aris.length) {
			return 0x00ffffff; // EOF
		}
		
		return aris[index];
	}
	
	public PericopeBlock getBlock(BintexReader in, int index) {
		try {
			int ofset = offsets[index];
			int posKini = in.getPos();

			if (posKini > ofset) {
				throw new RuntimeException("posKini " + posKini + " > ofset " + ofset + ", ngaco!!!!"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			in.skip(ofset - posKini);
			
			return PericopeBlock.read(in);
		} catch (IOException e) {
			Log.e(TAG, "getBlok ngaco", e); //$NON-NLS-1$
			
			return null;
		}
	}
}
