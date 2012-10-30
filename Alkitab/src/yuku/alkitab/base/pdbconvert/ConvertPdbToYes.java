package yuku.alkitab.base.pdbconvert;

import android.content.Context;
import android.util.Log;

import gnu.trove.map.hash.TIntIntHashMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import yuku.afw.D;
import yuku.alkitab.R;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.IsiSeksi;
import yuku.alkitab.yes.YesFile.Kitab;
import yuku.bintex.BintexWriter;

import com.compactbyte.android.bible.PDBFileStream;
import com.compactbyte.bibleplus.reader.BiblePlusPDB;
import com.compactbyte.bibleplus.reader.BookInfo;

public class ConvertPdbToYes {
	public static final String TAG = ConvertPdbToYes.class.getSimpleName();

	public static final int VERSI_CONVERTER = 1;

	BiblePlusPDB pdb;

	TIntIntHashMap kitabPosToBookPosMap_;
	int nblokPerikop_ = 0;
	ByteArrayOutputStream nantinyaPerikopBlokBaos_ = new ByteArrayOutputStream();
	BintexWriter nantinyaPerikopBlok_ = new BintexWriter(nantinyaPerikopBlokBaos_);
	IntArrayList xariPerikopBlok_ = new IntArrayList();
	IntArrayList xposisiPerikopBlok_ = new IntArrayList();

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
		public Throwable exception;
		public List<String> wronglyConvertedBookNames;
	}
	
	public void setConvertProgressListener(ConvertProgressListener l) {
		this.convertProgressListener = l;
	}
	
	void progress(int at, String message) {
		if (convertProgressListener != null) {
			convertProgressListener.onProgress(at, message);
		}
	}
	
	void finish() {
		if (convertProgressListener != null) {
			convertProgressListener.onFinish();
		}
	}
	
	public ConvertPdbToYes() {
	}
	
	public ConvertResult convert(final Context context, String filenamepdb, String namafileyes, ConvertParams params) {
		ConvertResult res = new ConvertResult();
		
		try {
			progress(0, context.getString(R.string.cp_opening_pdb_file));
			pdb = new BiblePlusPDB(new PDBFileStream(filenamepdb), Tabs.hebrewTab, Tabs.greekTab);
			if (params.inputEncoding != null) pdb.setEncoding(params.inputEncoding);
			Log.d(TAG, "Encoding used: " + params.inputEncoding); //$NON-NLS-1$
			progress(10, context.getString(R.string.cp_loading_version_info));
			pdb.loadVersionInfo();
			progress(20, context.getString(R.string.cp_loading_word_index));
			pdb.loadWordIndex();
			
			Log.d(TAG, "============ baca info versi selesai"); //$NON-NLS-1$
			
			Log.d(TAG, "versionName: " + pdb.getVersionName()); //$NON-NLS-1$
			Log.d(TAG, "encoding: " + pdb.getEncoding()); //$NON-NLS-1$
			
			int nbook = pdb.getBookCount();
			Log.d(TAG, "getBookCount = " + nbook); //$NON-NLS-1$
			
			// 0 = kejadian
			// 65 = wahyu
			// 66 sampe 87, terdaftar dalam PdbNumberToAriMapping
			// selain itu, belum ada di mana2, maka kita buang aja dan kasih warning.
			progress(30, context.getString(R.string.cp_analyzing_available_books));
			{
				for (int bookPos = 0; bookPos < nbook; bookPos++) {
					BookInfo bookInfo = pdb.getBook(bookPos);
					bookInfo.openBook();
					int bookNumber = bookInfo.getBookNumber();
					int kitabPos = PdbNumberToAriMapping.pdbNumberToAriKitab(bookNumber);
					if (kitabPos < 0) {
						Log.w(TAG, "bookNumber " + bookNumber + " GA DIKENAL"); //$NON-NLS-1$ //$NON-NLS-2$
						if (res.wronglyConvertedBookNames == null) {
							res.wronglyConvertedBookNames = new ArrayList<String>();
						}
						res.wronglyConvertedBookNames.add(bookInfo.getFullName() + " (" + bookNumber + ")"); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
				kitabPosToBookPosMap_ = new TIntIntHashMap();
			}
			
			progress(40, context.getString(R.string.cp_mapping_books));
			for (int bookPos = 0; bookPos < nbook; bookPos++) {
				BookInfo bookInfo = pdb.getBook(bookPos);
				bookInfo.openBook();

				int bookNumber = bookInfo.getBookNumber();
				int kitabPos = PdbNumberToAriMapping.pdbNumberToAriKitab(bookNumber);
				if (kitabPos < 0) {
					Log.w(TAG, "bookNumber " + bookNumber + " GA DIKENAL"); //$NON-NLS-1$ //$NON-NLS-2$
				} else {
					if (kitabPosToBookPosMap_.containsKey(kitabPos)) {
						// just a warning of duplicate
						if (res.wronglyConvertedBookNames == null) {
							res.wronglyConvertedBookNames = new ArrayList<String>();
						}
						res.wronglyConvertedBookNames.add(bookInfo.getFullName() + " (" + bookNumber + "): duplicate"); //$NON-NLS-1$ //$NON-NLS-2$
					}
					kitabPosToBookPosMap_.put(kitabPos, bookPos);
				}
			}
			
			Log.d(TAG, "kitabPosToBookPosMap_ (size " + kitabPosToBookPosMap_.size() + ") = " + kitabPosToBookPosMap_.toString()); //$NON-NLS-1$ //$NON-NLS-2$

			Log.d(TAG, "============ baca daftar kitab selesai"); //$NON-NLS-1$
			

			final int[] xkitabPos = kitabPosToBookPosMap_.keys();
			Arrays.sort(xkitabPos);
			
			// sekaligus bangun perikop blok dan perikop index.
			progress(100, context.getString(R.string.cp_constructing_book_info));
			final InfoKitab infoKitab = getInfoKitab(context, 100, params.includeAddlTitle, xkitabPos);
			
			progress(200, context.getString(R.string.cp_constructing_version_info));
			final InfoEdisi infoEdisi = getInfoEdisi();
			
			progress(400, context.getString(R.string.cp_constructing_translated_file));
			YesFile file = new YesFile() {{
				boolean adaPerikop = nblokPerikop_ > 0;
				this.xseksi = new Seksi[adaPerikop? 5: 3];
				
				xseksi[0] = new SeksiBernama("infoEdisi___") { //$NON-NLS-1$
					@Override public IsiSeksi isi() {
						return infoEdisi;
					}
				};
				xseksi[1] = new SeksiBernama("infoKitab___") { //$NON-NLS-1$
					@Override public IsiSeksi isi() {
						return infoKitab;
					}
				};
				if (adaPerikop) {
					xseksi[2] = new SeksiBernama("perikopIndex") { //$NON-NLS-1$
						@Override public IsiSeksi isi() {
							return new IsiSeksi() {
								@Override public void toBytes(BintexWriter writer) throws Exception {
									progress(710, context.getString(R.string.cp_writing_num_section_pericope_indexes, nblokPerikop_));
									writer.writeInt(nblokPerikop_);
									for (int i = 0; i < nblokPerikop_; i++) {
										writer.writeInt(xariPerikopBlok_.get(i)); // ari untuk entri ini
										writer.writeInt(xposisiPerikopBlok_.get(i)); // ofset ke blok untuk entri ini
									}
								}
							};
						}
					};
					xseksi[3] = new SeksiBernama("perikopBlok_") { //$NON-NLS-1$
						@Override public IsiSeksi isi() {
							return new IsiSeksi() {
								@Override public void toBytes(BintexWriter writer) throws Exception {
									progress(720, context.getString(R.string.cp_writing_num_section_pericope_titles, nblokPerikop_));
									nantinyaPerikopBlokBaos_.writeTo(writer.getOutputStream());
								}
							};
						}
					};
				}
				xseksi[xseksi.length - 1] = new SeksiBernama("teks________") { //$NON-NLS-1$
					@Override public IsiSeksi isi() {
						return new LazyTeks(context, 800, xkitabPos);
					}
				};
			}};
			
			progress(600, context.getString(R.string.cp_opening_translated_file));
			RandomAccessFile out = new RandomAccessFile(namafileyes, "rw"); //$NON-NLS-1$
			progress(700, context.getString(R.string.cp_writing_translated_file));
			file.output(out);
			out.close();
			
			pdb.close();
			new PDBFileStream(filenamepdb).close();
		} catch (Throwable e) {
			pdb = null;
			Log.e(TAG, "Eror baca pdb: ", e); //$NON-NLS-1$
			res.exception = e;
		}
		finish();
		return res;
	}
	
	private InfoEdisi getInfoEdisi() {
		return new InfoEdisi() {{
			versi = 2;
			nama = pdb.getVersionName();
			longName = pdb.getVersionName();
			keterangan = pdb.getVersionInfo();
			nkitab = kitabPosToBookPosMap_.size();
			perikopAda = nblokPerikop_ == 0? 0: 1;
			encoding = 2; // utf-8
		}};
	}

	private InfoKitab getInfoKitab(final Context context, int baseProgress, boolean includeAddlTitle, int[] sortedXkitabPos) throws Exception {
		// no nulls allowed
		final List<Kitab> res = new ArrayList<Kitab>();
		
		// untuk offset teks dari awal seksi teks
		int offsetTotal = 0;
		// untuk offset teks dari awal kitab
		int offsetLewat = 0;
		
		for (int kitabPos: sortedXkitabPos) {
			int bookPos = kitabPosToBookPosMap_.get(kitabPos);
			BookInfo bookInfo = pdb.getBook(bookPos);
			bookInfo.openBook();
			
			progress(baseProgress + 1 + kitabPos, context.getString(R.string.cp_reading_book_info, bookInfo.getFullName()));

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
					String[] complete = getCompleteVerseWithPreprocess(bookInfo, pasal_0, ayat_0);
					offsetLewat += complete[0].getBytes("utf-8").length + 1; // +1 buat \n //$NON-NLS-1$
					
					// perikop!
					if (includeAddlTitle) {
						if (complete[3].length() > 0) simpanBlok(3, complete[3], kitabPos, pasal_0, ayat_0); 
						if (complete[2].length() > 0) simpanBlok(2, complete[2], kitabPos, pasal_0, ayat_0); 
					}
					if (complete[1].length() > 0) simpanBlok(1, complete[1], kitabPos, pasal_0, ayat_0);
				}
				k.pasal_offset[pasal_0+1] = offsetLewat;
			}
			Log.d(TAG, "kitab " + k.judul + " (bookNumber=" + bookInfo.getBookNumber() + ", kitabPos=" + kitabPos + ") pasal_offset: " + Arrays.toString(k.pasal_offset));  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

			res.add(k);
			
			//# reset
			offsetTotal += offsetLewat;
			offsetLewat = 0;
		}
		
		if (res.size() != kitabPosToBookPosMap_.size()) {
			throw new RuntimeException("Some internal error, res size != kitabPosToBookPos size"); //$NON-NLS-1$
		}
		
		return new InfoKitab() {{
			this.xkitab = res.toArray(new Kitab[0]);
		}};
	}

	/**
	 * Replaces (0x0e 'b' 0x0e) with (at 9) to start, or (at 7) to end.
	 * Replaces (0x0e 'n' 0x0e) with (at 8).
	 * and will add (at at) on the beginning of such verses.
	 * @return
	 */
	String[] getCompleteVerseWithPreprocess(BookInfo bookInfo, int pasal_0, int ayat_0) {
		String[] ss = bookInfo.getCompleteVerse(pasal_0 + 1, ayat_0 + 1);
		
		for (int i = 0; i < ss.length; i++) {
			String s = ss[i];
			if (s == null || s.length() == 0) {
				continue;
			}
			
			// search for 0x0e shortcut
			if (s.indexOf(0x0e) < 0) {
				continue;
			}
			
			boolean prependAtAt = false;
			
			// look for 0x0e 'n' 0x0e
			if (s.indexOf("\u000en\u000e") >= 0) { //$NON-NLS-1$
				prependAtAt = true;
				s = s.replaceAll("\\s*\u000en\u000e\\s*", "@8"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			
			boolean startingItalic = true;
			while (true) {
				int pos = s.indexOf("\u000eb\u000e"); //$NON-NLS-1$
				if (pos > 0) {
					prependAtAt = true;
					String tag = startingItalic ? "@9" : "@7"; //$NON-NLS-1$ //$NON-NLS-2$
					s = s.substring(0, pos) + tag + s.substring(pos + 3); // TODO remove extraneous spaces
					startingItalic = !startingItalic;
				} else {
					break;
				}
			}
			
			if (prependAtAt) {
				s = "@@" + s; //$NON-NLS-1$
			}
			
			ss[i] = s;
		}
		return ss;
	}
	
	/** Perhatikan {@link PericopeBlock} untuk cara bikinnya. */
	private void simpanBlok(int jenis, String judul, int kitab_0, int pasal_0, int ayat_0) {
		int ari = Ari.encode(kitab_0, pasal_0 + 1, ayat_0 + 1);

		if (D.EBUG) {
			Log.d(TAG, "blok jenis " + jenis + " di " + Integer.toHexString(ari) + ": " + judul); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
		private final Context context;
		private final int[] sortedXkitabPos;
		
		public LazyTeks(Context context, int baseProgress, int[] sortedXkitabPos) {
			this.context = context;
			this.baseProgress = baseProgress;
			this.sortedXkitabPos = sortedXkitabPos;
		}
		
		@Override
		public void toBytes(BintexWriter writer) throws Exception {
			for (int kitabPos: sortedXkitabPos) {
				int bookPos = kitabPosToBookPosMap_.get(kitabPos);
				BookInfo bookInfo = pdb.getBook(bookPos);
				bookInfo.openBook();

				int npasal = bookInfo.getChapterCount();
				progress(baseProgress + 1 + kitabPos, context.getString(R.string.cp_writing_text_of_book_chapters, bookInfo.getFullName(), npasal));
				
				for (int pi = 0; pi < npasal; pi++) {
					int nayat = bookInfo.getVerseCount(pi + 1);
					for (int ai = 0; ai < nayat; ai++) {
						String s = getCompleteVerseWithPreprocess(bookInfo, pi, ai)[0];
						writer.writeRaw(s.getBytes("utf-8")); //$NON-NLS-1$
						writer.writeUint8('\n');
					}
				}
			}
		}
	}
}
