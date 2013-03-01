package yuku.alkitabconverter.yes_common;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import yuku.alkitab.yes2.Yes2Writer;
import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.alkitab.yes2.model.Yes2Book;
import yuku.alkitab.yes2.section.BooksInfoSection;
import yuku.alkitab.yes2.section.VersionInfoSection;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.util.TextDb.VerseState;
import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;
import yuku.snappy.codec.Snappy;

public class Yes2Common {
	public static final String TAG = Yes2Common.class.getSimpleName();

	public static class VersionInfo {
		public String locale;
		public String shortName;
		public String longName;
		public String description;
		
		private List<String> bookNames;
		
		public String getBookShortName(int bookId) {
			return bookNames.get(bookId);
		}

		public void setBookNames(List<String> bookNames) {
			this.bookNames = bookNames;
		}
	}

	private static BooksInfoSection getBooksInfoSection(VersionInfo versionInfo, TextDb textDb) throws Exception {
		// no nulls allowed
		final List<Yes2Book> yes2books = new ArrayList<Yes2Book>();
		
		// for the text offset from the beginning of text section
		int offsetTotal = 0;
		// for the text offset from the beginning of book
		int offsetPassed = 0;
		
		for (int bookId: textDb.getBookIds()) {
			Yes2Book b = new Yes2Book();
			b.bookId = bookId;
			b.chapter_count = textDb.getChapterCountForBook(bookId);
			b.chapter_offsets = new int[b.chapter_count + 1];
			b.offset = offsetTotal;
			b.shortName = versionInfo.getBookShortName(bookId);
			b.verse_counts = new int[b.chapter_count];
			
			b.chapter_offsets[0] = 0;
			for (int chapter_0 = 0; chapter_0 < b.chapter_count; chapter_0++) {
				b.verse_counts[chapter_0] = textDb.getVerseCountForBookChapter(bookId, chapter_0 + 1);
				
				for (int verse_0 = 0; verse_0 < b.verse_counts[chapter_0]; verse_0++) {
					String verseText = textDb.getVerseText(bookId, chapter_0+1, verse_0+1);
					offsetPassed += VerseBytes.bytesForAVerse(verseText).length;
				}
				b.chapter_offsets[chapter_0 + 1] = offsetPassed;
			}

			yes2books.add(b);
			
			//# reset
			offsetTotal += offsetPassed;
			offsetPassed = 0;
		}
		
		BooksInfoSection res = new BooksInfoSection();
		res.yes2Books = yes2books;
		return res;
	}

	private static VersionInfoSection getVersionInfoSection(VersionInfo versionInfo, TextDb textDb, boolean hasPericopes) {
		VersionInfoSection res = new VersionInfoSection();
		res.book_count = textDb.getBookCount();
		res.description = versionInfo.description;
		res.hasPericopes = hasPericopes? 1: 0;
		res.locale = versionInfo.locale;
		res.longName = versionInfo.longName;
		res.shortName = versionInfo.shortName;
		res.textEncoding = 2; // utf-8
		return res;
	}

	// TODO support for pericopes
	public static void createYesFile(final File outputFile, final VersionInfo versionInfo, final TextDb textDb, boolean compressed) throws Exception {
		VersionInfoSection versionInfoSection = getVersionInfoSection(versionInfo, textDb, false);
		BooksInfoSection booksInfoSection = getBooksInfoSection(versionInfo, textDb);
		
		Yes2Writer yesWriter = new Yes2Writer();
		yesWriter.sections.add(versionInfoSection);
		yesWriter.sections.add(booksInfoSection);
		yesWriter.sections.add(new LazyText(textDb, compressed));
		
		RandomOutputStream output = new RandomOutputStream(new RandomAccessFile(outputFile, "rw")); //$NON-NLS-1$
		yesWriter.writeToFile(output);
		
		output.close();
	}

	/** Get the complete bytes (including information about length and/or separators for a verse */
	static class VerseBytes {
		static ThreadLocal<ByteArrayOutputStream> baos_ = new ThreadLocal<ByteArrayOutputStream>() {
			@Override protected ByteArrayOutputStream initialValue() {
				return new ByteArrayOutputStream(1000);
			}
		};
		
