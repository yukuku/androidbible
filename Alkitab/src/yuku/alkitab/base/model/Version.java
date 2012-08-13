package yuku.alkitab.base.model;

import yuku.alkitab.base.storage.Pembaca;

public class Version {
	public Pembaca pembaca;
	
	public Version(Pembaca pembaca) {
		this.pembaca = pembaca;
	}
	
	private Book[] volatile_xkitab;
	private Book[] volatile_xkitabConsecutive;
	private IndexPerikop volatile_indexPerikop;
	private boolean volatile_indexPerikopSudahCobaBaca = false;
	
	private synchronized Book[] getXkitab() {
		if (volatile_xkitab == null) {
			volatile_xkitab = this.pembaca.bacaInfoKitab();
		}
		return volatile_xkitab;
	}
	
	/**
	 * @return kitab pos yang tertinggi (walau tengah2nya bisa ada null) + 1
	 */
	public synchronized int getMaxKitabPosTambahSatu() {
		return getXkitab().length;
	}
	
	/**
	 * @return same as getXkitab, but none of the array elements are null. For enumerating available kitabs.
	 * Note that using this, no guarantee that return_value[pos].pos == pos.
	 */
	public synchronized Book[] getConsecutiveBooks() {
		if (volatile_xkitabConsecutive == null) {
			Book[] xkitab1 = getXkitab();
			// count
			int nkitabc = 0;
			for (Book k: xkitab1) {
				if (k != null) {
					nkitabc++;
				}
			}
			Book[] xkitab2 = new Book[nkitabc];
			int c = 0;
			for (Book k: xkitab1) {
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
	public synchronized Book getKitab(int kitabPos) {
		Book[] xkitab = getXkitab();
		if (kitabPos < 0 || kitabPos >= xkitab.length) {
			return null;
		}
		return xkitab[kitabPos];
	}
	
	public synchronized Book getKitabPertama() {
		Book[] xkitab = getXkitab();
		for (Book k: xkitab) {
			if (k != null) return k;
		}
		// aneh skali kalo kena ini, tapi toh kena juga
		throw new RuntimeException("Ga ketemu satu pun kitab yang ga null. Info edisi: " + (this.pembaca == null? "pembaca=null": (this.pembaca.getJudul() + " nkitab=" + xkitab.length)));    //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
	}
	
	public synchronized IndexPerikop getIndexPerikop() {
		if (!volatile_indexPerikopSudahCobaBaca) {
			volatile_indexPerikop = this.pembaca.bacaIndexPerikop();
			volatile_indexPerikopSudahCobaBaca = true;
		}
		return volatile_indexPerikop;
	}
}
