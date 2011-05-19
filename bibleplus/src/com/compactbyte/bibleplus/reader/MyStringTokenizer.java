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
 * Blackberry doesn't have the StringTokenizer class. This is a simple implementation of StringTokenizer
 */
public class MyStringTokenizer {

	private String tokens[];
	private int current;

	public MyStringTokenizer(String str, String delim) {
		if (str == null || str.length() == 0) {
			return;
		}

		// compute necessary space
		int n = 0;
		int i = 0;
		while (i < str.length()) {
			// skip delim
			while (i < str.length()) {
				if (delim.indexOf(str.charAt(i)) < 0) {
					break;
				}
				i++;
			}
			if (i >= str.length()) {
				break;
			}
			// read token until delim
			while (i < str.length()) {
				if (delim.indexOf(str.charAt(i)) >= 0) {
					break;
				}
				i++;
			}
			n++;
		}
		tokens = new String[n];
		i = 0;
		n = 0;
		while (i < str.length()) {
			// skip delim
			while (i < str.length()) {
				if (delim.indexOf(str.charAt(i)) < 0) {
					break;
				}
				i++;
			}
			if (i >= str.length()) {
				break;
			}
			// read token until delim
			int start = i;
			while (i < str.length()) {
				if (delim.indexOf(str.charAt(i)) >= 0) {
					break;
				}
				i++;
			}
			tokens[n++] = str.substring(start, i);
		}

	}

	public int countTokens() {
		return tokens.length;
	}

	public String[] getTokens() {
		return tokens;
	}

	public boolean hasMoreTokens() {
		return current < tokens.length;
	}

	public String nextToken() {
		return tokens[current++];
	}

}