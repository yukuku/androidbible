package yuku.alkitab.base.storage;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import yuku.alkitab.base.S;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.PericopeIndex;
import yuku.alkitab.base.model.Version;
import yuku.bintex.BintexReader;

public class InternalReader implements Reader {
	public static final String TAG = InternalReader.class.getSimpleName();

	// # buat cache Asset
	private static InputStream cache_inputStream = null;
	private static String cache_file = null;
	private static int cache_posInput = -1;

	private final String edisiPrefix;
	private final String edisiShortName;
	private final String edisiLongName;
	private final ReaderDecoder readerDecoder;

	public InternalReader(String edisiPrefix, String edisiShortName, String edisiLongName, ReaderDecoder readerDecoder) {
		this.edisiPrefix = edisiPrefix;
		this.edisiShortName = edisiShortName;
		this.edisiLongName = edisiLongName;
		this.readerDecoder = readerDecoder;
	}
	
	@Override public String getShortName() {
		return edisiShortName;
	}

	@Override public String getLongName() {
		return edisiLongName;
	}

	@Override public Book[] loadBooks() {
		InputStream is = S.openRaw(edisiPrefix + "_index_bt"); //$NON-NLS-1$
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

	private static Book bacaKitab(BintexReader in, int pos) throws IOException {
		Book k = new Book();
		k.bookId = pos;

		// autostring bookName
		// shortstring resName
		// int chapter_count
		// uint8[chapter_count] verse_counts
		// int[chapter_count+1] chapter_offsets

		k.nama = k.judul = in.readAutoString();
		k.file = in.readShortString();
		k.nchapter = in.readInt();

		k.nverses = new int[k.nchapter];
		for (int i = 0; i < k.nchapter; i++) {
			k.nverses[i] = in.readUint8();
		}

		k.pasal_offset = new int[k.nchapter + 1];
		for (int i = 0; i < k.nchapter + 1; i++) {
			k.pasal_offset[i] = in.readInt();
		}

		return k;
	}

	@Override public String[] loadVerseText(Book book, int pasal_1, boolean janganPisahAyat, boolean hurufKecil) {
		if (pasal_1 < 1 || pasal_1 > book.nchapter) {
			return null;
		}

		int offset = book.pasal_offset[pasal_1 - 1];
		int length = 0;

		try {
			InputStream in;

			// Log.d("alki", "muatTeks kitab=" + kitab.nama + " pasal[1base]=" + pasal + " offset=" + offset);
			// Log.d("alki", "muatTeks cache_file=" + cache_file + " cache_posInput=" + cache_posInput);
			if (cache_inputStream == null) {
				// kasus 1: belum buka apapun
				in = S.openRaw(book.file);
				cache_inputStream = in;
				cache_file = book.file;

				in.skip(offset);
				cache_posInput = offset;
				// Log.d("alki", "muatTeks masuk kasus 1");
			} else {
				// kasus 2: uda pernah buka. Cek apakah filenya sama
				if (book.file.equals(cache_file)) {
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

						in = S.openRaw(book.file);
						cache_inputStream = in;

						in.skip(offset);
						cache_posInput = offset;
						// Log.d("alki", "muatTeks masuk kasus 2.1 jelek");
					}
				} else {
					// kasus 2.2: filenya beda, tutup dan buka baru
					cache_inputStream.close();

					in = S.openRaw(book.file);
					cache_inputStream = in;
					cache_file = book.file;

					in.skip(offset);
					cache_posInput = offset;
					// Log.d("alki", "muatTeks masuk kasus 2.2");
				}
			}

			if (pasal_1 == book.nchapter) {
				length = in.available();
			} else {
				length = book.pasal_offset[pasal_1] - offset;
			}

			byte[] ba = new byte[length];
			in.read(ba);
			cache_posInput += ba.length;
			// jangan ditutup walau uda baca. Siapa tau masih sama filenya dengan sebelumnya.

			if (janganPisahAyat) {
				return new String[] { readerDecoder.jadikanStringTunggal(ba, hurufKecil) };
			} else {
				return readerDecoder.pisahJadiAyat(ba, hurufKecil);
			}
		} catch (IOException e) {
			return new String[] { e.getMessage() };
		}
	}

	@Override public PericopeIndex loadPericopeIndex() {
		long wmulai = System.currentTimeMillis();

		InputStream is = S.openRaw(edisiPrefix + "_pericope_index_bt"); //$NON-NLS-1$
		if (is == null) {
			return null;
		}

		BintexReader in = new BintexReader(is);
		try {
			return PericopeIndex.read(in);

		} catch (IOException e) {
			Log.e(TAG, "baca perikop index ngaco", e); //$NON-NLS-1$
			return null;
		} finally {
			in.close();
			Log.d(TAG, "Muat index perikop butuh ms: " + (System.currentTimeMillis() - wmulai)); //$NON-NLS-1$
		}
	}

	@Override public int loadPericope(Version version, int kitab, int pasal, int[] xari, PericopeBlock[] xblok, int max) {
		PericopeIndex pericopeIndex = version.getIndexPerikop();

		if (pericopeIndex == null) {
			return 0; // ga ada perikop!
		}

		int ariMin = Ari.encode(kitab, pasal, 0);
		int ariMax = Ari.encode(kitab, pasal + 1, 0);
		int res = 0;

		int pertama = pericopeIndex.findFirst(ariMin, ariMax);

		if (pertama == -1) {
			return 0;
		}

		int kini = pertama;

		BintexReader in = new BintexReader(S.openRaw(edisiPrefix + "_pericope_blocks_bt")); //$NON-NLS-1$
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
					xari[res] = ari;
					xblok[res] = pericopeBlock;
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
}
