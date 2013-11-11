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
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a book in the bible
 * 
 * 
 */
public class BookInfo {

	/**
	 * Factory method for creating BookInfo
	 * 
	 * @param bible
	 *            the bible object
	 * @param data
	 *            array data for this object
	 * @param offset
	 *            the position of the data that should be read by this object
	 * @param book_pos
	 *            the position of the book in the bible
	 * @return BookInfo object
	 */
	static BookInfo createFromData(BiblePlusPDB bible, byte data[], int offset, int book_pos) {
		BookInfo bi = new BookInfo(bible);
		int idx = 0;
		bi.bookPosition = book_pos;
		bi.bookNum = Util.readShort(data, offset + idx);
		idx += 2;
		bi.bookIndex = Util.readShort(data, offset + idx);
		idx += 2;
		bi.totalBookRec = Util.readShort(data, offset + idx);
		if (bi.bookIndex + bi.totalBookRec > bible.getHeader().getRecordCount()) {
			return null;
		}

		idx += 2;
		bi.simpleName = new byte[8];
		System.arraycopy(data, offset + idx, bi.simpleName, 0, 8);
		idx += 8;
		bi.complexName = new byte[32];
		System.arraycopy(data, offset + idx, bi.complexName, 0, 32);
		
		return bi;
	}

	/**
	 * The Book number
	 * PDB Files usually have standard book numbering (10 = Genesis, etc)
	 * Note: some PDB files are created with non-standard book numbering
	 */
	private int bookNum;
	/*
	 * index record inside PDB file
	 */
	private int bookIndex;
	private int totalBookRec;
	/**
	 * The position of the book in the bible file.
	 * For example Matthew can be in position 0 for Bible files with new testament only
	 * But it can be position 39 for Protestant Bible with old and new testament
	 */
	private int bookPosition;
	/**
	 * The sort name of the book, for example GEN for Genesis
	 */
	private byte[] simpleName;
	/**
	 * The complete/long name of the book
	 */
	private byte[] complexName;

	/**
	 * Reference to the bible object
	 */
	private BiblePlusPDB bible;

	/**
	 * total number of chapter in this book
	 */
	int totalChapters;

	/**
	 * flag to indicate whether this book has been opened or not
	 */
	boolean bookOpened; // default initialized to false;

	/***
	 * These constants are special numbers to indicate "tags"
	 * 
	 */
	private final int bookTextType = 0xFFFF;

	private final int chapTextType = 0xFFFE;

	private final int descTextType = 0xFFFD;
	private final int versTextType = 0xFFFC;
	/**
	 * PDB Record size
	 */
	private final int RECORD_SIZE = 4096;
	private byte index_data[];

	private byte data[];

	private int totalVersesAcc[];
	private int totalChapterCharsAcc[];
	private int totalVerseCharsAcc[];
	/**
	 * Temporary StringBuffer
	 * Note: Blackberry doesn't have StringBuilder class
	 */
	// <yuku>
	// <original>
//	private StringBuffer[] sb;
	// </original>
	// </yuku>
	/**
	 * lookup table for byteshifted PDB
	 */
	private int shiftLookup[] = { 0, 3, 2, 1 };

	private int verseShiftLookup[] = { 10, 4, 6, 8 };

	/**
	 * Construct this BookInfo. This is not public, will only be called by createFromData
	 * 
	 * @param bible
	 *            object that creates this book
	 */
	private BookInfo(BiblePlusPDB bible) {
		this.bible = bible;
		// <yuku>
		// <original>
//		
//		/**
//		 * One string buffer for each "tag"
//		 */
//		sb = new StringBuffer[4];
//		
//		for (int i = 0; i < 4; i++) {
//			sb[i] = new StringBuffer();
//		}
//		
		// </original>
		// </yuku>
	}

	/**
	 * Add character separator a StringBuffer given the previous word that was appended
	 * If the word was a delimiter, then we don't append any separator
	 */
	private void addSepChar(StringBuffer sb, String word) {
		if (sb.length() > 0) {
			if (word.length() == 1) {
				if (".,?!;:-".indexOf(word.charAt(0)) < 0) { //$NON-NLS-1$
					sb.append(bible.getSepChar());
				}
			} else {
				sb.append(bible.getSepChar());
			}
		}
	}

