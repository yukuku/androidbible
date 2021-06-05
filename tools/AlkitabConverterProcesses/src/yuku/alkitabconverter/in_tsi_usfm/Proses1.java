package yuku.alkitabconverter.in_tsi_usfm;

import yuku.alkitabconverter.util.Usfm2Usfx;
import yuku.alkitabconverter.util.UsfmBookName;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.Scanner;

public class Proses1 {
	static String INPUT_TEXT_ENCODING = "utf-8";

	public static void main(String[] args) throws Exception {
		new Proses1().u(args);
	}

	private void u(final String[] args) throws Exception {
		final String INPUT_TEXT_1_DIR = args[0];
		final String MID_DIR = args[1].endsWith("/") ? args[1] : (args[1] + "/");

		for (final File inputFile : new File(INPUT_TEXT_1_DIR).listFiles(new FilenameFilter() {
			@Override
			public boolean accept(final File dir, final String name) {
				return name.endsWith(".usfm") || name.endsWith(".SFM");
			}
		})) {
			final Scanner sc = new Scanner(inputFile, INPUT_TEXT_ENCODING);

			PrintWriter splitFile = null;

			while (sc.hasNextLine()) { // look for "\id"
				final String baris = sc.nextLine();

				if (baris.startsWith("\\id ")) {
					final String newId = baris.split(" ")[1];
					final int book_0 = UsfmBookName.toBookId(newId);

					final File outputFile = new File(MID_DIR, String.format("%02d-utf8.usfm", book_0));
					//noinspection ResultOfMethodCallIgnored
					outputFile.getParentFile().mkdir();

					splitFile = new PrintWriter(outputFile, "utf-8");
				}
			}

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

		final File midFile = new File(MID_DIR);
		midFile.mkdirs();
		String[] usfms = midFile.list(new FilenameFilter() {
			@Override
			public boolean accept(File parent, String name) {
				return (name.endsWith("-utf8.usfm"));
			}
		});

		for (String usfm: usfms) {
			String usfx = usfm.replace(".usfm", ".usfx.xml");
			Usfm2Usfx.convert(MID_DIR + usfm, MID_DIR + usfx);
		}
	}
}
