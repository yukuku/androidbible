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

/**
 * Bookmark object.
 * <p>
 * It stores the bookNumber, chapter, verse and excerpt It also stores bibleVersion, location and bookname Currently the logic in retrieving bookmark in the Bibleplus for
 * Blackberry is just loading the book, chapter, and verse in the curerent bible.
 * </p>
 * 
 * <p>
 * In the future, we may want to go to the last bible version (for example if the bookmark was made in NIV, and we are in KJV, we can go to the verse location in NIV instead of
 * KJV.
 * </p>
 */
public class Bookmark {

	/**
	 * Deserialize from string
	 * 
	 * @param s
	 *            string to deserialize
	 * @return Bookmark from string representation of this object
	 */
	public static Bookmark fromStringRepresentation(String s) {
		MyStringTokenizer myst = new MyStringTokenizer(s, ":");
		if (myst.countTokens() != 7) {
			return null;
		}

		String versionName = Util.unescape(myst.nextToken());
		String bibleLocation = Util.unescape(myst.nextToken());
		String bookname = Util.unescape(myst.nextToken());
		String booknumber_s = myst.nextToken();
		String chapter_s = myst.nextToken();
		String verse_s = myst.nextToken();

		int booknumber = 0;
		int chapter = 0;
		int verse = 0;

		try {
			booknumber = Integer.parseInt(booknumber_s);
			chapter = Integer.parseInt(chapter_s);
			verse = Integer.parseInt(verse_s);
		} catch (NumberFormatException e) {
			return null;
		}

		String excerpt = Util.unescape(myst.nextToken());

		return new Bookmark(versionName, bibleLocation,
					bookname, booknumber,
					chapter, verse, excerpt);
	}

	private String bibleVersion;
	private String bibleLocation;
	private String bookName;
	private int bookNumber;
	private int chapter;
	private int verse;

	private String excerpt;

	// public String getBookName() {
	// return bookName;
	// }

	Bookmark(String _bibleVersion,
			String _bibleLocation,
			String _bookName, int _bookNumber,
			int _chapter, int _verse, String _exceprt) {
		bibleVersion = _bibleVersion;
		bibleLocation = _bibleLocation;
		bookName = _bookName;
		bookNumber = _bookNumber;
		chapter = _chapter;
		verse = _verse;
		excerpt = _exceprt;
	}

	/**
	 * Check if this bookmark object is equal to another Bookmark object.
	 * This will only compare book number, chapter and verse.
	 * 
	 * @return true if this object is equal with <code>other</code> object.
	 */
	public boolean equals(Bookmark other) {
		return (bookNumber == other.bookNumber
			&& chapter == other.chapter && verse == other.verse);
	}

	/**
	 * Get book number
	 * 
	 * @return book number
	 */
	public int getBookNumber() {
		return bookNumber;
	}

	/**
	 * Get chapter number
	 * 
	 * @return chapter number
	 */
	public int getChapter() {
		return chapter;
	}

	/**
	 * Get bible File location
	 * 
	 * @return chapter number
	 */
	public String getLocation() {
		return bibleLocation;
	}

	// public String getExcerpt() {
	// return excerpt;
	// }

	/**
	 * Get verse number
	 * 
	 * @return verse number
	 */
	public int getVerse() {
		return verse;
	}

	/**
	 * Get bible version name
	 * 
	 * @return version name
	 */
	public String getVersion() {
		return bibleVersion;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(bookName).append(" ");
		sb.append(chapter).append(":");
		sb.append(verse).append(" ");
		sb.append(excerpt);
		return sb.toString();
	}

	/**
	 * Serialize to string
	 * 
	 * @return string representation of this object
	 */
	public String toStringRepresentation() {
		StringBuffer sb = new StringBuffer();
		sb.append(Util.escape(bibleVersion));
		sb.append(":");
		sb.append(Util.escape(bibleLocation));
		sb.append(":");
		sb.append(Util.escape(bookName));
		sb.append(":");
		sb.append(bookNumber);
		sb.append(":");
		sb.append(chapter);
		sb.append(":");
		sb.append(verse);
		sb.append(":");
		sb.append(Util.escape(excerpt));
		return sb.toString();
	}

}
