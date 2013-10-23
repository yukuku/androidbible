package yuku.alkitab.base.util;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OsisBookNames {
	public static final String TAG = OsisBookNames.class.getSimpleName();
	
	private static TObjectIntHashMap<String> bookNameToBookIdMap;
	private static Pattern bookNamePattern;
	private static Pattern bookNameWithChapterAndOptionalVersePattern;
	

	static String[] names = {
		"Gen",   //$NON-NLS-1$
		"Exod",  //$NON-NLS-1$
		"Lev",   //$NON-NLS-1$
		"Num",   //$NON-NLS-1$
		"Deut",  //$NON-NLS-1$
		"Josh",  //$NON-NLS-1$
		"Judg",  //$NON-NLS-1$
		"Ruth",  //$NON-NLS-1$
		"1Sam",  //$NON-NLS-1$
		"2Sam",  //$NON-NLS-1$
		"1Kgs",  //$NON-NLS-1$
		"2Kgs",  //$NON-NLS-1$
		"1Chr",  //$NON-NLS-1$
		"2Chr",  //$NON-NLS-1$
		"Ezra",  //$NON-NLS-1$
		"Neh",   //$NON-NLS-1$
		"Esth",  //$NON-NLS-1$
		"Job",   //$NON-NLS-1$
		"Ps",    //$NON-NLS-1$
		"Prov",  //$NON-NLS-1$
		"Eccl",  //$NON-NLS-1$
		"Song",  //$NON-NLS-1$
		"Isa",   //$NON-NLS-1$
		"Jer",   //$NON-NLS-1$
		"Lam",   //$NON-NLS-1$
		"Ezek",  //$NON-NLS-1$
		"Dan",   //$NON-NLS-1$
		"Hos",   //$NON-NLS-1$
		"Joel",  //$NON-NLS-1$
		"Amos",  //$NON-NLS-1$
		"Obad",  //$NON-NLS-1$
		"Jonah", //$NON-NLS-1$
		"Mic",   //$NON-NLS-1$
		"Nah",   //$NON-NLS-1$
		"Hab",   //$NON-NLS-1$
		"Zeph",  //$NON-NLS-1$
		"Hag",   //$NON-NLS-1$
		"Zech",  //$NON-NLS-1$
		"Mal",   //$NON-NLS-1$
		"Matt",  //$NON-NLS-1$
		"Mark",  //$NON-NLS-1$
		"Luke",  //$NON-NLS-1$
		"John",  //$NON-NLS-1$
		"Acts",  //$NON-NLS-1$
		"Rom",   //$NON-NLS-1$
		"1Cor",  //$NON-NLS-1$
		"2Cor",  //$NON-NLS-1$
		"Gal",   //$NON-NLS-1$
		"Eph",   //$NON-NLS-1$
		"Phil",  //$NON-NLS-1$
		"Col",   //$NON-NLS-1$
		"1Thess",//$NON-NLS-1$ 
		"2Thess",//$NON-NLS-1$ 
		"1Tim",  //$NON-NLS-1$
		"2Tim",  //$NON-NLS-1$
		"Titus", //$NON-NLS-1$
		"Phlm",  //$NON-NLS-1$
		"Heb",   //$NON-NLS-1$
		"Jas",   //$NON-NLS-1$
		"1Pet",  //$NON-NLS-1$
		"2Pet",  //$NON-NLS-1$
		"1John", //$NON-NLS-1$
		"2John", //$NON-NLS-1$
		"3John", //$NON-NLS-1$
		"Jude",  //$NON-NLS-1$
		"Rev",   //$NON-NLS-1$
	};
	
	static {
		bookNameToBookIdMap = new TObjectIntHashMap<String>(250, 0.75f, -1);
		for (int i = 0; i < names.length; i++) {
			bookNameToBookIdMap.put(names[i], i);
		}
	}
	
	public static Pattern getBookNamePattern() {
		if (bookNamePattern == null) {
			StringBuilder sb = new StringBuilder(400);
			sb.append('(');
			for (int i = 0; i < names.length; i++) {
				if (i != 0) sb.append('|');
				sb.append(names[i]);
			}
			sb.append(')');
			
			bookNamePattern = Pattern.compile(sb.toString());
		}
		return bookNamePattern;
	}
	
	public static Pattern getBookNameWithChapterAndOptionalVersePattern() {
		if (bookNameWithChapterAndOptionalVersePattern == null) {
			StringBuilder sb = new StringBuilder(400);
			sb.append('(');
			for (int i = 0; i < names.length; i++) {
				if (i != 0) sb.append('|');
				sb.append(names[i]);
			}
			sb.append(')');
			
			sb.append("\\.([1-9][0-9]{0,2})(?:\\.([1-9][0-9]{0,2}))?"); //$NON-NLS-1$
			
			bookNameWithChapterAndOptionalVersePattern = Pattern.compile(sb.toString());
		}
		return bookNameWithChapterAndOptionalVersePattern;
	}
	
	/** 
	 * @param osisBookName OSIS Book Name (only OT and NT currently supported)
	 * @return 0 to 65 when OK, -1 when not found
	 */
	public static int osisBookNameToBookId(String osisBookName) {
		if (osisBookName == null) return -1;
		return bookNameToBookIdMap.get(osisBookName);
	}

	public static int osisToAri(final String osis) {
		final Matcher m = getBookNameWithChapterAndOptionalVersePattern().matcher(osis);
		if (m.matches()) {
			String osisBookName = m.group(1);
			String chapter_s = m.group(2);
			String verse_s = m.group(3);

			final int bookId = osisBookNameToBookId(osisBookName);
			final int chapter_1 = Integer.parseInt(chapter_s);
			final int verse_1 = (verse_s == null || verse_s.length() == 0)? 0: Integer.parseInt(verse_s);

			return Ari.encode(bookId, chapter_1, verse_1);
		}

		return 0;
	}
}
