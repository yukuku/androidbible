package yuku.alkitabconverter.in_tsi_usfm;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.Usfm2Usfx;
import yuku.alkitabconverter.util.UsfmBookName;

public class Proses1 {
	static String INPUT_TEKS_1 = "./bahan/in-tsi-usfm/in/TSI21Mar12.txt";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "./bahan/in-tsi-usfm/in/in-tsi-usfm-kitab.txt";
	static final String MID_DIR = "./bahan/in-tsi-usfm/mid/";
	static String OUTPUT_YES = "./bahan/in-tsi-usfm/out/in-tsi.yes";
	public static int OUTPUT_ADA_PERIKOP = 0;
	static String INFO_NAMA = "in-tsi";
	static String INFO_JUDUL = "TSI";
	static String INFO_KETERANGAN = "Terjemahan Sederhana Indonesia";

	List<Rec> xrec = new ArrayList<Rec>();

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
				
				int kitab_0 = UsfmBookName.toKitab0(newId);
				
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
