package yuku.alkitabconverter.zh_ckjv;

import yuku.alkitabconverter.util.Hitungan31102;
import yuku.alkitabconverter.util.Rec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class BareToYet {
	static String INPUT_TEXT_1 = "./bahan/zh-ckjv/in/ckjv_shangdi_t-bare.txt";
	static String INPUT_TEXT_ENCODING = "utf-8";
	static String INPUT_BOOK_NAMES = "./bahan/zh-ckjv/in/book_names_t.txt";
	static String OUTPUT_YET = "./bahan/zh-ckjv/out/ckjv_shangdi_t.yet";
	static String INFO_SHORT_NAME = "CKJVT";
	static String INFO_LONG_NAME = "Chinese KJV Trad";
	static String INFO_DESCRIPTION = "Chinese King James Version (Traditional)";
	static String INFO_LOCALE = "zh";

	public static void main(String[] args) throws Exception {
		new BareToYet().u();
	}

	private void u() throws Exception {
		Scanner sc = new Scanner(new File(INPUT_TEXT_1), INPUT_TEXT_ENCODING);
		
		List<Rec> recs = new ArrayList<Rec>();
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
			
			recs.add(rec);
			offset_0++;
		}
		
		System.out.println("Total verses: " + recs.size());

		////////// Process to YET file
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(OUTPUT_YET), "utf-8"));

		// header
		pw.printf(Locale.US, "info\tshortName\t%s\n", INFO_SHORT_NAME);
		pw.printf(Locale.US, "info\tlongName\t%s\n", INFO_LONG_NAME);
		pw.printf(Locale.US, "info\tdescription\t%s\n", INFO_DESCRIPTION);
		pw.printf(Locale.US, "info\tlocale\t%s\n", INFO_LOCALE);

		// book names
		int counter = 0;
		Scanner sc2 = new Scanner(new File(INPUT_BOOK_NAMES), "utf-8");
		while (sc2.hasNext()) {
			final String line = sc2.nextLine();
			if (line.trim().length() > 0) {
				pw.printf(Locale.US, "book_name\t%d\t%s\n", ++counter, line.trim());
			}
		}

		// verses
		for (Rec rec: recs) {
			pw.printf(Locale.US, "verse\t%d\t%d\t%d\t%s\n", rec.book_1, rec.chapter_1, rec.verse_1, rec.text);
		}

		pw.close();
	}
}
