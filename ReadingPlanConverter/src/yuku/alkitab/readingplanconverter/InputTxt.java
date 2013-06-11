package yuku.alkitab.readingplanconverter;

import yuku.alkitab.readingplan.model.ByDayPlanData;
import yuku.alkitab.readingplan.model.Plan;

import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

public class InputTxt {
	public static final String TAG = InputTxt.class.getSimpleName();

	public Plan readPlan(File file) throws Exception {
		Plan res = new Plan();

		ByDayPlanData planData = new ByDayPlanData();

		try (Scanner sc = new Scanner(file, "utf-8")) {
			while (sc.hasNext()) {
				final String command = sc.next();
				if (command.startsWith("#")) {
					// comment
					sc.nextLine(); // slurp
				} else {
					System.out.println("command: " + command);

					switch (command) {
						case "name":
							res.name = sc.nextLine().trim();
							break;
						case "title":
							res.title = sc.nextLine().trim();
							break;
						case "description":
							res.description = sc.nextLine().trim();
							break;
						case "kind":
							final String kind_value_string = sc.next().trim();
							System.out.println("kind_value_string: " + kind_value_string);
							res.kind = Plan.Kind.valueOf(kind_value_string);
							break;
						default:
							try {
								int day_no = Integer.parseInt(command);

								if (res.data == null) {
									res.data = planData;
									planData.dayReadings = new ArrayList<>();
								}

								if (day_no > planData.dayReadings.size() + 1) {
									throw new RuntimeException("day_no " + day_no + " is not in order");
								} else {
									final ByDayPlanData.DayReading dayReading = new ByDayPlanData.DayReading();
									final int ari_pair_count = sc.nextInt();
									dayReading.aris = new int[ari_pair_count * 2];
									for (int i = 0; i < ari_pair_count; i++) {
										final int b1 = sc.nextInt();
										final int c1 = sc.nextInt();
										final int v1 = sc.nextInt();
										final int b2 = sc.nextInt();
										final int c2 = sc.nextInt();
										final int v2 = sc.nextInt();
										dayReading.aris[i * 2 + 0] = b1 << 16 | c1 << 8 | v1;
										dayReading.aris[i * 2 + 1] = b2 << 16 | c2 << 8 | v2;
									}
									planData.dayReadings.add(dayReading);
								}
							} catch (NumberFormatException e) {
								throw new RuntimeException("Unknown command: " + command);
							}
							break;
					}
				}
			}
		}

		return res;
	}
}
