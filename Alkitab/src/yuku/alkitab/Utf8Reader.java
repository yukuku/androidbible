package yuku.alkitab;

import java.io.*;

/**
 * Kilat
 */
public class Utf8Reader implements Closeable {
	private InputStream in_;
	public static final char replacementChar = 0xFFFD;

	public Utf8Reader(InputStream in) {
		in_ = in;
	}

	public int read() throws IOException {
		int c0 = in_.read();

		if (c0 == -1) {
			// EOF
			return -1;
		}

		if (c0 < 0x80) {
			// input 1 byte, output 7 bit
			return c0;
		}

		int c1 = in_.read();

		if (c1 == -1) {
			// partial EOF
			return -1;
		}

		if (c0 < 0xe0) {
			// input 2 byte, output 5+6 = 11 bit
			return ((c0 & 0x1f) << 6) | (c1 & 0x3f);
		}

		int c2 = in_.read();

		if (c2 == -1) {
			// partial EOF
			return -1;
		}

		// input 3 byte, output 4+6+6 = 16 bit
		return ((c0 & 0x0f) << 12) | ((c1 & 0x3f) << 6) | (c2 & 0x3f);
	}

	@Override
	public void close() throws IOException {
		in_.close();
	}
}
