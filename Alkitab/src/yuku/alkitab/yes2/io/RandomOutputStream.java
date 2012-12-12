package yuku.alkitab.yes2.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public class RandomOutputStream extends OutputStream {
	private final RandomAccessFile f;

	public RandomOutputStream(RandomAccessFile f) {
		this.f = f;
	}

	@Override
	public void write(int b) throws IOException {
		f.write(b);
	}
	
	@Override
	public void write(byte[] buffer) throws IOException {
		f.write(buffer);
	}
	
	@Override
	public void write(byte[] buffer, int offset, int count) throws IOException {
		f.write(buffer, offset, count);
	}
}
