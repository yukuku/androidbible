package yuku.alkitab.base.storage;

import android.content.Context;

import yuku.alkitab.base.model.Blok;
import yuku.alkitab.base.model.Edisi;
import yuku.alkitab.base.model.IndexPerikop;
import yuku.alkitab.base.model.Kitab;

public abstract class Pembaca {
	private Context context;
	
	public Pembaca(Context context) {
		this.context = context;
	}
	
	public Context getContext() {
		return context;
	}
	
	public abstract String getJudul();
	public abstract Kitab[] bacaInfoKitab();
	/**
	 * @return boleh return null kalau yang diminta tak tersedia. 
	 */
	public abstract String[] muatTeks(Kitab kitab, int pasal_1, boolean janganPisahAyat, boolean hurufKecil);
	public abstract IndexPerikop bacaIndexPerikop();
	public abstract int muatPerikop(Edisi edisi, int kitab, int pasal, int[] xari, Blok[] xblok, int max);
}
