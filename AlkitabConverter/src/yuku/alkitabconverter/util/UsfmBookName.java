package yuku.alkitabconverter.util;

public class UsfmBookName {
	public static final String TAG = UsfmBookName.class.getSimpleName();

	static String[] ntBookNames = "MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL 1TH 2TH 1TI 2TI TIT PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV".split(" ");
	
	public static int toBookId(String name) {
		for (int i = 0; i < ntBookNames.length; i++) {
			if (name.equals(ntBookNames[i])) {
				return 39 + i;
			}
		}
		throw new RuntimeException("unknown usfm book name: " + name);
	}
}
