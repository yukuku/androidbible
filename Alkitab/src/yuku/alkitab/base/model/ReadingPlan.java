package yuku.alkitab.base.model;

import java.util.ArrayList;
import java.util.List;

public class ReadingPlan {

	public ReadingPlanInfo info = new ReadingPlanInfo();
	public List<int[]> dailyVerses = new ArrayList<int[]>();

	public static class ReadingPlanInfo {
		public long id;
		public int version;
		public String title;
		public String description;
		public int duration;
		public long startDate;
		public String filename;
		public String url;
	}

	public static class ReadingPlanProgress {

		public long readingPlanId;
		public int readingPlanProgressCode;

		public static int toReadingCode(int dayNumber, int readingSequence) {
			return dayNumber << 8 | readingSequence;
		}

		public static int toDayNumber(int readingCode) {
			return (readingCode & 0x00ffff00) >> 8;
		}

		public static int toSequence(int readingCode) {
			return (readingCode & 0x000000ff);
		}
	}
}
