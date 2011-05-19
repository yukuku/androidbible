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
import java.util.Vector;

/**
 * This is for importing bookmark and notes from PalmBible+. Palmbible+ stores
 * bookmark in separate PDB files (not inside the bible files).
 * 
 * Palmbible+ merges the bookmark and note concept.
 * 
 */
public class BookmarkPDB {

	PDBAccess pdbaccess;

	Vector bookmarks = new Vector();

	public BookmarkPDB(PDBDataStream is) {
		pdbaccess = new PDBAccess(is);
	}

	/**
	 * Decode a PDBRecord to PDBBookmark object
	 * 
	 * @param rec
	 *            PDB record
	 * @return PDBBookmark object or null if failed
	 */
	private PDBBookmark decode(PDBRecord rec) {
		if (rec == null) {
			return null;
		}

		byte[] data = rec.getData();

		if (data.length < 6) {
			return null;
		}

		int booknumber = Util.readShort(data, 0);
		int chapter = Util.readShort(data, 2);
		int verse = Util.readShort(data, 4);
		String note = null;
		if (data.length > 6) {
			note = Util.readString(data, 6,
							data.length - 6,
							"UTF-8");
		}

		return new PDBBookmark(booknumber, chapter, verse, note);
	}

	/**
	 * Get bookmark at index i
	 * 
	 * @return PDBBookmark at index i
	 */
	public PDBBookmark getBookmark(int i) {
		return (PDBBookmark) bookmarks.elementAt(i);
	}

	/**
	 * Get the number of bookmarks
	 */
	public int getBookmarkCount() {
		return bookmarks.size();
	}

	/*
	 * Load bookmark file. if this fails, then the PDB file is not valid, or not enough memory available
	 * 
	 * @return true if everything is ok and false if not
	 */
	public boolean loadBookmarkFile() throws IOException {

		PDBHeader header = pdbaccess.getHeader();

		if (!header.getType().equals("bkmk")) {
			return false;
		}

		int reccount = header.getRecordCount();

		for (int i = 0; i < reccount; i++) {
			PDBRecord rec = pdbaccess.readRecord(i);
			PDBBookmark bm = decode(rec);
			if (bm == null) {
				bookmarks.removeAllElements();
				return false;
			}
			bookmarks.addElement(bm);
		}

		return true;
	}

	// public void close() {

	// if (pdbaccess!=null) {
	// pdbaccess.close();
	// pdbaccess = null;
	// }
	// //System.gc();
	// }
}
