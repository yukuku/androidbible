package yuku.alkitabconverter.ro_cornilescu;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.PericopeData;
import yuku.alkitab.yes.YesFile.PericopeData.Block;
import yuku.alkitab.yes.YesFile.PericopeData.Entry;
import yuku.alkitab.yes.YesFile.PerikopBlok;
import yuku.alkitab.yes.YesFile.PerikopIndex;
import yuku.alkitab.yes.YesFile.Teks;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.RecUtil;
import yuku.alkitabconverter.yes_common.YesCommon;

public class Proses1 {
	public static final String TAG = Proses1.class.getSimpleName();
	
	static String INPUT_TEKS_1 = "./bahan/ro-cornilescu/ro-cornilescu.1.txt";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "./bahan/ro-cornilescu/ro-cornilescu-kitab.txt";
	static String OUTPUT_YES = "./bahan/ro-cornilescu/ro-cornilescu.yes";
	public static int OUTPUT_ADA_PERIKOP = 1;

	final static Charset utf8 = Charset.forName("utf-8");
	
	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		Scanner sc = new Scanner(new File(INPUT_TEKS_1), INPUT_TEKS_ENCODING);
		
		List<Rec> xrec = new ArrayList<Rec>();
		PericopeData pericopeData = new PericopeData();
		pericopeData.entries = new ArrayList<Entry>();
		
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
				rec.book_1 = kitab_1;
				rec.chapter_1 = pasal_1;
				rec.verse_1 = ayat_1;
				rec.text = isi;
				
				xrec.add(rec);
			} else if (line.startsWith("Capitolul ")) {
				pasal_1 = Integer.parseInt(line.substring(10));
				if (pasal_1 <= lastPasal_1) {
					kitab_1++;
				}
				lastPasal_1 = pasal_1;
			} else if (line.startsWith("(") && line.endsWith(")")) {
				Entry entry = new Entry();
				entry.ari = (kitab_1 - 1) << 16 | pasal_1 << 8 | 1 /* ayat_1 == 1 */;
				entry.block = new Block();
				entry.block.version = 2;
				entry.block.title = line.substring(1, line.length() - 1);
				pericopeData.entries.add(entry);
			} else {
				System.out.println("unknown line: " + line);
			}
		}
		
		System.out.println("Total verses: " + xrec.size());
		System.out.println("last kitab_1: " + kitab_1);

		////////// PROSES KE YES

		final InfoEdisi infoEdisi = YesCommon.infoEdisi("ro-cornilescu", null, "Cornilescu", RecUtil.hitungKitab(xrec), OUTPUT_ADA_PERIKOP, "Biblia sau Sfânta Scriptură a Vechiului şi Noului Testament - Traducerea: Dumitru Cornilescu, 1921", INPUT_TEKS_ENCODING_YES);
		final InfoKitab infoKitab = YesCommon.infoKitab(xrec, INPUT_KITAB, INPUT_TEKS_ENCODING, INPUT_TEKS_ENCODING_YES);
		final Teks teks = YesCommon.teks(xrec, INPUT_TEKS_ENCODING);
		final PerikopBlok perikopBlok = new PerikopBlok(pericopeData);
		final PerikopIndex perikopIndex = new PerikopIndex(pericopeData);
		
		YesFile file = YesCommon.bikinYesFile(infoEdisi, infoKitab, teks, perikopBlok, perikopIndex);
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}
}
