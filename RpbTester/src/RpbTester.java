import yuku.alkitab.util.Ari;
import yuku.alkitabconverter.util.KjvUtils;
import yuku.bintex.BintexReader;
import yuku.bintex.ValueMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RpbTester {
	private static final String FILE_PATH = System.getProperty("user.dir") + "/RpbTester/file/";
	private static final String FILE_NAME = "rp_esv_daily_reading_bible";
	private static final String INPUT_FILE = FILE_NAME + ".rpb";
	private static final String OUTPUT_FILE = "summary " + FILE_NAME + ".txt";

	private static final byte[] RPB_HEADER = { 0x52, (byte) 0x8a, 0x61, 0x34, 0x00, (byte) 0xe0, (byte) 0xea};

	static String[] names = {
	"Gen",   //$NON-NLS-1$
	"Exod",  //$NON-NLS-1$
	"Lev",   //$NON-NLS-1$
	"Num",   //$NON-NLS-1$
	"Deut",  //$NON-NLS-1$
	"Josh",  //$NON-NLS-1$
	"Judg",  //$NON-NLS-1$
	"Ruth",  //$NON-NLS-1$
	"1Sam",  //$NON-NLS-1$
	"2Sam",  //$NON-NLS-1$
	"1Kgs",  //$NON-NLS-1$
	"2Kgs",  //$NON-NLS-1$
	"1Chr",  //$NON-NLS-1$
	"2Chr",  //$NON-NLS-1$
	"Ezra",  //$NON-NLS-1$
	"Neh",   //$NON-NLS-1$
	"Esth",  //$NON-NLS-1$
	"Job",   //$NON-NLS-1$
	"Ps",    //$NON-NLS-1$
	"Prov",  //$NON-NLS-1$
	"Eccl",  //$NON-NLS-1$
	"Song",  //$NON-NLS-1$
	"Isa",   //$NON-NLS-1$
	"Jer",   //$NON-NLS-1$
	"Lam",   //$NON-NLS-1$
	"Ezek",  //$NON-NLS-1$
	"Dan",   //$NON-NLS-1$
	"Hos",   //$NON-NLS-1$
	"Joel",  //$NON-NLS-1$
	"Amos",  //$NON-NLS-1$
	"Obad",  //$NON-NLS-1$
	"Jonah", //$NON-NLS-1$
	"Mic",   //$NON-NLS-1$
	"Nah",   //$NON-NLS-1$
	"Hab",   //$NON-NLS-1$
	"Zeph",  //$NON-NLS-1$
	"Hag",   //$NON-NLS-1$
	"Zech",  //$NON-NLS-1$
	"Mal",   //$NON-NLS-1$
	"Matt",  //$NON-NLS-1$
	"Mark",  //$NON-NLS-1$
	"Luke",  //$NON-NLS-1$
	"John",  //$NON-NLS-1$
	"Acts",  //$NON-NLS-1$
	"Rom",   //$NON-NLS-1$
	"1Cor",  //$NON-NLS-1$
	"2Cor",  //$NON-NLS-1$
	"Gal",   //$NON-NLS-1$
	"Eph",   //$NON-NLS-1$
	"Phil",  //$NON-NLS-1$
	"Col",   //$NON-NLS-1$
	"1Thess",//$NON-NLS-1$
	"2Thess",//$NON-NLS-1$
	"1Tim",  //$NON-NLS-1$
	"2Tim",  //$NON-NLS-1$
	"Titus", //$NON-NLS-1$
	"Phlm",  //$NON-NLS-1$
	"Heb",   //$NON-NLS-1$
	"Jas",   //$NON-NLS-1$
	"1Pet",  //$NON-NLS-1$
	"2Pet",  //$NON-NLS-1$
	"1John", //$NON-NLS-1$
	"2John", //$NON-NLS-1$
	"3John", //$NON-NLS-1$
	"Jude",  //$NON-NLS-1$
	"Rev",   //$NON-NLS-1$
	};
	private static BufferedWriter writer;
	private static String output;

	public static void main(String[] args) {
		read();
	}

	private static void read() {
		try {
			InputStream is = new FileInputStream(new File(FILE_PATH + INPUT_FILE));
			BintexReader reader = new BintexReader(is);
			byte[] headers = new byte[8];
			reader.readRaw(headers);

			File file = new File(FILE_PATH + OUTPUT_FILE);
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			writer = new BufferedWriter(fw);

			for (int i = 0; i < 7; i++) {
				if (RPB_HEADER[i] != headers[i]) {
					System.out.println("Header is not recognized.");
					throw new IOException();
				}
			}
			output = "Versi: " + headers[7] + "\n";
			System.out.print(output);
			writer.write(output);

			ValueMap map = reader.readValueSimpleMap();
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				output = entry.getKey() + " " + entry.getValue() + "\n";
				System.out.print(output);
				writer.write(output);
			}

			int days = (Integer) map.get("duration");
			List<int[]> plans = new ArrayList<int[]>();

			int counter = 0;
			while (counter < days) {
				int count = reader.readUint8();
				if (count == -1) {
					break;
				}
				counter++;

				int[] aris = new int[count];
				for (int j = 0; j < count; j++) {
					aris[j] = reader.readInt();
				}
				plans.add(aris);
			}
			if (reader.readUint8() != 0) {
				output = "Error! No footer\n";
				System.out.print(output);
				writer.write(output);
			}

			output = "Jumlah plan = " + plans.size() + "\n";
			System.out.print(output);
			writer.write(output);

			int[] lids = new int[31102];
			for (int[] aris : plans) {
				for (int i = 0; i < aris.length / 2; i++) {
					int ariStart = aris[i * 2];
					int ariEnd = aris[i * 2 + 1];
					writeLid(lids, ariStart, ariEnd);
				}
			}

			int max = 0;
			for (int lid : lids) {
				if (lid > max) {
					max = lid;
				}
			}

			int total = 0;
			for (int i = 0; i <= max; i++) {
				output = "------------- Dibaca : " + i + " kali -----------\n";
				System.out.print(output);
				writer.write(output);

				int totalX = 0;
				for (int j = 0; j < lids.length; j++) {
					if (lids[j] == i) {
						int ari = KjvUtils.lidToAri(j + 1);
						output = "ari: " + ari + " lid: " + j + " " + names[Ari.toBook(ari)] + " " + Ari.toChapter(ari) + ":" + Ari.toVerse(ari) + "\n";
						System.out.print(output);
						writer.write(output);
						totalX++;
					}
				}
				output = "Total yang dibaca sebanyak " + i + " kali adalah= " + totalX + "\n";
				System.out.println(output);
				writer.write(output);
				total += totalX;
			}
			if (total == 31102) {
				output = "Jumlah ayat benar = 31102.";
				System.out.print(output);
				writer.write(output);
			} else {
				output = "Jumlah ayat salah. Yang dibaca ada: " + total;
				System.out.print(output);
				writer.write(output);
			}

			writer.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void writeLid(int[] lids, int ariStart, int ariEnd) throws IOException {
		int bookStart = Ari.toBook(ariStart);
		int chapterStart = Ari.toChapter(ariStart);
		int verseStart = Ari.toVerse(ariStart);
		int bookEnd = Ari.toBook(ariEnd);
		int chapterEnd = Ari.toChapter(ariEnd);
		int verseEnd = Ari.toVerse(ariEnd);

		// kej 1
		// kej 1 - 3
		// kej 1 - kej 3:5
		// kej 1:3 - kej 3
		// kej 1 : 3 - 6
		// kej 1:1 - kej 3:4

		if (verseStart == 0) {
			verseStart = 1;
		}
		if (verseEnd == 0) {
			verseEnd = KjvUtils.getVerseCount(bookStart, chapterEnd);
		}
		for (int i = chapterStart; i <= chapterEnd; i++) {
			int chapterVerseStart;
			int verseCount;
			if (chapterStart == chapterEnd) {
				chapterVerseStart = verseStart;
				verseCount = verseEnd - verseStart + 1;
			} else if (i == chapterStart) {
				chapterVerseStart=verseStart;
				verseCount = KjvUtils.getVerseCount(bookStart, i) - verseStart + 1;
				int a = 0;
				if (verseStart != 1) {
					a = verseCount;
				}
				a++;
			} else if (i == chapterEnd) {
				chapterVerseStart = 1;
				verseCount = verseEnd;
			} else {
				chapterVerseStart = 1;
				verseCount = KjvUtils.getVerseCount(bookStart, i);
			}

			int ari = Ari.encode(bookStart, i, chapterVerseStart);
			for (int j = 0; j < verseCount; j++) {
				int lid = KjvUtils.ariToLid(ari + j);
				if (lid == 0) {
					output = "ERROR: Verse error on ari " + (ari + j) + ": " + names[Ari.toBook(ari)] + " " + Ari.toChapter(ari + j) + ":" + Ari.toVerse(ari + j) + "\n";
					System.out.print(output);
					writer.write(output);
					continue;
				}
				lids[lid - 1]++;
			}
		}

	}
}
