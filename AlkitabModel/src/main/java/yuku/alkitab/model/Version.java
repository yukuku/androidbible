package yuku.alkitab.model;

import android.support.annotation.Nullable;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

import java.util.List;

public abstract class Version {
	/**
	 * Get the short name (abbreviation) of this version.
	 */
	public abstract String getShortName();

	public abstract String getLongName();

	public abstract String getLocale();

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
	@Nullable public abstract String loadVerseText(int ari);

	/**
	 * Load a single verse from this version.
	 * @return null if the verse is not available.
	 */
	@Nullable public abstract String loadVerseText(Book book, int chapter_1, int verse_1);

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

	private static class UnavailableBookNames {
		static String[] names = {
			"Gen", "Ex", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam", "1Ki", "2Ki", "1Chr", "2Chr", "Ezr", "Neh", "Est", "Job", "Ps", "Prov", "Ecc", "Song", "Isa", "Jer", "Lam", "Eze", "Dan", "Hos", "Joel", "Amos", "Ob", "Jon", "Mi", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal", "Mt", "Mk", "Luk", "Jn", "Acts", "Rm", "1Cor", "2Cor", "Gal", "Eph", "Php", "Col", "1Ths", "2The", "1Tim", "2Tim", "Tit", "Phm", "Hb", "Jam", "1Pt", "2Pt", "1Jn", "2Jn", "3Jn", "Jud", "Rev",
		};
	}

	public String reference(int ari) {
		int bookId = Ari.toBook(ari);
		int chapter_1 = Ari.toChapter(ari);
		int verse_1 = Ari.toVerse(ari);

		return reference(bookId, chapter_1, verse_1);
	}

	public String referenceWithVerseCount(final int ari, final int verseCount) {
		int bookId = Ari.toBook(ari);
		int chapter_1 = Ari.toChapter(ari);
		int verse_1 = Ari.toVerse(ari);

		if (verse_1 == 0 || verseCount == 1) { // verseCount does not matter
			return reference(bookId, chapter_1, verse_1);
		} else {
			return reference(bookId, chapter_1, verse_1) + "\u2013" /* endash */ + (verse_1 + verseCount - 1);
		}
	}

	private String getBookOrPlaceholder(final int bookId) {
		final Book book = getBook(bookId);

		if (book != null) {
			return book.shortName;
		}

		if (bookId < 0 || bookId >= UnavailableBookNames.names.length) {
			return "[?]";
		}

		return "[[" + UnavailableBookNames.names[bookId] + "]]";
	}

	public String reference(int bookId, int chapter_1, int verse_1) {
		final String shortName = getBookOrPlaceholder(bookId);

		if (verse_1 == 0) {
			if (chapter_1 == 0) {
				return shortName;
			} else {
				return Book.reference(shortName, chapter_1);
			}
		} else {
			return Book.reference(shortName, chapter_1, verse_1);
		}
	}

	private String getLastVerseString(final int bookId, final int chapter_1) {
		final Book book = getBook(bookId);
		if (book == null) {
			return "end";
		}

		if (chapter_1 < 1 || chapter_1 > book.chapter_count) {
			return "end";
		}

		return "" + book.verse_counts[chapter_1 - 1];
	}

	public String referenceRange(final int ari_start, final int ari_end) {
		// 5 parts required for e.g. Matthew 5:3–6:10
		// bcstr_start "Matthew 5"
		// vstr_start ":3"
		// en-dash "–"
		// bcstr_end "6"
		// vstr_end ":10"
		final String bcstr_start;
		final String vstr_start;
		final String en_dash;
		final String bcstr_end;
		final String vstr_end;

		final boolean same_book_and_chapter;

		if (Ari.toBookChapter(ari_start) == Ari.toBookChapter(ari_end)) { // same book, same chapter
			final int bookId = Ari.toBook(ari_start);
			final int chapter_1 = Ari.toChapter(ari_start);
			bcstr_start = Book.reference(getBookOrPlaceholder(bookId), chapter_1);
			bcstr_end = "";
			same_book_and_chapter = true;

		} else if (Ari.toBook(ari_start) == Ari.toBook(ari_end)) { // same book, different chapter
			final int bookId = Ari.toBook(ari_start);
			final int chapter_1_start = Ari.toChapter(ari_start);
			final int chapter_1_end = Ari.toChapter(ari_end);
			bcstr_start = Book.reference(getBookOrPlaceholder(bookId), chapter_1_start);
			bcstr_end = "" + chapter_1_end;
			same_book_and_chapter = false;

		} else { // different book, different chapter
			final int bookId_start = Ari.toBook(ari_start);
			final int bookId_end = Ari.toBook(ari_end);
			final int chapter_1_start = Ari.toChapter(ari_start);
			final int chapter_1_end = Ari.toChapter(ari_end);
			bcstr_start = Book.reference(getBookOrPlaceholder(bookId_start), chapter_1_start);
			bcstr_end = Book.reference(getBookOrPlaceholder(bookId_end), chapter_1_end);
			same_book_and_chapter = false;
		}

		// en-dash is only empty if both aris are exactly the same
		en_dash = ari_start == ari_end ? "" : "\u2013";

		// How to show verse depending on whether the verse is 0:
		// start, end, show start as, show end as (0 means verse is 0, 1 means verse is non-0)
		// 0, 0, nothing, nothing
		// 0, 1, :1, :verse
		// 1, 0, :verse, :end
		// 1, 1, :verse, :verse
		final int verse_1_start = Ari.toVerse(ari_start);
		final int verse_1_end = Ari.toVerse(ari_end);

		switch ((verse_1_start != 0 ? 2 : 0) + (verse_1_end != 0 ? 1 : 0)) {
			case 0:
			default: // should not happen
				vstr_start = vstr_end = "";
				break;
			case 1:
				vstr_start = ":1";
				vstr_end = (same_book_and_chapter ? "" : ":") + verse_1_end;
				break;
			case 2:
				vstr_start = ":" + verse_1_start;
				vstr_end = (same_book_and_chapter ? "" : ":") + getLastVerseString(Ari.toBook(ari_end), Ari.toChapter(ari_end));
				break;
			case 3:
				vstr_start = ":" + verse_1_start;
				if (ari_start == ari_end) {
					vstr_end = "";
				} else {
					vstr_end = (same_book_and_chapter ? "" : ":") + verse_1_end;
				}
				break;
		}

		return bcstr_start + vstr_start + en_dash + bcstr_end + vstr_end;
	}
}
