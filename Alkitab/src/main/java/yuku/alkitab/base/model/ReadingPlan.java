package yuku.alkitab.base.model;

public class ReadingPlan {
	public static final String PROGRESS_GID_PREFIX = "g2:rp_progress:";

	public ReadingPlanInfo info = new ReadingPlanInfo();
	public int[][] dailyVerses;

	public static class ReadingPlanInfo {
		public long id;
		public int version;
		public String name;
		public String title;
		public String description;
		public int duration;
		/** starting time of this reading plan in millis */
		public long startTime;
		public String url;
	}

	public static String gidFromName(String name) {
		return PROGRESS_GID_PREFIX + name;
	}
}
