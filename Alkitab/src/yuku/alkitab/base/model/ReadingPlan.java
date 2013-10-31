package yuku.alkitab.base.model;

import java.util.ArrayList;
import java.util.List;

public class ReadingPlan {
	public static final String READING_PLAN_ARI_RANGES = "reading_plan_ari_ranges";

	public int version;
	public String title;
	public String description;
	public int duration;
	public long startDate;
	public List<int[]> dailyVerses = new ArrayList<int[]>();

	public static class ReadingPlanProgress {
		public static final String READING_PLAN_PROGRESS_READ = "reading_plan_progress_read";

		public long readingPlanId;
		public int readingPlanProgressCode;
	}
}
