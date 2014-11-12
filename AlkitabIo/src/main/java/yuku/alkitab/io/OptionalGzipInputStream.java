package yuku.alkitab.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Works like GzipInputStream, but if the source is not
 * detected as gzip (header 1F 8B) then it is streamed through as is.
 */
public class OptionalGzipInputStream extends InputStream {
	InputStream wrapper;

	static class PeekInputStream extends InputStream {
		private final InputStream source;
		int pos = 0; // current read position. 2 means 2 or more
		int len = -1; // -1 unknown, 0 zero bytes, 1 one byte
		int firstByte;
		int secondByte;

		PeekInputStream(InputStream source) throws IOException {
			this.source = source;

			// immediately read 2 bytes
			firstByte = source.read();
			secondByte = source.read();

			if (firstByte == -1) len = 0;
			else if (secondByte == -1) len = 1;
		}

		public int getFirstByte() {
			return firstByte;
		}

		public int getSecondByte() {
			return secondByte;
		}

		@Override
		public int read() throws IOException {
			if (len == 0) {
				return -1;
			} else if (len == 1) {
				if (pos == 0) {
					pos++;
					return firstByte;
				} else {
					return -1;
				}
			} else { // unknown length
				if (pos == 0) {
					pos++;
					return firstByte;
				} else if (pos == 1) {
					pos++;
					return secondByte;
				} else {
					pos++;
					return source.read();
				}
			}
		}

		@Override
		public int read(final byte[] buffer) throws IOException {
			return read(buffer, 0, buffer.length);
		}

		@Override
		public int read(final byte[] buffer, final int byteOffset, final int byteCount) throws IOException {
			if (byteCount == 0) {
				return 0;
			}

			if (byteCount == 1) {
				final int b = read();
				if (b == -1) {
					return -1;
				} else {
					buffer[byteOffset] = (byte) b;
					return 1;
				}
			}

			if (len == 0) {
				return -1;
			}

			if (len == 1 && pos == 0) {
				final int b = read();
				buffer[byteOffset] = (byte) b;
				return 1;
			} else if (len == 1) {
				return -1;
			}

			// byteCount is 2 or more, length of source is 2 or more
			int remainingToRead;
			int newOffset;
			if (pos == 0) {
				buffer[byteOffset] = (byte) firstByte;
				buffer[byteOffset + 1] = (byte) secondByte;
				pos = 2;
				newOffset = byteOffset + 2;
				remainingToRead = byteCount - 2;
			} else if (pos == 1) {
				buffer[byteOffset] = (byte) secondByte;
				pos = 2;
				newOffset = byteOffset + 1;
				remainingToRead = byteCount - 1;
			} else {
				newOffset = byteOffset;
				remainingToRead = byteCount;
			}

			final int read = source.read(buffer, newOffset, remainingToRead);
			if (read == -1) {
				final int res = newOffset - byteOffset;
				if (res == 0) {
					// about to return 0, but source says we are EOF. Return -1 instead
					return -1;
				}
				return res;
			}

			return read + newOffset - byteOffset;
		}

		@Override
		public void close() throws IOException {
			source.close();
		}
	}

	public OptionalGzipInputStream(InputStream source) throws IOException {
		final PeekInputStream peek = new PeekInputStream(source);

		final int first = peek.getFirstByte();
		final int second = peek.getSecondByte();

		if (first == 0x1f && second == 0x8b) {
			try {
				wrapper = new GZIPInputStream(peek);
			} catch (EOFException e) { // error reading gzip header, so this is not gzip
				wrapper = peek;
			}
		} else {
			wrapper = peek;
		}
	}

	@Override
	public int read() throws IOException {
		return wrapper.read();
	}

	@Override
	public int read(final byte[] buffer) throws IOException {
		return wrapper.read(buffer);
	}

	@Override
	public int read(final byte[] buffer, final int byteOffset, final int byteCount) throws IOException {
		return wrapper.read(buffer, byteOffset, byteCount);
	}

	@Override
	public void close() throws IOException {
		wrapper.close();
	}
}
