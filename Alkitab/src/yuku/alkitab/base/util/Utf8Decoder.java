package yuku.alkitab.base.util;

public class Utf8Decoder {
	public static char[] buf = new char[1000];

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
				buf[pos++] =  (char) (((c0 & 0x0f) << 12) | ((c1 & 0x3f) << 6) | (c2 & 0x3f));
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
					buf[pos++] = (char) (((c0 & 0x1f) << 6) | (c1 & 0x3f));
					continue;
				}

				i++;
				int c2 = ba[i] & 0xff;

				// input 3 byte, output 4+6+6 = 16 bit
				buf[pos++] =  (char) (((c0 & 0x0f) << 12) | ((c1 & 0x3f) << 6) | (c2 & 0x3f));
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			// biarin
		}

		return new String(buf, 0, pos);
	}
}
