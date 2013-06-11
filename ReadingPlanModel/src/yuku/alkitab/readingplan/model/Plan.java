package yuku.alkitab.readingplan.model;

public class Plan {
	public static final String TAG = Plan.class.getSimpleName();

	public enum Kind {
		by_day,
		;
	}

	public Kind kind;
	public String name;
	public String title;
	public String description;

	public PlanData data;
}
