/*
    Bible Plus A Bible Reader for Blackberry
    Copyright (C) 2010 Yohanes Nugroho

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

    Yohanes Nugroho (yohanes@gmail.com)
 */

package com.compactbyte.bibleplus.reader;

import java.io.*;
import java.util.*;

/**
 * 
 * <p>
 * Usage:
 * </p>
 * 
 * <pre>
 * BiblePlusPDB bible = new BiblePlusPDB();
 * if (!bible.loadVersionInfo()) {
 * 	// error
 * }
 * </pre>
 * <p>
 * At this point we can access some basic information, such as the bible version name and version info. We can also have the list of book names at this point.
 * </p>
 * <p>
 * If we want to access the content of the bible, we need to load the word index. The word index contains the table that maps word number to a word.
 * </p>
 * 
 * <pre>
 * bible.loadWordIndex();
 * </pre>
 * <p>
 * To know how many books that we have, use:
 * </p>
 * 
 * <pre>
 * int book_count = bible.getBookCount();
 * </pre>
 * <p>
 * And to get the BookInfo object, use:
 * </p>
 * 
 * <pre>
 * BookInfo b = bible.getBook(index);
 * </pre>
 * <p>
 * The data for the book (the verse content) is not loaded until we call:
 * </p>
 * 
 * <pre>
 * b.openBook();
 * </pre>
 * 
 * 
 * <p>
 * To get the verse text only, we can call:
 * </p>
 * 
 * <pre>
 * String verse = getVerse(chapter, verse);
 * </pre>
 * 
 * If we want to have the chapter title, or verse title, we can use:
 * 
 * <pre>
 * StringBuffer[] sb = getCompleteVerse(chapter, verse);
 * </pre>
 * <p>
 * See the documentation of the return value in BookInfo.
 * </p>
 * 
 */

public class BiblePlusPDB {

	private String versionName;
	private String versionInfo;
	private String sepChar;
	private int versionAttr;
	private int wordIndex;
	private int totalWordRec;
	private int totalBooks;
	private PDBHeader header;

	private SearchProgressListener listener;

	/**
	 * Default encoding used to read strings
	 * Note: Blackberry only supports very limited encoding
	 */
	private String encoding = "UTF-8";

	private boolean wordIndexLoaded; // initialized with false;

	private final static int infVerInfoRecord = 0;

	private final static int infCopyProtected = 0x01;
	private final static int infByteNotShifted = 0x02;
	private final static int infRightAligned = 0x04;
	private BitVector bv;

	/**
	 * Standard book numbering
	 */
	private final static int[] book_numbers = {
		10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130,
		140, 150, 160, 170, 175, 180, 190, 200, 210, 220, 230,
		240, 250, 260, 270, 280, 290, 300, 310, 320, 330, 340,
		350, 360, 370, 380, 390, 400, 410, 420, 430, 440, 450,
		460, 470, 480, 490, 500, 510, 520, 530, 540, 550, 560,
		570, 580, 590, 600, 610, 620, 630, 640, 650, 660, 670,
		680, 690, 700, 710, 720, 730, 740, 750
	};

	/**
	 * The numbers above corresponds to these English booknames
	 * 
	 * For Blackberry, moving this to external data reduces the class size significantly
	 * 
	 */
	// private final static String[] book_names = {
	// "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy",
	// "Joshua", "Judges", "Ruth", "1 Samuel", "2 Samuel", "1 Kings",
	// "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah",
	// "Tobit", "Tobias", "Judit", "Esther", "1 Maccabees",
	// "2 Maccabees", "Job", "Psalms", "Proverbs", "Ecclesiastes",
	// "Song of Solomon", "Wisdom", "Sirach", "Isaiah", "Jeremiah",
	// "Lamentations", "Baruch", "Ezekiel", "Daniel", "Hosea", "Joel",
	// "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk",
	// "Zephaniah", "Haggai", "Zechariah", "Malachi", "Matthew",
	// "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians",
	// "2 Corinthians", "Galatians", "Ephesians", "Philippians",
	// "Colossians", "1 Thessalonians", "2 Thessalonians", "1 Timothy",
	// "2 Timothy", "Titus", "Philemon", "Hebrews", "James", "1 Peter",
	// "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation",
	// "1 Esdras", "2 Esdras"
	// };

	final static String EMPTY_BOOKNAME = "";

	/**
	 * If we have a book number we can try to find the index for the book.
	 * This is useful when we switch from one bible file to another bible file:
	 * get the book number in the old bible, and try to find the number in the new bible.
	 * 
	 * @param booknumber
	 *            book number to search
	 * @return index of book number in this bible
	 */
	public static int findGlobalBookNumberIndex(int booknumber) {
		/**
		 * Blackberry doesn't implement binarySearch in the Array class
		 * So I separate the implementation for binarySearch in Util class
		 */

		return Util.binarySearch(book_numbers, booknumber);
	}

