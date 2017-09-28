package yuku.alkitab.base.storage;

import android.util.Log;
import yuku.alkitab.base.App;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.io.BibleReader;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.FootnoteEntry;
import yuku.alkitab.model.InternalBook;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.XrefEntry;
import yuku.alkitab.util.Ari;
import yuku.alkitab.yes1.Yes1PericopeIndex;
import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.section.FootnotesSection;
import yuku.alkitab.yes2.section.XrefsSection;
import yuku.bintex.BintexReader;

import java.io.IOException;
import java.io.InputStream;

public class InternalReader implements BibleReader {
	public static final String TAG = InternalReader.class.getSimpleName();

	// # for asset cache
	private static InputStream cache_inputStream = null;
	private static String cache_file = null;
	private static int cache_posInput = -1;

	private final String versionPrefix;
	private final String versionLocale;
	private final String versionShortName;
	private final String versionLongName;
	private final VerseTextDecoder verseTextDecoder;

	private Yes1PericopeIndex pericopeIndex_;
	private XrefsSection xrefsSection_;
	private boolean xrefsKnownNotAvailable;
	private FootnotesSection footnotesSection_;
	private boolean footnotesKnownNotAvailable;

	public InternalReader(String versionPrefix, String versionLocale, String versionShortName, String versionLongName, VerseTextDecoder verseTextDecoder) {
		this.versionPrefix = versionPrefix;
		this.versionLocale = versionLocale;
		this.versionShortName = versionShortName;
		this.versionLongName = versionLongName;
		this.verseTextDecoder = verseTextDecoder;
	}
	
	static class InternalSingleChapterVerses extends SingleChapterVerses {
		private final String[] verses;

		public InternalSingleChapterVerses(String[] verses) {
			this.verses = verses;
		}
		
		@Override public String getVerse(int verse_0) {
			return verses[verse_0];
		}

		@Override public int getVerseCount() {
			return verses.length;
		}
	}

	@Override
	public String getLocale() {
		return versionLocale;
	}

	@Override public String getShortName() {
		return versionShortName;
	}

	@Override public String getLongName() {
		return versionLongName;
	}
	
	@Override public String getDescription() {
		return null;
	}

	@Override public Book[] loadBooks() {
		BintexReader br = null;
		try {
			final InputStream is = App.context.getAssets().open("internal/" + versionPrefix + "_index_bt.bt");
			br = new BintexReader(is);

			// uint8 version = 3
			// uint8 book_count
			final int version = br.readUint8();
			if (version != 3) throw new RuntimeException("Internal index version not supported: " + version);
			final int book_count = br.readUint8();
			final Book[] res = new Book[book_count];
			for (int i = 0; i < book_count; i++) {
				res[i] = readBook(br);
			}
			return res;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (br != null) br.close();
		}
	}

	private static InternalBook readBook(final BintexReader br) throws IOException {
		// uint8 bookId;
		// value<string> shortName
		// value<string> abbreviation
		// value<string> resName
		// uint8 chapter_count
		// uint8[chapter_count] verse_counts
		// varuint[chapter_count+1] chapter_offsets

		final InternalBook res = new InternalBook();

		res.bookId = br.readUint8();
		res.shortName = br.readValueString();
		res.abbreviation = br.readValueString();
		res.resName = br.readValueString();
		res.chapter_count = br.readUint8();

		res.verse_counts = new int[res.chapter_count];
		for (int i = 0; i < res.chapter_count; i++) {
			res.verse_counts[i] = br.readUint8();
		}

		res.chapter_offsets = new int[res.chapter_count + 1];
		for (int i = 0; i < res.chapter_count + 1; i++) {
			res.chapter_offsets[i] = br.readVarUint();
		}

		return res;
	}

	@Override public SingleChapterVerses loadVerseText(Book book, int chapter_1, boolean dontSplitVerses, boolean lowercased) {
		InternalBook internalBook = (InternalBook) book;

		if (chapter_1 < 1 || chapter_1 > book.chapter_count) {
			return null;
		}
		
		int offset = internalBook.chapter_offsets[chapter_1 - 1];

		try {
			InputStream in;

			if (cache_inputStream == null) {
				// case 1: haven't opened anything
				in = App.context.getAssets().open("internal/" + internalBook.resName + ".txt");
				cache_inputStream = in;
				cache_file = internalBook.resName;

				//noinspection ResultOfMethodCallIgnored
				in.skip(offset);
				cache_posInput = offset;
			} else {
				// case 2: we have ever opened. Check if the file is the same
				if (internalBook.resName.equals(cache_file)) {
					// case 2.1: yes the file was the same
					if (offset >= cache_posInput) {
						// we can go forward
						in = cache_inputStream;

						//noinspection ResultOfMethodCallIgnored
						in.skip(offset - cache_posInput);
						cache_posInput = offset;
					} else {
						// but can't go backward, so we close the stream and reopen it
						cache_inputStream.close();

						in = App.context.getAssets().open("internal/" + internalBook.resName + ".txt");
						cache_inputStream = in;

						//noinspection ResultOfMethodCallIgnored
						in.skip(offset);
						cache_posInput = offset;
					}
				} else {
					// case 2.2: different file. So close current and open the new one
					cache_inputStream.close();

					in = App.context.getAssets().open("internal/" + internalBook.resName + ".txt");
					cache_inputStream = in;
					cache_file = internalBook.resName;

					//noinspection ResultOfMethodCallIgnored
					in.skip(offset);
					cache_posInput = offset;
				}
			}

			final int length;
			if (chapter_1 == internalBook.chapter_count) {
				length = in.available();
			} else {
				length = internalBook.chapter_offsets[chapter_1] - offset;
			}

			byte[] ba = new byte[length];
			in.read(ba);
			cache_posInput += ba.length;
			// do not close even though we finished reading. The asset file could be the same as before.

			if (dontSplitVerses) {
				return new InternalSingleChapterVerses(new String[] { verseTextDecoder.makeIntoSingleString(ba, lowercased) });
			} else {
				return new InternalSingleChapterVerses(verseTextDecoder.separateIntoVerses(ba, lowercased));
			}
		} catch (IOException e) {
			return new InternalSingleChapterVerses(new String[] { e.getMessage() });
		}
	}

