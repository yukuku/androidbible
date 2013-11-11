package yuku.alkitabconverter.daily_verse;

import yuku.bintex.BintexWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class DailyVerseProses {

	private static final String INPUT_FILE = System.getProperty("user.dir") + "/AlkitabConverter/file/daily_verses_bt.csv";
	private static final String OUTPUT_FILE = System.getProperty("user.dir") + "/AlkitabConverter/file/daily_verses_bt.bt";
	private List<Integer> aris = new ArrayList<Integer>();
	private List<Integer> verseCounts = new ArrayList<Integer>();

	public static void main(String[] args) {
		DailyVerseProses proses = new DailyVerseProses();
		proses.parse();
		try {
			FileOutputStream fos = new FileOutputStream(new File(OUTPUT_FILE));
			BintexWriter bw = new BintexWriter(fos);
			proses.write(bw);
			bw.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Process finished.");

	}
	private void parse() {
		try {
			Scanner scanner = new Scanner(new File(INPUT_FILE), "utf-8");

			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String[] data = line.split(",");
				aris.add(Integer.parseInt(data[0]));
				verseCounts.add(Integer.parseInt(data[1]));
			}


		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

	}

	private void write(BintexWriter bw) {
		if (aris.size() == verseCounts.size()) {
			for (int i = 0; i < aris.size(); i++) {
				try {
					bw.writeInt(aris.get(i));
					bw.writeUint8(verseCounts.get(i));
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		}
	}

}