	/**
	 * Object to access the PDB
	 */
	private PDBAccess pdbaccess;

	/**
	 * Array of BookInfo
	 */
	private BookInfo[] booksinfo;

	private boolean canSeek;

	/*
	 * Try to go to the bookmark
	 * if booknumber is not found, it will try bookname
	 */

	// int[] parseBookmark(Bookmark bm) {
	// return null;
	// }

	private String pathName;

	/**
	 * Blackberry doesn't have 'File.separator' so to be generic
	 * we will ask the user of this class to set it for us
	 * 
	 */

	private String pathSeparator = "/";

	private String filenamepart;

	private boolean is_greek = false;

	private boolean is_hebrew = false;

	private byte word_data[];

	private int[] wordLength;

	private int[] totalWord;

	private boolean[] compressed;

	private boolean[] nothing;

	private int byteacc[];

	private int totalWords; // initialized with 0;

	private final static int BOOK_REC_SIZE = 46;

	private int fail_reason;

	/**
	 * No error
	 */
	public final static int SUCCESS = 0;

	/**
	 * The file is not a PDB File
	 */
	public final static int ERR_NOT_PDB_FILE = 1;
	/**
	 * The file is a PDB file, but it is not in Bible+ format
	 */
	public final static int ERR_NOT_BIBLE_PLUS_FILE = 2;

	// public void setPathSeparator(String sep) {
	// pathSeparator = sep;
	// }

	/**
	 * The file is a corrupted
	 */
	public final static int ERR_FILE_CORRUPTED = 3;

	/**
	 * Create a bible object.
	 * 
	 * These Greek and Hebrew tables are separated for two
	 * reasons: (1) to reduce file size (2) we can use different
	 * translation, for example using accented or non accented
	 * greek
	 * 
	 * @param is
	 *            PDBDataStream usually from file but it can be from memory or other source
	 * @param _hebrewtab
	 *            hebrew table for translating to unicode
	 * @param _greektab
	 *            greek table for translating to unicode
	 */
	public BiblePlusPDB(PDBDataStream is, char[] _hebrewtab, char[] _greektab) {
		Util.setTables(_hebrewtab, _greektab);
		canSeek = is.canSeek();
		pdbaccess = new PDBAccess(is);
		pathName = is.getPathName();
		getFileNamePart();
	}

	/**
	 * Create a bible object
	 * 
	 * @param is
	 *            PDBDataStream usually from file but it can be from memory or other source
	 * @param _encoding
	 *            encoding used
	 */
	public BiblePlusPDB(PDBDataStream is, String _encoding) {
		canSeek = is.canSeek();
		pdbaccess = new PDBAccess(is);
		encoding = _encoding;
		pathName = is.getPathName();
		getFileNamePart();
	}

	/**
	 * Add search result to vector
	 * 
	 * @param result
	 *            search result
	 * @param b
	 *            book index
	 * @param c
	 *            chapter number
	 * @param v
	 *            verse number
	 */
	private void AddResult(Vector result, int b, int c, int v) {
		result.addElement(new Integer(b));
		result.addElement(new Integer(c));
		result.addElement(new Integer(v));
	}

	/**
	 * Cleanup object. Only used for debugging.
	 */
	public void close() {

		word_data = null;
		wordLength = null;
		totalWord = null;
		compressed = null;
		nothing = null;
		byteacc = null;

		if (booksinfo != null) {
			for (int i = 0; i < booksinfo.length; i++) {
				if (booksinfo[i] != null) {
					booksinfo[i].close();
					booksinfo[i] = null;
				}
			}
		}
		if (pdbaccess != null) {
			pdbaccess.close();
			pdbaccess = null;
		}
		// System.gc();
	}

	/**
	 * Create a bookmark object from a book index, chapter and verse number.
	 * The bookmark will contain excerpt from the verse
	 * 
	 * @param bookIndex
	 *            book index
	 * @param chapter
	 *            chapter number
	 * @param verse
	 *            verse number
	 * @return bookmark object
	 * 
	 */
	public Bookmark createBookMark(int bookIndex, int chapter, int verse) {
		BookInfo bi = getBook(bookIndex);
		if (bi == null) {
			return null;
		}

		String excerpt = null;

		try {
			bi.openBook();
			excerpt = bi.getVerse(chapter, verse);
		} catch (IOException e) {

		}

		Bookmark bm = new Bookmark(pathName, versionName, bi.getFullName(),
						bi.getBookNumber(), chapter, verse, excerpt);
		return bm;
	}

