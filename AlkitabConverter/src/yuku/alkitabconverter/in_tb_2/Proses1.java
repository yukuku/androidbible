package yuku.alkitabconverter.in_tb_2;

import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;

import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.Teks;
import yuku.alkitabconverter.bdb.BdbProses;
import yuku.alkitabconverter.bdb.BdbProses.Rec;
import yuku.alkitabconverter.util.RecUtil;
import yuku.alkitabconverter.yes_common.YesCommon;

public class Proses1 {
	static String INPUT_TEKS_1 = "./bahan/in-tb-2/in/TB(2)-utf8.txt";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "./bahan/in-tb-2/in/in-tb-kitab.txt";
	static String OUTPUT_YES = "./bahan/in-tb-2/out/in-tb.yes";
	public static int OUTPUT_ADA_PERIKOP = 0;
	static String INFO_NAMA = "in-tb";
	static String INFO_JUDUL = "Terjemahan Baru";
	static String INFO_KETERANGAN = "Terjemahan Baru (c) LAI";

	final Charset utf8 = Charset.forName("utf-8");
	
	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		ArrayList<Rec> xrec = new BdbProses().parse(INPUT_TEKS_1, "utf-8");
		
		System.out.println("Total verses: " + xrec.size());

		// post-process
		// pindahin semua @8 pada awal ayat ke akhir dari ayat sebelumnya jika ayat sebelumnya masih dalam pasal yang sama
		for (int i = 0; i < xrec.size(); i++) {
			Rec rec = xrec.get(i);
			
			if (rec.isi.startsWith("@@@8")) {
				Rec recSblum = xrec.get(i - 1);
				if (recSblum.kitab_1 == rec.kitab_1 && recSblum.pasal_1 == recSblum.pasal_1) {
					while (rec.isi.substring(2, 4).equals("@8")) {
						rec.isi = "@@" + rec.isi.substring(4); // buang @8
						recSblum.isi = recSblum.isi + "@8"; // tambah @8
					}
					if (!recSblum.isi.startsWith("@@")) {
						recSblum.isi = "@@" + recSblum.isi;
					}
				} else {
					throw new RuntimeException("@8 ga bisa dipindah ke depan pada: " + rec.kitab_1 + " " + rec.pasal_1 + " " + rec.ayat_1);
				}
			}
		}
		
		// post-process
		// pindahin semua \p pada awal ayat menjadi @8 pada akhir dari ayat sebelumnya jika ayat sebelumnya masih dalam pasal yang sama
		for (int i = 0; i < xrec.size(); i++) {
			Rec rec = xrec.get(i);
			
			if (rec.isi.startsWith("@@\\p") || rec.isi.startsWith("\\p")) {
				if (rec.ayat_1 == 1) { // di ayat 1
					if (rec.isi.startsWith("@@\\p")) {
						rec.isi = "@@" + rec.isi.substring(4); // buang \p
					} else if (rec.isi.startsWith("\\p")) {
						rec.isi = rec.isi.substring(2); // buang \p
					}
				} else {
					Rec recSblum = xrec.get(i - 1);
					if (recSblum.kitab_1 == rec.kitab_1 && recSblum.pasal_1 == recSblum.pasal_1) {
						if (rec.isi.startsWith("@@\\p")) {
							rec.isi = "@@" + rec.isi.substring(4); // buang \p
						} else if (rec.isi.startsWith("\\p")) {
							rec.isi = rec.isi.substring(2); // buang \p
						}
						recSblum.isi = recSblum.isi + "@8"; // tambah @8
						if (!recSblum.isi.startsWith("@@")) {
							recSblum.isi = "@@" + recSblum.isi;
						}
					} 
				}
			}
		}
		
		// post-process
		// cari semua \p di tengah2 ayat, ganti dengan @8@8
		for (int i = 0; i < xrec.size(); i++) {
			Rec rec = xrec.get(i);
			if (rec.isi.contains("\\p")) {
				System.out.println("\\p di tengah2: " + rec.kitab_1 + " " + rec.pasal_1 + " " + rec.ayat_1 + " " + rec.isi);
				rec.isi = rec.isi.replace("\\p", "@8@8");
				if (!rec.isi.startsWith("@@")) {
					rec.isi = "@@" + rec.isi;
				}	
			}
		}
		
		////////// PROSES KE YES
		
		final InfoEdisi infoEdisi = YesCommon.infoEdisi(INFO_NAMA, null, INFO_JUDUL, RecUtil.hitungKitab(xrec), OUTPUT_ADA_PERIKOP, INFO_KETERANGAN, INPUT_TEKS_ENCODING_YES);
		final InfoKitab infoKitab = YesCommon.infoKitab(xrec, INPUT_KITAB, INPUT_TEKS_ENCODING, INPUT_TEKS_ENCODING_YES);
		final Teks teks = YesCommon.teks(xrec, INPUT_TEKS_ENCODING);
		
		YesFile file = YesCommon.bikinYesFile(infoEdisi, infoKitab, teks);
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}
}
