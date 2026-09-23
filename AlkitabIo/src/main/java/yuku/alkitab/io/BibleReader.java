package yuku.alkitab.io;

import java.util.List;
import java.util.Map;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.FootnoteEntry;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.XrefEntry;
import yuku.alkitab.util.IntArrayList;

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

	int loadPericope(int bookId, int chapter_1, IntArrayList aris, List<PericopeBlock> pericopeBlocks);

	/**
	 * @param arif 24bit ari at the MSB + which xref field at the 8bit LSB (starts from 1)
	 */
	XrefEntry getXrefEntry(int arif);

	/**
	 * @param arif 24bit ari at the MSB + which xref field at the 8bit LSB (starts from 1)
	 */
	FootnoteEntry getFootnoteEntry(int arif);

	/**
	 * The word families smart search widens a query to: each root mapped to the forms of it that
	 * occur in this version.
	 *
	 * @return null if this version carries none
	 */
	default Map<String, List<String>> loadLexicon() {
		return null;
	}
}
