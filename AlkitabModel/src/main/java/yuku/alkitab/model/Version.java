package yuku.alkitab.model;

import yuku.alkitab.util.IntArrayList;

import java.util.List;

public interface Version {
	/**
	 * Get the short name (abbreviation) of this version.
	 */
	String getShortName();

	String getLongName();

	/**
	 * @return The highest bookId on this version plus one.
	 */
	int getMaxBookIdPlusOne();
	
	/**
	 * For enumerating available books.
	 * Note that using this, no guarantee that return_value[bookId].bookId == bookId.
	 */
	Book[] getConsecutiveBooks();
	
	/**
	 * @return null if bookId is out of range, or the book is not available on this version.
	 */
	Book getBook(int bookId);
	
	Book getFirstBook();

	/**
	 * Load a single verse from this version.
	 * @return null if the verse is not available.
	 */
	String loadVerseText(int ari);

	/**
	 * Load a single verse from this version.
	 * @return null if the verse is not available.
	 */
	String loadVerseText(Book book, int chapter_1, int verse_1);

	/**
	 * @param ariRanges list of aris where even-indexed elements are start and odd-indexed elements are end (inclusive) aris
	 * @param result_aris (non-null, will be cleared first) list of aris loaded
	 * @param result_verses (non-null, will be cleared first) list of verse texts loaded
	 * @return the number of verses successfully loaded
	 */
	int loadVersesByAriRanges(IntArrayList ariRanges, IntArrayList result_aris, List<String> result_verses);

	/**
	 * Loads the list of pericopes for a chapter
	 * @param aris output parameter; will be filled in with the aris where the pericopes start
	 * @param pericopeBlocks output parameter; will be filled with the content of the pericopes
	 * @param max the maximum number of pericopes to return. The output arrays must have at least max entries.
	 * @return the number of pericopes loaded. 0 if the version does not have pericopes or some errors happen.
	 */
	int loadPericope(int bookId, int chapter_1, int[] aris, PericopeBlock[] pericopeBlocks, int max);

	SingleChapterVerses loadChapterText(Book book, int chapter_1);

	SingleChapterVerses loadChapterTextLowercased(Book book, int chapter_1);

	/**
	 * Load a whole chapter as a single string with verses separated by '\n' from this version.
	 * @return null if the chapter is not available.
	 */
	String loadChapterTextLowercasedWithoutSplit(Book book, int chapter_1);
	
	/**
	 * @param arif 24bit ari at the MSB + which xref field at the 8bit LSB (starts from 1)
	 */
	XrefEntry getXrefEntry(final int arif);

	/**
	 * @param arif 24bit ari at the MSB + which xref field at the 8bit LSB (starts from 1)
	 */
	FootnoteEntry getFootnoteEntry(final int arif);

	String reference(int ari);

	String referenceWithVerseCount(int ari, int verseCount);

	String reference(int bookId, int chapter_1, int verse_1);

	String referenceRange(int ari_start, int ari_end);
}
