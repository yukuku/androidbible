package yuku.alkitabconverter.ro_cornilescu;

import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.yes_common.Yes2Common;
import yuku.alkitabconverter.yet.YetFileOutput;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Proses1 {
	public static final String TAG = Proses1.class.getSimpleName();
	public static final String INFO_LONG_NAME = "Cornilescu";
	private static final String INFO_LOCALE = "ro";
	private static final String INFO_SHORT_NAME = "VDC";
	public static final String INFO_DESCRIPTION = "Biblia sau Sfânta Scriptură a Vechiului şi Noului Testament - Traducerea: Dumitru Cornilescu, 1921";

	static String INPUT_TEKS_1 = "./ro-cornilescu/ro-cornilescu.1.txt";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "./ro-cornilescu/ro-cornilescu-kitab.txt";
	static String OUTPUT_YET = "./ro-cornilescu/ro-cornilescu.yet";
	public static int OUTPUT_ADA_PERIKOP = 1;

	final static Charset utf8 = Charset.forName("utf-8");
	
	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		Scanner sc = new Scanner(new File(INPUT_TEKS_1), INPUT_TEKS_ENCODING);
		
		List<Rec> xrec = new ArrayList<>();
		PericopeData pericopeData = new PericopeData();
		pericopeData.entries = new ArrayList<>();
		
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
				PericopeData.Entry entry = new PericopeData.Entry();
				entry.ari = (kitab_1 - 1) << 16 | pasal_1 << 8 | 1 /* ayat_1 == 1 */;
				entry.block = new PericopeData.Block();
				entry.block.title = line.substring(1, line.length() - 1);
				pericopeData.entries.add(entry);
			} else {
				System.out.println("unknown line: " + line);
			}
		}
		
		System.out.println("Total verses: " + xrec.size());
		System.out.println("last kitab_1: " + kitab_1);

		////////// PROSES KE YET

		{
			YetFileOutput yet = new YetFileOutput(new File(OUTPUT_YET));
			yet.setTextDb(new TextDb(xrec));
			yet.setPericopeData(pericopeData);
			Yes2Common.VersionInfo versionInfo = new Yes2Common.VersionInfo();
			versionInfo.locale = INFO_LOCALE;
			versionInfo.shortName = INFO_SHORT_NAME;
			versionInfo.longName = INFO_LONG_NAME;
			versionInfo.description = INFO_DESCRIPTION;
			versionInfo.setBookNamesFromFile(INPUT_KITAB);
			yet.setVersionInfo(versionInfo);
			yet.write();
		}
	}
}
