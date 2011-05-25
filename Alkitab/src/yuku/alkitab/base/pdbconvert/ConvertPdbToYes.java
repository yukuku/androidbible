package yuku.alkitab.base.pdbconvert;

import android.content.*;
import android.util.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;

import yuku.alkitab.yes.*;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.IsiSeksi;
import yuku.alkitab.yes.YesFile.Kitab;
import yuku.bintex.*;

import com.compactbyte.android.bible.*;
import com.compactbyte.bibleplus.reader.*;

public class ConvertPdbToYes {
	public static final String TAG = ConvertPdbToYes.class.getSimpleName();

	public static final int VERSI_CONVERTER = 1;

	private BiblePlusPDB pdb;

	private Kitab[] xkitab_;
	private int[] kitabPosToBookPosMap_;

	ConvertProgressListener convertProgressListener;
	
	public interface ConvertProgressListener {
		void onProgress(int at, String message);
		void onFinish();
	}
	
	public static class ConvertResult {
		public Exception exception;
	}
	
	public void setConvertProgressListener(ConvertProgressListener l) {
		this.convertProgressListener = l;
	}
	
	private void progress(int at, String message) {
		if (convertProgressListener != null) {
			convertProgressListener.onProgress(at, message);
		}
	}
	
	private void finish() {
		if (convertProgressListener != null) {
			convertProgressListener.onFinish();
		}
	}
	
	public ConvertPdbToYes() {
		SortedMap<String,Charset> charsets = Charset.availableCharsets();
		for (Map.Entry<String, Charset> charset: charsets.entrySet()) {
			Log.d(TAG, "available charset: " + charset.getKey());
		}
	}
	
	public ConvertResult convert(Context context, String filenamepdb, String namafileyes) {
		ConvertResult res = new ConvertResult();
		
		try {
			progress(0, "Opening PDB file");
			PDBFileStream stream = new PDBFileStream(filenamepdb);
			pdb = new BiblePlusPDB(stream, Tabs.hebrewTab, Tabs.greekTab);
			progress(10, "Loading version info");
			pdb.loadVersionInfo();
			progress(20, "Loading word index");
			pdb.loadWordIndex();

			Log.d(TAG, "============ baca info versi selesai");
			
			Log.d(TAG, "versionName: " + pdb.getVersionName());
			Log.d(TAG, "encoding: " + pdb.getEncoding());
			
			int nbook = pdb.getBookCount();
			Log.d(TAG, "getBookCount = " + nbook);
			
			// tempatin kitab2 di posisi yang betul, index array xkitab
			// 0 = kejadian
			// 65 = wahyu
			// 66 sampe 87, terdaftar dalam PdbNumberToAriMapping
			// selain itu, belum ada di mana2, maka kita buang aja (FIXME kasih warning)
			progress(30, "Analyzing available books");
			{
				int maxKitabPos = 0;
				for (int bookPos = 0; bookPos < nbook; bookPos++) {
					BookInfo bookInfo = pdb.getBook(bookPos);
					bookInfo.openBook();
					int bookNumber = bookInfo.getBookNumber();
					int kitabPos = PdbNumberToAriMapping.pdbNumberToAriKitab(bookNumber);
					if (kitabPos >= 0) {
						if (kitabPos > maxKitabPos) maxKitabPos = kitabPos;
					} else {
						Log.w(TAG, "bookNumber " + bookNumber + " GA DIKENAL");
					}
				}
				// panjang array xkitab_ adalah menurut maxKitabPos
				xkitab_ = new Kitab[maxKitabPos + 1];
				kitabPosToBookPosMap_ = new int[maxKitabPos + 1];
				for (int i = 0; i < kitabPosToBookPosMap_.length; i++) kitabPosToBookPosMap_[i] = -1;
			}
			
			progress(40, "Mapping books");
			for (int bookPos = 0; bookPos < nbook; bookPos++) {
				BookInfo bookInfo = pdb.getBook(bookPos);
				bookInfo.openBook();

				int bookNumber = bookInfo.getBookNumber();
				int kitabPos = PdbNumberToAriMapping.pdbNumberToAriKitab(bookNumber);
				if (kitabPos < 0) {
					Log.w(TAG, "bookNumber " + bookNumber + " GA DIKENAL");
				} else {
					kitabPosToBookPosMap_[kitabPos] = bookPos;
				}
			}
			
			Log.d(TAG, "kitabPosToBookPosMap_ (len " + kitabPosToBookPosMap_.length + ") = " + Arrays.toString(kitabPosToBookPosMap_));

			Log.d(TAG, "============ baca daftar kitab selesai");
			
			progress(100, "Constructing version info");
			final InfoEdisi infoEdisi = getInfoEdisi();

			progress(200, "Constructing book info");
			final InfoKitab infoKitab = getInfoKitab(200);
			
			progress(400, "Constructing translated file");
			YesFile file = new YesFile() {{
				this.xseksi = new Seksi[] {
					new SeksiBernama("infoEdisi___") {
						@Override public IsiSeksi isi() {
							return infoEdisi;
						}
					},
					new SeksiBernama("infoKitab___") {
						@Override public IsiSeksi isi() {
							return infoKitab;
						}
					},
//					new SeksiBernama("perikopIndex") {
//						@Override public IsiSeksi isi() {
//							return new NemplokSeksi("../Alkitab/publikasi/bis_perikop_index_bt.bt");
//						}
//					},
//					new SeksiBernama("perikopBlok_") {
//						@Override public IsiSeksi isi() {
//							return new NemplokSeksi("../Alkitab/publikasi/bis_perikop_blok_bt.bt");
//						}
//					},
					new SeksiBernama("teks________") {
						@Override public IsiSeksi isi() {
							return new LazyTeks();
						}
					}
				};
			}};
			
			progress(600, "Opening translated file");
			RandomAccessFile out = new RandomAccessFile(namafileyes, "rw");
			progress(700, "Writing translated file");
			file.output(out);
			out.close();
			
			pdb.close();
			stream.close();
		} catch (Exception e) {
			pdb = null;
			Log.e(TAG, "Eror baca pdb: ", e);
			res.exception = e;
		}
		finish();
		return res;
	}
	