	/**
	 * Fast version of search, should work on most latin
	 * languages. It uses string matching to match the query
	 * string to the word list, then uses integer comparison to
	 * search inside the bible. (TODO: add explanation about the algorithm)
	 * 
	 * @param query
	 *            array of string to search
	 * @param range
	 *            book range (for example: new testament only)
	 * @param partial
	 *            if true, we are looking for partial match (e.g: "love" matches "love" and "lovely"
	 * @param allwords
	 *            if true, all words in query must appear in verse
	 * @param max_match
	 *            maximum match to return
	 * @return list of matches
	 */
	public Vector fastsearch(String query[],
					BookRange range,
					boolean partial,
					boolean allwords,
					int max_match) {

		if (query == null || query.length == 0 || range == null) {
			return null;
		}

		String[] lower_query = new String[query.length];

		for (int i = 0; i < query.length; i++) {
			lower_query[i] = query[i].toLowerCase();
		}

		int range_start = range.getBookIndexStart();
		int range_end = range.getBookIndexEnd();

		if (range_start > range_end) {
			return null;
		}

		// long ls = System.currentTimeMillis();

		int maxsteps = 2 * totalWord.length + (range_end - range_start) + 1;

		if (listener != null) {
			listener.searchStarted(maxsteps);
		}

		BitVector bv = getWordNumbers(lower_query, partial, allwords, maxsteps);

		if (bv == null) {
			searchFinished();
			return null;
		}

		int[] r = bv.listSet();

		// for (int i =0; i < r.length; i++) {
		// System.out.print(r[i]+" ");
		// System.out.println(getWord(r[i]));
		// }
		// System.out.println();

		Vector result = new Vector();

		int result_pos = 0;

		final int qlength = query.length;

		if (allwords && qlength > 1) {
			bv.setStartPos();
		}

		for (int tb = range_start; tb < range_end; tb++) {

			int b;
			if (range.isSequential()) {
				b = findBookNumber(book_numbers[tb]);
			} else {
				b = findBookNumber(range.getBookAt(tb));
			}

			if (b == -1) {
				continue;
			}

			// System.out.println("searching book " + b);
			BookInfo bi = booksinfo[b];
			try {
				bi.openBook();
			} catch (IOException e) {
				searchFinished();
				return null;
			}
			byte[] data = bi.getData();
			if (allwords && query.length > 1) {

				int filter = 0;
				for (int kx = 0; kx < query.length; kx++) {
					filter |= (1 << kx);
				}

				int stc = 1;
				if (tb == range_start) {
					stc = range.getStartChapter();
				}

				// System.out.println("searching " + stc + " to " + bi.getChapterCount());

				int ccount = bi.getChapterCount();

				for (int c = stc; c <= ccount; c++) {
					// System.out.println(" C= " + c);
					int stv = 1;
					if (tb == range_start && c == range.getStartChapter()) {
						stv = range.getStartVerse();
					}

					int vcount = bi.getVerseCount(c);

					for (int v = stv; v <= vcount; v++) {
						// System.out.println(" c= " + c + " v= " + v);

						int vlen = bi.getVerseLength(c, v);

						if (vlen < qlength) {
							continue;
						}

						int vstart = bi.getVerseStart(c, v);
						// System.out.println(vlen);
						int idx = vstart << 1;
						int bits = 0;
						for (int w = 0; w < vlen; w++) {
							int decWordNum = (data[idx] & 0xff) * 256 + (data[idx + 1] & 0xff);
							idx += 2;
							// System.out.print(decWordNum+" ");
							if (bv.get(decWordNum) > 0) {
								bits |= bv.getMatchBitsForWord(decWordNum);
							}
						}
						// System.out.println("------------> " + bits + "---- " + bv.countBits(bits));

						// boolean m = matchAllQueryToVerse(lower_query, b, c, v);
						// boolean m = bv.countBits(bits)==query.length;
						// boolean m = (bits & filter)==filter;
						if ((bits & filter) == filter) {
							// System.out.println("match" + b + " C " + c + " v" + v);
							AddResult(result, b, c, v);
							if ((result.size() / 3) >= max_match) {
								searchFinished();
								return result;
							}
						}

					}
					if (listener != null && listener.searchCanceled()) {
						searchFinished();
						return result;
					}

				}

			} else {
				/********************************************/
				/* Some words matching, or 1 word matching */
				/********************************************/

				int stc = 1;
				if (tb == range_start) {
					stc = range.getStartChapter();
				}

				for (int c = stc; c <= bi.getChapterCount(); c++) {
					// System.out.println(" C= " + c);
					int stv = 1;
					if (tb == range_start && c == range.getStartChapter()) {
						stv = range.getStartVerse();
					}

					for (int v = stv; v <= bi.getVerseCount(c); v++) {
						// System.out.println(" c= " + c + " v= " + v);
						int vstart = bi.getVerseStart(c, v);
						int vlen = bi.getVerseLength(c, v);
						// System.out.println(vlen);
						int idx = vstart * 2;
						for (int w = 0; w < vlen; w++) {
							int decWordNum = (data[idx] & 0xff) * 256 + (data[idx + 1] & 0xff);
							idx += 2;
							// System.out.print(decWordNum+" ");
							if (bv.get(decWordNum) > 0) {
								// System.out.println("match" + b + " C " + c + " v" + v);
								AddResult(result, b, c, v);
								if ((result.size() / 3) >= max_match) {
									searchFinished();
									return result;
								}
								break;
							}
						}
					}

					if (listener != null && listener.searchCanceled()) {
						searchFinished();
						return result;
					}

				}
			}
			if (listener != null) {
				listener.notify(2 * totalWord.length + (tb - range_start), maxsteps,
						b, result.size() / 3);
				if (listener.searchCanceled()) {
					searchFinished();
					return result;
				}
			}

		}
		searchFinished();
		return result;
	}

