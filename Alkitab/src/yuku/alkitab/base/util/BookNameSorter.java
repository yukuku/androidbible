package yuku.alkitab.base.util;

import yuku.alkitab.model.Book;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

public class BookNameSorter {
	public static final String TAG = BookNameSorter.class.getSimpleName();

	static final String[] numberedBookStartsWiths = {null, "I ", "II ", "III ", "IV ", "V "}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	static final String[] numberedBookStartsWithNumbers = {null, "1", "2", "3", "4", "5"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	static final String[] numberedBookReplaceWiths = {null, "1", "2", "3", "4", "5"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	static final int[] numberedBookMap;
	static final HashMap<String, String> hardcodedAbbrs = new HashMap<>();
	
	static {
		// for these book numbers, replace "I", "II", "III", "IV", "V" with numbers 
		// to save space, to make them still understandable when truncated
		int[] numberedBooks_1 = {0 /*moses*/, 8, 10, 12, 45, 51, 53, 59, 61, 66, 70, };
		int[] numberedBooks_2 = {1 /*moses*/, 9, 11, 13, 46, 52, 54, 60, 62, 67, 71, }; 
		int[] numberedBooks_3 = {2 /*moses*/, 63 /*john*/, 72 /*makabe*/, };
		int[] numberedBooks_4 = {3 /*moses*/, 73 /*makabe*/, };
		int[] numberedBooks_5 = {4 /*moses*/, };
		
		numberedBookMap = new int[74]; // as large as the max number above + 1
		for (int bookId: numberedBooks_1) numberedBookMap[bookId] = 1;
		for (int bookId: numberedBooks_2) numberedBookMap[bookId] = 2;
		for (int bookId: numberedBooks_3) numberedBookMap[bookId] = 3;
		for (int bookId: numberedBooks_4) numberedBookMap[bookId] = 4;
		for (int bookId: numberedBooks_5) numberedBookMap[bookId] = 5;
		
		hardcodedAbbrs.put("Filemon", "Flm");
		hardcodedAbbrs.put("Amos", "Amos");
		hardcodedAbbrs.put("Ayub", "Ayub");
		hardcodedAbbrs.put("Yoel", "Yoel");
		hardcodedAbbrs.put("Pengkhotbah", "Pkh");
		hardcodedAbbrs.put("Wahyu", "Why");
		hardcodedAbbrs.put("1 Timotius", "1Tim");
		hardcodedAbbrs.put("2 Timotius", "2Tim");
		hardcodedAbbrs.put("1 Tesalonika", "1Tes");
		hardcodedAbbrs.put("2 Tesalonika", "2Tes");
		hardcodedAbbrs.put("1 Korintus", "1Kor");
		hardcodedAbbrs.put("2 Korintus", "2Kor");
		hardcodedAbbrs.put("1 Raja-raja", "1Raj");
		hardcodedAbbrs.put("2 Raja-raja", "2Raj");
		hardcodedAbbrs.put("1 Petrus", "1Pet");
		hardcodedAbbrs.put("2 Petrus", "2Pet");
		hardcodedAbbrs.put("1 Samuel", "1Sam");
		hardcodedAbbrs.put("2 Samuel", "2Sam");
		hardcodedAbbrs.put("1 Tawarikh", "1Taw");
		hardcodedAbbrs.put("2 Tawarikh", "2Taw");
		hardcodedAbbrs.put("1 Yohanes", "1Yoh");
		hardcodedAbbrs.put("2 Yohanes", "2Yoh");
		hardcodedAbbrs.put("3 Yohanes", "3Yoh");
		
		hardcodedAbbrs.put("Philemon", "Phm");
		hardcodedAbbrs.put("Philippians", "Phil");
		hardcodedAbbrs.put("Song of Solomon", "Song");
		hardcodedAbbrs.put("Zephaniah", "Zeph");
		hardcodedAbbrs.put("Ruth", "Ruth");
		hardcodedAbbrs.put("1 Corinthians", "1Cor");
		hardcodedAbbrs.put("2 Corinthians", "2Cor");
	}
	
	public static String getBookAbbr(Book book) {
		if (book.abbreviation != null) {
			return book.abbreviation;
		}

		String name = book.shortName;

		{
			String hardcodedAbbr = hardcodedAbbrs.get(name);
			if (hardcodedAbbr != null) {
				return hardcodedAbbr;
			}
		}

		int numberedBookCategory = book.bookId >= numberedBookMap.length? 0: numberedBookMap[book.bookId];
		if (numberedBookCategory > 0) {
			String startsWith = numberedBookStartsWiths[numberedBookCategory];
			String replaceWith = numberedBookReplaceWiths[numberedBookCategory];
			if (name.startsWith(startsWith)) {
				name = replaceWith + name.substring(startsWith.length());
			}
		}

		// remove spaces and '.'
		name = name.replace(" ", ""); //$NON-NLS-1$ //$NON-NLS-2$
		name = name.replace(".", ""); //$NON-NLS-1$ //$NON-NLS-2$

		if (name.length() > 3) name = name.substring(0, 3);
		return name;
	}
	
	/**
	 * @param books This array will NOT be modified.
	 * @return a new array of books already sorted.
	 */
	public static Book[] sortAlphabetically(Book[] books) {
		class Collation {
			Book book;
			String base;
			int number;
		}
		
		Collation[] cols = new Collation[books.length];
		for (int i = 0; i < books.length; i++) {
			Collation c = new Collation();
			Book book = books[i];
			c.book = book;
			c.base = book.shortName; // default
			c.number = 0; // default
			
			int numberedBookCategory = book.bookId >= numberedBookMap.length? 0: numberedBookMap[book.bookId];
			if (numberedBookCategory > 0) {
				String startsWith = numberedBookStartsWiths[numberedBookCategory];
				
				if (book.shortName.startsWith(startsWith)) {
					c.base = book.shortName.substring(startsWith.length()).trim();
					c.number = numberedBookCategory;
				} else {
					String startsWithNumber = numberedBookStartsWithNumbers[numberedBookCategory];
					if (book.shortName.startsWith(startsWithNumber)) {
						c.base = book.shortName.substring(startsWithNumber.length()).trim();
						c.number = numberedBookCategory;
					}
				}
			}
			
			cols[i] = c;
		}
		
		Arrays.sort(cols, new Comparator<Collation>() {
			@Override public int compare(Collation a, Collation b) {
				int compare = a.base.compareToIgnoreCase(b.base);
				if (compare != 0) return compare;
				return a.number - b.number;
			}
		});
		
		Book[] res = new Book[books.length];
		for (int i = 0; i < cols.length; i++) {
			res[i] = cols[i].book;
		}
		
		return res;
	}
}
