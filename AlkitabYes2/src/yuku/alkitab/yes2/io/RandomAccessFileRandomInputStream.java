package yuku.alkitab.yes2.io;

import java.io.IOException;
import java.io.RandomAccessFile;

public class RandomAccessFileRandomInputStream extends RandomInputStream {
	public static final String TAG = RandomAccessFileRandomInputStream.class.getSimpleName();

	private final RandomAccessFile f;

	public RandomAccessFileRandomInputStream(String filename) throws IOException {
		this.f = new RandomAccessFile(filename, "r");
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

	@Override public void seek(long n) throws IOException {
		f.seek(n);
	}

	@Override public long getFilePointer() throws IOException {
		return f.getFilePointer();
	}

	@Override public void close() throws IOException {
		f.close();
	}

	public RandomAccessFile getFile() {
		return f;
	}
}
