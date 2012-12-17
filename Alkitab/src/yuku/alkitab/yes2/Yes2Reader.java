package yuku.alkitab.yes2;

import android.util.Log;

import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;

import yuku.afw.D;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.PericopeIndex;
import yuku.alkitab.base.model.SingleChapterVerses;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.storage.BibleReader;
import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.io.Yes2VerseTextDecoder;
import yuku.alkitab.yes2.model.SectionIndex;
import yuku.alkitab.yes2.model.Yes2Book;
import yuku.alkitab.yes2.section.BooksInfoSection;
import yuku.alkitab.yes2.section.TextSection;
import yuku.alkitab.yes2.section.VersionInfoSection;
import yuku.bintex.BintexReader;

public class Yes2Reader implements BibleReader {
	private static final String TAG = Yes2Reader.class.getSimpleName();

	private String filename_;
	private RandomInputStream file_;
	private SectionIndex sectionIndex_;
	private Yes2VerseTextDecoder decoder_;

	private VersionInfoSection versionInfo_;

	static class Yes2SingleChapterVerses extends SingleChapterVerses {
		private final String[] verses;

		public Yes2SingleChapterVerses(String[] verses) {
			this.verses = verses;
		}
		
		@Override public String getVerse(int verse_0) {
			return verses[verse_0];
		}

		@Override public int getVerseCount() {
			return verses.length;
		}
	}
	
	public Yes2Reader(String filename) {
		this.filename_ = filename;
	}

	/** Read section index */
	private synchronized void loadSectionIndex() throws Exception {
		if (file_ != null) { // we have read it previously.
			return;
		}
			
		file_ = new RandomInputStream(new RandomAccessFile(filename_, "r")); //$NON-NLS-1$
		file_.seek(0);

		{ // check header
			byte[] buf = new byte[8];
			file_.read(buf);
			if (!Arrays.equals(buf, new byte[] { (byte) 0x98, 0x58, 0x0d, 0x0a, 0x00, 0x5d, (byte) 0xe0, 0x02 /* yes version 2 */})) {
				throw new RuntimeException("YES2: Header is incorrect. Found: " + Arrays.toString(buf)); //$NON-NLS-1$
			}
		}
		
		file_.seek(12); // start of sectionIndex
		sectionIndex_ = SectionIndex.read(new BintexReader(file_));
	}
	
	private synchronized void loadVersionInfo() throws Exception {
		if (seekToSection("versionInfo")) {
			versionInfo_ = new VersionInfoSection.Reader().read(file_);
		}
	}
	
	private synchronized boolean seekToSection(String sectionName) throws Exception {
		loadSectionIndex();
		
		if (sectionIndex_ == null) return false;
		return sectionIndex_.seekToSection(sectionName, file_);
	}

	@Override public String getShortName() {
		try {
			loadVersionInfo();
			return versionInfo_.shortName;
		} catch (Exception e) {
			Log.e(TAG, "yes load version info error", e); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
	}

	@Override public String getLongName() {
		try {
			loadVersionInfo();
			return versionInfo_.longName;
		} catch (Exception e) {
			Log.e(TAG, "yes load version info error", e); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
	}

	public String getDescription() {
		try {
			loadVersionInfo();
			return versionInfo_.description;
		} catch (Exception e) {
			Log.e(TAG, "yes load version info error", e); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
	}

	@Override public Book[] loadBooks() {
		try {
			loadVersionInfo();

			if (seekToSection(BooksInfoSection.SECTION_NAME)) {
				BooksInfoSection section = new BooksInfoSection.Reader().read(file_);
				List<Yes2Book> books = section.yes2Books;
				return books.toArray(new Yes2Book[books.size()]);
			}
			
			Log.e(TAG, "no section named " + BooksInfoSection.SECTION_NAME); //$NON-NLS-1$
			return null;
		} catch (Exception e) {
			Log.e(TAG, "loadBooks error", e); //$NON-NLS-1$
			return null;
		}
	}

	@Override public Yes2SingleChapterVerses loadVerseText(Book book, int chapter_1, boolean dontSeparateVerses, boolean lowercase) {
		Yes2Book yes2Book = (Yes2Book) book;
		
		try {
			// init text decoder 
			if (decoder_ == null) {
				int textEncoding = versionInfo_.textEncoding;
				if (textEncoding == 1) {
					decoder_ = new Yes2VerseTextDecoder.Ascii();
				} else if (textEncoding == 2) {
					decoder_ = new Yes2VerseTextDecoder.Utf8();
				} else {
					Log.e(TAG, "Text encoding " + textEncoding + " not supported! Fallback to ascii."); //$NON-NLS-1$ //$NON-NLS-2$
					decoder_ = new Yes2VerseTextDecoder.Ascii();
				}
			}
			
			if (chapter_1 <= 0 || chapter_1 > yes2Book.chapter_count) {
				return null;
			}

			long seekTo = sectionIndex_.getOffsetForSection(TextSection.SECTION_NAME);
			seekTo += yes2Book.offset;
			seekTo += yes2Book.chapter_offsets[chapter_1 - 1];
			file_.seek(seekTo);

			int verse_count = yes2Book.verse_counts[chapter_1 - 1];
			
			BintexReader br = new BintexReader(file_);

			if (dontSeparateVerses) {
				return new Yes2SingleChapterVerses(new String[] { 
					decoder_.makeIntoSingleString(br, verse_count, lowercase),
				});
			} else {
				return new Yes2SingleChapterVerses(decoder_.separateIntoVerses(br, verse_count, lowercase));
			}
		} catch (Exception e) {
			Log.e(TAG, "loadVerseText error", e); //$NON-NLS-1$
			return null;
		}
	}

	@Override public PericopeIndex loadPericopeIndex() {
		try {
			loadVersionInfo();

			if (versionInfo_.hasPericopes == 0) {
				return null;
			}

			if (sectionIndex_.seekToSection(PericopeIndexSection.SECTION_NAME, file_)) {
				return new PericopeIndexSection.Reader().read(file_);
			} else {
				return null;
			}
		} catch (Exception e) {
			Log.e(TAG, "loadPericopeIndex error", e); //$NON-NLS-1$
			return null;
		}
	}

	@Override public int loadPericope(Version version, int bookId, int chapter_1, int[] aris, PericopeBlock[] blocks, int max) {
		try {
			loadSectionIndex();

			PericopeIndex pericopeIndex = version.getIndexPerikop();
			if (pericopeIndex == null) {
				return 0; // no pericopes
			}

			int ariMin = Ari.encode(bookId, chapter_1, 0);
			int ariMax = Ari.encode(bookId, chapter_1 + 1, 0);

			int first = pericopeIndex.findFirst(ariMin, ariMax);
			if (first == -1) {
				return 0;
			}

			int cur = first;
			int res = 0;

			if (sectionIndex_.seekToSection(PericopeBlocksSection.SECTION_NAME, file_)) {
				while (true) {
					int ari = pericopeIndex.getAri(cur);
					if (ari >= ariMax) { // no more
						break;
					}
					
					int offset = pericopeIndex.offsets[cur];
					PericopeBlocksSection.read
					PericopeBlock block = pericopeIndex.getBlock(in, cur);
					cur++;
					
					if (res < max) {
						aris[res] = ari;
						blocks[res] = block;
						res++;
					} else {
						break;
					}
				}
			}

			return res;
		} catch (Exception e) {
			Log.e(TAG, "gagal muatPerikop", e); //$NON-NLS-1$
			return 0;
		}
	}
}
