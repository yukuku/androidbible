package yuku.alkitab.yes2;

import android.util.Log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import yuku.afw.D;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.PericopeIndex;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.storage.Reader;
import yuku.alkitab.base.storage.VerseTextDecoder;
import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.model.Yes2Book;
import yuku.alkitab.yes2.section.BooksInfo;
import yuku.alkitab.yes2.section.VersionInfo;
import yuku.bintex.BintexReader;

public class Yes2Reader implements Reader {
	private static final String TAG = Yes2Reader.class.getSimpleName();

	private String filename_;
	private RandomAccessFile file_;
	private VerseTextDecoder decoder_;

	private long text_offsetBase_;
	private long pericopeBlock_offsetBase_;

	private VersionInfo versionInfo_;

	public Yes2Reader(String filename) {
		this.filename_ = filename;
	}

	/**
	 * Jump to a section. After this method, the file pointer will be at the beginning of a section content.
	 * 
	 * @return section size or -1 if fails
	 */
	private int skipUntilSection(String sectionName) throws Exception {
		file_.seek(8); // setelah header

		while (true) {
			String name = readSectionName(file_);

			if (name == null || name.equals("____________")) { //$NON-NLS-1$
				// We have reached EOF. Tell that the looked for section does not exist
				Log.d(TAG, "Section not found: " + sectionName); //$NON-NLS-1$
				return -1;
			}

			int size = readSectionSize(file_);

			if (name.equals(sectionName)) {
				return size;
			} else {
				Log.d(TAG, "section skipped: " + name); //$NON-NLS-1$
				file_.skipBytes(size);
			}
		}
	}

	private synchronized void init() throws Exception {
		if (file_ == null) {
			file_ = new RandomAccessFile(filename_, "r"); //$NON-NLS-1$
			file_.seek(0);

			{ // check header
				byte[] buf = new byte[8];
				file_.read(buf);
				if (!Arrays.equals(buf, new byte[] { (byte) 0x98, 0x58, 0x0d, 0x0a, 0x00, 0x5d, (byte) 0xe0, 0x02 /* yes version 2 */})) {
					throw new RuntimeException("YES2: Header is incorrect. Found: " + Arrays.toString(buf)); //$NON-NLS-1$
				}
			}

			readVersionInfo();

			skipUntilSection("text________"); //$NON-NLS-1$
			text_offsetBase_ = file_.getFilePointer();
			Log.d(TAG, "text_offsetBase_=" + text_offsetBase_); //$NON-NLS-1$
		}
	}

