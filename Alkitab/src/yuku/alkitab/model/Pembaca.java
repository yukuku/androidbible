package yuku.alkitab.model;

import android.content.Context;

public interface Pembaca {
	Kitab[] bacaInfoKitab(Edisi edisi, Context context);
	String[] muatTeks(Context context, Edisi edisi, Kitab kitab, int pasal_1, boolean janganPisahAyat, boolean hurufKecil);
}
