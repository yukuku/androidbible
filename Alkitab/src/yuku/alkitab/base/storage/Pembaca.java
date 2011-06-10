package yuku.alkitab.base.storage;

import android.content.*;

import yuku.alkitab.base.model.*;

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
