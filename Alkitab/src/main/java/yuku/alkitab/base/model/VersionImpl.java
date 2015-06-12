package yuku.alkitab.base.model;

import android.util.Log;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.storage.InternalReader;
import yuku.alkitab.base.storage.OldVerseTextDecoder;
import yuku.alkitab.io.BibleReader;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.FootnoteEntry;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.Version;
import yuku.alkitab.model.XrefEntry;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

import java.util.List;

public class VersionImpl extends Version {
	public static final String TAG = VersionImpl.class.getSimpleName();

	private BibleReader bibleReader;

	private Book[] cache_books;
	private Book[] cache_consecutiveBooks;

	private static Version internalVersion;

	public VersionImpl(BibleReader bibleReader) {
		super();
		this.bibleReader = bibleReader;
	}

	public static synchronized Version getInternalVersion() {
		if (internalVersion == null) {
			final AppConfig c = AppConfig.get();
			internalVersion = new VersionImpl(new InternalReader(c.internalPrefix, c.internalLocale, c.internalShortName, c.internalLongName, new OldVerseTextDecoder.Utf8()));
		}
		return internalVersion;
	}

	/**
	 * Get the short name (abbreviation) of this version.
	 */
	@Override
	public String getShortName() {
		return bibleReader.getShortName();
	}

	@Override
	public String getLongName() {
		return bibleReader.getLongName();
	}

	/**
	 * Some books can be null. Using this method, the return value indexed will have the same value as the {@link Book#bookId}.
	 * i.e. return_value[bookId].bookId == bookId.
	 */
	private synchronized Book[] getBooks() {
		if (cache_books == null) {
			cache_books = this.bibleReader.loadBooks();
		}
		return cache_books;
	}

	/**
	 * @return The highest bookId on this version plus one.
	 */
	@Override
	public synchronized int getMaxBookIdPlusOne() {
		int max = -1;
		for (Book b: getBooks()) {
			if (b != null) {
				if (b.bookId > max) max = b.bookId;
			}
		}
		return max + 1;
	}

	/**
	 * @return same as {@link #getBooks()}, but none of the array elements is null.
	 * For enumerating available books.
	 * Note that using this, no guarantee that return_value[bookId].bookId == bookId.
	 */
	@Override
	public synchronized Book[] getConsecutiveBooks() {
		if (cache_consecutiveBooks == null) {
			Book[] books1 = getBooks();
			// count
			int book_count = 0;
			for (Book b: books1) {
				if (b != null) {
					book_count++;
				}
			}
			Book[] books2 = new Book[book_count];
			int c = 0;
			for (Book b: books1) {
				if (b != null) {
					books2[c++] = b;
				}
			}
			cache_consecutiveBooks = books2;
		}
		return cache_consecutiveBooks;
	}

	/**
	 * @return null if bookId is out of range, or the book is not available on this version.
	 */
	@Override
	public synchronized Book getBook(int bookId) {
		if (bookId < 0) return null;
		final Book[] books = getBooks();
		if (books == null) return null;
		if (bookId < books.length) {
			// fast path for OT+NT complete versions
			Book book = books[bookId];
			if (book != null && book.bookId == bookId) return book;
		}
		// linear search
		for (Book book: books) {
			if (book != null && book.bookId == bookId) {
				return book;
			}
		}
		return null;
	}

	@Override
	public synchronized Book getFirstBook() {
		Book[] books = getBooks();
		for (Book b: books) {
			if (b != null) return b;
		}

		Log.e(TAG, "No books available on this version. Version info: " + (this.bibleReader == null? "reader=null": (this.bibleReader.getLongName() + " books.length=" + books.length)));    //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
		return null;
	}

	@Override
	public synchronized String loadVerseText(int ari) {
		return loadVerseText(getBook(Ari.toBook(ari)), Ari.toChapter(ari), Ari.toVerse(ari));
	}

	@Override
	public synchronized String loadVerseText(Book book, int chapter_1, int verse_1) {
		if (book == null) {
			return null;
		}
		SingleChapterVerses verses = bibleReader.loadVerseText(book, chapter_1, false, false);

		if (verses == null) {
			return null;
		}

		int verse_0 = verse_1 - 1;
		if (verse_0 >= verses.getVerseCount()) {
			return null;
		}
		return verses.getVerse(verse_0);
	}

