package yuku.alkitab.io;

import java.util.List;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.FootnoteEntry;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.XrefEntry;

public interface BibleReader {
	String getLocale();

	String getShortName();

	String getLongName();

	String getDescription();

	Book[] loadBooks();

	/**
	 * @return may return null if the requested is not available
	 */
	SingleChapterVerses loadVerseText(Book book, int chapter_1, boolean dontSplitVerses, boolean lowercased);

	int loadPericope(int bookId, int chapter_1, List<Integer> aris, List<PericopeBlock> pericopeBlocks);

	/**
	 * @param arif 24bit ari at the MSB + which xref field at the 8bit LSB (starts from 1)
	 */
	XrefEntry getXrefEntry(int arif);

	/**
	 * @param arif 24bit ari at the MSB + which xref field at the 8bit LSB (starts from 1)
	 */
	FootnoteEntry getFootnoteEntry(int arif);
}
