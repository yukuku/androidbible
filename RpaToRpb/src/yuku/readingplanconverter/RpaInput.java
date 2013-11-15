package yuku.readingplanconverter;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class RpaInput {

	public static class Rpa {

		public Map<String, String> infos;
		public List<int[]> plans;

		private void addInfo(String k, String v) {
			if (infos == null) infos = new LinkedHashMap<String, String>();
			infos.put(k, v);
		}

		private void addPlans(int[] aris) {
			if (plans == null) plans = new ArrayList<int[]>();
			plans.add(aris);
		}
	}


	public Rpa parse(String inputFile) {
		Rpa brp = new Rpa();

		try {
			Scanner scanner = new Scanner(new File(inputFile), "utf-8");

			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String[] splits = line.split("\t", -1);

				if ("info".equals(splits[0])) {
					brp.addInfo(splits[1], splits[2]);
				} else if ("plan".equals(splits[0])) {
					int count = Integer.parseInt(splits[1]) * 2;
					int[] aris = new int[count];
					for (int i = 0; i < count; i++) {
						aris[i] = Integer.parseInt(splits[i + 2]);
					}
					brp.addPlans(aris);
				}
			}


		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		return brp;
	}
}
