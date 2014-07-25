package yuku.alkitab.model;

import yuku.alkitab.util.IntArrayList;

import java.util.List;

public interface Version {
	public static final String TAG = Version.class.getSimpleName();

	/**
	 * Get the short name (abbreviation) of this version.
	 */
	public abstract String getShortName();

	public abstract String getLongName();

	/**
	 * @return The highest bookId on this version plus one.
	 */
	public abstract int getMaxBookIdPlusOne();
	
	/**
	 * For enumerating available books.
	 * Note that using this, no guarantee that return_value[bookId].bookId == bookId.
	 */
	public abstract Book[] getConsecutiveBooks();
	
	/**
	 * @return null if bookId is out of range, or the book is not available on this version.
	 */
	public abstract Book getBook(int bookId);
	
	public abstract Book getFirstBook();

	/**
	 * Load a single verse from this version.
	 * @return null if the verse is not available.
	 */
	public abstract String loadVerseText(int ari);

	/**
	 * Load a single verse from this version.
	 * @return null if the verse is not available.
	 */
	public abstract String loadVerseText(Book book, int chapter_1, int verse_1);

	/**
	 * @param ariRanges list of aris where even-indexed elements are start and odd-indexed elements are end (inclusive) aris
	 * @param result_aris (non-null, will be cleared first) list of aris loaded
	 * @param result_verses (non-null, will be cleared first) list of verse texts loaded
	 * @return the number of verses successfully loaded
	 */
	public abstract int loadVersesByAriRanges(IntArrayList ariRanges, IntArrayList result_aris, List<String> result_verses);

	/**
	 * Loads the list of pericopes for a chapter
	 * @param aris output parameter; will be filled in with the aris where the pericopes start
	 * @param pericopeBlocks output parameter; will be filled with the content of the pericopes
	 * @param max the maximum number of pericopes to return. The output arrays must have at least max entries.
	 * @return the number of pericopes loaded. 0 if the version does not have pericopes or some errors happen.
	 */
	public abstract int loadPericope(int bookId, int chapter_1, int[] aris, PericopeBlock[] pericopeBlocks, int max);

	public abstract SingleChapterVerses loadChapterText(Book book, int chapter_1);

	public abstract SingleChapterVerses loadChapterTextLowercased(Book book, int chapter_1);

	/**
	 * Load a whole chapter as a single string with verses separated by '\n' from this version.
	 * @return null if the chapter is not available.
	 */
	public abstract String loadChapterTextLowercasedWithoutSplit(Book book, int chapter_1);
	
	/**
	 * @param arif 24bit ari at the MSB + which xref field at the 8bit LSB (starts from 1)
	 */
	public abstract XrefEntry getXrefEntry(final int arif);

	/**
	 * @param arif 24bit ari at the MSB + which xref field at the 8bit LSB (starts from 1)
	 */
	public abstract FootnoteEntry getFootnoteEntry(final int arif);

	public abstract String reference(int ari);

	public abstract String reference(int bookId, int chapter_1, int verse_1);
}
