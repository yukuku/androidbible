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
 * Represents range of books in a bible. Some predefined range exists in BookRanges (such as new testament, old testament)
 */
final public class BookRange {

	/* the range is sequential */
	final static int RANGE_SEQUENTIAL = 0;
	/* the range is a list (for example for deuterocanonical books) */
	final static int RANGE_LIST = 1;

	private int type;
	private String name;
	private int index_start;
	private int index_end;

	private int start_chapter;
	private int start_verse;

	private int[] list;

	BookRange(int _index_start,
			int _index_end) {
		type = RANGE_SEQUENTIAL;
		index_start = _index_start;
		index_end = _index_end;
		start_chapter = 1;
		start_verse = 1;
	}

	BookRange(int[] _list) {
		index_start = 0;
		index_end = _list.length;
		list = _list;
	}

	int getBookAt(int i) {
		return list[i];
	}

	public int getBookIndexEnd() {
		return index_end;
	}

	public int getBookIndexStart() {
		return index_start;
	}

	int getStartChapter() {
		return start_chapter;
	}

	int getStartVerse() {
		return start_verse;
	}

	boolean isSequential() {
		return type == RANGE_SEQUENTIAL;
	}

	public void setName(String _name) {
		name = _name;
	}

	public void setStart(int _start, int _chapter, int _verse) {
		index_start = _start;
		start_chapter = _chapter;
		start_verse = _verse;
	}

	@Override
	public String toString() {
		return name;
	}

}
