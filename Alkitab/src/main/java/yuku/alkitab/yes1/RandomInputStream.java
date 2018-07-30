package yuku.alkitab.yes1;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class RandomInputStream extends InputStream {
	private RandomAccessFile f;

	public RandomInputStream(RandomAccessFile f) {
		this.f = f;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return f.read(b);
	}

	@Override
	public int read(byte[] b, int offset, int length) throws IOException {
		return f.read(b, offset, length);
	}

	@Override
	public int read() throws IOException {
		return f.read();
	}

	@Override
	public long skip(long n) throws IOException {
		return f.skipBytes((int) n);
	}
}