	@Override public String getShortName() {
		try {
			init();
			return versionInfo_.shortName;
		} catch (Exception e) {
			Log.e(TAG, "init error", e); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
	}

	@Override public String getLongName() {
		try {
			init();
			return versionInfo_.longName;
		} catch (Exception e) {
			Log.e(TAG, "init error", e); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
	}

	public String getDescription() {
		try {
			init();
			return versionInfo_.description;
		} catch (Exception e) {
			Log.e(TAG, "init error", e); //$NON-NLS-1$
			return null;
		}
	}

	public void readVersionInfo() {
		Log.d(TAG, "@@readVersionInfo"); //$NON-NLS-1$

		try {
			skipUntilSection("versionInfo_"); //$NON-NLS-1$

			RandomInputStream ris = new RandomInputStream(file_);
			versionInfo_ = new VersionInfo.Reader().toSection(ris);

			Log.d(TAG, "readVersionInfo done");
		} catch (Exception e) {
			Log.e(TAG, "readVersionInfo error", e); //$NON-NLS-1$
		}
	}

	@Override public Book[] loadBooks() {
		Log.d(TAG, "@@loadBooks"); //$NON-NLS-1$

		try {
			init();

			skipUntilSection("booksInfo___"); //$NON-NLS-1$
			RandomInputStream ris = new RandomInputStream(file_);
			BooksInfo booksInfo = new BooksInfo.Reader().toSection(ris);

			Book[] res = new Book[booksInfo.yes2Books.size()];
			for (int i = 0; i < res.length; i++) {
				Yes2Book yes2Book = booksInfo.yes2Books.get(i);
				Book book = res[i] = new Book();

				book.bookId = yes2Book.bookId;
				book.judul = yes2Book.shortName;
				book.nama = yes2Book.shortName;
				book.nchapter = yes2Book.chapter_count;
				book.nverses = yes2Book.verse_counts;
				book.offset = yes2Book.offset;
				book.pasal_offset = yes2Book.chapter_offsets;
			}

			return res;
		} catch (Exception e) {
			Log.e(TAG, "loadBooks error", e); //$NON-NLS-1$
			return null;
		}
	}

	@Override public String[] loadVerseText(Book book, int chapter_1, boolean dontSeparateVerses, boolean lowercase) {
		// init text decoder 
		if (decoder_ == null) {
			int textEncoding = versionInfo_.textEncoding;
			if (textEncoding == 1) {
				decoder_ = new VerseTextDecoder.Ascii();
			} else if (textEncoding == 2) {
				decoder_ = new VerseTextDecoder.Utf8();
			} else {
				Log.e(TAG, "Text encoding " + textEncoding + " not supported! Fallback to ascii."); //$NON-NLS-1$ //$NON-NLS-2$
				decoder_ = new VerseTextDecoder.Ascii();
			}
		}

		try {
			init();

			if (chapter_1 <= 0 || chapter_1 > book.nchapter) {
				return null;
			}

			long seekTo = text_offsetBase_;
			seekTo += book.offset;
			seekTo += book.pasal_offset[chapter_1 - 1];
			file_.seek(seekTo);

			int length = book.pasal_offset[chapter_1] - book.pasal_offset[chapter_1 - 1];

			byte[] ba = new byte[length];
			file_.read(ba);

			if (dontSeparateVerses) {
				return new String[] { decoder_.makeIntoSingleString(ba, lowercase) };
			} else {
				return decoder_.separateIntoVerses(ba, lowercase);
			}
		} catch (Exception e) {
			Log.e(TAG, "loadVerseText error", e); //$NON-NLS-1$
			return null;
		}
	}

	@SuppressWarnings("deprecation") static String readSectionName(RandomAccessFile f) throws IOException {
		byte[] buf = new byte[12];
		int read = f.read(buf);
		return read <= 0 ? null : new String(buf, 0);
	}

	static int readSectionSize(RandomAccessFile f) throws IOException {
		return f.readInt();
	}

	@Override public PericopeIndex loadPericopeIndex() {
		long wmulai = System.currentTimeMillis();
		try {
			init();

			if (versionInfo_.hasPericopes == 0) {
				return null;
			}

			int ukuran = skipUntilSection("perikopIndex"); //$NON-NLS-1$

			if (ukuran < 0) {
				Log.d(TAG, "Tidak ada seksi 'perikopIndex'"); //$NON-NLS-1$
				return null;
			}

			BintexReader in = new BintexReader(new RandomInputStream(file_));
			return PericopeIndex.read(in);
		} catch (Exception e) {
			Log.e(TAG, "bacaIndexPerikop error", e); //$NON-NLS-1$
			return null;
		} finally {
			Log.d(TAG, "Muat index perikop butuh ms: " + (System.currentTimeMillis() - wmulai)); //$NON-NLS-1$
		}
	}

	@Override public int loadPericope(Version version, int kitab, int pasal, int[] xari, PericopeBlock[] xblok, int max) {
		try {
			init();

			if (D.EBUG) Log.d(TAG, "muatPerikop dipanggil untuk kitab=" + kitab + " pasal_1=" + pasal); //$NON-NLS-1$ //$NON-NLS-2$

			PericopeIndex pericopeIndex = version.getIndexPerikop();
			if (pericopeIndex == null) {
				return 0; // ga ada perikop!
			}

			int ariMin = Ari.encode(kitab, pasal, 0);
			int ariMax = Ari.encode(kitab, pasal + 1, 0);

			int pertama = pericopeIndex.findFirst(ariMin, ariMax);
			if (pertama == -1) {
				return 0;
			}

			int kini = pertama;
			int res = 0;

			if (pericopeBlock_offsetBase_ != 0) {
				file_.seek(pericopeBlock_offsetBase_);
			} else {
				skipUntilSection("perikopBlok_"); //$NON-NLS-1$
				pericopeBlock_offsetBase_ = file_.getFilePointer();
			}

			BintexReader in = new BintexReader(new RandomInputStream(file_));
			while (true) {
				int ari = pericopeIndex.getAri(kini);

				if (ari >= ariMax) {
					// habis. Uda ga relevan
					break;
				}

				PericopeBlock pericopeBlock = pericopeIndex.getBlock(in, kini);
				kini++;

				if (res < max) {
					xari[res] = ari;
					xblok[res] = pericopeBlock;
					res++;
				} else {
					break;
				}
			}

			return res;
		} catch (Exception e) {
			Log.e(TAG, "gagal muatPerikop", e); //$NON-NLS-1$
			return 0;
		}
	}
}
