package yuku.alkitab.yes2.model;

import yuku.bintex.BintexWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Get the complete bytes (including information about length and/or separators for a verse.
 * Each verse is written as follows:
 *
 *	- varuint length_in_bytes
 *  - byte[length_in_bytes] encoded_text
 */
public class VerseBytes {
	static ThreadLocal<ByteArrayOutputStream> baos_ = new ThreadLocal<ByteArrayOutputStream>() {
		@Override protected ByteArrayOutputStream initialValue() {
			return new ByteArrayOutputStream(1000);
		}
	};

	public static byte[] bytesForAVerse(String verse) {
		ByteArrayOutputStream baos = baos_.get();
		baos.reset();
		BintexWriter bw = new BintexWriter(baos);

		try {
			byte[] verse_bytes = verse.getBytes("utf-8");
			bw.writeVarUint(verse_bytes.length);
			bw.writeRaw(verse_bytes);
			return baos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
