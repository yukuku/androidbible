package yuku.alkitab.base.model;

import yuku.alkitab.base.util.IntArrayList;


public class Book {
	public int bookId;
	public String shortName;
	public int chapter_count;
	public int[] verse_counts;
	public String abbreviation;

	public String reference(int chapter_1) {
		return this.shortName + " " + chapter_1; //$NON-NLS-1$ 
	}

	public String reference(int chapter_1, int verse_1) {
		return this.shortName + " " + chapter_1 + ":" + verse_1;  //$NON-NLS-1$//$NON-NLS-2$ 
	}
	
	public CharSequence reference(int chapter_1, IntArrayList verses_1) {
		StringBuilder sb = new StringBuilder(this.shortName); 
		sb.append(' ').append(chapter_1);
		if (verses_1 == null || verses_1.size() == 0) {
			return sb;
		}
		sb.append(':');
		writeVerseRange(verses_1, sb);
		return sb;
	}

	public static void writeVerseRange(IntArrayList verses_1, StringBuilder sb) {
		int origLen = sb.length();
		int lastVerse_1 = 0;
		int beginVerse_1 = 0;
		
		for (int i = 0; i < verses_1.size(); i++) {
			int verse_1 = verses_1.get(i);
			
			if (lastVerse_1 == 0) {
				// not exist yet, pass
			} else if (lastVerse_1 == verse_1 - 1) {
				// still continuation, store the beginning
				if (beginVerse_1 == 0) beginVerse_1 = lastVerse_1;
			} else {
				// just jumped
				if (beginVerse_1 != 0) {
					sb.append(origLen == sb.length()? "": ", ").append(beginVerse_1).append('-').append(lastVerse_1); //$NON-NLS-1$ //$NON-NLS-2$
					beginVerse_1 = 0;
				} else {
					sb.append(origLen == sb.length()? "": ", ").append(lastVerse_1); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			
			lastVerse_1 = verses_1.get(i);
		}
		
		// drain the remainings
		if (beginVerse_1 != 0) {
			sb.append(origLen == sb.length()? "": ", ").append(beginVerse_1).append('-').append(lastVerse_1);  //$NON-NLS-1$//$NON-NLS-2$
			beginVerse_1 = 0; // no need, only to make it consitent with above
		} else {
			sb.append(origLen == sb.length()? "": ", ").append(lastVerse_1); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
}
