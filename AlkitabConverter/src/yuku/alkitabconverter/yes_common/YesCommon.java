package yuku.alkitabconverter.yes_common;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.Kitab;
import yuku.alkitab.yes.YesFile.PerikopBlok;
import yuku.alkitab.yes.YesFile.PerikopIndex;
import yuku.alkitab.yes.YesFile.Teks;
import yuku.alkitabconverter.bdb.BdbProses.Rec;

public class YesCommon {
	public static final String TAG = YesCommon.class.getSimpleName();

	public final static Charset ascii = Charset.forName("ascii");

	public static Teks teks(List<Rec> xrec, String _encoding) {
		final ArrayList<String> ss = new ArrayList<String>();
		for (Rec rec: xrec) {
			ss.add(rec.isi);
		}
		
		return new Teks(_encoding) {{
			xisi = ss.toArray(new String[ss.size()]);
		}};
	}

	public static InfoEdisi infoEdisi(final String _nama, final String _judul, final int _perikopAda, final String _keterangan, final int _encoding) {
		return new InfoEdisi() {{
			versi = 1;
			nama = _nama;
			judul = _judul;
			nkitab = 66;
			perikopAda = _perikopAda;
			keterangan = _keterangan;
			encoding = _encoding;
		}};
	}

	public static InfoKitab infoKitab(List<Rec> xrec, String _namafileInputKitab, String _encoding, int _encodingYes) throws Exception {
		final Kitab[] xkitab_ = new Kitab[66];
		
		String[] xjudul, xnama;
		xjudul = new String[66];
		xnama = new String[66];
		int p = 0;
		
		Scanner sc = new Scanner(new File(_namafileInputKitab));
		while (sc.hasNextLine()) {
			String judul = sc.nextLine().trim();
			if (judul.length() > 0) {
				xjudul[p] = judul.replace('_', ' ');
				xnama[p] = judul.replace('_', ' ');
				p++;
			}
		}
		sc.close();
		
		int offsetTotal = 0;
		int offsetLewat = 0;
		int maxpasal_1 = 1;
		int lastpasal_1 = 1;
		int[] xnayat = new int[256];
		int[] xpasal_offset = new int[256];
		
		for (int kitabPos = 0; kitabPos < 66; kitabPos++) {
			xpasal_offset[0] = 0;
			
			for (Rec rec: xrec) {
				if (kitabPos + 1 == rec.kitab_1) {
					xnayat[rec.pasal_1 - 1]++;
					
					if (rec.pasal_1 > maxpasal_1) {
						maxpasal_1 = rec.pasal_1;
					}
					
					if (rec.pasal_1 != lastpasal_1) {
						xpasal_offset[lastpasal_1] = offsetLewat;
						lastpasal_1 = rec.pasal_1;
					}
					
					offsetLewat += rec.isi.getBytes(_encoding).length + 1; // tambah 1 karena '\n' nya
				}
			}
			xpasal_offset[maxpasal_1] = offsetLewat;
			
			Kitab kitab = new Kitab();
			kitab.versi = 1;
			kitab.pos = kitabPos;
			kitab.nama = xnama[kitabPos];
			kitab.judul = xjudul[kitabPos];
			kitab.npasal = maxpasal_1;
			kitab.nayat = new int[kitab.npasal];
			System.arraycopy(xnayat, 0, kitab.nayat, 0, kitab.npasal);
			System.out.println("kitab " + kitab.judul + " nayat: " + Arrays.toString(kitab.nayat));
			kitab.ayatLoncat = 0;
			kitab.pasal_offset = new int[kitab.npasal + 1];
			System.arraycopy(xpasal_offset, 0, kitab.pasal_offset, 0, kitab.npasal+1);
			System.out.println("kitab " + kitab.judul + " pasal_offset: " + Arrays.toString(kitab.pasal_offset));
			kitab.encoding = _encodingYes;
			kitab.offset = offsetTotal;
			System.out.println("kitab " + kitab.judul + " offset: " + kitab.offset);
			
			xkitab_[kitabPos] = kitab;
			
			//# reset
			offsetTotal += offsetLewat;
			offsetLewat = 0;
			for (int i = 0; i < xnayat.length; i++) xnayat[i] = 0;
			maxpasal_1 = 1;
			lastpasal_1 = 0;
		}
		return new InfoKitab() {{
			this.xkitab = xkitab_;
		}};
	}

	public static YesFile bikinYesFile(final InfoEdisi infoEdisi, final InfoKitab infoKitab, final Teks teks, final PerikopBlok perikopBlok, final PerikopIndex perikopIndex) {
		return new YesFile() {{
			this.xseksi = new Seksi[] {
				new Seksi() {
					@Override public byte[] nama() {
						return "infoEdisi___".getBytes(ascii);
					}
	
					@Override public IsiSeksi isi() {
						return infoEdisi;
					}
				},
				new Seksi() {
					@Override public byte[] nama() {
						return "infoKitab___".getBytes(ascii);
					}
	
					@Override public IsiSeksi isi() {
						return infoKitab;
					}
				},
				new Seksi() {
					@Override public byte[] nama() {
						return "teks________".getBytes(ascii);
					}
					
					@Override public IsiSeksi isi() {
						return teks;
					}
				},
				new Seksi() {
					@Override public byte[] nama() {
						return "perikopBlok_".getBytes(ascii);
					}
	
					@Override public IsiSeksi isi() {
						return perikopBlok;
					}
				},
				new Seksi() {
					@Override public byte[] nama() {
						return "perikopIndex".getBytes(ascii);
					}
					
					@Override public IsiSeksi isi() {
						return perikopIndex;
					}
				},
			};
		}};
	}

	public static YesFile bikinYesFile(final InfoEdisi infoEdisi, final InfoKitab infoKitab, final Teks teks) {
		return new YesFile() {{
			this.xseksi = new Seksi[] {
				new Seksi() {
					@Override public byte[] nama() {
						return "infoEdisi___".getBytes(ascii);
					}
	
					@Override public IsiSeksi isi() {
						return infoEdisi;
					}
				},
				new Seksi() {
					@Override public byte[] nama() {
						return "infoKitab___".getBytes(ascii);
					}
	
					@Override public IsiSeksi isi() {
						return infoKitab;
					}
				},
				new Seksi() {
					@Override public byte[] nama() {
						return "teks________".getBytes(ascii);
					}
					
					@Override public IsiSeksi isi() {
						return teks;
					}
				},
			};
		}};
	}
}
