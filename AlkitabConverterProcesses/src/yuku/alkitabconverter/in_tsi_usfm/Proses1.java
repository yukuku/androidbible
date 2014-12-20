package yuku.alkitabconverter.in_tsi_usfm;

import yuku.alkitabconverter.util.Usfm2Usfx;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.Scanner;

public class Proses1 {
	static String INPUT_TEXT_1_DIR = "../../../bahan-alkitab/in-tsi/in/";
	static String INPUT_TEXT_ENCODING = "utf-8";
	static final String MID_DIR = "../../../bahan-alkitab/in-tsi/mid/";

	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		for (final File inputFile : new File(INPUT_TEXT_1_DIR).listFiles(new FilenameFilter() {
			@Override
			public boolean accept(final File dir, final String name) {
				return name.endsWith(".usfm");
			}
		})) {
			final Scanner sc = new Scanner(inputFile, INPUT_TEXT_ENCODING);

			final int book_0 = Integer.parseInt(inputFile.getName().substring(0, 2)) - 1;

			final File outputFile = new File(MID_DIR, String.format("%02d-utf8.usfm", book_0));
			//noinspection ResultOfMethodCallIgnored
			outputFile.getParentFile().mkdir();

			final PrintWriter splitFile = new PrintWriter(outputFile, "utf-8");

			while (sc.hasNextLine()) {
				String baris = sc.nextLine();
				// remove ALL BOM (0xfeff)
				if (baris.length() > 0) {
					baris = baris.replace("\ufeff", "");
				}

				// patch: Ganti \s1 dengan \s2
				baris = baris.replace("\\s1", "\\s2");

				splitFile.println(baris);
			}

			splitFile.close();
		}

		String[] usfms = new File(MID_DIR).list(new FilenameFilter() {
			@Override public boolean accept(File parent, String name) {
				return (name.endsWith("-utf8.usfm"));
			}
		});
		
		for (String usfm: usfms) {
			String usfx = usfm.replace(".usfm", ".usfx.xml");
			Usfm2Usfx.convert(MID_DIR + usfm, MID_DIR + usfx);
		}
	}
}
