package yuku.alkitab.yes2.io;

import yuku.alkitab.io.Utf8Decoder;
import yuku.bintex.BintexReader;

public interface Yes2VerseTextDecoder {
	String[] separateIntoVerses(BintexReader br, int verse_count, boolean lowercased) throws Exception;
	String makeIntoSingleString(BintexReader br, int verse_count, boolean lowercased) throws Exception;
	
	class Ascii implements Yes2VerseTextDecoder {
		final ThreadLocal<byte[]> verseBuf_ = ThreadLocal.withInitial(() -> new byte[4000]);
		
		@Override public String[] separateIntoVerses(BintexReader br, int verse_count, boolean lowercased) throws Exception {
			byte[] verseBuf = verseBuf_.get();
			
			String[] res = new String[verse_count];
			
			for (int i = 0; i < verse_count; i++) {
				int verse_len = br.readVarUint();
				br.readRaw(verseBuf, 0, verse_len);
				
				if (lowercased) {
					for (int j = 0; j < verse_len; j++) {
						byte b = verseBuf[j];
						if (b <= (byte)'Z' && b >= (byte)'A') {
							verseBuf[i] |= 0x20;
						}
					}
				}
				
				//# WARNING: This will work only if all bytes are less than 0x80.
				@SuppressWarnings("deprecation") String verse = new String(verseBuf, 0, 0, verse_len);
				res[i] = verse;
			}
			
			return res;
		}

		/* (2017-01) Can be optimized, but we are moving away from ascii encoding. So probably it is not worth it to optimize this. */
		@Override public String makeIntoSingleString(BintexReader br, int verse_count, boolean lowercased) throws Exception {
			StringBuilder sb = new StringBuilder();
			String[] verses = separateIntoVerses(br, verse_count, lowercased);
			for (String verse: verses) {
				sb.append(verse).append('\n');
			}
			return sb.toString();
		}
	}
	
	class Utf8 implements Yes2VerseTextDecoder {
		final ThreadLocal<byte[]> verseBuf_ = ThreadLocal.withInitial(() -> new byte[4000]);
		
		@Override public String[] separateIntoVerses(BintexReader br, int verse_count, boolean lowercased) throws Exception {
			byte[] verseBuf = verseBuf_.get();
			
			String[] res = new String[verse_count];
			
			for (int i = 0; i < verse_count; i++) {
				int verse_len = br.readVarUint();
				
				if (verse_len > verseBuf.length) {
					verseBuf = new byte[verse_len + 1000];
					verseBuf_.set(verseBuf);
				}
				
				br.readRaw(verseBuf, 0, verse_len);
				
				String verse;
				if (lowercased) {
					verse = Utf8Decoder.toStringLowerCase(verseBuf, 0, verse_len);
				} else {
					verse = Utf8Decoder.toString(verseBuf, 0, verse_len);
				}
				
				res[i] = verse;
			}
			
			return res;
		}

		@Override public String makeIntoSingleString(BintexReader br, int verse_count, boolean lowercased) throws Exception {
			return Utf8Decoder.toStringFromVersesWithPrependedLengths(br, verse_count, lowercased);
		}
	}
}
