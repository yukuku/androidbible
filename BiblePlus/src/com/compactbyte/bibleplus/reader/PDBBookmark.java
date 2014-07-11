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
 * Represents a bookmark in Palmbible+ PDB bookmark
 */
public class PDBBookmark {

	private int bookNumber;
	private int chapter;
	private int verse;
	private String note;

	public PDBBookmark(int _bookNumber,
			int _chapter, int _verse, String _note) {
		bookNumber = _bookNumber;
		chapter = _chapter;
		verse = _verse;
		note = _note;
	}

	public int getBookNumber() {
		return bookNumber;
	}

	public int getChapter() {
		return chapter;
	}

	public String getNote() {
		return note;
	}

	public int getVerse() {
		return verse;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(bookNumber).append(" "); //$NON-NLS-1$
		sb.append(chapter).append(":"); //$NON-NLS-1$
		sb.append(verse).append(" "); //$NON-NLS-1$
		sb.append(note);
		return sb.toString();
	}
}
