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

final public class BookRanges {

	public final static int ALL_BOOKS_FROM_THIS_POINT = 0;
	public final static int ALL_BOOKS = 1;
	public final static int OLD_TESTAMENT = 2;
	public final static int NEW_TESTAMENT = 3;
	public final static int DEUTEROCANONICAL = 4;
	public final static int PENTATEUCH = 5;
	public final static int HISTORICAL = 6;
	public final static int WISDOM = 7;
	public final static int MAJOR_PROPHETS = 8;
	public final static int MINOR_PROPHETS = 9;
	public final static int GOSPELS = 10;
	public final static int APOSTOLIC_HISTORY = 11;
	public final static int PAULINE_EPISTLES = 12;
	public final static int GENERAL_EPISTLES = 13;
	public final static int APOCALYPSE = 14;
	public final static int APOCRYPHA = 15;

	private BookRange[] ranges;

	private static BookRanges _instance;

	public static BookRange getRange(int n) {
		BookRanges br = instance();
		return br.ranges[n];
	}

	// public int getRangeCount() {
	// return ranges.length;
	// }

	static BookRanges instance() {
		if (_instance == null) {
			_instance = new BookRanges();
		}
		return _instance;
	}

	private BookRanges() {
		ranges = new BookRange[APOCRYPHA + 1];
		// call setStart for this
		ranges[ALL_BOOKS_FROM_THIS_POINT] = new BookRange(0, 76);
		ranges[ALL_BOOKS] = new BookRange(0, 76);
		ranges[OLD_TESTAMENT] = new BookRange(0, 47);
		ranges[NEW_TESTAMENT] = new BookRange(47, 76);
		int[] _deuteoricanonical = new int[] { 16, 17, 18, 20, 21, 27, 28, 32 };
		ranges[DEUTEROCANONICAL] = new BookRange(_deuteoricanonical);
		ranges[PENTATEUCH] = new BookRange(0, 6);
		/* TODO: fix these ranges */
		ranges[HISTORICAL] = new BookRange(0, 6);
		ranges[WISDOM] = new BookRange(0, 6);
		ranges[MAJOR_PROPHETS] = new BookRange(0, 6);
		ranges[MINOR_PROPHETS] = new BookRange(0, 6);
		ranges[GOSPELS] = new BookRange(0, 6);
		ranges[APOSTOLIC_HISTORY] = new BookRange(0, 6);
		ranges[PAULINE_EPISTLES] = new BookRange(0, 6);
		ranges[GENERAL_EPISTLES] = new BookRange(0, 6);
		ranges[APOCALYPSE] = new BookRange(0, 6);
		ranges[APOCRYPHA] = new BookRange(0, 6);

	}

}
