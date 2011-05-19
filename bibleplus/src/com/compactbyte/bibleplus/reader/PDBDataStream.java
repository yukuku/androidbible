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
 * I build this because Blackberry Java implementation doesn't support java.io.File.
 * 
 * Using this interface, we don't care about the implementation used to read the PDBFile
 * 
 */
public abstract class PDBDataStream {

	/**
	 * Can we seek backward/forward? In Blackberry OS before 5.0 we cannot go back
	 * 
	 */
	public abstract boolean canSeek();

	/**
	 * Close the stream
	 */
	public abstract void close() throws IOException;

	/**
	 * get current read position
	 * 
	 * @return current position when reading
	 */
	public abstract int getCurrentPosition();

	/**
	 * get the location of file in filesystem
	 * This is used for determining hebrew/greek file from filename
	 */
	public abstract String getPathName();

	/**
	 * get size of file
	 */
	public abstract long getSize() throws IOException;

	/**
	 * read array of data
	 * 
	 * @param data
	 *            data to read
	 */
	public abstract void read(byte[] data) throws IOException;

	/**
	 * if canSeek is false, we can still seek forward using skip
	 */
	public void seek(int position) throws IOException {
		int cpos = getCurrentPosition();
		skip(position - cpos);
	}

	/**
	 * skip bytes (forward pointer)
	 * 
	 * @param nbytes
	 *            bytes to skip
	 */
	public abstract void skip(int nbytes) throws IOException;
}