	private Yes1PericopeIndex loadPericopeIndex() {
		if (pericopeIndex_ != null) {
			return pericopeIndex_;
		}

		final long startTime = System.currentTimeMillis();

		final InputStream is;
		try {
			is = App.context.getAssets().open("internal/" + versionPrefix + "_pericope_index_bt.bt");
		} catch (IOException e) {
			return null;
		}

		BintexReader in = new BintexReader(is);
		try {
			pericopeIndex_ = Yes1PericopeIndex.read(in);
			return pericopeIndex_;

		} catch (IOException e) {
			AppLog.e(TAG, "Error reading pericope index", e);
			return null;
		} finally {
			in.close();
			AppLog.d(TAG, "Read pericope index needed: " + (System.currentTimeMillis() - startTime));
		}
	}

	@Override public int loadPericope(int bookId, int chapter_1, int[] aris, PericopeBlock[] pericopeBlocks, int max) {
		Yes1PericopeIndex pericopeIndex = loadPericopeIndex();

		if (pericopeIndex == null) {
			return 0; // no pericopes!
		}

		int ariMin = Ari.encode(bookId, chapter_1, 0);
		int ariMax = Ari.encode(bookId, chapter_1 + 1, 0);
		int res = 0;

		int pertama = pericopeIndex.findFirst(ariMin, ariMax);

		if (pertama == -1) {
			return 0;
		}

		int kini = pertama;

		BintexReader in = null;
		try {
			in = new BintexReader(App.context.getAssets().open("internal/" + versionPrefix + "_pericope_blocks_bt.bt"));
			while (true) {
				int ari = pericopeIndex.getAri(kini);

				if (ari >= ariMax) {
					// That's all. No longer relevant.
					break;
				}

				PericopeBlock pericopeBlock = pericopeIndex.getBlock(in, kini);
				kini++;

				if (res < max) {
					aris[res] = ari;
					pericopeBlocks[res] = pericopeBlock;
					res++;
				} else {
					break;
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (in != null) in.close();
		}

		return res;
	}
	
	@Override public XrefEntry getXrefEntry(int arif) {
		if (xrefsKnownNotAvailable) return null;

		if (xrefsSection_ == null) {
			final String assetName = "internal/" + AppConfig.get().internalPrefix + "_xrefs_bt.bt";

			try {
				App.context.getAssets().list(assetName);
			} catch (IOException e) {
				AppLog.d(TAG, "Can't load xrefs from internal, marking it as not available.");
				xrefsKnownNotAvailable = true;
				return null;
			}

			try {
				xrefsSection_ = new XrefsSection.Reader().read(new AssetRandomInputStream(assetName));
			} catch (IOException e) {
				throw new RuntimeException("Error reading xrefs section from internal", e);
			}
		}

		return xrefsSection_.getXrefEntry(arif);
	}
	
	@Override public FootnoteEntry getFootnoteEntry(int arif) {
		if (footnotesKnownNotAvailable) return null;

		if (footnotesSection_ == null) {
			final String assetName = "internal/" + AppConfig.get().internalPrefix + "_footnotes_bt.bt";

			try {
				App.context.getAssets().list(assetName);
			} catch (IOException e) {
				AppLog.d(TAG, "Can't load footnotes from internal, marking it as not available.");
				footnotesKnownNotAvailable = true;
				return null;
			}

			try {
				footnotesSection_ = new FootnotesSection.Reader().read(new AssetRandomInputStream(assetName));
			} catch (Exception e) {
				AppLog.e(TAG, "Error reading footnotes section from internal", e);
				return null;
			}
		}

		return footnotesSection_.getFootnoteEntry(arif);
	}
}

class AssetRandomInputStream extends RandomInputStream {
	final String assetName;
	InputStream in;
	int pos;

	public AssetRandomInputStream(final String assetName) {
		this.assetName = assetName;
		reopen();
	}

	private void reopen() {
		try {
			this.in = App.context.getAssets().open(assetName);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		this.pos = 0;
	}

	@Override public int read() throws IOException {
		final int res = in.read();
		if (res >= 0) {
			pos++;
		}
		return res;
	}

	@Override public int read(byte[] buffer) throws IOException {
		final int read = in.read(buffer);
		pos += read;
		return read;
	}

	@Override public int read(byte[] buffer, int offset, int length) throws IOException {
		final int read = in.read(buffer, offset, length);
		pos += read;
		return read;
	}

	@Override public long skip(long n) throws IOException {
		final long read = in.skip(n);
		pos += (int) read;
		return read;
	}

	@Override public void seek(long n) throws IOException {
		if (n >= pos) {
			//noinspection ResultOfMethodCallIgnored
			skip(n - pos);
		} else {
			reopen();
			//noinspection ResultOfMethodCallIgnored
			skip(n);
		}
	}

	@Override public long getFilePointer() throws IOException {
		return pos;
	}

	@Override public void close() throws IOException {
		// NOP, no need to close asset
	}
}

