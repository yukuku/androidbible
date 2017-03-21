package yuku.alkitab.base.storage;

import yuku.alkitab.io.Utf8Decoder;

import java.util.ArrayList;

public class OldVerseTextDecoder {
	public static class Ascii implements VerseTextDecoder {
		private void lowercase(byte[] ba) {
			for (int i = 0, blen = ba.length; i < blen; i++) {
				byte b = ba[i];
				if (b <= (byte)'Z' && b >= (byte)'A') {
					ba[i] |= 0x20;
				}
			}
		}
		
		@Override public String[] separateIntoVerses(byte[] ba, boolean lowercased) {
			ArrayList<String> versesBuf = new ArrayList<>(60);
			char[] verseBuf = new char[4000];
			int i = 0;
			
			if (lowercased) {
				lowercase(ba);
			}
			
			//# WARNING: This will work only if all bytes are less than 0x80. 
			for (byte c : ba) {
				if (c == (byte) 0x0a) {
					String single = new String(verseBuf, 0, i);
					versesBuf.add(single);
					i = 0;
				} else {
					verseBuf[i++] = (char) c;
				}
			}
			
			return versesBuf.toArray(new String[versesBuf.size()]);
		}

		@SuppressWarnings("deprecation") @Override public String makeIntoSingleString(byte[] ba, boolean lowercased) {
			if (lowercased) {
				lowercase(ba);
			}

			return new String(ba, 0);
		}
	}
	
	public static class Utf8 implements VerseTextDecoder {
		@Override public String[] separateIntoVerses(byte[] ba, boolean lowercased) {
			ArrayList<String> versesBuf = new ArrayList<>(60);
			
			int len = ba.length;
			int from = 0;
			for (int pos = 0; pos < len; pos++) {
				byte c = ba[pos];
				if (c == (byte)0x0a) {
					String single;
					if (lowercased) {
						single = Utf8Decoder.toStringLowerCase(ba, from, pos - from);
					} else {
						single = Utf8Decoder.toString(ba, from, pos - from);
					}
					versesBuf.add(single);
					from = pos + 1;
				}
			}
			
			return versesBuf.toArray(new String[versesBuf.size()]);
		}

		@Override public String makeIntoSingleString(byte[] ba, boolean lowercased) {
			String res;
			if (lowercased) {
				res = Utf8Decoder.toStringLowerCase(ba);
			} else {
				res = Utf8Decoder.toString(ba);
			}
			return res;
		}
	}
}
