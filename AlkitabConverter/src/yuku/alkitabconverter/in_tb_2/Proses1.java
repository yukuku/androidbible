package yuku.alkitabconverter.in_tb_2;

import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;

import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.Teks;
import yuku.alkitabconverter.bdb.BdbProses;
import yuku.alkitabconverter.util.Rec;
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
			
			if (rec.text.startsWith("@@@8")) {
				Rec recSblum = xrec.get(i - 1);
				if (recSblum.book_1 == rec.book_1 && recSblum.chapter_1 == recSblum.chapter_1) {
					while (rec.text.substring(2, 4).equals("@8")) {
						rec.text = "@@" + rec.text.substring(4); // buang @8
						recSblum.text = recSblum.text + "@8"; // tambah @8
					}
					if (!recSblum.text.startsWith("@@")) {
						recSblum.text = "@@" + recSblum.text;
					}
				} else {
					throw new RuntimeException("@8 ga bisa dipindah ke depan pada: " + rec.book_1 + " " + rec.chapter_1 + " " + rec.verse_1);
				}
			}
		}
		
		// post-process
		// pindahin semua \p pada awal ayat menjadi @8 pada akhir dari ayat sebelumnya jika ayat sebelumnya masih dalam pasal yang sama
		for (int i = 0; i < xrec.size(); i++) {
			Rec rec = xrec.get(i);
			
			if (rec.text.startsWith("@@\\p") || rec.text.startsWith("\\p")) {
				if (rec.verse_1 == 1) { // di ayat 1
					if (rec.text.startsWith("@@\\p")) {
						rec.text = "@@" + rec.text.substring(4); // buang \p
					} else if (rec.text.startsWith("\\p")) {
						rec.text = rec.text.substring(2); // buang \p
					}
				} else {
					Rec recSblum = xrec.get(i - 1);
					if (recSblum.book_1 == rec.book_1 && recSblum.chapter_1 == recSblum.chapter_1) {
						if (rec.text.startsWith("@@\\p")) {
							rec.text = "@@" + rec.text.substring(4); // buang \p
						} else if (rec.text.startsWith("\\p")) {
							rec.text = rec.text.substring(2); // buang \p
						}
						recSblum.text = recSblum.text + "@8"; // tambah @8
						if (!recSblum.text.startsWith("@@")) {
							recSblum.text = "@@" + recSblum.text;
						}
					} 
				}
			}
		}
		
		// post-process
		// cari semua \p di tengah2 ayat, ganti dengan @8@8
		for (int i = 0; i < xrec.size(); i++) {
			Rec rec = xrec.get(i);
			if (rec.text.contains("\\p")) {
				System.out.println("\\p di tengah2: " + rec.book_1 + " " + rec.chapter_1 + " " + rec.verse_1 + " " + rec.text);
				rec.text = rec.text.replace("\\p", "@8@8");
				if (!rec.text.startsWith("@@")) {
					rec.text = "@@" + rec.text;
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
