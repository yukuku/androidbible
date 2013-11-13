package yuku.alkitabconverter.reading_plan;

import yuku.alkitabconverter.util.DesktopVerseFinder;
import yuku.alkitabconverter.util.DesktopVerseParser;
import yuku.alkitabconverter.util.IntArrayList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class RpaConverter {
	private final static String FILE_INPUT = System.getProperty("user.dir") + "/AlkitabConverter/file/rp_blueletter_one_year_canonical.txt";
	private final static String FILE_OUTPUT = System.getProperty("user.dir") + "/AlkitabConverter/file/rp_blueletter_one_year_canonical.rpa";


	public static void main(String[] args) {
		RpaConverter processor = new RpaConverter();
		processor.convert();
	}

	public void convert() {
		File file = new File(FILE_OUTPUT);
		try {
			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			writeInfo(bw);
			writePlan(bw);

			bw.close();

		}catch(IOException e) {
			e.printStackTrace();
		}

	}

	private void writeInfo(BufferedWriter bufferedWriter) {
		try {
			StringBuilder info = new StringBuilder();
			info.append("info\tversion\t1\n");
			info.append("info\ttitle\t" + "Blue Letter One Year Canonical Plan\n");
			info.append("info\tdescription\t" + "This plan goes straight through the Bible from Genesis to Revelation. You will be supplied with reading for each day of the week as a steady guide toward finishing the entire Bible in one calendar year.\n");
			info.append("info\tduration\t" + 365 + "\n");
			bufferedWriter.write(info.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void writePlan(final BufferedWriter bufferedWriter) {
		try {
			Scanner scanner = new Scanner(new File(FILE_INPUT));

			while (scanner.hasNextLine()) {

				final String line = scanner.nextLine();

				final IntArrayList ariRanges = new IntArrayList();

				DesktopVerseFinder.findInText(line, new DesktopVerseFinder.DetectorListener() {
					@Override
					public boolean onVerseDetected(final int start, final int end, final String verse) {
						final IntArrayList aris = DesktopVerseParser.verseStringToAri(verse);
						for (int i = 0; i < aris.size(); i++) {
							ariRanges.add(aris.get(i));
						}
						return true;
					}

					@Override
					public void onNoMoreDetected() {
						String ariText = "";
						for (int i = 0; i < ariRanges.size(); i++) {
							ariText += "\t" + ariRanges.get(i);
						}
						int length = ariRanges.size() / 2;

						ariText += "\n";

						String dailyPlan = "plan\t" + length + ariText;

						try {
							bufferedWriter.write(dailyPlan);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});

			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
