package yuku.alkitabconverter.in_tb_usfm;

import yuku.alkitabconverter.util.Usfm2Usfx;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.Scanner;

// process from sfm file to usfx files
public class Proses1 {
	static String INPUT_TEKS_1 = "../../../bahan-alkitab/in-tb-usfm/in/tb-woj-utf8-revyuku.sfm";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "../../../bahan-alkitab/in-tb-usfm/in/in-tb-usfm-kitab.txt";
	public static int OUTPUT_ADA_PERIKOP = 0;

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
				splitFile = new PrintWriter(new File("../../../bahan-alkitab/in-tb-usfm/mid", String.format("%02d-%s-utf8.usfm", c, newId)), "utf-8");
				c++;
			}
			
			// patch: Ganti \s1 dengan \s2
			baris = baris.replace("\\s1", "\\s2");
			
			splitFile.println(baris);
		}
		if (splitFile != null) splitFile.close();
		
		String[] usfms = new File("../../../bahan-alkitab/in-tb-usfm/mid/").list(new FilenameFilter() {
			@Override public boolean accept(File parent, String name) {
				return (name.endsWith(".usfm"));
			}
		});
		
		for (String usfm: usfms) {
			String usfx = usfm.replace(".usfm", ".usfx.xml");
			Usfm2Usfx.convert("../../../bahan-alkitab/in-tb-usfm/mid/" + usfm, "../../../bahan-alkitab/in-tb-usfm/mid/" + usfx);
		}
	}
}
