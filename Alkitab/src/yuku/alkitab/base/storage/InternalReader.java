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
import java.util.ArrayList;

public class InternalReader implements BibleReader {
	public static final String TAG = InternalReader.class.getSimpleName();

	// # buat cache Asset
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
		InputStream is = S.openRaw(versionPrefix + "_index_bt"); //$NON-NLS-1$
		BintexReader in = new BintexReader(is);
		try {
			ArrayList<Book> xkitab = new ArrayList<Book>();

			try {
				int pos = 0;
				while (true) {
					Book k = bacaKitab(in, pos++);
					xkitab.add(k);
				}
			} catch (IOException e) {
				Log.d(TAG, "siapinKitab selesai memuat"); //$NON-NLS-1$
			}

			return xkitab.toArray(new Book[xkitab.size()]);
		} finally {
			in.close();
		}
	}

	private static InternalBook bacaKitab(BintexReader in, int pos) throws IOException {
		InternalBook k = new InternalBook();
		k.bookId = pos;

		// autostring bookName
		// shortstring resName
		// int chapter_count
		// uint8[chapter_count] verse_counts
		// int[chapter_count+1] chapter_offsets

		k.shortName = in.readAutoString();
		k.file = in.readShortString();
		k.chapter_count = in.readInt();

		k.verse_counts = new int[k.chapter_count];
		for (int i = 0; i < k.chapter_count; i++) {
			k.verse_counts[i] = in.readUint8();
		}

		k.chapter_offsets = new int[k.chapter_count + 1];
		for (int i = 0; i < k.chapter_count + 1; i++) {
			k.chapter_offsets[i] = in.readInt();
		}

		return k;
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
				// kasus 1: belum buka apapun
				in = S.openRaw(internalBook.file);
				cache_inputStream = in;
				cache_file = internalBook.file;

				in.skip(offset);
				cache_posInput = offset;
				// Log.d("alki", "muatTeks masuk kasus 1");
			} else {
				// kasus 2: uda pernah buka. Cek apakah filenya sama
				if (internalBook.file.equals(cache_file)) {
					// kasus 2.1: filenya sama.
					if (offset >= cache_posInput) {
						// bagus, kita bisa maju.
						in = cache_inputStream;

						in.skip(offset - cache_posInput);
						cache_posInput = offset;
						// Log.d("alki", "muatTeks masuk kasus 2.1 bagus");
					} else {
						// ga bisa mundur. tutup dan buka lagi.
						cache_inputStream.close();

						in = S.openRaw(internalBook.file);
						cache_inputStream = in;

						in.skip(offset);
						cache_posInput = offset;
						// Log.d("alki", "muatTeks masuk kasus 2.1 jelek");
					}
				} else {
					// kasus 2.2: filenya beda, tutup dan buka baru
					cache_inputStream.close();

					in = S.openRaw(internalBook.file);
					cache_inputStream = in;
					cache_file = internalBook.file;

					in.skip(offset);
					cache_posInput = offset;
					// Log.d("alki", "muatTeks masuk kasus 2.2");
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
			// jangan ditutup walau uda baca. Siapa tau masih sama filenya dengan sebelumnya.

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
		
		long wmulai = System.currentTimeMillis();

		InputStream is = S.openRaw(versionPrefix + "_pericope_index_bt"); //$NON-NLS-1$
		if (is == null) {
			return null;
		}

		BintexReader in = new BintexReader(is);
		try {
			pericopeIndex_ = Yes1PericopeIndex.read(in);
			return pericopeIndex_;

		} catch (IOException e) {
			Log.e(TAG, "baca perikop index ngaco", e); //$NON-NLS-1$
			return null;
		} finally {
			in.close();
			Log.d(TAG, "Muat index perikop butuh ms: " + (System.currentTimeMillis() - wmulai)); //$NON-NLS-1$
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
			final int resId = App.context.getResources().getIdentifier(AppConfig.get().internalPrefix + "_xref_bt", "raw", App.context.getPackageName());
			if (resId == 0) {
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
			final int resId = App.context.getResources().getIdentifier(AppConfig.get().internalPrefix + "_footnote_bt", "raw", App.context.getPackageName());
			if (resId == 0) {
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
