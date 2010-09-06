package yuku.alkitab.base.model;

import android.content.Context;

public interface Pembaca {
	Kitab[] bacaInfoKitab(Context context, Edisi edisi);
	String[] muatTeks(Context context, Edisi edisi, Kitab kitab, int pasal_1, boolean janganPisahAyat, boolean hurufKecil);
	IndexPerikop bacaIndexPerikop(Context context, Edisi edisi);
	int muatPerikop(Context context, Edisi edisi, int kitab, int pasal, int[] xari, Blok[] xblok, int max);
}
