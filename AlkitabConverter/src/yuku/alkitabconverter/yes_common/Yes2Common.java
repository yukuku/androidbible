package yuku.alkitabconverter.yes_common;

import yuku.alkitab.yes2.Yes2Writer;
import yuku.alkitab.yes2.compress.SnappyOutputStream;
import yuku.alkitab.yes2.io.MemoryRandomAccessFile;
import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitab.yes2.model.Yes2Book;
import yuku.alkitab.yes2.section.BooksInfoSection;
import yuku.alkitab.yes2.section.PericopesSection;
import yuku.alkitab.yes2.section.VersionInfoSection;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.util.TextDb.VerseState;
import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

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
		res.buildTime = (int) (System.currentTimeMillis() / 1000);
		res.textEncoding = 2; // utf-8
		return res;
	}

	public static void createYesFile(final File outputFile, final VersionInfo versionInfo, final TextDb textDb, PericopeData pericopeData, boolean compressed) throws Exception {
		VersionInfoSection versionInfoSection = getVersionInfoSection(versionInfo, textDb, pericopeData != null);
		BooksInfoSection booksInfoSection = getBooksInfoSection(versionInfo, textDb);
		
		Yes2Writer yesWriter = new Yes2Writer();
		yesWriter.sections.add(versionInfoSection);
		yesWriter.sections.add(booksInfoSection);
		if (pericopeData != null) {
			yesWriter.sections.add(new CompressiblePericopesSection(pericopeData, compressed));
		}
		yesWriter.sections.add(new CompressibleLazyText(textDb, compressed));
		
		RandomAccessFile raf = new RandomAccessFile(outputFile, "rw"); //$NON-NLS-1$
		raf.setLength(0);
		RandomOutputStream output = new RandomOutputStream(raf);
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
			
			try {
				BintexWriter bw = new BintexWriter(baos);
				try {
					byte[] verse_bytes = verse.getBytes("utf-8");
					bw.writeVarUint(verse_bytes.length);
					bw.writeRaw(verse_bytes);
					return baos.toByteArray();
				} finally {
					bw.close();
				}
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
	static class CompressibleLazyText extends SectionContent implements SectionContent.Writer {
		private final TextDb textDb;
		private final boolean compressed;
		private final int COMPRESS_BLOCK_SIZE = 32768;
		
		private int[] compressed_block_sizes;
		private ByteArrayOutputStream toOutput = new ByteArrayOutputStream();
		
		public CompressibleLazyText(TextDb textDb, boolean compressed) {
			super("text");
			this.textDb = textDb;
			this.compressed = compressed;
			
			processNow();
		}
		
		@Override public ValueMap getAttributes() {
			ValueMap res = new ValueMap();
			if (compressed) {
				ValueMap compressionInfo = new ValueMap();
				compressionInfo.put("block_size", COMPRESS_BLOCK_SIZE);
				compressionInfo.put("compressed_block_sizes", compressed_block_sizes);
				res.put("compression.name", "snappy-blocks");
				res.put("compression.version", 1);
				res.put("compression.info", compressionInfo);
			}
			return res;
		}
		
		@SuppressWarnings("resource") private void processNow() {
			SnappyOutputStream snappyOutputStream = null;
			
			final BintexWriter bw;
			if (!this.compressed) {
				bw = new BintexWriter(toOutput);
			} else {
				snappyOutputStream = new SnappyOutputStream(toOutput, COMPRESS_BLOCK_SIZE);
				bw = new BintexWriter(snappyOutputStream);
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
			
			if (snappyOutputStream != null) {
				try {
					snappyOutputStream.flush();
					compressed_block_sizes = snappyOutputStream.getCompressedBlockSizes();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

		@Override public void write(RandomOutputStream output) throws Exception {
			toOutput.writeTo(output);
		}
	}

	static class CompressiblePericopesSection extends PericopesSection implements SectionContent.Writer {
		private final boolean compressed;
		private final int COMPRESS_BLOCK_SIZE = 32768;
		
		private int[] compressed_block_sizes;
		private ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
		
		public CompressiblePericopesSection(PericopeData pericopeData, boolean compressed) {
			super(pericopeData);
			this.compressed = compressed;
			
			processNow();
		}
		
		@Override public ValueMap getAttributes() {
			ValueMap res = new ValueMap();
			if (compressed) {
				ValueMap compressionInfo = new ValueMap();
				compressionInfo.put("block_size", COMPRESS_BLOCK_SIZE);
				compressionInfo.put("compressed_block_sizes", compressed_block_sizes);
				res.put("compression.name", "snappy-blocks");
				res.put("compression.version", 1);
				res.put("compression.info", compressionInfo);
			}
			return res;
		}
		
		private void processNow() {
			MemoryRandomAccessFile mem = null;
			try {
				mem = new MemoryRandomAccessFile();
				super.write(new RandomOutputStream(mem));
				
				SnappyOutputStream snappyOutputStream = null;
				final OutputStream os;
				if (!this.compressed) {
					os = compressedOutput;
				} else {
					os = snappyOutputStream = new SnappyOutputStream(compressedOutput, COMPRESS_BLOCK_SIZE);
				}
				
				os.write(mem.getBuffer(), mem.getBufferOffset(), mem.getBufferLength());
				if (snappyOutputStream != null) {
					snappyOutputStream.flush();
					try {
						snappyOutputStream.flush();
						compressed_block_sizes = snappyOutputStream.getCompressedBlockSizes();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override public void write(RandomOutputStream output) throws Exception {
			// DO NOT CALL SUPER!
			compressedOutput.writeTo(output);
		}
	}
}


