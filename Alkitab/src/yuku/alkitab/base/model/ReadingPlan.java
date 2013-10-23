package yuku.alkitab.base.model;

import android.util.Pair;
import yuku.alkitab.base.util.IntArrayList;

import java.util.ArrayList;
import java.util.List;

public class ReadingPlan {
	public int version;
	public String title;
	public String description;
	public int totalDays;
	public List<Pair<Integer, IntArrayList>> dailyVerse = new ArrayList<Pair<Integer, IntArrayList>>();

}
