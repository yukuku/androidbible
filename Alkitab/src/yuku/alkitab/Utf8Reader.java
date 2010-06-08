package yuku.alkitab;


/**
 * Kilat
 */
public class Utf8Reader {
	private byte[] ba_;
	private int pos_;
	public static final char replacementChar = 0xFFFD;

	public Utf8Reader(byte[] ba) {
		ba_ = ba;
		pos_ = 0;
	}

	public int read() {
		int len = ba_.length;
		
		if (pos_ >= len) {
			// EOF
			return -1;
		}

		int c0 = ba_[pos_++];
		
		if (c0 < 0x80) {
			// input 1 byte, output 7 bit
			return c0;
		}

		if (pos_ >= len) {
			// partial EOF
			return -1;
		}

		int c1 = ba_[pos_++];

		if (c0 < 0xe0) {
			// input 2 byte, output 5+6 = 11 bit
			return ((c0 & 0x1f) << 6) | (c1 & 0x3f);
		}

		if (pos_ >= len) {
			// partial EOF
			return -1;
		}
		
		int c2 = ba_[pos_++];

		// input 3 byte, output 4+6+6 = 16 bit
		return ((c0 & 0x0f) << 12) | ((c1 & 0x3f) << 6) | (c2 & 0x3f);
	}

	public int read(char[] buf, int offset, int count) {
		int c = 0;
		
		for (int i = offset; i < offset + count; i++) {
			int read = read();
			
			if (read == -1) {
				break;
			}
			buf[i] = (char) read;
			
			c++;
		}
		
		return c;
	}
}
