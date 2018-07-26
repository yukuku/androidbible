package yuku.alkitabconverter.ro_ortodoxa;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import yuku.alkitabconverter.yes1.Yes1File;
import yuku.alkitabconverter.yes1.Yes1File.InfoEdisi;
import yuku.alkitabconverter.yes1.Yes1File.InfoKitab;
import yuku.alkitabconverter.yes1.Yes1File.Teks;
import yuku.alkitabconverter.util.Hitungan31102;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.RecUtil;
import yuku.alkitabconverter.yes_common.Yes1Common;

public class Proses1 {
	static String INPUT_TEKS_1 = "./bahan/ro-ortodoxa/in/ro-ortodoxa-bare.txt";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "./bahan/ro-ortodoxa/in/ro-ortodoxa-kitab.txt";
	static String OUTPUT_YES = "./bahan/ro-ortodoxa/out/ro-ortodoxa.yes";
	public static int OUTPUT_ADA_PERIKOP = 0;
	static String INFO_NAMA = "ro-ortodoxa";
	static String INFO_JUDUL = "Biblia Ortodoxă";
	static String INFO_KETERANGAN = "Biblia Ortodoxă (Biblia Sinodală / Bible of the Holy Synod)";

	List<Rec> xrec = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		Scanner sc = new Scanner(new File(INPUT_TEKS_1), INPUT_TEKS_ENCODING);
		
		List<Rec> xrec = new ArrayList<>();
		int offset_0 = 0;
		
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			line = line.trim();
			
			// ayat
			Rec rec = new Rec();
			rec.book_1 = Hitungan31102.kitab_1(offset_0);
			rec.chapter_1 = Hitungan31102.pasal_1(offset_0);
			rec.verse_1 = Hitungan31102.ayat_1(offset_0);
			rec.text = line;
			
			xrec.add(rec);
			offset_0++;
		}
		
		System.out.println("Total verses: " + xrec.size());

		////////// PROSES KE YES

		final InfoEdisi infoEdisi = Yes1Common.infoEdisi(INFO_NAMA, null, INFO_JUDUL, RecUtil.hitungKitab(xrec), OUTPUT_ADA_PERIKOP, INFO_KETERANGAN, INPUT_TEKS_ENCODING_YES, null);
		final InfoKitab infoKitab = Yes1Common.infoKitab(xrec, INPUT_KITAB, INPUT_TEKS_ENCODING, INPUT_TEKS_ENCODING_YES);
		final Teks teks = Yes1Common.teks(xrec, INPUT_TEKS_ENCODING);
		
		Yes1File file = Yes1Common.bikinYesFile(infoEdisi, infoKitab, teks);
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}
}
