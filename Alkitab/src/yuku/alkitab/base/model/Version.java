package yuku.alkitab.base.model;

import yuku.alkitab.base.storage.BibleReader;

public class Version {
	public BibleReader bibleReader;
	
	public Version(BibleReader bibleReader) {
		this.bibleReader = bibleReader;
	}
	
	private Book[] volatile_xkitab;
	private Book[] volatile_xkitabConsecutive;
	private PericopeIndex volatile_indexPerikop;
	private boolean volatile_indexPerikopSudahCobaBaca = false;
	
	private synchronized Book[] getXkitab() {
		if (volatile_xkitab == null) {
			volatile_xkitab = this.bibleReader.loadBooks();
		}
		return volatile_xkitab;
	}
	
	/**
	 * @return kitab pos yang tertinggi (walau tengah2nya bisa ada null) + 1
	 */
	public synchronized int getMaxBookIdPlusOne() {
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
	public synchronized Book getBook(int kitabPos) {
		Book[] xkitab = getXkitab();
		if (kitabPos < 0 || kitabPos >= xkitab.length) {
			return null;
		}
		return xkitab[kitabPos];
	}
	
	public synchronized Book getFirstBook() {
		Book[] xkitab = getXkitab();
		for (Book k: xkitab) {
			if (k != null) return k;
		}
		// aneh skali kalo kena ini, tapi toh kena juga
		throw new RuntimeException("Ga ketemu satu pun kitab yang ga null. Info edisi: " + (this.bibleReader == null? "pembaca=null": (this.bibleReader.getLongName() + " nkitab=" + xkitab.length)));    //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
	}
	
	public synchronized PericopeIndex getIndexPerikop() {
		if (!volatile_indexPerikopSudahCobaBaca) {
			volatile_indexPerikop = this.bibleReader.loadPericopeIndex();
			volatile_indexPerikopSudahCobaBaca = true;
		}
		return volatile_indexPerikop;
	}
}
