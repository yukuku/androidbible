package yuku.alkitab.base.storage;

import android.util.Log;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.FootnoteEntry;
import yuku.alkitab.base.model.InternalBook;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.SingleChapterVerses;
import yuku.alkitab.base.model.XrefEntry;
import yuku.alkitab.yes1.Yes1PericopeIndex;
import yuku.alkitab.yes2.io.RawResourceRandomInputStream;
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
	private final String versionShortName;
	private final String versionLongName;
	private final VerseTextDecoder verseTextDecoder;

	private Yes1PericopeIndex pericopeIndex_;
	private XrefsSection xrefsSection_;
	private boolean xrefsKnownNotAvailable;
	private FootnotesSection footnotesSection_;
	private boolean footnotesKnownNotAvailable;

	public InternalReader(String versionPrefix, String versionShortName, String versionLongName, VerseTextDecoder verseTextDecoder) {
		this.versionPrefix = versionPrefix;
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
		final InputStream is = S.openRaw(versionPrefix + "_index_bt"); //$NON-NLS-1$
		final BintexReader br = new BintexReader(is);
		try {
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
			br.close();
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
				in = S.openRaw(internalBook.resName);
				cache_inputStream = in;
				cache_file = internalBook.resName;

				in.skip(offset);
				cache_posInput = offset;
			} else {
				// case 2: we have ever opened. Check if the file is the same
				if (internalBook.resName.equals(cache_file)) {
					// case 2.1: yes the file was the same
					if (offset >= cache_posInput) {
						// we can go forward
						in = cache_inputStream;

						in.skip(offset - cache_posInput);
						cache_posInput = offset;
					} else {
						// but can't go backward, so we close the stream and reopen it
						cache_inputStream.close();

						in = S.openRaw(internalBook.resName);
						cache_inputStream = in;

						in.skip(offset);
						cache_posInput = offset;
					}
				} else {
					// case 2.2: different file. So close current and open the new one
					cache_inputStream.close();

					in = S.openRaw(internalBook.resName);
					cache_inputStream = in;
					cache_file = internalBook.resName;

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
		
		InputStream is = S.openRaw(versionPrefix + "_pericope_index_bt"); //$NON-NLS-1$
		if (is == null) {
			return null;
		}

		BintexReader in = new BintexReader(is);
		try {
			pericopeIndex_ = Yes1PericopeIndex.read(in);
			return pericopeIndex_;

		} catch (IOException e) {
			Log.e(TAG, "Error reading pericope index", e); //$NON-NLS-1$
			return null;
		} finally {
			in.close();
			Log.d(TAG, "Read pericope index needed: " + (System.currentTimeMillis() - startTime)); //$NON-NLS-1$
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

		BintexReader in = new BintexReader(S.openRaw(versionPrefix + "_pericope_blocks_bt")); //$NON-NLS-1$
		try {
			while (true) {
				int ari = pericopeIndex.getAri(kini);

				if (ari >= ariMax) {
					// habis. Uda ga relevan
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
		} finally {
			in.close();
		}

		return res;
	}
	
	@Override public XrefEntry getXrefEntry(int arif) {
		if (xrefsKnownNotAvailable) return null;

		if (xrefsSection_ == null) {
			final int resId = App.context.getResources().getIdentifier(AppConfig.get().internalPrefix + "_xrefs_bt", "raw", App.context.getPackageName());
			if (resId == 0) {
				Log.d(TAG, "Can't load xrefs from internal, marking it as not available.");
				xrefsKnownNotAvailable = true;
				return null;
			}

			try {
				xrefsSection_ = new XrefsSection.Reader().read(new RawResourceRandomInputStream(resId));
			} catch (Exception e) {
				Log.e(TAG, "Error reading xrefs section from internal", e);
				return null;
			}
		}

		return xrefsSection_.getXrefEntry(arif);
	}
	
	@Override public FootnoteEntry getFootnoteEntry(int arif) {
		if (footnotesKnownNotAvailable) return null;

		if (footnotesSection_ == null) {
			final int resId = App.context.getResources().getIdentifier(AppConfig.get().internalPrefix + "_footnotes_bt", "raw", App.context.getPackageName());
			if (resId == 0) {
				Log.d(TAG, "Can't load footnotes from internal, marking it as not available.");
				footnotesKnownNotAvailable = true;
				return null;
			}

			try {
				footnotesSection_ = new FootnotesSection.Reader().read(new RawResourceRandomInputStream(resId));
			} catch (Exception e) {
				Log.e(TAG, "Error reading footnotes section from internal", e);
				return null;
			}
		}

		return footnotesSection_.getFootnoteEntry(arif);
	}
}
