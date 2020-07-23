package yuku.alkitab.yes1;

import java.util.List;
import yuku.alkitab.base.storage.OldVerseTextDecoder;
import yuku.alkitab.base.storage.VerseTextDecoder;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.io.BibleReader;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.FootnoteEntry;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.XrefEntry;
import yuku.alkitab.util.Ari;
import yuku.bintex.BintexReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class Yes1Reader implements BibleReader {
	private static final String TAG = Yes1Reader.class.getSimpleName();

	private RandomAccessFile f;
	private boolean initted = false;
	private VerseTextDecoder verseTextDecoder;

	private long text_baseOffset;
	private long pericopeBlock_baseOffset;

	private String locale;
	private String shortName;
	private String longName;
	private String description;
	private int book_count;
	private int has_pericopes = 0; // default ga ada
	private int encoding = 1; // 1 = ascii; 2 = utf-8;

	private Yes1PericopeIndex pericopeIndex_;

	public Yes1Reader(String filename) throws IOException {
		this.f = new RandomAccessFile(filename, "r");
	}

	/**
	 * @return size of section
	 */
	private int skipUntilSection(String section) throws Exception {
		f.seek(8); // setelah header

		while (true) {
			String sectionName = readSectionName(f);

			if (sectionName == null || sectionName.equals("____________")) {
				// sudah mencapai EOF. Maka kasih tau seksi ini ga ada.
				AppLog.d(TAG, "Seksi tidak ditemukan: " + section);
				return -1;
			}

			int ukuran = readSectionSize(f);

			if (sectionName.equals(section)) {
				return ukuran;
			} else {
				AppLog.d(TAG, "seksi dilewati: " + sectionName);
				f.skipBytes(ukuran);
			}
		}
	}

	private synchronized void init() throws Exception {
		if (initted) {
			return;
		}

		initted = true;

		f.seek(0);

		// cek header
		{
			byte[] buf = new byte[8];
			f.read(buf);
			if (!Arrays.equals(buf, new byte[]{(byte) 0x98, 0x58, 0x0d, 0x0a, 0x00, 0x5d, (byte) 0xe0, 0x01})) {
				throw new RuntimeException("Header ga betul. Ketemunya: " + Arrays.toString(buf));
			}
		}

		readVersionInfo();

		skipUntilSection("teks________");
		text_baseOffset = f.getFilePointer();
		AppLog.d(TAG, "text_baseOffset = " + text_baseOffset);
	}

	@Override
	public String getLocale() {
		return locale;
	}

	@Override
	public String getShortName() {
		try {
			init();
			return shortName;
		} catch (Exception e) {
			AppLog.e(TAG, "init error", e);
			return "";
		}
	}

	@Override
	public String getLongName() {
		try {
			init();
			return longName;
		} catch (Exception e) {
			AppLog.e(TAG, "init error", e);
			return "";
		}
	}

	public void readVersionInfo() {
		try {
			int size = skipUntilSection("infoEdisi___");
			byte[] buf = new byte[size];
			f.read(buf);
			BintexReader in = new BintexReader(new ByteArrayInputStream(buf));

			String nama = null;
			label:
			while (true) {
				String key = in.readShortString();

				switch (key) {
					case "versi":
						int versi = in.readInt();
						if (versi > 2) throw new RuntimeException("Version number in version info: " + versi + " not supported");

						break;
					case "format": // ini deprecated, sudah diganti jadi "versi". Tapi harus tetap dikenali, kalo ga akan crash.
						in.readInt(); // buang

						break;
					case "nama":
						nama = in.readShortString();
						break;
					case "shortName":
						this.shortName = in.readShortString();
						break;
					case "shortTitle":
						this.shortName = in.readShortString();
						break;
					case "judul":
						this.longName = in.readShortString();
						break;
					case "keterangan":
						this.description = in.readLongString();
						break;
					case "nkitab":
						this.book_count = in.readInt();
						break;
					case "perikopAda":
						this.has_pericopes = in.readInt();
						break;
					case "encoding":
						this.encoding = in.readInt();
						break;
					case "locale":
						this.locale = in.readShortString();
						break;
					case "end":
						break label;
					default:
						throw new RuntimeException("got unknown key in version info: " + key);

				}
			}

			AppLog.d(TAG, "readVersionInfo selesai, nama=" + nama + " judul=" + longName + " book_count=" + book_count);
		} catch (Exception e) {
			AppLog.e(TAG, "readVersionInfo error", e);
		}
	}

	@Override
	public Book[] loadBooks() {
		try {
			AppLog.d(TAG, "bacaInfoKitab dipanggil");

			init();

			Book[] res = new Book[256];

			int ukuran = skipUntilSection("infoKitab___");
			byte[] buf = new byte[ukuran];
			f.read(buf);
			BintexReader in = new BintexReader(new ByteArrayInputStream(buf));

			AppLog.d(TAG, "akan membaca " + this.book_count + " kitab");
			for (int kitabIndex = 0; kitabIndex < this.book_count; kitabIndex++) {
				Yes1Book k = new Yes1Book();

				// kalau true, berarti ini kitab NULL
				boolean kosong = false;

				label:
				for (int keyKe = 0; ; keyKe++) {
					String key = in.readShortString();

					switch (key) {
						case "versi":
							int versi = in.readInt();
							if (versi > 2) throw new RuntimeException("Versi Kitab (lebih dari 2): " + versi + " tidak dikenal");
							break;
						case "pos":
							k.bookId = in.readInt();
							break;
						case "nama":
							k.shortName = in.readShortString();
							break;
						case "judul":
							k.shortName = in.readShortString();
							break;
						case "npasal":
							k.chapter_count = in.readInt();
							break;
						case "nayat":
							k.verse_counts = new int[k.chapter_count];
							for (int i = 0; i < k.chapter_count; i++) {
								k.verse_counts[i] = in.readUint8();
							}
							break;
						case "ayatLoncat":
							// ignored
							in.readInt();
							break;
						case "pdbBookNumber":
							// ignored
							in.readInt();
							break;
						case "pasal_offset":
							k.chapter_offsets = new int[k.chapter_count + 1]; // harus ada +1nya kalo YesPembaca

							for (int i = 0; i < k.chapter_offsets.length; i++) {
								k.chapter_offsets[i] = in.readInt();
							}
							break;
						case "encoding":
							// ignored, deprecated
							in.readInt();
							break;
						case "offset":
							k.offset = in.readInt();
							break;
						case "end":
							// sudah end sebelum baca apapun?
							if (keyKe == 0) kosong = true;
							break label;
						default:
							AppLog.w(TAG, "ada key ga dikenal di kitab " + k + " di infoKitab: " + key);
							break label;
					}
				}

				if (!kosong) {
					if (k.bookId < 0 || k.bookId >= res.length) {
						throw new RuntimeException("ada kitabPos yang sangat besar: " + k.bookId);
					}
					res[k.bookId] = k;
				}
			}

			// truncate res supaya ukuran arraynya jangan terlalu besar, sampe non-null terakhir
			int lenBaru = 0;
			for (int i = 0; i < res.length; i++) {
				if (res[i] != null) lenBaru = i + 1;
			}
			Book[] resBaru = new Book[lenBaru];
			System.arraycopy(res, 0, resBaru, 0, lenBaru);
			res = resBaru;

			return res;
		} catch (Exception e) {
			AppLog.e(TAG, "bacaInfoKitab error", e);
			return null;
		}
	}

	@Override
	public Yes1SingleChapterVerses loadVerseText(Book book, int pasal_1, boolean janganPisahAyat, boolean hurufKecil) {
		// init pembacaDecoder
		if (verseTextDecoder == null) {
			if (encoding == 1) {
				verseTextDecoder = new OldVerseTextDecoder.Ascii();
			} else if (encoding == 2) {
				verseTextDecoder = new OldVerseTextDecoder.Utf8();
			} else {
				AppLog.e(TAG, "Encoding " + encoding + " not recognized!");
				verseTextDecoder = new OldVerseTextDecoder.Ascii();
			}
			AppLog.d(TAG, "encoding " + encoding + " so decoder is " + verseTextDecoder.getClass().getName());
		}

		try {
			init();

			if (pasal_1 > book.chapter_count) {
				return null;
			}

			Yes1Book yesBook = (Yes1Book) book;

			long seekTo = text_baseOffset;
			seekTo += yesBook.offset;
			seekTo += yesBook.chapter_offsets[pasal_1 - 1];
			f.seek(seekTo);

			int length = yesBook.chapter_offsets[pasal_1] - yesBook.chapter_offsets[pasal_1 - 1];

			if (BuildConfig.DEBUG) AppLog.d(TAG, "muatTeks kitab=" + book.shortName + " pasal_1=" + pasal_1 + " offset=" + yesBook.offset + " offset pasal: " + yesBook.chapter_offsets[pasal_1 - 1]);

			byte[] ba = new byte[length];
			f.read(ba);

			if (janganPisahAyat) {
				return new Yes1SingleChapterVerses(new String[]{verseTextDecoder.makeIntoSingleString(ba, hurufKecil)});
			} else {
				return new Yes1SingleChapterVerses(verseTextDecoder.separateIntoVerses(ba, hurufKecil));
			}
		} catch (Exception e) {
			AppLog.e(TAG, "muatTeks error", e);
			return null;
		}
	}

	@SuppressWarnings("deprecation")
	static String readSectionName(RandomAccessFile f) throws IOException {
		byte[] buf = new byte[12];
		int read = f.read(buf);
		return read <= 0 ? null : new String(buf, 0);
	}

	static int readSectionSize(RandomAccessFile f) throws IOException {
		return f.readInt();
	}

	private Yes1PericopeIndex loadPericopeIndex() {
		if (pericopeIndex_ != null) {
			return pericopeIndex_;
		}

		long wmulai = System.currentTimeMillis();
		try {
			init();

			if (has_pericopes == 0) {
				return null;
			}

			int ukuran = skipUntilSection("perikopIndex");

			if (ukuran < 0) {
				AppLog.d(TAG, "Tidak ada seksi 'perikopIndex'");
				return null;
			}

			BintexReader in = new BintexReader(new RandomInputStream(f));

			pericopeIndex_ = Yes1PericopeIndex.read(in);
			return pericopeIndex_;
		} catch (Exception e) {
			AppLog.e(TAG, "bacaIndexPerikop error", e);
			return null;
		} finally {
			AppLog.d(TAG, "Muat index perikop butuh ms: " + (System.currentTimeMillis() - wmulai));
		}
	}

	@Override
	public int loadPericope(int bookId, int chapter_1, List<Integer> aris, List<PericopeBlock> pericopeBlocks) {
		try {
			init();

			if (BuildConfig.DEBUG) AppLog.d(TAG, "loadPericope called for bookId=" + bookId + " chapter_1=" + chapter_1);

			Yes1PericopeIndex pericopeIndex = loadPericopeIndex();
			if (pericopeIndex == null) {
				return 0; // ga ada perikop!
			}

			int ariMin = Ari.encode(bookId, chapter_1, 0);
			int ariMax = Ari.encode(bookId, chapter_1 + 1, 0);

			int first = pericopeIndex.findFirst(ariMin, ariMax);
			if (first == -1) {
				return 0;
			}

			int current = first;
			int res = 0;

			if (pericopeBlock_baseOffset != 0) {
				f.seek(pericopeBlock_baseOffset);
			} else {
				skipUntilSection("perikopBlok_");
				pericopeBlock_baseOffset = f.getFilePointer();
			}

			BintexReader in = new BintexReader(new RandomInputStream(f));
			while (true) {
				int ari = pericopeIndex.getAri(current);

				if (ari >= ariMax) {
					// habis. Uda ga relevan
					break;
				}

				Yes1PericopeBlock pericopeBlock = pericopeIndex.getBlock(in, current);
				current++;

				aris.add(ari);
				pericopeBlocks.add(pericopeBlock);
				res++;
			}

			return res;
		} catch (Exception e) {
			AppLog.e(TAG, "failed to loadPericope", e);
			return 0;
		}
	}

	/**
	 * Mungkin null kalo ga ada.
	 */
	@Override
	public String getDescription() {
		try {
			init();
			return description;
		} catch (Exception e) {
			AppLog.e(TAG, "init error", e);
			return null;
		}
	}

	@Override
	public XrefEntry getXrefEntry(int arif) {
		// YES1 file cannot contain xref entries.
		return null;
	}

	@Override
	public FootnoteEntry getFootnoteEntry(final int arif) {
		// YES1 file cannot contain footnote entries.
		return null;
	}
}