	public void setEncoding(String _encoding) {
		encoding = _encoding;
	}

	/**
	 * return book index
	 */
	public int findBookNumber(int booknum) {
		for (int i = 0; i < booksinfo.length; i++) {
			BookInfo bi = booksinfo[i];
			if (bi.getBookNumber() == booknum) {
				return i;
			}
		}
		return -1;
	}

	/* is substr in str (fullmatch) */
	private boolean fullMatch(String s, String str) {
		if (str.indexOf(sepChar + s + sepChar) >= 0) {
			return true;
		}
		if (str.startsWith(s + sepChar)) {
			return true;
		}
		if (str.endsWith(sepChar + s)) {
			return true;
		}
		return false;
	}

	/**
	 * Get book info at specified index
	 * 
	 * @param index
	 *            index of book
	 * @return BookInfo index (note: <code>loadVersionInfo</code> and <code>loadWordIndex</code> should be called before )
	 */
	public BookInfo getBook(int index) {
		return booksinfo[index];
	}

	/**
	 * Get number of books
	 */
	public int getBookCount() {
		return booksinfo.length;
	}

	/**
	 * Get array of book info
	 */
	public BookInfo[] getBooksInfo() {
		return booksinfo;
	}

	/**
	 * Get currently used encoding
	 * 
	 * @return currently used encoding
	 */
	public String getEncoding() {
		return encoding;
	}

	/**
	 * In case bible loading failed, get the reason for failure
	 * 
	 * @return fail reason (SUCCESS, ERR_NOT_PDB_FILE, ERR_NOT_BIBLE_PLUS_FILE, ERR_FILE_CORRUPTED)
	 */
	public int getFailReason() {
		return fail_reason;
	}

	private void getFileNamePart() {
		int pos = pathName.lastIndexOf(pathSeparator.charAt(0));
		if (pos < 0) {
			filenamepart = pathName;
			return;
		}
		filenamepart = pathName.substring(pos + 1);
		is_greek = filenamepart.startsWith("z");
		is_hebrew = filenamepart.startsWith("q");
	}

	/**
	 * Get PDB Header
	 */
	PDBHeader getHeader() {
		return header;
	}

	PDBAccess getPDBAccess() {
		return pdbaccess;
	}

	int[] getRepeat(int pos, int wordNum) {

		int repeat = 1;
		int[] result;
		// System.out.println("word repeat----");
		if (wordNum < 0xFFF0) {
			boolean compressed = pos == 0 ? true : isCompressed(pos);
			int len = getWordLength(pos);
			int wordIndex = getWordIndex(pos, wordNum);
			if (compressed) {
				// System.out.println("compressed\n");
				repeat = len / 2;
				if (repeat == 0) {
					return null;
				}

				result = new int[repeat];
				int st = wordIndex;
				// System.out.println("repeat " + repeat + " wi " + wordIndex);
				for (int i = 0; i < repeat; i++) {
					result[i] = Util.readShort(word_data, st);
					// System.out.println("numval = " + result[i]);
					st += 2;
				}
				return result;
			} else {
				// System.out.println("Not compressed");
				result = new int[1];
				result[0] = wordNum;
				return result;
			}
		}
		result = new int[1];
		result[0] = wordNum;
		return result;
	}

	/**
	 * Get word separator. Not private because it is used by BookInfo
	 * 
	 * @return word separator
	 */
	String getSepChar() {
		return sepChar;
	}

	/**
	 * Get the version name of this bible
	 */
	public String getVersionName() {
		return versionName;
	}

	String getWord(int wordNum) {
		int pos = getWordPos(wordNum);
		int index = getWordIndex(pos, wordNum);
		int len = getWordLength(pos);
		if (index == -1) {
			return "";
		}
		return readString(index, len);
	}

	int getWordIndex(int pos, int wordNum) {
		int relNum = wordNum - 1;
		int decWordIndex = 0;
		for (int i = 0; i <= pos; i++) {
			int _totalWord = totalWord[i];
			if (relNum < _totalWord) {
				int decWordLen = wordLength[i];
				// System.out.println("wl;"+decWordLen);
				decWordIndex = byteacc[i] + relNum * decWordLen;
				break;
			} else {
				relNum = (relNum - _totalWord);
			}
		}
		return decWordIndex;
	}

	private int getWordLength(int pos) {
		return wordLength[pos];
	}

	/**
	 * Note: this is public only for testing
	 * 
	 * partial = partial match, "he" will match "whether"
	 * allwords = all words must be present in that verse
	 */