	/**
	 * Set references to null. Not really necessary, just for
	 * debugging. If we set everything to null it makes it easy to
	 * detect in case we try to access something that we've thrown
	 * away (there will be null pointer exception).
	 */
	void close() {
		index_data = null;
		data = null;
		totalVersesAcc = null;
		totalChapterCharsAcc = null;
		totalVerseCharsAcc = null;
	}

	/**
	 * Get book numbefr. PDB Files usually have standard book numbering (10 = Genesis, etc)
	 * Note: some PDB files are created with non-standard book numbering.
	 * 
	 * @return the book number
	 */
	public int getBookNumber() {
		return bookNum;
	}

	/**
	 * The position of the book in the bible file.
	 * For example Matthew can be in position 0 for Bible files with new testament only
	 * But it can be position 39 for Protestant Bible with old and new testament
	 * 
	 * @return book position
	 */
	public int getBookPosition() {
		return bookPosition;
	}

	/**
	 * Get number of chapters in this book
	 * 
	 * @return chapter count
	 */
	public int getChapterCount() {
		return totalChapters;
	}

	/**
	 * Get complete verse content for this chapter/verse
	 * 
	 * @param chapter
	 *            chapter number
	 * @param verse
	 *            verse number
	 * @return null on error (book not opened, invalid
	 *         chapter/verse) or the content complete content of the verse
	 *         (for each "Tags"). Index 0 contains verse (always exist),
	 *         index 1 contains pericope title (may not exist, if not
	 *         exist it will be empty string), Index 2 contains chapter
	 *         title (if exist, this will only appear in verse 1 of a
	 *         chapter), Index 3 contains book title (if exists, this will
	 *         only appear in chapter 1 verse 1 of a book.
	 * 
	 */
	public String[] getCompleteVerse(int chapter, int verse) {

		if (!bookOpened) {
			return null;
		}

		if (chapter < 0 || chapter > getChapterCount()) {
			return null;
		}

		if (verse < 0 || verse > getVerseCount(chapter)) {
			return null;
		}

		if (bible.isByteShifted()) {
			return getVerseByteShifted(chapter, verse);
		}

		// <yuku>
		// <original>
//		for (int i = 0; i < 4; i++) {
//			sb[i].setLength(0);
//		}
		// </original>
		// </yuku>
		
		// <yuku>
		@SuppressWarnings("unchecked")
		ArrayList<String>[] words = new ArrayList[4];
		words[0] = new ArrayList<String>();
		words[1] = new ArrayList<String>();
		words[2] = new ArrayList<String>();
		words[3] = new ArrayList<String>();
		// </yuku>
		

		int sbpos = 0; // verse

		int verseStart = getVerseStart(chapter, verse);
		// System.out.println("versestart " + verseStart);

		int verseLength = getVerseLength(chapter, verse);

		// System.out.println("Start " + verseStart + " length " + verseLength);

		int compStart = verseStart * 2;

		int idx = compStart;

		for (int i = 0; i < verseLength; i++) {
			int decWordNum = (data[idx] & 0xff) * 256 + (data[idx + 1] & 0xff);
			idx += 2;
			// System.out.println("decWordNum " + decWordNum);
			int pos = bible.getWordPos(decWordNum);
			// System.out.println("wordpos " + pos);
			// System.out.println("wordlength " + bible.getWordLength(pos));
			int wordIndex = bible.getWordIndex(pos, decWordNum);
			// System.out.println("wordindex " + wordIndex);
			int[] r = bible.getRepeat(pos, decWordNum);
			if (r != null) {
				for (int j = 0; j < r.length; j++) {
					// if (r[j]<0) break;
					// System.out.print("rj " + r[j]+" ");

					if (r[j] == bookTextType || r[j] == chapTextType ||
						r[j] == descTextType || r[j] == versTextType) {
						sbpos = r[j] - versTextType;
						// System.out.print("switch sbpos " + sbpos+" ");
						continue;
					}

					String word = bible.getWord(r[j]);

					// System.out.println("Word " + word);
					// <yuku>
					// <original>
					// addSepChar(sb[sbpos], word);
					// sb[sbpos].append(word);
					// </original>
					words[sbpos].add(word);
					// </yuku>
				}
			} else {
				String word = bible.getWord(decWordNum);
				// <yuku>
				// <original>
				// addSepChar(sb[sbpos], word);
				// sb[sbpos].append(word);
				// </original>
				words[sbpos].add(word);
				// </yuku>
				// System.out.println("r is null" + word);
			}
		}
		
		// <yuku>
		String sepChar = bible.getSepChar();
		String[] res = new String[4];
		res[0] = stringFromWords(words[0], sepChar);
		res[1] = stringFromWords(words[1], sepChar);
		res[2] = stringFromWords(words[2], sepChar);
		res[3] = stringFromWords(words[3], sepChar);
		// </yuku>

		// <yuku>
		// <original>
		// return sb;
		// </original>
		return res;
		// </yuku>
	}

