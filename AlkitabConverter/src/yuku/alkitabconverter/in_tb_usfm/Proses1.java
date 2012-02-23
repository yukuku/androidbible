package yuku.alkitabconverter.in_tb_usfm;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import yuku.alkitabconverter.bdb.BdbProses.Rec;
import yuku.alkitabconverter.util.Usfm2Usfx;

public class Proses1 {
	static String INPUT_TEKS_1 = "./bahan/in-tb-usfm/in/tb-woj-utf8.sfm";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "./bahan/hu-karoli/in/hu-karoli-kitab.txt";
	static String OUTPUT_YES = "./bahan/hu-karoli/out/hu-karoli.yes";
	public static int OUTPUT_ADA_PERIKOP = 0;
	static String INFO_NAMA = "hu-karoli";
	static String INFO_JUDUL = "Károli";
	static String INFO_KETERANGAN = "Hungarian Vizsoly Biblia translated by Károli Gáspár (1590)";

	List<Rec> xrec = new ArrayList<Rec>();

	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		Scanner sc = new Scanner(new File(INPUT_TEKS_1), INPUT_TEKS_ENCODING);
		
		int c = 0;
		PrintWriter splitFile = null;
		while (sc.hasNextLine()) {
			String baris = sc.nextLine();
			if (baris.startsWith("\\id ")) {
				String newId = baris.substring(4, baris.indexOf(' ', 4));
				if (splitFile != null) {
					splitFile.close();
				}
				splitFile = new PrintWriter(new File("./bahan/in-tb-usfm/mid", String.format("%02d-%s-utf8.usfm", c, newId)), "utf-8");
				c++;
			}
			
			// patch: Ganti \s1 dengan \s2
			baris = baris.replace("\\s1", "\\s2");
			
			splitFile.println(baris);
		}
		if (splitFile != null) splitFile.close();
		
		String[] usfms = new File("./bahan/in-tb-usfm/mid/").list(new FilenameFilter() {
			@Override public boolean accept(File parent, String name) {
				return (name.endsWith(".usfm"));
			}
		});
		
		for (String usfm: usfms) {
			String usfx = usfm.replace(".usfm", ".usfx.xml");
			Usfm2Usfx.convert("./bahan/in-tb-usfm/mid/" + usfm, "./bahan/in-tb-usfm/mid/" + usfx);
		}
	}
}