	/**
	 * @param ariRanges list of aris where even-indexed elements are start and odd-indexed elements are end (inclusive) aris
	 * @param result_aris (non-null, will be cleared first) list of aris loaded
	 * @param result_verses (non-null, will be cleared first) list of verse texts loaded
	 * @return the number of verses successfully loaded
	 */
	@Override
	public synchronized int loadVersesByAriRanges(IntArrayList ariRanges, IntArrayList result_aris, List<String> result_verses) {
		int res = 0;

		result_aris.clear();
		result_verses.clear();

		for (int i = 0, len = ariRanges.size(); i < len; i+=2) {
			int ari_start = ariRanges.get(i);
			int ari_end = ariRanges.get(i + 1);

			if (ari_start == 0 || ari_end == 0) {
				continue;
			}

			if (Ari.toVerse(ari_start) == 0) {
				// if start has verse 0, it means the start is verse 1.
				ari_start |= 0x01;
			}

			if (Ari.toVerse(ari_end) == 0) {
				// if end has verse 0, it means until the end of chapter.
				ari_end |= 0xff;
			}

			if (ari_start == ari_end) {
				// case: single verse
				//noinspection UnnecessaryLocalVariable
				int ari = ari_start;
				Book book = getBook(Ari.toBook(ari));
				if (book != null) {
					result_aris.add(ari);
					result_verses.add(loadVerseText(ari));
					res++;
				}
			} else {
				int ari_start_bc = Ari.toBookChapter(ari_start);
				int ari_end_bc = Ari.toBookChapter(ari_end);

				if (ari_start_bc == ari_end_bc) {
					// case: multiple verses in the same chapter
					Book book = getBook(Ari.toBook(ari_start));
					if (book != null) {
						res += resultForOneChapter(book, ari_start_bc, Ari.toVerse(ari_start), Ari.toVerse(ari_end), result_aris, result_verses);
					}
				} else {
					// case: multiple verses in different chapters
					for (int ari_bc = ari_start_bc; ari_bc <= ari_end_bc; ari_bc += 0x0100) {
						Book book = getBook(Ari.toBook(ari_bc));
						int chapter_1 = Ari.toChapter(ari_bc);
						if (book == null || chapter_1 <= 0 || chapter_1 > book.chapter_count) {
							continue;
						}

						if (ari_bc == ari_start_bc) { // we're at the first requested chapter
							res += resultForOneChapter(book, ari_bc, Ari.toVerse(ari_start), 0xff, result_aris, result_verses);
						} else if (ari_bc == ari_end_bc) { // we're at the last requested chapter
							res += resultForOneChapter(book, ari_bc, 0x01, Ari.toVerse(ari_end), result_aris, result_verses);
						} else { // we're at the middle, request all verses!
							res += resultForOneChapter(book, ari_bc, 0x01, 0xff, result_aris, result_verses);
						}
					}
				}
			}
		}

		return res;
	}

	/**
	 * @return number of verses put into the cursor
	 */
	private int resultForOneChapter(Book book, int ari_bc, int v_1_start, int v_1_end, IntArrayList result_aris, List<String> result_verses) {
		int count = 0;
		SingleChapterVerses verses = loadChapterText(book, Ari.toChapter(ari_bc));
		for (int v_1 = v_1_start; v_1 <= v_1_end; v_1++) {
			int v_0 = v_1 - 1;
			if (v_0 < verses.getVerseCount()) {
				int ari = ari_bc | v_1;
				result_aris.add(ari);
				result_verses.add(verses.getVerse(v_0));
				count++;
			} else {
				// we're done with this chapter, no need to loop again
				break;
			}
		}
		return count;
	}

	/**
	 * Loads the list of pericopes for a chapter
	 * @param aris output parameter; will be filled in with the aris where the pericopes start
	 * @param pericopeBlocks output parameter; will be filled with the content of the pericopes
	 * @param max the maximum number of pericopes to return. The output arrays must have at least max entries.
	 * @return the number of pericopes loaded. 0 if the version does not have pericopes or some errors happen.
	 */
	@Override
	public synchronized int loadPericope(int bookId, int chapter_1, int[] aris, PericopeBlock[] pericopeBlocks, int max) {
		return bibleReader.loadPericope(bookId, chapter_1, aris, pericopeBlocks, max);
	}

	@Override
	public synchronized SingleChapterVerses loadChapterText(Book book, int chapter_1) {
		if (book == null) {
			return null;
		}

		return bibleReader.loadVerseText(book, chapter_1, false, false);
	}

	@Override
	public synchronized SingleChapterVerses loadChapterTextLowercased(Book book, int chapter_1) {
		if (book == null) {
			return null;
		}

		return bibleReader.loadVerseText(book, chapter_1, false, true);
	}

	@Override
	public synchronized String loadChapterTextLowercasedWithoutSplit(Book book, int chapter_1) {
		if (book == null) {
			return null;
		}

		SingleChapterVerses singleVerse = bibleReader.loadVerseText(book, chapter_1, true, true);

		if (singleVerse == null) {
			return null;
		}

		return singleVerse.getVerse(0);
	}

	/**
	 * @param arif 24bit ari at the MSB + which xref field at the 8bit LSB (starts from 1)
	 */
	@Override
	public synchronized XrefEntry getXrefEntry(final int arif) {
		return bibleReader.getXrefEntry(arif);
	}

	/**
	 * @param arif 24bit ari at the MSB + which xref field at the 8bit LSB (starts from 1)
	 */
	@Override
	public synchronized FootnoteEntry getFootnoteEntry(final int arif) {
		return bibleReader.getFootnoteEntry(arif);
	}
}
