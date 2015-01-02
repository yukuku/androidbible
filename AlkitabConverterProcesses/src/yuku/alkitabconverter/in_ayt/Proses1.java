package yuku.alkitabconverter.in_ayt;

import yuku.alkitabconverter.util.Usfm2Usfx;
import yuku.alkitabconverter.util.UsfmBookName;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.Scanner;

public class Proses1 {
	static String INPUT_TEXT_1 = "../../../bahan-alkitab/in-ayt/in/AYT_ALFA_PL.SFM";
	static String INPUT_TEXT_2 = "../../../bahan-alkitab/in-ayt/in/AYT_BETA_PB.SFM";
	static String INPUT_TEXT_ENCODING = "utf-8";
	static final String MID_DIR = "../../../bahan-alkitab/in-ayt/mid/";

	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		for (String inputfn : new String[]{INPUT_TEXT_1, INPUT_TEXT_2}) {
			Scanner sc = new Scanner(new File(inputfn), INPUT_TEXT_ENCODING);

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

					final File outputfile = new File(MID_DIR, String.format("%02d-%s-utf8.usfm", kitab_0, newId));
					outputfile.getParentFile().mkdir();
					splitFile = new PrintWriter(outputfile, "utf-8");
				}

				// patch: Ganti \s1 dengan \s2
				baris = baris.replace("\\s1", "\\s2");

				// patch: remove erronous U+001A character
				baris = baris.replace("\u001a", "");

				splitFile.println(baris);
			}

			if (splitFile != null) splitFile.close();
		}

		String[] usfms = new File(MID_DIR).list(new FilenameFilter() {
			@Override
			public boolean accept(File parent, String name) {
				return (name.endsWith("-utf8.usfm"));
			}
		});

		for (String usfm : usfms) {
			String usfx = usfm.replace(".usfm", ".usfx.xml");
			Usfm2Usfx.convert(MID_DIR + usfm, MID_DIR + usfx);
		}
	}
}
