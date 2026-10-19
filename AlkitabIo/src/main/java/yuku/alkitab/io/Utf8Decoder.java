package yuku.alkitab.io;

import java.io.IOException;
import yuku.bintex.BintexReader;

public class Utf8Decoder {
	static final ThreadLocal<byte[]> byte_buf_ = ThreadLocal.withInitial(() -> new byte[1000]);
	static final ThreadLocal<char[]> char_buf_ = ThreadLocal.withInitial(() -> new char[8000]);

	public static String toString(byte[] ba) {
		return toString(ba, 0, ba.length);
	}

	public static String toString(byte[] ba, int start, int length) {
		return decodeString(ba, start, length, false);
	}

	public static String toStringLowerCase(byte[] ba) {
		return toStringLowerCase(ba, 0, ba.length);
	}

	public static String toStringLowerCase(byte[] ba, int start, int length) {
		return decodeString(ba, start, length, true);
	}

	private static String decodeString(byte[] ba, int start, int length, boolean isLowercased) {
		if (start < 0 || length < 0 || start > ba.length - length) {
			throw new IndexOutOfBoundsException();
		}
		char[] buffer = char_buf_.get();
		if (buffer.length < length) {
			buffer = new char[length + 1000];
			char_buf_.set(buffer);
		}
		int count = decode(ba, start, length, buffer, 0, isLowercased);
		return new String(buffer, 0, count);
	}

	private static int decode(byte[] input, int start, int length, char[] output, int pos, boolean isLowercased) {
		final int end = start + length;
		for (int i = start; i < end;) {
			final int c0 = input[i] & 0xff;
			if (c0 < 0x80) {
				output[pos++] = (char) (isLowercased && c0 >= 'A' && c0 <= 'Z' ? c0 | 0x20 : c0);
				i++;
				continue;
			}

			int codePoint;
			if (c0 >= 0xc2 && c0 <= 0xdf && i + 1 < end && isContinuation(input[i + 1])) {
				codePoint = ((c0 & 0x1f) << 6) | (input[i + 1] & 0x3f);
				i += 2;
			} else if (c0 >= 0xe0 && c0 <= 0xef && i + 2 < end
				&& isContinuation(input[i + 1]) && isContinuation(input[i + 2])
				&& (c0 != 0xe0 || (input[i + 1] & 0xff) >= 0xa0)
				&& (c0 != 0xed || (input[i + 1] & 0xff) < 0xa0)) {
				codePoint = ((c0 & 0x0f) << 12) | ((input[i + 1] & 0x3f) << 6) | (input[i + 2] & 0x3f);
				i += 3;
			} else if (c0 >= 0xf0 && c0 <= 0xf4 && i + 3 < end
				&& isContinuation(input[i + 1]) && isContinuation(input[i + 2]) && isContinuation(input[i + 3])
				&& (c0 != 0xf0 || (input[i + 1] & 0xff) >= 0x90)
				&& (c0 != 0xf4 || (input[i + 1] & 0xff) < 0x90)) {
				codePoint = ((c0 & 0x07) << 18) | ((input[i + 1] & 0x3f) << 12)
					| ((input[i + 2] & 0x3f) << 6) | (input[i + 3] & 0x3f);
				i += 4;
			} else {
				// Consume only the invalid byte so recovery cannot swallow the next character or verse.
				output[pos++] = '\ufffd';
				i++;
				continue;
			}

			// Simple code-point casing preserves search offsets without expanding characters.
			if (isLowercased) codePoint = Character.toLowerCase(codePoint);
			if (codePoint < 0x10000) {
				output[pos++] = (char) codePoint;
			} else {
				output[pos++] = Character.highSurrogate(codePoint);
				output[pos++] = Character.lowSurrogate(codePoint);
			}
		}
		return pos;
	}

	private static boolean isContinuation(byte value) {
		return (value & 0xc0) == 0x80;
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

			char_pos = decode(byte_buf, 0, verse_len, char_buf, char_pos, lowercased);

			// verse separator
			char_buf[char_pos++] = '\n';
		}

		return new String(char_buf, 0, char_pos);
	}
}
