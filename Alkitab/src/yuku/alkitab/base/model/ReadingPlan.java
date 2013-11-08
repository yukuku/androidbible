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

}
