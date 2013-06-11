package yuku.alkitab.readingplan.model;

import java.util.List;

public class ByDayPlanData extends PlanData {
	public static final String TAG = ByDayPlanData.class.getSimpleName();

	public static class DayReading {
		/** [start, end, start, end, ...] */
		public int[] aris;
	}

	public List<DayReading> dayReadings;
}
