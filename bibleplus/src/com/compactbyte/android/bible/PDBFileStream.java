package com.compactbyte.android.bible;

import java.io.*;

import com.compactbyte.bibleplus.reader.PDBDataStream;

public class PDBFileStream extends PDBDataStream {

	RandomAccessFile fis;
	int pos;

	public int getCurrentPosition() {
		return pos;
	}

	String filename;
	
	public PDBFileStream(String _filename) throws IOException  {
		filename = _filename;
		fis = new RandomAccessFile(filename, "r");
		pos = 0;
	}
	
	public void read(byte[]data) throws IOException {
		fis.read(data);
		pos += data.length;
	}

	public void seek(int position)  throws IOException {
		fis.seek(position);
		pos = position;
	}

	public void skip(int nbytes)  throws IOException {
		seek(pos + nbytes);
	}

	public boolean canSeek() {
		return true;
	}

	public void close() throws IOException {
		fis.close();
	}

	public long getSize() throws IOException {
		return fis.length();
	}

	public String getPathName() {
		return filename;
	}
}