	public BitVector getWordNumbers(String query[], boolean partial, boolean allwords, int maxsteps) {

		if (query == null || query.length == 0) {
			return null;
		}

		int minlength = query[0].length();
		int maxlength = minlength;

		// System.out.println(query[0]);

		for (int i = 1; i < query.length; i++) {

			// System.out.println(query[i]);

			int l = query[i].length();
			if (l < minlength) {
				minlength = l;
			}
			if (l > maxlength) {
				maxlength = l;
			}
		}

		// System.out.println("minl " + minlength + " maxl " + maxlength);

		if (bv == null) {
			bv = new BitVector(totalWords);
		} else {
			bv.reset();
		}

		int wn = 0;
		for (int i = 0; i < totalWord.length; i++) {
			int len = wordLength[i];
			// System.out.println("words with length " + len);
			if (compressed[i] || (len < minlength)) {
				wn += totalWord[i];
				continue;
			}

			if (!partial && len > maxlength) {
				wn += totalWord[i];
				continue;
			}

			for (int j = 0; j < totalWord[i]; j++) {
				wn++;
				int index = byteacc[i] + j * len;
				String word;

				word = readString(index, len);

				String wordlc = word.toLowerCase();
				if (partial) {
					// System.out.println(wn + "word " + word);

					int bits = 0;
					for (int k = 0; k < query.length; k++) {
						if (wordlc.indexOf(query[k]) >= 0) {
							// System.out.println("partial match " + wordlc);
							bv.set(wn);
							if (!allwords) {
								break;
							}
							bits |= (1 << k);
						}
					}
					if (allwords && bits != 0) {
						// System.out.print(word + " " );
						bv.storeMatchInfo(wn, bits);
					}
				} else {
					// must be full match
					int bits = 0;
					for (int k = 0; k < query.length; k++) {
						if (query[k].equals(wordlc)) {
							bv.set(wn);
							if (!allwords) {
								break;
							}
							bits |= (1 << k);
						}
					}
					if (allwords && bits != 0) {
						bv.storeMatchInfo(wn, bits);
					}
				}
				// System.out.println(word);
			}

			if (listener != null) {
				listener.notify(i, maxsteps, -1, 0);
				if (listener.searchCanceled()) {
					return null;
				}
			}

		}

		wn = 0;

		// handle compressed case

		if (allwords && query.length > 1) {
			bv.setStartPos();
		}

		for (int i = 0; i < totalWord.length; i++) {
			int len = wordLength[i];
			if (!compressed[i]) {
				wn += totalWord[i];
				continue;
			}
			int repeat = len / 2;
			for (int j = 0; j < totalWord[i]; j++) {
				wn++;
				int index = byteacc[i] + j * len;
				int bits = 0;
				for (int r = 0; r < repeat; r++) {
					int n = Util.readShort(word_data, index + r * 2);
					if (bv.get(n) == 1) {
						bv.set(wn);
						if (!allwords) {
							break;
						}
						int _b = bv.getMatchBitsForWord(n);
						bits |= _b;
					}
				}
				if (allwords) {
					bv.storeMatchInfo(wn, bits);
				}
			}
			if (listener != null) {
				listener.notify(totalWord.length + i, maxsteps, -1, 0);
				if (listener.searchCanceled()) {
					return null;
				}
			}

		}

		if (allwords) {
			int filter = 0;
			for (int k = 0; k < query.length; k++) {
				filter |= (1 << k);
			}

			/**
			 * If word in query never appear, then the search result is empty
			 */
			int res = 0;

			for (int i = 0; i < bv.wcount; i++) {
				long l = bv.matchbits_array[i];
				res |= l;
			}

			if ((filter & res) != filter) {
				return null;
			}
			bv.setStartPos();
		}

		if (bv.countSet() == 0) {
			return null;
		}

		return bv;
	}

	int getWordPos(int wordNum) {
		int relNum = wordNum - 1;
		for (int i = 0; i < totalWord.length; i++) {
			int _totalWord = totalWord[i];
			if (relNum < _totalWord) {
				return i;
			} else {
				relNum = (relNum - _totalWord);
			}
		}
		return 0;
	}

	/**
	 * Check if the bible is byteshifted
	 * 
	 * @return true if the bible is byteshifted
	 */
	public boolean isByteShifted() {
		return (versionAttr & infByteNotShifted) == 0;
	}

	/**
	 * Check if the word number at pos is actually represents multiple words
	 */
	private boolean isCompressed(int pos) {
		return compressed[pos];
	}

	/**
	 * Check if this is a Greek bible. Using the same convention
	 * as the PalmBiblePlus, the Greek bible file name must start
	 * with 'z'
	 */
	public boolean isGreek() {
		return is_greek;
	}

	/**
	 * Check if this is a Hebrew bible. Using the same convention
	 * as the PalmBiblePlus, the Hebrew bible file name must start
	 * with 'q'
	 */
	public boolean isHebrew() {
		return is_hebrew;
	}

