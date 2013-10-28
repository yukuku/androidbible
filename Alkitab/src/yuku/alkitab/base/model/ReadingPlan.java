package yuku.alkitab.base.model;

import java.util.ArrayList;
import java.util.List;

public class ReadingPlan {
	public int version;
	public String title;
	public String description;
	public int duration;
	public List<int[]> dailyVerses = new ArrayList<int[]>();

}