	private InfoEdisi getInfoEdisi() {
		return new InfoEdisi() {{
			versi = 2;
			nama = pdb.getVersionName();
			judul = pdb.getVersionName();
			nkitab = xkitab_.length; // INGAT: BISA BOLONG_BOLONG
			perikopAda = 0; // FIXME ada
			encoding = 2; // utf-8
		}};
	}

	private InfoKitab getInfoKitab(int baseProgress) throws Exception {
		// untuk offset teks dari awal seksi teks
		int offsetTotal = 0;
		// untuk offset teks dari awal kitab
		int offsetLewat = 0;
		
		for (int kitabPos = 0; kitabPos < xkitab_.length; kitabPos++) {
			int bookPos = kitabPosToBookPosMap_[kitabPos];
			if (bookPos == -1) {
				continue;
			}
			BookInfo bookInfo = pdb.getBook(bookPos);
			bookInfo.openBook();
			
			progress(baseProgress + 1 + kitabPos, "Reading book info: " + bookInfo.getFullName());

			Kitab k = new Kitab();
			k.versi = 2;
			k.pos = kitabPos;
			k.ayatLoncat = 0;
			k.encoding = 2; // utf-8
			k.judul = bookInfo.getFullName();
			k.nama = bookInfo.getShortName();
			k.npasal = bookInfo.getChapterCount();
			k.nayat = new int[k.npasal];
			k.offset = offsetTotal;
			k.pasal_offset = new int[k.npasal + 1];
			k.pdbBookNumber = bookInfo.getBookNumber();

			k.pasal_offset[0] = 0;
			for (int pasal_0 = 0; pasal_0 < k.npasal; pasal_0++) {
				k.nayat[pasal_0] = bookInfo.getVerseCount(pasal_0 + 1);
				
				for (int ayat_0 = 0; ayat_0 < k.nayat[pasal_0]; ayat_0++) {
					offsetLewat += bookInfo.getVerse(pasal_0 + 1, ayat_0 + 1).getBytes("utf-8").length + 1; // +1 buat \n
				}
				k.pasal_offset[pasal_0+1] = offsetLewat;
			}
			Log.d(TAG, "kitab " + k.judul + " (bookNumber=" + bookInfo.getBookNumber() + ", kitabPos=" + kitabPos + ") pasal_offset: " + Arrays.toString(k.pasal_offset));

			xkitab_[kitabPos] = k;
			
			//# reset
			offsetTotal += offsetLewat;
			offsetLewat = 0;
		}
		
		return new InfoKitab() {{
			this.xkitab = xkitab_;
		}};
	}
	
	public class LazyTeks implements IsiSeksi {
		@Override
		public void toBytes(BintexWriter writer) throws Exception {
			final int nkitab = pdb.getBookCount();
			for (int ki = 0; ki < nkitab; ki++) {
				BookInfo bookInfo = pdb.getBook(ki);
				bookInfo.openBook();

				int npasal = bookInfo.getChapterCount();
				for (int pi = 0; pi < npasal; pi++) {
					int nayat = bookInfo.getVerseCount(pi + 1);
					for (int ai = 0; ai < nayat; ai++) {
						String s = bookInfo.getVerse(pi + 1, ai + 1);
						writer.writeRaw(s.getBytes("utf-8"));
						writer.writeUint8('\n');
					}
				}
			}
		}
	}

	private String[] bacaPasal(Kitab kitab, int pasal_1) throws IOException {
		BookInfo bookInfo = pdb.getBook(kitab.pos);
		bookInfo.openBook();
		int nayat = bookInfo.getVerseCount(pasal_1);
		
		String[] xayat = new String[nayat];
		for (int i = 0; i < nayat; i++) {
			int ayat_1 = i + 1;
			StringBuffer[] vv = bookInfo.getCompleteVerse(pasal_1, ayat_1);
			xayat[i] = vv[0].toString();
			
			for (int j = 1; j < 4; j++) {
				if (vv[j].length() != 0) {
					Log.d(TAG, "!!! Kitab " + kitab.judul + "(" + kitab.pos + ") " + pasal_1 + ":" + ayat_1 + " bagian " + j + ": " + vv[j].toString());
				}
			}
		}

		return xayat;
	}
}
