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

import java.util.*;

public class Util {

	static char[] hebrewtab;

	static char[] greektab;

	public static StringBuffer addField(StringBuffer sb, int i) {
		return sb.append(i).append(":"); //$NON-NLS-1$
	}

	public static StringBuffer addField(StringBuffer sb, String s) {
		return sb.append(Util.escape(s)).append(":"); //$NON-NLS-1$
	}

	public static StringBuffer addLastField(StringBuffer sb, int i) {
		return sb.append(i);
	}

	public static StringBuffer addLastField(StringBuffer sb, String s) {
		return sb.append(Util.escape(s));
	}

	public static int binarySearch(int[] array, int el) {
		return Arrays.binarySearch(array, el);
	}

	public static String escape(String s) {
		return Util.escape(s, '%', ":\r\n"); //$NON-NLS-1$
	}

	public static String escape(String s, char prefix, String charsToReplace) {
		if (s == null || charsToReplace == null) {
			return null;
		}
		StringBuilder res = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == prefix || charsToReplace.indexOf(c) >= 0) {
				res.append(prefix);
				int v = c;
				String vs = Integer.toHexString(v);
				if (vs.length() < 4) {
					res.append("0000".substring(vs.length())); //$NON-NLS-1$
				}
				res.append(vs);

			} else {
				res.append(c);
			}
		}
		return res.toString();
	}

	/*
	 * public static void dumpBytes(byte[] data) {
	 * for (int i = 0; i < data.length; i++) {
	 * if (i>0 && (i%16==0)) {
	 * // System.out.println();
	 * }
	 * String s= Integer.toHexString(data[i] & 0xff);
	 * if (s.length()==1)
	 * s = "0" + s;
	 * // System.out.print(s + " ");
	 * }
	 * //System.out.println();
	 * }
	 */

	public static int readInt(byte[] data, int offs) {
		return (((data[offs] & 0xff) << 24) |
			((data[offs + 1] & 0xff) << 16) |
			((data[offs + 2] & 0xff) << 8) | (data[offs + 3] & 0xff));
	}

	public static int readShort(byte[] data, int offs) {
		return ((data[offs] & 0xff) << 8) | (data[offs + 1] & 0xff);
	}

	public static String readString(byte[] data, int offs, int length, String encoding) {
		try {
			String s = new String(data, offs, length, encoding);
//			byte[] b = new byte[length];
//			System.arraycopy(data, offs, b, 0, length);
//			Log.d("readString", (s.length() > 20? s.substring(0, 20): s) + " encoding " + encoding + " from " + Arrays.toString(b));
			return s;
		} catch (java.io.UnsupportedEncodingException e) {
			return readStringISO8859_1(data, offs, length);
		}
	}

	public static String readStringGreek(byte[] data, int offs, int length) {
		return readStringWithTable(data, offs, length, greektab);
	}

	public static String readStringHebrew(byte[] data, int offs, int length) {
		return readStringWithTable(data, offs, length, hebrewtab);
	}
	
	private static String readStringWithTable(byte[] data, int offs, int length, char[] tab) {
		char[] s = new char[length];
		for (int i = 0; i < length; i++) {
			s[i] = tab[data[offs+i] & 0xff];
		}
		return new String(s);
	}

	public static String readStringISO8859_1(byte[] data, int offs, int length) {
		try {
			return new String(data, offs, length, "ISO-8859-1"); //$NON-NLS-1$
		} catch (java.io.UnsupportedEncodingException e) {

			return ""; //$NON-NLS-1$
		}
	}

	public static String readStringTrimZeroWithMaybeGreekHebrew(byte[] data, int offs, int length, BiblePlusPDB bible) {
		if (bible.isGreek()) {
			return readStringTrimZeroWithTable(data, offs, length, greektab);
		} else if (bible.isHebrew()) {
			return readStringTrimZeroWithTable(data, offs, length, hebrewtab);
		} else {
			return readStringTrimZero(data, offs, length, bible.getEncoding());
		}
	}
	
	public static String readStringWithMaybeGreekHebrew(byte[] data, int offs, int length, BiblePlusPDB bible) {
		if (bible.isGreek()) {
			return readStringWithTable(data, offs, length, greektab);
		} else if (bible.isHebrew()) {
			return readStringWithTable(data, offs, length, hebrewtab);
		} else {
			return readString(data, offs, length, bible.getEncoding());
		}
	}
	
	public static String readStringTrimZeroWithTable(byte[] data, int offs, int length, char[] tab) {
		int i = offs + length - 1;
		while (i >= offs) {
			if (data[i] != 0) {
				break;
			}
			i--;
		}
		int len = i - offs + 1;
		if (len < 0) {
			return ""; //$NON-NLS-1$
		}
		
		return readStringWithTable(data, offs, len, tab);
	}
	
	public static String readStringTrimZero(byte[] data, int offs, int length, String encoding) {
		try {
			int i = offs + length - 1;
			while (i >= offs) {
				if (data[i] != 0) {
					break;
				}
				i--;
			}
			int len = i - offs + 1;
			if (len < 0) {
				return ""; //$NON-NLS-1$
			}
			
			String s;
						
			s = new String(data, offs, len, encoding);
			
//			byte[] b = new byte[len];
//			System.arraycopy(data, offs, b, 0, len);
//			Log.d("readString(TZ)", (s.length() > 20? s.substring(0, 20): s) + " encoding " + encoding + " from " + dumpByteArray(b));

			return s;
		} catch (java.io.UnsupportedEncodingException e) {
			return ""; //$NON-NLS-1$
		}
	}
	
	static String dumpByteArray(byte[] ba) {
		StringBuilder sb = new StringBuilder();
		for (byte b: ba) {
			sb.append("0x").append(Integer.toHexString(0xff & b)).append(' '); //$NON-NLS-1$
		}
		return sb.toString();
	}

	public static void setTables(char[] _hebrewtab, char[] _greektab) {
		hebrewtab = _hebrewtab;
		greektab = _greektab;
	}

	public static String unescape(String s) {
		return Util.unescape(s, '%');
	}

	public static String unescape(String s, char prefix) {
		if (s == null) {
			return null;
		}
		StringBuilder res = new StringBuilder();
		int i = 0;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c == prefix) {
				if (i + 4 >= s.length()) {
					res.append(c);
				} else {
					String vs = s.substring(i + 1, i + 5);
					try {
						int v = Integer.parseInt(vs, 16);
						char cn = (char) v;
						res.append(cn);
						i += 5;
						continue;
					} catch (NumberFormatException e) {
						res.append(c);
					}
				}
			} else {
				res.append(c);
			}
			i++;
		}
		return res.toString();
	}

}
