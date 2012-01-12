package yuku.alkitabconverter.util;

import java.util.ArrayList;
import java.util.List;

import yuku.alkitabconverter.bdb.BdbProses.Rec;

public class RecUtil {
	public static final String TAG = RecUtil.class.getSimpleName();
	
	/** hitung ada berapa macam kitab dalam list rec. */
	public static int hitungKitab(List<Rec> xrec) {
		List<Integer> xkitab_1 = new ArrayList<Integer>();
		for (Rec rec: xrec) {
			if (xkitab_1.size() > 0 && xkitab_1.get(xkitab_1.size() - 1).intValue() == rec.kitab_1) {
				continue;
			}
			if (!xkitab_1.contains(rec.kitab_1)) {
				xkitab_1.add(rec.kitab_1);
			}
		}
		return xkitab_1.size();
	}
}
