package yuku.alkitab.model;

public abstract class SingleChapterVerses {
	public static final String TAG = SingleChapterVerses.class.getSimpleName();
	
	private static final String[] VERSE_NUMBER_STRINGS = {
		"0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
		"10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
		"20", "21", "22", "23", "24", "25", "26", "27", "28", "29",
		"30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
		"40", "41", "42", "43", "44", "45", "46", "47", "48", "49",
		"50", "51", "52", "53", "54", "55", "56", "57", "58", "59",
		"60", "61", "62", "63", "64", "65", "66", "67", "68", "69",
		"70", "71", "72", "73", "74", "75", "76", "77", "78", "79",
	}; // up to [79]
	
	public abstract String getVerse(int verse_0);

	public abstract int getVerseCount();
	
	public String getVerseNumberText(int verse_0) {
		int verse_1 = verse_0 + 1;
		if (verse_1 < 80) {
			return VERSE_NUMBER_STRINGS[verse_1];
		} else {
			return Integer.toString(verse_1);
		}
	}
}
