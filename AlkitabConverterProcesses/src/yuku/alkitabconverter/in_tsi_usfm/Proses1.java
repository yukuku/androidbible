package yuku.alkitabconverter.in_tsi_usfm;

import yuku.alkitabconverter.util.Usfm2Usfx;
import yuku.alkitabconverter.util.UsfmBookName;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.Scanner;

public class Proses1 {
	static String INPUT_TEKS_1 = "../../../bahan-alkitab/in-tsi-usfm/in/nt.sfm";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "../../../bahan-alkitab/in-tsi-usfm/in/in-tsi-usfm-kitab.txt";
	static final String MID_DIR = "../../../bahan-alkitab/in-tsi-usfm/mid/";
	public static int OUTPUT_ADA_PERIKOP = 0;

	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		Scanner sc = new Scanner(new File(INPUT_TEKS_1), INPUT_TEKS_ENCODING);
		
		PrintWriter splitFile = null;
		while (sc.hasNextLine()) {
			String baris = sc.nextLine();
			// remove ALL BOM (0xfeff)
			if (baris.length() > 0) {
				baris = baris.replace("\ufeff", "");
			}
			if (baris.startsWith("\\id ")) {
				int firstSpaceAfterId = baris.indexOf(' ', 4);
				String newId;
				if (firstSpaceAfterId == -1) {
					newId = baris.substring(4);
				} else {
					newId = baris.substring(4, firstSpaceAfterId);
				}
				if (splitFile != null) {
					splitFile.close();
				}
				
				int kitab_0 = UsfmBookName.toBookId(newId);
				
				splitFile = new PrintWriter(new File(MID_DIR, String.format("%02d-%s-utf8.usfm", kitab_0, newId)), "utf-8");
			}
			
			// patch: Ganti \s1 dengan \s2
			baris = baris.replace("\\s1", "\\s2");
			
			splitFile.println(baris);
		}
		if (splitFile != null) splitFile.close();
		
		String[] usfms = new File(MID_DIR).list(new FilenameFilter() {
			@Override public boolean accept(File parent, String name) {
				return (name.endsWith(".usfm"));
			}
		});
		
		for (String usfm: usfms) {
			String usfx = usfm.replace(".usfm", ".usfx.xml");
			Usfm2Usfx.convert(MID_DIR + usfm, MID_DIR + usfx);
		}
	}
}
