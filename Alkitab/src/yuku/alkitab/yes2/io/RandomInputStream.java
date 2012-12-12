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
}
