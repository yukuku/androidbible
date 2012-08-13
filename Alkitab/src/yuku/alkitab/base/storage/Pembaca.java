package yuku.alkitab.base.storage;

import android.content.Context;

import yuku.alkitab.base.model.Blok;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.model.IndexPerikop;
import yuku.alkitab.base.model.Book;

public abstract class Pembaca {
	private Context context;
	
	public Pembaca(Context context) {
		this.context = context;
	}
	
	public Context getContext() {
		return context;
	}
	
	public abstract String getJudul();
	public abstract Book[] bacaInfoKitab();
	/**
	 * @return boleh return null kalau yang diminta tak tersedia. 
	 */
	public abstract String[] muatTeks(Book book, int pasal_1, boolean janganPisahAyat, boolean hurufKecil);
	public abstract IndexPerikop bacaIndexPerikop();
	public abstract int loadPericope(Version version, int kitab, int pasal, int[] xari, Blok[] xblok, int max);
}
