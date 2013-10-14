package yuku.alkitab.yes2.io;

import yuku.alkitab.base.App;

import java.io.IOException;
import java.io.InputStream;

public class RawResourceRandomInputStream extends RandomInputStream {
	public static final String TAG = RawResourceRandomInputStream.class.getSimpleName();

	final int resId;
	InputStream in;
	int pos;

	public RawResourceRandomInputStream(final int resId) {
		this.resId = resId;
		reopen();
	}

	private void reopen() {
		this.in = App.context.getResources().openRawResource(resId);
		this.pos = 0;
	}

	@Override public int read() throws IOException {
		final int res = in.read();
		if (res >= 0) {
			pos++;
		}
		return res;
	}

	@Override public int read(byte[] buffer) throws IOException {
		final int read = in.read(buffer);
		pos += read;
		return read;
	}

	@Override public int read(byte[] buffer, int offset, int length) throws IOException {
		final int read = in.read(buffer, offset, length);
		pos += read;
		return read;
	}

	@Override public long skip(long n) throws IOException {
		final long read = in.skip(n);
		pos += (int) read;
		return read;
	}

	@Override public void seek(long n) throws IOException {
		if (n >= pos) {
			skip(n - pos);
		} else {
			reopen();
			skip(n);
		}
	}

	@Override public long getFilePointer() throws IOException {
		return pos;
	}

	@Override public void close() throws IOException {
		// NOP, no need to close asset
	}
}
