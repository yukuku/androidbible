package yuku.alkitab.base.model;

import yuku.alkitab.base.storage.*;

public class Edisi {
	public Pembaca pembaca;
	
	public Edisi(Pembaca pembaca) {
		this.pembaca = pembaca;
	}
	
	private Kitab[] volatile_xkitab;
	private Kitab[] volatile_xkitabConsecutive;
	private IndexPerikop volatile_indexPerikop;
	private boolean volatile_indexPerikopSudahCobaBaca = false;
	
	private synchronized Kitab[] getXkitab() {
		if (volatile_xkitab == null) {
			volatile_xkitab = this.pembaca.bacaInfoKitab();
		}
		return volatile_xkitab;
	}
	
	/**
	 * @return panjang dari array kitab (walau tengah2nya bisa ada null)
	 */
	public synchronized int getMaxKitabPos() {
		return getXkitab().length;
	}
	
	/**
	 * @return same as getXkitab, but none of the array elements are null. For enumerating available kitabs.
	 * Note that using this, no guarantee that return_value[pos].pos == pos.
	 */
	public synchronized Kitab[] getConsecutiveXkitab() {
		if (volatile_xkitabConsecutive == null) {
			Kitab[] xkitab1 = getXkitab();
			// count
			int nkitabc = 0;
			for (Kitab k: xkitab1) {
				if (k != null) {
					nkitabc++;
				}
			}
			Kitab[] xkitab2 = new Kitab[nkitabc];
			int c = 0;
			for (Kitab k: xkitab1) {
				if (k != null) {
					xkitab2[c++] = k;
				}
			}
			volatile_xkitabConsecutive = xkitab2;
		}
		return volatile_xkitabConsecutive;
	}
	
	/**
	 * @return null if kitabPos is out of range, or the kitab is not available.
	 */
	public synchronized Kitab getKitab(int kitabPos) {
		Kitab[] xkitab = getXkitab();
		if (kitabPos < 0 || kitabPos >= xkitab.length) {
			return null;
		}
		return xkitab[kitabPos];
	}
	
	public synchronized Kitab getKitabPertama() {
		Kitab[] xkitab = getXkitab();
		for (Kitab k: xkitab) {
			if (k != null) return k;
		}
		// aneh skali kalo kena ini, tapi toh kena juga
		throw new RuntimeException("Ga ketemu satu pun kitab yang ga null. Info edisi: " + (this.pembaca == null? "pembaca=null": (this.pembaca.getJudul() + " nkitab=" + xkitab.length))); 
	}
	
	public synchronized IndexPerikop getIndexPerikop() {
		if (!volatile_indexPerikopSudahCobaBaca) {
			volatile_indexPerikop = this.pembaca.bacaIndexPerikop();
			volatile_indexPerikopSudahCobaBaca = true;
		}
		return volatile_indexPerikop;
	}
}
