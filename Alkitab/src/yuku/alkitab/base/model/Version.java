package yuku.alkitab.base.model;

import android.util.Log;

import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.storage.BibleReader;
import yuku.alkitab.base.storage.InternalReader;
import yuku.alkitab.base.storage.OldVerseTextDecoder;

public class Version {
	public static final String TAG = Version.class.getSimpleName();
	private static final String NOT_AVAILABLE_TEXT = "[?]"; //$NON-NLS-1$
	
	private BibleReader bibleReader;
	
	private Book[] cache_books;
	private Book[] cache_consecutiveBooks;
	
	private static Version internalVersion;

	public Version(BibleReader bibleReader) {
		this.bibleReader = bibleReader;
	}
	
	public static synchronized Version getInternalVersion() {
		if (internalVersion == null) {
			AppConfig c = AppConfig.get();
			internalVersion = new Version(new InternalReader(c.internalPrefix, c.internalShortName, c.internalLongName, new OldVerseTextDecoder.Utf8()));
		}
		return internalVersion;
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
	public synchronized int getMaxBookIdPlusOne() {
		return getBooks().length;
	}
	
	/**
	 * @return same as {@link #getBooks()}, but none of the array elements is null. 
	 * For enumerating available books.
	 * Note that using this, no guarantee that return_value[bookId].bookId == bookId.
	 */
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
	public synchronized Book getBook(int bookId) {
		Book[] books = getBooks();
		if (bookId < 0 || bookId >= books.length) {
			return null;
		}
		return books[bookId];
	}
	
	public synchronized Book getFirstBook() {
		Book[] books = getBooks();
		for (Book b: books) {
			if (b != null) return b;
		}

		Log.e(TAG, "No books available on this version. Version info: " + (this.bibleReader == null? "reader=null": (this.bibleReader.getLongName() + " books.length=" + books.length)));    //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
		return null;
	}

	public synchronized String loadVerseText(int ari) {
		return loadVerseText(getBook(Ari.toBook(ari)), Ari.toChapter(ari), Ari.toVerse(ari));
	}

	public synchronized String loadVerseText(Book book, int chapter_1, int verse_1) {
		if (book == null) {
			return NOT_AVAILABLE_TEXT;
		}
		SingleChapterVerses verses = bibleReader.loadVerseText(book, chapter_1, false, false);
		
		if (verses == null) {
			return NOT_AVAILABLE_TEXT;
		}
		
		int verse_0 = verse_1 - 1;
		if (verse_0 >= verses.getVerseCount()) {
			return NOT_AVAILABLE_TEXT;
		}
		return verses.getVerse(verse_0);
	}

	/**
	 * Loads the list of pericopes for a chapter
	 * @param aris output parameter; will be filled in with the aris where the pericopes start
	 * @param pericopeBlocks output parameter; will be filled with the content of the pericopes
	 * @param max the maximum number of pericopes to return. The output arrays must have at least max entries.
	 * @return the number of pericopes loaded. 0 if the version does not have pericopes or some errors happen.
	 */
	public synchronized int loadPericope(int bookId, int chapter_1, int[] aris, PericopeBlock[] pericopeBlocks, int max) {
		return bibleReader.loadPericope(bookId, chapter_1, aris, pericopeBlocks, max);
	}

	public synchronized SingleChapterVerses loadChapterText(Book book, int chapter_1) {
		if (book == null) {
			return null;
		}
		
		return bibleReader.loadVerseText(book, chapter_1, false, false);
	}

	public synchronized SingleChapterVerses loadChapterTextLowercased(Book book, int chapter_1) {
		if (book == null) {
			return null;
		}
		
		return bibleReader.loadVerseText(book, chapter_1, false, true);
	}

	public synchronized String loadChapterTextLowercasedWithoutSplit(Book book, int chapter_1) {
		if (book == null) {
			return NOT_AVAILABLE_TEXT;
		}
		
		SingleChapterVerses singleVerse = bibleReader.loadVerseText(book, chapter_1, true, true);
		
		if (singleVerse == null) {
			return NOT_AVAILABLE_TEXT;
		}
		
		return singleVerse.getVerse(0);
	}
}