	/**
	 * Try to load version information from this bible. If this
	 * fails, then the bible file is not valid, or not enough
	 * memory available. To get the error reason, call <code>getFailReason()</code>
	 * 
	 * @return true if loading is OK or false if it failed
	 */
	public boolean loadVersionInfo() throws IOException {

		fail_reason = SUCCESS;

		header = pdbaccess.getHeader();

		if (header == null) {
			fail_reason = ERR_NOT_PDB_FILE;
			return false;
		}

		if (pdbaccess.isCorrupted()) {
			fail_reason = ERR_FILE_CORRUPTED;
			return false;
		}

		if (!header.getType().equals("bibl")) {
			fail_reason = ERR_NOT_BIBLE_PLUS_FILE;
			return false;
		}

		PDBRecord version = pdbaccess.readRecord(0);

		byte[] data = version.getData();

		int idx = 0;
		versionName = Util.readStringTrimZero(data, idx, 16, encoding);
		// System.out.println("Version name: ;" + versionName+";");
		idx += 16;
		versionInfo = Util.readStringTrimZero(data, idx, 128, encoding);
		// System.out.println("Info:" + versionInfo);

		idx += 128;
		sepChar = Util.readString(data, idx, 1, encoding);
		idx++;
		versionAttr = data[idx] & 0xff;
		// System.out.println("version attr " + versionAttr);

		int v = (versionAttr & infCopyProtected);
		// System.out.println("Copy protected:" + v);
		v = (versionAttr & infByteNotShifted);
		// System.out.println("Byte not shifted:" + v);
		v = (versionAttr & infRightAligned);
		// System.out.println("right aligned:" + v);

		idx++;
		wordIndex = Util.readShort(data, idx);
		// System.out.println("Word index: " + wordIndex);

		idx += 2;
		totalWordRec = Util.readShort(data, idx);
		// System.out.println("totalWordRecord: " + totalWordRec);

		if (wordIndex + totalWordRec >= header.getRecordCount()) {
			fail_reason = ERR_FILE_CORRUPTED;
			return false;
		}

		idx += 2;
		totalBooks = Util.readShort(data, idx);
		idx += 2;
		// System.out.println("totalBooks: " + totalBooks);

		if (totalBooks < 0) {
			fail_reason = ERR_FILE_CORRUPTED;
			return false;
		}

		booksinfo = new BookInfo[totalBooks];

		for (int i = 0; i < totalBooks; i++) {
			if (idx + BOOK_REC_SIZE > data.length) {
				fail_reason = ERR_FILE_CORRUPTED;
				return false;
			}

			booksinfo[i] = BookInfo.createFromData(this, data, idx, i);
			if (booksinfo[i] == null) {
				fail_reason = ERR_FILE_CORRUPTED;
				return false;
			}

			// System.out.println(booksinfo[i]);
			idx += BOOK_REC_SIZE;
		}

		pdbaccess.removeFromCache(0);

		return true;
	}

	/**
	 * load word index
	 * 
	 */
	public void loadWordIndex() throws IOException {

		if (wordIndexLoaded) {
			return;
		}

		PDBRecord r = pdbaccess.readRecord(wordIndex);
		int idx = 0;
		byte[] index_data = r.getData();
		int totalIndexes = Util.readShort(index_data, idx);
		// System.out.println("total indexes: " + totalIndexes);
		idx += 2;
		wordLength = new int[totalIndexes];
		totalWord = new int[totalIndexes];
		compressed = new boolean[totalIndexes];
		nothing = new boolean[totalIndexes];

		for (int i = 0; i < totalIndexes; i++) {
			wordLength[i] = Util.readShort(index_data, idx);
			idx += 2;
			totalWord[i] = Util.readShort(index_data, idx);
			totalWords += totalWord[i];
			idx += 2;
			compressed[i] = index_data[idx++] != 0;
			// System.out.println("len " + wordLength[i] + " totalword[" +i + "]=" + totalWord[i] + " compressed = "+compressed[i]);
			nothing[i] = index_data[idx++] != 0;
		}
		// System.out.println("all total " + totalWords);

		int totalByteAcc = 0;
		byteacc = new int[totalIndexes + 1];
		byteacc[0] = 0;
		for (int i = 1; i <= totalIndexes; i++) {
			int _totalWord = totalWord[i - 1];
			int _wordLen = wordLength[i - 1];
			totalByteAcc += _totalWord * _wordLen;
			byteacc[i] = totalByteAcc;
		}

		PDBRecord[] records = new PDBRecord[totalWordRec];
		int total_len = 0;
		for (int i = 0; i < totalWordRec; i++) {
			records[i] = pdbaccess.readRecord(wordIndex + i + 1);
			byte[] d = records[i].getData();
			total_len += d.length;
		}

		word_data = new byte[total_len];
		int l = 0;
		for (int i = 0; i < totalWordRec; i++) {
			byte[] d = records[i].getData();
			System.arraycopy(d, 0, word_data, l, d.length);
			l += d.length;
			pdbaccess.removeFromCache(wordIndex + i + 1);
		}

		wordIndexLoaded = true;
	}

