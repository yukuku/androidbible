package yuku.alkitab.model;

import java.util.Arrays;

public class PericopeIndex {
	public int[] aris;
	public int[] offsets;
	
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
}
