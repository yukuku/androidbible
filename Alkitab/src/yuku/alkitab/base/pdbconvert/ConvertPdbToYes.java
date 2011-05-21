package yuku.alkitab.base.pdbconvert;

import android.content.*;
import android.util.*;

import java.io.*;
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

	private BiblePlusPDB pdb;

	public void convert(Context context, String filename) {
		try {
			PDBFileStream stream = new PDBFileStream(filename);
			pdb = new BiblePlusPDB(stream, null, null);
			pdb.loadVersionInfo();
			pdb.loadWordIndex();

			Log.d(TAG, "============ baca info versi selesai");
			
			Log.d(TAG, "versionName: " + pdb.getVersionName());
			Log.d(TAG, "encoding: " + pdb.getEncoding());
			
			int nkitab = pdb.getBookCount();
			Log.d(TAG, "nkitab = " + nkitab);

			Log.d(TAG, "============ baca daftar kitab selesai");
			
			YesFile file = new YesFile() {{
				this.xseksi = new Seksi[] {
					new Seksi() {
						@Override
						public byte[] nama() {
							return "infoEdisi___".getBytes();
						}

						@Override
						public IsiSeksi isi() {
							return getInfoEdisi();
						}
					},
					new Seksi() {
						@Override
						public byte[] nama() {
							return "infoKitab___".getBytes();
						}

						@Override
						public IsiSeksi isi() {
							try {
								return getInfoKitab();
							} catch (Exception e) {
								Log.e(TAG, "aaa", e); // FIXME
								return null;
							}
						}
					},
//					new Seksi() {
//						@Override
//						public byte[] nama() {
//							return "perikopIndex".getBytes();
//						}
//						
//						@Override
//						public IsiSeksi isi() {
//							return new NemplokSeksi("../Alkitab/publikasi/bis_perikop_index_bt.bt");
//						}
//					},
//					new Seksi() {
//						@Override
//						public byte[] nama() {
//							return "perikopBlok_".getBytes();
//						}
//						
//						@Override
//						public IsiSeksi isi() {
//							return new NemplokSeksi("../Alkitab/publikasi/bis_perikop_blok_bt.bt");
//						}
//					},
					new Seksi() {
						@Override
						public byte[] nama() {
							return "teks________".getBytes();
						}

						@Override
						public IsiSeksi isi() {
							return new LazyTeks();
						}
					}
				};
			}};
			
			RandomAccessFile out = new RandomAccessFile("/sdcard/Bibles/_output.yes", "rw");
			file.output(out);
			out.close();
			
			pdb.close();
			stream.close();
		} catch (Exception e) {
			pdb = null;
			Log.e(TAG, "Eror baca pdb: ", e);
		}
	}
	
	private InfoEdisi getInfoEdisi() {
		return new InfoEdisi() {{
			versi = 1;
			nama = pdb.getVersionName();
			judul = pdb.getVersionName();
			nkitab = pdb.getBookCount(); // FIXME salah! yang betul harus liat jadinya xkitab dulu
			perikopAda = 0; // FIXME ada
		}};
	}

	private InfoKitab getInfoKitab() throws Exception {
		final int nbook = pdb.getBookCount();
		
		// tempatin kitab2 di posisi yang betul, index array xkitab
		// 0 = kejadian
		// 65 = wahyu
		// 66 sampe 87, terdaftar dalam PdbNumberToAriMapping
		// selain itu, belum ada di mana2, maka kita buang aja (FIXME kasih warning)
		final Kitab[] xkitab_;
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
		}
		
		// untuk offset teks dari awal seksi teks
		int offsetTotal = 0;
		// untuk offset teks dari awal kitab
		int offsetLewat = 0;
		
		for (int bookPos = 0; bookPos < nbook; bookPos++) {
			BookInfo bookInfo = pdb.getBook(bookPos);
			bookInfo.openBook();

			int bookNumber = bookInfo.getBookNumber();
			int kitabPos = PdbNumberToAriMapping.pdbNumberToAriKitab(bookNumber);
			if (kitabPos < 0) {
				Log.w(TAG, "bookNumber " + bookNumber + " GA DIKENAL");
				continue;
			}
			
			Kitab k = new Kitab();
			k.versi = 2;
			k.pos = kitabPos;
			k.ayatLoncat = 0;
			k.encoding = 2; // UTF8 FIXME cek spesifikasi
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
			Log.d(TAG, "kitab " + k.judul + " (bookNumber=" + bookNumber + ", kitabPos=" + kitabPos + ") pasal_offset: " + Arrays.toString(k.pasal_offset));

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