	private boolean matchAllQueryToVerse(String lower_query[], int book, int chapter, int verse, boolean partial) {
		BookInfo bi = getBook(book);
		if (bi == null) {
			return false;
		}
		try {
			bi.openBook();
		} catch (IOException e) {
			return false;
		}
		String s = bi.getVerse(chapter, verse).toLowerCase();
		for (int i = 0; i < lower_query.length; i++) {

			if (s.indexOf(lower_query[i]) < 0) {
				return false;
			}
			if (!partial) {
				if (!fullMatch(lower_query[i], s)) {
					return false;
				}
			}
		}
		// System.out.println("match: " + s);
		return true;
	}

	private boolean matchAtLeastOneQueryToVerse(String lower_query[], int book, int chapter, int verse, boolean partial) {
		BookInfo bi = getBook(book);
		if (bi == null) {
			return false;
		}
		try {
			bi.openBook();
		} catch (IOException e) {
			return false;
		}
		String s = bi.getVerse(chapter, verse).toLowerCase();
		for (int i = 0; i < lower_query.length; i++) {
			if (partial) {
				if (s.indexOf(lower_query[i]) >= 0) {
					return false;
				}
			} else {
				if (fullMatch(lower_query[i], s)) {
					return true;
				}
			}
		}
		return true;
	}

	/**
	 * Try to parse a string to bookname, chapter and verse (if exist).
	 * If no chapter/verse, it will use chapter "1" and verse "1".
	 * Examples: "John 1:1" to "John" "1" "1"
	 * Or "Kisah Para Rasul 2" to "Kisah Para Rasul" "2" "1"
	 * 
	 * @param s
	 *            string to parse
	 * @return string array of 3 components (bookname, chapter, verse)
	 */
	private String[] parse(String s) {
		String[] res = new String[3];
		if (s == null || s.length() == 0) {
			return null;
		}
		MyStringTokenizer st = new MyStringTokenizer(s, " :");
		int count = st.countTokens();
		if (count == 0) {
			return null;
		}
		String[] temp = new String[count];
		String chapter = "1";
		String verse = "1";

		for (int i = 0; i < count; i++) {
			temp[i] = st.nextToken();
		}
		if (count == 1) {
			res[0] = temp[0];
			res[1] = chapter;
			res[2] = verse;
			return res;
		}

		int end = count;

		if (validInteger(temp[count - 1])) {
			if (count > 2) {
				if (validInteger(temp[count - 2])) {
					chapter = temp[count - 2];
					verse = temp[count - 1];
					end = count - 2;
				} else {
					chapter = temp[count - 1];
					end = count - 1;
				}
			} else {
				chapter = temp[count - 1];
				end = count - 1;
			}
		}
		String book = "";
		for (int i = 0; i < end; i++) {
			if (i > 0) {
				book += " ";
			}
			book += temp[i];
		}

		res[0] = book;
		res[1] = chapter;
		res[2] = verse;

		return res;
	}

	/**
	 * Parse query from user in the form of string ("John 3:16")
	 * 
	 * @param query
	 *            the query from user
	 * @return array containing [bookIndex, chapterNumber, verseNumber] or null if error
	 */
	public int[] parseQuery(String query) {
		String res[] = parse(query);
		if (res == null) {
			// System.out.println("failed parsing to parts");
			return null;
		}

		// System.out.println("part 0: "+res[0]);
		// System.out.println("part 1: "+res[1]);
		// System.out.println("part 2: "+res[2]);

		// try matching first part to the full book name

		int bookidx = -1;

		for (int i = 0; i < booksinfo.length; i++) {
			BookInfo bi = getBook(i);
			// System.out.println("'"+bi.getFullName()+"'");
			// todo: may be we should remove spaces here
			if (res[0].equalsIgnoreCase(bi.getFullName())) {
				bookidx = i;
				break;
			}
		}
		// try the short name
		if (bookidx == -1) {
			for (int i = 0; i < booksinfo.length; i++) {
				BookInfo bi = getBook(i);
				// todo: may be we should remove spaces here
				if (res[0].equalsIgnoreCase(bi.getShortName())) {
					bookidx = i;
					break;
				}
			}
		}
		// try substring of long name
		if (bookidx == -1) {
			String sname = res[0].toLowerCase();
			for (int i = 0; i < booksinfo.length; i++) {
				BookInfo bi = getBook(i);
				// todo: may be we should remove spaces here
				String fname = bi.getFullName().toLowerCase();
				if (fname.indexOf(sname) >= 0) {
					bookidx = i;
					break;
				}
			}
		}
		if (bookidx == -1) {
			return null;
		}

		int r[] = new int[3];
		r[0] = bookidx;

		try {
			r[1] = Integer.parseInt(res[1]);
			r[2] = Integer.parseInt(res[2]);
		} catch (NumberFormatException e) {
			return null;
		}

		return r;
	}