		static byte[] bytesForAVerse(String verse) {
			ByteArrayOutputStream baos = baos_.get();
			baos.reset();
			
			try (BintexWriter bw = new BintexWriter(baos)) {
				byte[] verse_bytes = verse.getBytes("utf-8");
				bw.writeVarUint(verse_bytes.length);
				bw.writeRaw(verse_bytes);
				return baos.toByteArray();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	/**
	 * Each verse is written as follows:
	 *
	 *	- varuint length_in_bytes
	 *  - byte[length_in_bytes] encoded_text
	 */
	static class LazyText extends SectionContent implements SectionContent.Writer {
		private final TextDb textDb;
		private final boolean compressed;
		private final int COMPRESS_BLOCK_SIZE = 32768; 
		
		private int[] compressed_block_sizes;
		private ByteArrayOutputStream toOutput = new ByteArrayOutputStream();
		
		public LazyText(TextDb textDb, boolean compressed) {
			super("text");
			this.textDb = textDb;
			this.compressed = compressed;
			
			processNow();
		}
		
		@Override public ValueMap getAttributes() {
			ValueMap res = new ValueMap();
			if (compressed) {
				res.put("compression", "snappy-blocks");
				res.put("snappy.blocks.block_size", COMPRESS_BLOCK_SIZE);
				res.put("snappy.blocks.compressed_block_sizes", compressed_block_sizes);
			}
			return res;
		}
		
		@SuppressWarnings("resource") private void processNow() {
			Compressor compressor = null;
			
			final BintexWriter bw;
			if (!this.compressed) {
				bw = new BintexWriter(toOutput);
			} else {
				compressor = new Compressor(toOutput);
				bw = new BintexWriter(compressor);
			}
			
			textDb.processEach(new TextDb.TextProcessor() {
				@Override public void process(int ari, VerseState verseState) {
					byte[] bytes = VerseBytes.bytesForAVerse(verseState.text);
					try {
						bw.writeRaw(bytes);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			});
			
			if (compressor != null) {
				try {
					compressor.flush();
					compressed_block_sizes = compressor.getCompressedBlockSizes();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

		@Override public void write(RandomOutputStream output) throws Exception {
			toOutput.writeTo(output);
		}

		public class Compressor extends FilterOutputStream {
			public final String TAG = Compressor.class.getSimpleName();
			
			private Snappy s;
			private byte[] uncompressed = new byte[COMPRESS_BLOCK_SIZE];
			private byte[] compressed;
			private int uncompressed_offset;
			private List<Integer> compressed_block_sizes = new ArrayList<>(); 

			public Compressor(OutputStream out) {
				super(out);
				this.s = new Snappy.Factory().newInstance();
				this.compressed = new byte[s.maxCompressedLength(uncompressed.length)];
			}

			@Override public void write(int b) throws IOException {
				if (uncompressed_offset >= uncompressed.length) {
					dump();
				}
				
				uncompressed[uncompressed_offset++] = (byte) b;
			}
			
			@Override public void write(byte[] b, int off, int len) throws IOException {
				int remaining = len;
				int src_off = off;
				
				while (remaining > 0) {
					int can_write = uncompressed.length - uncompressed_offset;
					int will_write = Math.min(remaining, can_write);
					
					System.arraycopy(b, src_off, uncompressed, uncompressed_offset, will_write);
					uncompressed_offset += will_write;
					src_off += will_write;
					remaining -= will_write;
					
					if (uncompressed_offset >= uncompressed.length) {
						dump();
					}
				}
				
				assert src_off == off + len;
			}
			
			private void dump() throws IOException {
				if (uncompressed_offset > 0) {
					int compressed_len = s.compress(uncompressed, 0, compressed, 0, uncompressed_offset);
					out.write(compressed, 0, compressed_len);
					compressed_block_sizes.add(compressed_len);
					uncompressed_offset = 0;
				}
			}
			
			@Override public void flush() throws IOException {
				dump();
				out.flush();
			}
			
			public int[] getCompressedBlockSizes() {
				int[] res = new int[compressed_block_sizes.size()];
				for (int i = 0; i < res.length; i++) {
					res[i] = compressed_block_sizes.get(i);
				}
				return res;
			}
		}
	}
}


