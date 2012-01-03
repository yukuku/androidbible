package yuku.alkitab.ro_cornilescu;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import yuku.alkitab.bdb.BdbProses.Rec;
import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.Kitab;
import yuku.alkitab.yes.YesFile.PerikopBlok;
import yuku.alkitab.yes.YesFile.PerikopData;
import yuku.alkitab.yes.YesFile.PerikopData.Blok;
import yuku.alkitab.yes.YesFile.PerikopData.Entri;
import yuku.alkitab.yes.YesFile.PerikopIndex;
import yuku.alkitab.yes.YesFile.Teks;

public class Proses1 {
	public static final String TAG = Proses1.class.getSimpleName();
	
	static String INPUT_TEKS_1 = "../Alkitab/publikasi/ro-cornilescu/ro-cornilescu.1.txt";
	static String INPUT_TEKS_ENCODING = "utf-8";
	static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	static String INPUT_KITAB = "../Alkitab/publikasi/ro-cornilescu/ro-cornilescu-kitab.txt";
	static String OUTPUT_YES = "../Alkitab/publikasi/ro-cornilescu/ro-cornilescu.yes";
	static int OUTPUT_ADA_PERIKOP = 1;

	final static Charset ascii = Charset.forName("ascii");
	final static Charset utf8 = Charset.forName("utf-8");
	
	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		Scanner sc = new Scanner(new File(INPUT_TEKS_1), INPUT_TEKS_ENCODING);
		
		List<Rec> xrec = new ArrayList<Rec>();
		PerikopData perikopData = new PerikopData();
		perikopData.xentri = new ArrayList<Entri>();
		
		int kitab_1 = 1;
		int pasal_1 = 1;
		int lastPasal_1 = 0;
		
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			line = line.trim();
			
			if (line.length() == 0) continue;
			
			if (line.matches("^[0-9]+\\.\\s.*")) {
				// ayat
				String[] splits = line.split("\\.", 2);
				int ayat_1 = Integer.parseInt(splits[0]);
				String isi = splits[1].trim();
				
				Rec rec = new Rec();
				rec.kitab_1 = kitab_1;
				rec.pasal_1 = pasal_1;
				rec.ayat_1 = ayat_1;
				rec.isi = isi;
				
				xrec.add(rec);
			} else if (line.startsWith("Capitolul ")) {
				pasal_1 = Integer.parseInt(line.substring(10));
				if (pasal_1 <= lastPasal_1) {
					kitab_1++;
				}
				lastPasal_1 = pasal_1;
			} else if (line.startsWith("(") && line.endsWith(")")) {
				Entri entri = new Entri();
				entri.ari = (kitab_1 - 1) << 16 | pasal_1 << 8 | 1 /* ayat_1 == 1 */;
				entri.blok = new Blok();
				entri.blok.versi = 2;
				entri.blok.judul = line.substring(1, line.length() - 1);
				perikopData.xentri.add(entri);
			} else {
				System.out.println("unknown line: " + line);
			}
		}
		
		System.out.println("Total verses: " + xrec.size());
		System.out.println("last kitab_1: " + kitab_1);

		////////// PROSES KE YES
		
		final InfoEdisi infoEdisi = infoEdisi();
		final InfoKitab infoKitab = infoKitab(xrec);
		final Teks teks = teks(xrec);
		final PerikopBlok perikopBlok = new PerikopBlok(perikopData);
		final PerikopIndex perikopIndex = new PerikopIndex(perikopData);
		
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
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}


	private static Teks teks(List<Rec> xrec) {
		final ArrayList<String> ss = new ArrayList<String>();
		for (Rec rec: xrec) {
			ss.add(rec.isi);
		}
		
		return new Teks(INPUT_TEKS_ENCODING) {{
			xisi = ss.toArray(new String[ss.size()]);
		}};
	}

	private static InfoEdisi infoEdisi() {
		return new InfoEdisi() {{
			versi = 1;
			nama = "ro-cornilescu";
			judul = "Romanian Cornilescu";
			nkitab = 66;
			perikopAda = OUTPUT_ADA_PERIKOP;
			keterangan = "BIBLIA SAU SFÂNTA SCRIPTURĂ A VECHIULUI ŞI NOULUI TESTAMENT\n" + 
					"Traducerea: Dumitru Cornilescu, 1921";
			encoding = INPUT_TEKS_ENCODING_YES;
		}};
	}

	private static InfoKitab infoKitab(List<Rec> xrec) throws Exception {
		final Kitab[] xkitab_ = new Kitab[66];
		
		String[] xjudul, xnama;
		xjudul = new String[66];
		xnama = new String[66];
		int p = 0;
		
		Scanner sc = new Scanner(new File(INPUT_KITAB));
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
					
					offsetLewat += rec.isi.getBytes(INPUT_TEKS_ENCODING).length + 1; // tambah 1 karena '\n' nya
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
			kitab.encoding = INPUT_TEKS_ENCODING_YES;
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