	// <yuku>
	private String stringFromWords(List<String> words, String sepChar) {
		if (words == null) return ""; //$NON-NLS-1$
		if (words.size() == 0) return ""; //$NON-NLS-1$
		
		StringBuilder sb = new StringBuilder();
		
		// put spaces in all, except:
		// word + )
		// word + ]
		// word + }
		// word + .
		// word + ,
		// word + :
		// word + ;
		// word + ?
		// word + !
		// word + -
		// word + [cjk 0x2e80..0x9fff]
		// ( + word
		// [ + word
		// { + word
		// - + word
		// [cjk 0x2e80..0x9fff] + word
		String prev = null;
		String cur = null;
		for (int i = 0, len = words.size(); i < len; i++) {
			cur = words.get(i);

			boolean sep = false;
			if (prev == null || prev.length() == 0) {
				// no space
			} else {
				char lastPrev = prev.charAt(prev.length() - 1);
				if (lastPrev == '(' || lastPrev == '[' || lastPrev == '{' || lastPrev == '-' || (lastPrev >= 0x2e80 && lastPrev <= 0x9fff)) {
					// no space
				} else if (cur.length() == 0) {
					// no space too, exceptional case
				} else {
					char firstCur = cur.charAt(0);
					if (")]}.,:;?!-".indexOf(firstCur) >= 0 || (firstCur >= 0x2e80 && firstCur <= 0x9fff)) { //$NON-NLS-1$
						// no space
					} else {
						sep = true;
					}
				}
			}
			
			if (sep) {
				sb.append(sepChar);
			}
			sb.append(cur);
			
			prev = cur;
		}
		
		return sb.toString();
	}
	// </yuku>

	/**
	 * Get raw data for this book
	 * 
	 * @return raw data bytes of this book
	 */
	byte[] getData() {
		return data;
	}

	/**
	 * Get the complete name of this book
	 * 
	 * @return The complete/long name of this book
	 */
	public String getFullName() {
		return Util.readStringTrimZeroWithMaybeGreekHebrew(complexName, 0, complexName.length, bible);
	}

	/**
	 * Get the short name of this book
	 * 
	 * @return The sort name of this book, for example GEN for Genesis
	 */
	public String getShortName() {
		return Util.readStringTrimZeroWithMaybeGreekHebrew(simpleName, 0, simpleName.length, bible);
	}

	/**
	 * Get the verse portion only (no chapter title/book title)
	 * 
	 * @param chapter
	 *            chapter number
	 * @param verse
	 *            verse number
	 * @return verse as string
	 */
	public String getVerse(int chapter, int verse) {
		String[] sb = getCompleteVerse(chapter, verse);
		if (sb == null) {
			return null;
		}
		return sb[0];
	}

