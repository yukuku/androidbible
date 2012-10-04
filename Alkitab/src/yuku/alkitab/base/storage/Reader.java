package yuku.alkitab.base.storage;

import android.content.Context;

import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.model.PericopeIndex;
import yuku.alkitab.base.model.Book;

public abstract class Reader {
	private Context context;
	
	public Reader(Context context) {
		this.context = context;
	}
	
	public Context getContext() {
		return context;
	}
	
	public abstract String getLongName();
	public abstract Book[] bacaInfoKitab();
	/**
	 * @return boleh return null kalau yang diminta tak tersedia. 
	 */
	public abstract String[] muatTeks(Book book, int pasal_1, boolean janganPisahAyat, boolean hurufKecil);
	public abstract PericopeIndex bacaIndexPerikop();
	public abstract int loadPericope(Version version, int kitab, int pasal, int[] xari, PericopeBlock[] xblok, int max);
}
