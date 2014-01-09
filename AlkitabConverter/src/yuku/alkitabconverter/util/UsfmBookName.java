package yuku.alkitabconverter.util;

public class UsfmBookName {
	public static final String TAG = UsfmBookName.class.getSimpleName();

	static String[] bookNames = "GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SNG ISA JER LAM EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL 1TH 2TH 1TI 2TI TIT PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV".split(" ");
	
	public static int toBookId(String name) {
		for (int i = 0; i < bookNames.length; i++) {
			if (name.equals(bookNames[i])) {
				return i;
			}
		}
		throw new RuntimeException("unknown usfm book name: " + name);
	}
}