	/**
	 * This is the byteshifted version of getCompleteVerse. It
	 * will be called by getCompleteVerse when the bible file is byte shifted
	 * 
	 * @param chapter
	 *            chapter number
	 * @param verse
	 *            verse number
	 * @return array of StringBuffer for each tag
	 */
	String[] getVerseByteShifted(int chapter, int verse) {
		int verseStart = getVerseStart(chapter, verse);
		int verseLength = getVerseLength(chapter, verse);

		// <yuku>
		// <original>
//		for (int i = 0; i < 4; i++) {
//			sb[i].setLength(0);
//		}
		// </original>
		// </yuku>

		// System.out.println("Start " + verseStart + " length " + verseLength);

		int decShift = 0;
		int decValueBuffer[] = new int[3];

		int compStart;
		compStart = verseStart * 7 / 4;
		decShift = shiftLookup[verseStart * 7 % 4];

		int idx = compStart;

		switch (decShift) {
		case 1:
			decValueBuffer[1] = data[idx++];
			break;
		case 2:
		case 3:
			decValueBuffer[2] = data[idx++];
			break;
		default:
			;
		}

		// <yuku>
		@SuppressWarnings("unchecked")
		ArrayList<String>[] words = new ArrayList[4];
		words[0] = new ArrayList<String>();
		words[1] = new ArrayList<String>();
		words[2] = new ArrayList<String>();
		words[3] = new ArrayList<String>();
		// </yuku>

		int sbpos = 0;

		ayam: for (int i = 0; i < verseLength; i++) {
			if (idx >= data.length) {Log.d("idx OOB", getFullName() + " c" + chapter + " v" + verse + " i=" + i + " verseLength=" + verseLength + " data.length=" + data.length + " idx=" + idx); break ayam;}
			
			switch (decShift) {
			case 0:
				decValueBuffer[0] = data[idx++] & 0xff;
				if (idx >= data.length) {Log.d("idx OOB", getFullName() + " c" + chapter + " v" + verse + " i=" + i + " verseLength=" + verseLength + " data.length=" + data.length + " idx=" + idx); break ayam;}
				decValueBuffer[1] = data[idx++] & 0xff;
				decValueBuffer[2] = 0;
				break;
			case 1:
				decValueBuffer[0] = decValueBuffer[1];
				decValueBuffer[1] = data[idx++] & 0xff;
				if (idx >= data.length) {Log.d("idx OOB", getFullName() + " c" + chapter + " v" + verse + " i=" + i + " verseLength=" + verseLength + " data.length=" + data.length + " idx=" + idx); break ayam;}
				decValueBuffer[2] = data[idx++] & 0xff;
				break;
			case 2:
				decValueBuffer[0] = decValueBuffer[2];
				decValueBuffer[1] = data[idx++] & 0xff;
				if (idx >= data.length) {Log.d("idx OOB", getFullName() + " c" + chapter + " v" + verse + " i=" + i + " verseLength=" + verseLength + " data.length=" + data.length + " idx=" + idx); break ayam;}
				decValueBuffer[2] = data[idx++] & 0xff;
				break;
			case 3:
				decValueBuffer[0] = decValueBuffer[2];
				decValueBuffer[1] = data[idx++] & 0xff;
				decValueBuffer[2] = 0;
				break;
			default:
				;
			}

			int value = decValueBuffer[0] << 16 | decValueBuffer[1] << 8 |
				decValueBuffer[2];
			value = value >> verseShiftLookup[decShift];
			value = value & 0x3FFF;
			decShift++;

			if (decShift == 4) {
				decShift = 0;
			}

			if (value > 0x3FF0) {
				value |= 0xC000;
			}

			int decWordNum = value;
			int pos = bible.getWordPos(decWordNum);
			int wordIndex = bible.getWordIndex(pos, decWordNum);
			int[] r = bible.getRepeat(pos, decWordNum);
			if (r != null) {
				for (int j = 0; j < r.length; j++) {
					if (r[j] > 0x3FF0) {
						r[j] |= 0xC000;
					}

					if (r[j] == bookTextType || r[j] == chapTextType ||
						r[j] == descTextType || r[j] == versTextType) {
						sbpos = r[j] - versTextType;
						continue;
					}

					String word = bible.getWord(r[j]);

					// System.out.println("Word " + word);
					// <yuku>
					// <original>
					// addSepChar(sb[sbpos], word);
					// sb[sbpos].append(word);
					// </original>
					words[sbpos].add(word);
					// </yuku>
				}
			} else {
				String word = bible.getWord(decWordNum);
				// <yuku>
				// <original>
				// addSepChar(sb[sbpos], word);
				// sb[sbpos].append(word);
				// </original>
				words[sbpos].add(word);
				// </yuku>
				// System.out.println("r is null" + word);
			}
		}

		// <yuku>
		String sepChar = bible.getSepChar();
		String[] res = new String[4];
		res[0] = stringFromWords(words[0], sepChar);
		res[1] = stringFromWords(words[1], sepChar);
		res[2] = stringFromWords(words[2], sepChar);
		res[3] = stringFromWords(words[3], sepChar);
		// </yuku>

		// <yuku>
		// <original>
		// return sb;
		// </original>
		return res;
		// </yuku>
	}

	/**
	 * Get the total number of verse in a chapter
	 * 
	 * @return total number of verse in a chapter
	 */
	public int getVerseCount(int chapter) {
		int v1 = totalVersesAcc[chapter - 1];
		int v2 = chapter == 1 ? 0 : totalVersesAcc[chapter - 2];
		return v1 - v2;
	}

