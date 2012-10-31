package yuku.alkitabconverter.col;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.Kitab;
import yuku.alkitab.yes.YesFile.Teks;
import yuku.alkitabconverter.util.Rec;

public class CuvColProses {
	static int edisi_index = -1;
	
	static String getKodeEdisi() {
		return new String[] {"cuvs", "cuvt"}[edisi_index];
	}

	static String getShortTitleEdisi() {
		return new String[] {"CUVS", "CUVT"}[edisi_index];
	}
	
	static String getJudulEdisi() {
		return new String[] {"Chinese Union Version (Simplified)", "Chinese Union Version (Traditional)"}[edisi_index];
	}
	
	static String getKeteranganEdisi() {
		return "Public domain.";
	}
	
	private static String getTeksCol() {
		return "../Alkitab/publikasi/" + getKodeEdisi() + "_teks_col.txt";
	}
	
	private static String getYesOutput() {
		return "../Alkitab/publikasi/" + getKodeEdisi() + ".yes";
	}
	
	static final Charset ascii = Charset.forName("ascii");
	static final Charset utf8 = Charset.forName("utf-8");
	
	public static void main(String[] args) throws Exception {
		edisi_index = Integer.parseInt(args[0]);
		
		
		ArrayList<Rec> xrec = new ColProses().parse(getTeksCol());
		
		final InfoEdisi infoEdisi = infoEdisi();
		final InfoKitab infoKitab = infoKitab(xrec);
		final Teks teks = teks(xrec);
		
		YesFile file = new YesFile() {{
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
				}
			};
		}};
		
		file.output(new RandomAccessFile(getYesOutput(), "rw"));
	}


	private static Teks teks(ArrayList<Rec> xrec) {
		final ArrayList<String> ss = new ArrayList<String>();
		for (Rec rec: xrec) {
			ss.add(rec.text);
		}
		
		return new Teks("utf-8") {{
			xisi = ss.toArray(new String[ss.size()]);
		}};
	}

	private static InfoEdisi infoEdisi() {
		return new InfoEdisi() {{
			versi = 1;
			nama = getKodeEdisi();
			shortName = getShortTitleEdisi();
			longName = getJudulEdisi();
			keterangan = getKeteranganEdisi();
			nkitab = 66;
			perikopAda = 0;
			encoding = 2;
		}};
	}

	private static InfoKitab infoKitab(ArrayList<Rec> xrec) throws Exception {
		final Kitab[] xkitab_ = new Kitab[66];
		
		String[] xjudul, xnama;
		xjudul = new String[66];
		xnama = new String[66];
		int p = 0;
		
		Scanner sc = new Scanner(new File("../Alkitab/publikasi/" + getKodeEdisi() + "_kitab.txt"));
		while (sc.hasNextLine()) {
			String judul = sc.nextLine().trim();
			if (judul.length() > 0) {
				xjudul[p] = judul;
				xnama[p] = judul.replaceAll(" ", "_");
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
				if (kitabPos + 1 == rec.book_1) {
					xnayat[rec.chapter_1 - 1]++;
					
					if (rec.chapter_1 > maxpasal_1) {
						maxpasal_1 = rec.chapter_1;
					}
					
					if (rec.chapter_1 != lastpasal_1) {
						xpasal_offset[lastpasal_1] = offsetLewat;
						lastpasal_1 = rec.chapter_1;
					}
					
					offsetLewat += rec.text.getBytes(utf8).length + 1; // tambah 1 karena '\n' nya
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
			kitab.encoding = 1;
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
}
