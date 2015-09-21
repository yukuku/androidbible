package yuku.alkitabconverter.util;

import java.util.ArrayList;
import java.util.List;


public class RecUtil {
	public static final String TAG = RecUtil.class.getSimpleName();
	
	/** hitung ada berapa macam kitab dalam list rec. */
	public static int hitungKitab(List<Rec> xrec) {
		List<Integer> xkitab_1 = new ArrayList<>();
		for (Rec rec: xrec) {
			if (xkitab_1.size() > 0 && xkitab_1.get(xkitab_1.size() - 1) == rec.book_1) {
				continue;
			}
			if (!xkitab_1.contains(rec.book_1)) {
				xkitab_1.add(rec.book_1);
			}
		}
		return xkitab_1.size();
	}
}
