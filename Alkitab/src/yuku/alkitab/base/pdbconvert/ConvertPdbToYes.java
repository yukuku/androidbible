package yuku.alkitab.base.pdbconvert;

import android.content.*;
import android.util.*;

import java.io.*;
import java.util.*;

import yuku.alkitab.base.config.*;
import yuku.alkitab.base.model.*;
import yuku.alkitab.yes.*;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.IsiSeksi;
import yuku.alkitab.yes.YesFile.Kitab;
import yuku.andoutil.*;
import yuku.bintex.*;

import com.compactbyte.android.bible.*;
import com.compactbyte.bibleplus.reader.*;

public class ConvertPdbToYes {
	public static final String TAG = ConvertPdbToYes.class.getSimpleName();

	public static final int VERSI_CONVERTER = 1;

	private BiblePlusPDB pdb;

	private Kitab[] xkitab_;
	private int[] kitabPosToBookPosMap_;
	private int nblokPerikop_ = 0;
	private ByteArrayOutputStream nantinyaPerikopBlokBaos_ = new ByteArrayOutputStream();
	private BintexWriter nantinyaPerikopBlok_ = new BintexWriter(nantinyaPerikopBlokBaos_);
	private IntArrayList xariPerikopBlok_ = new IntArrayList();
	private IntArrayList xposisiPerikopBlok_ = new IntArrayList();

	ConvertProgressListener convertProgressListener;
	
	public interface ConvertProgressListener {
		void onProgress(int at, String message);
		void onFinish();
	}
	
	public static class ConvertParams {
		public String inputEncoding;
		public boolean includeAddlTitle;
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
	}
	
	public ConvertResult convert(Context context, String filenamepdb, String namafileyes, ConvertParams params) {
		ConvertResult res = new ConvertResult();
		
		try {
			progress(0, "Opening PDB file");
			pdb = new BiblePlusPDB(new PDBFileStream(filenamepdb), Tabs.hebrewTab, Tabs.greekTab);
			if (params.inputEncoding != null) pdb.setEncoding(params.inputEncoding);
			Log.d(TAG, "Encoding used: " + params.inputEncoding);
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
			
			// sekaligus bangun perikop blok dan perikop index.
			progress(100, "Constructing book info");
			final InfoKitab infoKitab = getInfoKitab(100, params.includeAddlTitle);
			
			progress(200, "Constructing version info");
			final InfoEdisi infoEdisi = getInfoEdisi();
			
			progress(400, "Constructing translated file");
			YesFile file = new YesFile() {{
				boolean adaPerikop = nblokPerikop_ > 0;
				this.xseksi = new Seksi[adaPerikop? 5: 3];
				
				xseksi[0] = new SeksiBernama("infoEdisi___") {
					@Override public IsiSeksi isi() {
						return infoEdisi;
					}
				};
				xseksi[1] = new SeksiBernama("infoKitab___") {
					@Override public IsiSeksi isi() {
						return infoKitab;
					}
				};
				if (adaPerikop) {
					xseksi[2] = new SeksiBernama("perikopIndex") {
						@Override public IsiSeksi isi() {
							return new IsiSeksi() {
								@Override public void toBytes(BintexWriter writer) throws Exception {
									progress(710, "Writing " + nblokPerikop_ + " section/pericope indexes");
									writer.writeInt(nblokPerikop_);
									for (int i = 0; i < nblokPerikop_; i++) {
										writer.writeInt(xariPerikopBlok_.get(i)); // ari untuk entri ini
										writer.writeInt(xposisiPerikopBlok_.get(i)); // ofset ke blok untuk entri ini
									}
								}
							};
						}
					};
					xseksi[3] = new SeksiBernama("perikopBlok_") {
						@Override public IsiSeksi isi() {
							return new IsiSeksi() {
								@Override public void toBytes(BintexWriter writer) throws Exception {
									progress(720, "Writing " + nblokPerikop_ + " section/pericope titles");
									nantinyaPerikopBlokBaos_.writeTo(writer.getOutputStream());
								}
							};
						}
					};
				}
				xseksi[xseksi.length - 1] = new SeksiBernama("teks________") {
					@Override public IsiSeksi isi() {
						return new LazyTeks(800);
					}
				};
			}};
			
			progress(600, "Opening translated file");
			RandomAccessFile out = new RandomAccessFile(namafileyes, "rw");
			progress(700, "Writing translated file");
			file.output(out);
			out.close();
			
			pdb.close();
			new PDBFileStream(filenamepdb).close();
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
			keterangan = pdb.getVersionInfo();
			nkitab = xkitab_.length; // INGAT: BISA BOLONG_BOLONG
			perikopAda = nblokPerikop_ == 0? 0: 1;
			encoding = 2; // utf-8
		}};
	}

	private InfoKitab getInfoKitab(int baseProgress, boolean includeAddlTitle) throws Exception {
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
					String[] complete = bookInfo.getCompleteVerse(pasal_0 + 1, ayat_0 + 1);
					offsetLewat += complete[0].getBytes("utf-8").length + 1; // +1 buat \n
					
					// perikop!
					if (includeAddlTitle) {
						if (complete[3].length() > 0) simpanBlok(3, complete[3], kitabPos, pasal_0, ayat_0); 
						if (complete[2].length() > 0) simpanBlok(2, complete[2], kitabPos, pasal_0, ayat_0); 
					}
					if (complete[1].length() > 0) simpanBlok(1, complete[1], kitabPos, pasal_0, ayat_0);
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
	
	/** Perhatikan {@link Blok} untuk cara bikinnya. */
	private void simpanBlok(int jenis, String judul, int kitab_0, int pasal_0, int ayat_0) {
		int ari = Ari.encode(kitab_0, pasal_0 + 1, ayat_0 + 1);

		if (D.EBUG) {
			Log.d(TAG, "blok jenis " + jenis + " di " + Integer.toHexString(ari) + ": " + judul);
		}
		
		try {
			// simpen posisi blok terlebih dahulu
			nblokPerikop_++;
			xariPerikopBlok_.add(ari);
			xposisiPerikopBlok_.add(nantinyaPerikopBlok_.getPos());
			
			// nah sekarang baru bloknya
			if (judul.length() > 255) {
				// tulis versi = 2
				nantinyaPerikopBlok_.writeUint8(2);
				// tulis judul
				nantinyaPerikopBlok_.writeLongString(judul);
			} else {
				// tulis versi = 1
				nantinyaPerikopBlok_.writeUint8(1);
				// tulis judul
				nantinyaPerikopBlok_.writeShortString(judul);
			}
			
			// tulis nparalel
			nantinyaPerikopBlok_.writeUint8(0 /*xparalel.size()*/);
			// tulis xparalel, tapi kan ga ada.
			// NOP
		} catch (IOException e) {
			// won't happen, this is writing to memory only
		}
	}

	public class LazyTeks implements IsiSeksi {
		private final int baseProgress;

		public LazyTeks(int baseProgress) {
			this.baseProgress = baseProgress;
		}
		
		@Override
		public void toBytes(BintexWriter writer) throws Exception {
			for (int kitabPos = 0; kitabPos < xkitab_.length; kitabPos++) {
				int bookPos = kitabPosToBookPosMap_[kitabPos];
				if (bookPos == -1) {
					continue;
				}
				BookInfo bookInfo = pdb.getBook(bookPos);
				bookInfo.openBook();

				int npasal = bookInfo.getChapterCount();
				progress(baseProgress + 1 + kitabPos, "Writing text of book " + bookInfo.getFullName() + " (" + npasal + " chapters)");
				
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
}
