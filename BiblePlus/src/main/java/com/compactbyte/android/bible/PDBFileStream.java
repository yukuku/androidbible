package com.compactbyte.android.bible;

import com.compactbyte.bibleplus.reader.PDBDataStream;

import java.io.IOException;
import java.io.RandomAccessFile;

public class PDBFileStream extends PDBDataStream {

	RandomAccessFile fis;
	int pos;

	@Override public int getCurrentPosition() {
		return pos;
	}

	String filename;
	
	public PDBFileStream(String _filename) throws IOException  {
		filename = _filename;
		fis = new RandomAccessFile(filename, "r");
		pos = 0;
	}
	
	@Override public void read(byte[]data) throws IOException {
		fis.read(data);
		pos += data.length;
	}

	@Override public void seek(int position)  throws IOException {
		fis.seek(position);
		pos = position;
	}

	@Override public void skip(int nbytes)  throws IOException {
		seek(pos + nbytes);
	}

	@Override public boolean canSeek() {
		return true;
	}

	@Override public void close() throws IOException {
		fis.close();
	}

	@Override public long getSize() throws IOException {
		return fis.length();
	}

	@Override public String getPathName() {
		return filename;
	}
}
