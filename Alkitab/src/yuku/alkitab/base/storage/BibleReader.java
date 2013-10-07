package yuku.alkitab.base.storage;

import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.SingleChapterVerses;
import yuku.alkitab.base.model.XrefEntry;

public interface BibleReader {
	String getShortName();
	String getLongName();
	String getDescription();
	
	Book[] loadBooks();
	
	/**
	 * @return may return null if the requested is not available 
	 */
	SingleChapterVerses loadVerseText(Book book, int chapter_1, boolean dontSplitVerses, boolean lowercased);
	
	int loadPericope(int bookId, int chapter_1, int[] aris, PericopeBlock[] pericopeBlocks, int max);

	/**
	 * @param arif 24bit ari at the MSB + which xref field at the 8bit LSB (starts from 1)
	 */
	XrefEntry getXrefEntry(int arif);
}