	/**
	 * Get the total number of "word number" in a verse
	 * A "word number" can represent multiple words
	 * 
	 * @param chapter
	 *            chapter number
	 * @param verse
	 *            verse number
	 * @return the length/size of "word number"
	 */
	int getVerseLength(int chapter, int verse) {
		int verseAcc = (chapter == 1 ? 0 : totalVersesAcc[chapter - 2]) + verse;
		int verseLength;
		if (verse > 1) {
			verseLength = vlen(verseAcc - 1);
		} else {
			verseLength = verseAcc == 0 ? 0 : totalVerseCharsAcc[verseAcc - 1];
		}
		return verseLength;
	}

	/**
	 * Get the start position of a verse
	 * This is not public, to be called by BiblePlusPDB for fastsearch
	 * 
	 * @param chapter
	 *            chapter number
	 * @param verse
	 *            verse number
	 */
	int getVerseStart(int chapter, int verse) {
		// System.out.println("vstart = " + (chapter==1?0:totalVersesAcc[chapter - 2]));
		int verseAcc = (chapter == 1 ? 0 : totalVersesAcc[chapter - 2]) + verse;
		// System.out.println("verseacc = " + verseAcc);
		int verseStart = chapter == 0 ? 0 : totalChapterCharsAcc[chapter - 1];
		// System.out.println("versestart1: " + verseStart);

		if (verse > 1) {
			verseStart += verseAcc == 1 ? 0 : totalVerseCharsAcc[verseAcc - 2];
		}
		// System.out.println("versestart2: " + verseStart);

		return verseStart;
	}
	
	/**
	 * Try to open this book (load verse data for this book)
	 * 
	 * @throws IOException
	 *             in case of error in reading the book
	 */
	public void openBook() throws IOException {

		if (bookOpened) {
			return;
		}

		if (!bible.supportRandomAccess()) {
			/*
			 * Blackberry OS before 5.0 can not access randomly.
			 * if we don't have random access
			 * must make sure all previous books have been opened
			 * the good way to do it is to load all previous books in main thread, and make progress bar
			 * this is to prevent error
			 */
			for (int i = 0; i < bookPosition; i++) {
				BookInfo bi = bible.getBook(i);
				bi.openBook();
			}
		}

		PDBAccess access = bible.getPDBAccess();

		PDBRecord r = access.readRecord(bookIndex);
		index_data = r.getData();

		// Util.dumpBytes(index_data);

		PDBRecord[] records = new PDBRecord[totalBookRec];
		int total_len = 0;
		for (int i = 0; i < totalBookRec; i++) {
			records[i] = access.readRecord(bookIndex + i + 1);
			byte[] d = records[i].getData();
			// System.out.println("d = "+ d.length);
			total_len += d.length;
		}
		data = new byte[total_len];
		for (int i = 0; i < totalBookRec; i++) {
			byte[] d = records[i].getData();
			System.arraycopy(d, 0, data, i * RECORD_SIZE, d.length);
			access.removeFromCache(bookIndex + i + 1);
		}

		totalChapters = Util.readShort(index_data, 0);

		// System.out.println("total chapters: " + totalChapters);

		totalVersesAcc = new int[totalChapters];

		int offs = 2;

		for (int i = 0; i < totalChapters; i++) {
			totalVersesAcc[i] = Util.readShort(index_data, offs);
			// System.out.println("totalVersesAcc " + totalVersesAcc[i]);
			offs += 2;
		}

		totalChapterCharsAcc = new int[totalChapters];

		for (int i = 0; i < totalChapters; i++) {
			totalChapterCharsAcc[i] = Util.readInt(index_data, offs);
			offs += 4;
		}

		totalVerseCharsAcc = new int[(index_data.length - offs) / 2];

		for (int i = 0; offs < index_data.length; i++) {
			totalVerseCharsAcc[i] = Util.readShort(index_data, offs);
			offs += 2;
		}

		// System.out.println("total length " + total_len + " total chapter " + totalChapters);

		bookOpened = true;

	}

	@Override
	public String toString() {
		return getFullName();
	}

	/**
	 * Try to open a book
	 * 
	 * @return true if the book is opened and false on error
	 */
	public boolean tryOpenBook() {
		try {
			openBook();
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	private int vlen(int index) {
		// System.out.println("vlen = " + index);
		int v1 = totalVerseCharsAcc[index];
		int v2 = index == 0 ? 0 : totalVerseCharsAcc[index - 1];
		int diff = v1 - v2;
		if (diff < 0) {
			return 0;
		}
		return diff;
	}


    public static class Log {
        public static void d(String tag, String msg) {
            System.out.println(tag + " " + msg);
        }
    }
}
