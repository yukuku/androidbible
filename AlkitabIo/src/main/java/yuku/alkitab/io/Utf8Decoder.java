package yuku.alkitab.io;

import yuku.bintex.BintexReader;

import java.io.IOException;

/**
 * This class is an intentionally incomplete UTF-8 decoder that only supports up to U+FFFF.
 */
public class Utf8Decoder {
	public static char[] buf = new char[1000];

	static final ThreadLocal<byte[]> byte_buf_ = ThreadLocal.withInitial(() -> new byte[1000]);

	static final ThreadLocal<char[]> char_buf_ = ThreadLocal.withInitial(() -> new char[8000]);

	public static String toString(byte[] ba) {
		return toString(ba, 0, ba.length);
	}

	public static String toString(byte[] ba, int start, int length) {
		if (buf.length < ba.length) {
			buf = new char[ba.length + 1000];
		}

		int pos = 0;

		try {
			for (int i = start; i < start + length; i++) {
				int c0 = ba[i] & 0xff;

				if (c0 < 0x80) {
					// input 1 byte, output 7 bit
					buf[pos++] = (char) c0;
					continue;
				}

				i++;
				int c1 = ba[i] & 0xff;

				if (c0 < 0xe0) {
					// input 2 byte, output 5+6 = 11 bit
					buf[pos++] = (char) (((c0 & 0x1f) << 6) | (c1 & 0x3f));
					continue;
				}

				i++;
				int c2 = ba[i] & 0xff;

				// input 3 byte, output 4+6+6 = 16 bit
				buf[pos++] = (char) (((c0 & 0x0f) << 12) | ((c1 & 0x3f) << 6) | (c2 & 0x3f));
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			// biarin
		}

		return new String(buf, 0, pos);
	}

	public static String toStringLowerCase(byte[] ba) {
		return toStringLowerCase(ba, 0, ba.length);
	}

	public static String toStringLowerCase(byte[] ba, int start, int length) {
		if (buf.length < ba.length) {
			buf = new char[ba.length + 1000];
		}

		int pos = 0;

		try {
			for (int i = start; i < start + length; i++) {
				int c0 = ba[i] & 0xff;

				if (c0 < 0x80) {
					// input 1 byte, output 7 bit

					if (c0 >= 'A' && c0 <= 'Z') {
						buf[pos++] = (char) (c0 | 0x20);
					} else {
						buf[pos++] = (char) c0;
					}

					continue;
				}

				i++;
				int c1 = ba[i] & 0xff;

				if (c0 < 0xe0) {
					// input 2 byte, output 5+6 = 11 bit
					final int c = ((c0 & 0x1f) << 6) | (c1 & 0x3f);
					buf[pos++] = (char) Character.toLowerCase(c);
					continue;
				}

				i++;
				int c2 = ba[i] & 0xff;

				{
					// input 3 byte, output 4+6+6 = 16 bit
					final int c = ((c0 & 0x0f) << 12) | ((c1 & 0x3f) << 6) | (c2 & 0x3f);
					buf[pos++] = (char) Character.toLowerCase(c);
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			// biarin
		}

		return new String(buf, 0, pos);
	}

	public static String toStringFromVersesWithPrependedLengths(BintexReader br, int verse_count, boolean lowercased) throws IOException {
		byte[] byte_buf = byte_buf_.get();
		char[] char_buf = char_buf_.get();

		int char_pos = 0;
		for (int v = 0; v < verse_count; v++) {
			int verse_len = br.readVarUint();

			if (verse_len > byte_buf.length) {
				byte_buf = new byte[verse_len + 100];
				byte_buf_.set(byte_buf);
			}

			int will_need_char_len = char_pos + verse_len + 1 /*for separator*/;
			if (will_need_char_len > char_buf.length) {
				final char[] new_char_buf = new char[will_need_char_len + 1000];
				System.arraycopy(char_buf, 0, new_char_buf, 0, char_buf.length);
				char_buf = new_char_buf;
				char_buf_.set(char_buf);
			}

			br.readRaw(byte_buf, 0, verse_len);

			for (int i = 0; i < verse_len; i++) {
				int c0 = byte_buf[i] & 0xff;

				if (c0 < 0x80) {
					// input 1 byte, output 7 bit

					if (lowercased && (c0 >= 'A' && c0 <= 'Z')) {
						char_buf[char_pos++] = (char) (c0 | 0x20);
					} else {
						char_buf[char_pos++] = (char) c0;
					}

					continue;
				}

				i++;
				int c1 = byte_buf[i] & 0xff;

				if (c0 < 0xe0) {
					// input 2 byte, output 5+6 = 11 bit
					final int c = (((c0 & 0x1f) << 6) | (c1 & 0x3f));
					if (!lowercased) {
						char_buf[char_pos++] = (char) c;
					} else {
						char_buf[char_pos++] = (char) Character.toLowerCase(c);
					}
					continue;
				}

				i++;
				int c2 = byte_buf[i] & 0xff;

				{
					// input 3 byte, output 4+6+6 = 16 bit
					final int c = ((c0 & 0x0f) << 12) | ((c1 & 0x3f) << 6) | (c2 & 0x3f);
					if (!lowercased) {
						char_buf[char_pos++] = (char) c;
					} else {
						char_buf[char_pos++] = (char) Character.toLowerCase(c);
					}
				}
			}

			// verse separator
			char_buf[char_pos++] = '\n';
		}

		return new String(char_buf, 0, char_pos);
	}
}
