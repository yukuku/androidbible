package yuku.alkitab.yes2.io;

import java.io.IOException;
import java.io.RandomAccessFile;

public class RandomAccessFileRandomOutputStream extends RandomOutputStream {
	private final RandomAccessFile f;

	public RandomAccessFileRandomOutputStream(final RandomAccessFile f) {
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

    @Override
	public long getFilePointer() throws IOException {
		return f.getFilePointer();
	}

    @Override
	public void seek(long pos) throws IOException {
		f.seek(pos);
	}
	
	@Override public void close() throws IOException {
		f.close();
	}
}