	private String readString(int index, int len) {
		if (is_greek) {
			return Util.readStringGreek(word_data, index, len);
		} else if (is_hebrew) {
			return Util.readStringHebrew(word_data, index, len);
		} else {
			return Util.readString(word_data, index, len, encoding);
		}
	}

	/**
	 * Search inside the bible.
	 * 
	 * Note: max is 32 queries because in the fast search bits of integer is used for representation
	 * 
	 * @param query
	 *            array of string to search
	 * @param range
	 *            book range (for example: new testament only)
	 * @param partial
	 *            if true, we are looking for partial match (e.g: "love" matches "love" and "lovely"
	 * @param allwords
	 *            if true, all words in query must appear in verse
	 * @param max_match
	 *            maximum match to return
	 * @return list of matches
	 */
	public Vector search(String query[],
					BookRange range,
				boolean partial,
				boolean allwords,
				int max_match) {
		if (sepChar.equals(" ")) {
			return fastsearch(query, range, partial, allwords, max_match);
		} else {
			return textSearch(query, range, partial, allwords, max_match);
		}
	}

	private void searchFinished() {
		if (listener != null) {
			listener.searchFinished();
		}
	}

	/**
	 * Set object to notify when we make a progress in the search
	 * 
	 * @param _listener
	 *            the listener
	 */
	public void setSearchProgressListener(SearchProgressListener _listener) {
		listener = _listener;
	}

	boolean supportRandomAccess() {
		return canSeek;
	}

	/**
	 * Slower version of search, but should work on any
	 * languages. Used for byteshifted version. This search uses
	 * string matching exclusively.
	 * 
	 * @param query
	 *            array of string to search
	 * @param range
	 *            book range (for example: new testament only)
	 * @param partial
	 *            if true, we are looking for partial match (e.g: "love" matches "love" and "lovely"
	 * @param allwords
	 *            if true, all words in query must appear in verse
	 * @param max_match
	 *            maximum match to return
	 * @return list of matches
	 */
	public Vector textSearch(String query[],
					BookRange range,
					boolean partial,
					boolean allwords,
					int max_match) {

		if (query == null || query.length == 0) {
			return null;
		}

		Vector result = new Vector();

		String[] lower_query = new String[query.length];

		for (int i = 0; i < query.length; i++) {
			lower_query[i] = query[i].toLowerCase();
		}

		int range_start = range.getBookIndexStart();
		int range_end = range.getBookIndexEnd();

		int maxsteps = (range_end - range_start) + 1;

		if (listener != null) {
			listener.searchStarted(maxsteps);
		}

		int result_pos = 0;

		for (int tb = range_start; tb < range_end; tb++) {

			int b;
			if (range.isSequential()) {

				b = findBookNumber(book_numbers[tb]);
				// System.out.println("tb = " + tb + "book number: " + book_numbers[tb] + " b = " + b + " " + book_names[tb]);
			} else {
				b = findBookNumber(range.getBookAt(tb));
			}

			if (b == -1) {
				continue;
			}

			BookInfo bi = booksinfo[b];

			// System.out.println("searching book " + bi.toString());

			try {
				bi.openBook();
			} catch (IOException e) {
				return null;
			}

			int stc = 1;
			if (tb == range_start) {
				stc = range.getStartChapter();
			}

			// System.out.println("searching " + stc + " to " + bi.getChapterCount());

			for (int c = stc; c <= bi.getChapterCount(); c++) {
				// System.out.println(" C= " + c);
				int stv = 1;
				if (tb == range_start && c == range.getStartChapter()) {
					stv = range.getStartVerse();
				}

				for (int v = stv; v <= bi.getVerseCount(c); v++) {
					if (allwords) {
						if (matchAllQueryToVerse(lower_query, b, c, v, partial)) {
							// System.out.println("book " + b + " chap " + c + " verse " + v);
							AddResult(result, b, c, v);
							if ((result.size() / 3) >= max_match) {
								searchFinished();
								return result;
							}
						}
					} else {
						if (matchAtLeastOneQueryToVerse(lower_query, b, c, v, partial)) {
							AddResult(result, b, c, v);
							if ((result.size() / 3) >= max_match) {
								searchFinished();
								return result;
							}
						}
					}
				}
				if (listener != null && listener.searchCanceled()) {
					searchFinished();
					return result;
				}
			}

			if (listener != null) {
				listener.notify(tb - range_start, maxsteps,
						b, result.size() / 3);
				if (listener.searchCanceled()) {
					searchFinished();
					return result;
				}
			}

		}

		searchFinished();
		return result;
	}

	/**
	 * This is just a shortcut, i really don't like too many try catch blocks
	 */
	private boolean validInteger(String s) {
		try {
			Integer.parseInt(s);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

}
