package yuku.alkitab.base.model;

public class ReadingPlan {

	public ReadingPlanInfo info = new ReadingPlanInfo();
	public int[][] dailyVerses;

	public static class ReadingPlanInfo {
		public long id;
		public int version;
		public String name;
		public String title;
		public String description;
		public int duration;
		public long startDate;
		public String url;
	}

}
