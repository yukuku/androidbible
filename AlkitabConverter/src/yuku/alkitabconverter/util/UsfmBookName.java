package yuku.alkitabconverter.util;

public class UsfmBookName {
	public static final String TAG = UsfmBookName.class.getSimpleName();

	static String[] bookNames = ("GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SNG ISA JER LAM EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL 1TH 2TH 1TI 2TI TIT PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV" +
		" 1ES" + // book_0 66
		" 2ES" + // book_0 67
		" TOB" + // book_0 68
		" JDT" + // book_0 69
		" 1MA" + // book_0 70
		" 2MA" + // book_0 71
		" 3MA" + // book_0 72
		" 4MA" + // book_0 73
		" X74" + // book_0 74 -- not implemented
		" X75" + // book_0 75 -- not implemented
		" WIS" + // book_0 76
		" SIR" + // book_0 77
		" X78" + // book_0 78 -- not implemented
		" LJE" + // book_0 79
		" BAR" + // book_0 80
		" SUS" + // book_0 81
		" S3Y" + // book_0 82
		" BEL" + // book_0 83
		" MAN" + // book_0 84
		" ESG" + // book_0 85
		" PS2" + // book_0 86
		" X87" // book_0 87 -- not implemented
	).split(" ");
	
	public static int toBookId(String name) {
		for (int i = 0; i < bookNames.length; i++) {
			if (name.equals(bookNames[i])) {
				return i;
			}
		}
		throw new RuntimeException("unknown usfm book name: " + name);
	}
}
