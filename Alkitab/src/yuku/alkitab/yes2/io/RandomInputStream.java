package yuku.alkitab.yes2.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class RandomInputStream extends InputStream {
	private final RandomAccessFile f;

	public RandomInputStream(RandomAccessFile f) {
		this.f = f;
	}

	@Override public int read() throws IOException {
		return f.read();
	}

	@Override public int read(byte[] buffer) throws IOException {
		return f.read(buffer);
	}

	@Override public int read(byte[] buffer, int offset, int length) throws IOException {
		return f.read(buffer, offset, length);
	}

	@Override public long skip(long n) throws IOException {
		return f.skipBytes((int) n);
	}
	
	public void seek(long n) throws IOException {
		f.seek(n);
	}
	
	public long getFilePointer() throws IOException {
		return f.getFilePointer();
	}
	
	@Override public void close() throws IOException {
		f.close();
	}
}
