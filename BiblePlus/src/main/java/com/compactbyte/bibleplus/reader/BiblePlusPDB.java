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

import java.io.IOException;

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

	private byte[] versionName;
	private byte[] versionInfo;
	private byte[] sepChar;
	private int versionAttr;
	private int wordIndex;
	private int totalWordRec;
	private PDBHeader header;

	/**
	 * Default encoding used to read strings
	 * Note: Blackberry only supports very limited encoding
	 */
	private String encoding = "UTF-8";

	private boolean wordIndexLoaded; // initialized with false;

	private final static int infByteNotShifted = 0x02;

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

	private boolean is_greek = false;

	private boolean is_hebrew = false;

	private byte word_data[];

	private int[] wordLength;

	private int[] totalWord;

	private boolean[] compressed;

	private boolean[] nothing;

	private int byteacc[];

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
	 * Cleanup object. Only used for debugging.
	 */
	public void close() throws IOException {

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

	public void setEncoding(String _encoding) {
		encoding = _encoding;
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
		/*
	  Blackberry doesn't have 'File.separator' so to be generic
	  we will ask the user of this class to set it for us

	 */
		final String pathSeparator = "/";
		int pos = pathName.lastIndexOf(pathSeparator.charAt(0));
		final String filenamepart;
		if (pos < 0) {
			return;
		}
		filenamepart = pathName.substring(pos + 1);
		is_greek = filenamepart.startsWith("z");
		is_hebrew = filenamepart.startsWith("q");
	}

	/**
	 * Get PDB Header
	 */
	public PDBHeader getHeader() {
		return header;
	}

	PDBAccess getPDBAccess() {
		return pdbaccess;
	}

	int[] getRepeat(int pos, int wordNum) {

		int repeat;
		int[] result;
		// System.out.println("word repeat----");
		if (wordNum < 0xFFF0) {
			boolean compressed = pos == 0 || isCompressed(pos);
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
		return Util.readStringWithMaybeGreekHebrew(sepChar, 0, sepChar.length, this);
	}

	/**
	 * Get the version name of this bible
	 */
	public String getVersionName() {
		return Util.readStringTrimZeroWithMaybeGreekHebrew(versionName, 0, versionName.length, this);
	}
	
	public String getVersionInfo() {
		return Util.readStringTrimZeroWithMaybeGreekHebrew(versionInfo, 0, versionInfo.length, this);
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
		versionName = new byte[16];
		System.arraycopy(data, idx, versionName, 0, 16);
		// System.out.println("Version name: ;" + versionName+";");
		idx += 16;
		versionInfo = new byte[128];
		System.arraycopy(data, idx, versionInfo, 0, 128);
		// System.out.println("Info:" + versionInfo);

		idx += 128;
		sepChar = new byte[1];
		System.arraycopy(data, idx, sepChar, 0, 1);
		idx++;
		versionAttr = data[idx] & 0xff;
		// System.out.println("version attr " + versionAttr);

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
		final int totalBooks = Util.readShort(data, idx);
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

	private String readString(int index, int len) {
		if (is_greek) {
			return Util.readStringGreek(word_data, index, len);
		} else if (is_hebrew) {
			return Util.readStringHebrew(word_data, index, len);
		} else {
			return Util.readString(word_data, index, len, encoding);
		}
	}

	boolean supportRandomAccess() {
		return canSeek;
	}

}
