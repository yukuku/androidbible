package yuku.alkitab.base.pdbconvert;

import java.io.*;
import java.util.Arrays;

import yuku.alkitab.yes.*;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.IsiSeksi;
import yuku.alkitab.yes.YesFile.Kitab;
import yuku.bintex.BintexWriter;
import android.content.Context;
import android.util.Log;

import com.compactbyte.android.bible.PDBFileStream;
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
			nkitab = pdb.getBookCount();
			perikopAda = 0; // FIXME ada
		}};
	}

	private InfoKitab getInfoKitab() throws Exception {
		final int nkitab = pdb.getBookCount();
		final Kitab[] xkitab_ = new Kitab[nkitab];
		
		// FIXME sort books
		
		int offsetTotal = 0;
		int offsetLewat = 0;
		
		for (int kitabPos = 0; kitabPos < nkitab; kitabPos++) {
			BookInfo bookInfo = pdb.getBook(kitabPos);
			bookInfo.openBook();
			
			Kitab k = new Kitab();
			k.versi = 2;
			k.ayatLoncat = 0;
			k.encoding = 2; // UTF8 FIXME cek spesifikasi
			k.judul = bookInfo.getFullName();
			k.nama = bookInfo.getShortName();
			k.npasal = bookInfo.getChapterCount();
			k.nayat = new int[k.npasal];
			k.offset = offsetTotal;
			k.pasal_offset = new int[k.npasal + 1];
			k.pdbBookNumber = bookInfo.getBookNumber();
			k.pos = kitabPos;

			k.pasal_offset[0] = 0;
			for (int i = 0; i < k.npasal; i++) {
				k.nayat[i] = bookInfo.getVerseCount(i + 1);
				
				for (int j = 0; j < k.nayat[i]; j++) {
					offsetLewat += bookInfo.getVerse(i + 1, j + 1).getBytes("utf-8").length + 1; // +1 buat \n
				}
				k.pasal_offset[i+1] = offsetLewat;
			}
			Log.d(TAG, "kitab " + k.judul + " pasal_offset: " + Arrays.toString(k.pasal_offset));

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
