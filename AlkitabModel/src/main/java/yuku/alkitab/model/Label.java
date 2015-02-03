package yuku.alkitab.model;

import yuku.alkitab.model.util.Gid;

public class Label implements Comparable<Label> {
	public static final String TAG = Label.class.getSimpleName();

	public long _id;
	public String gid;
	public String title;
	public int ordering;
	public String backgroundColor;

	private Label() {}

	@Override
	public int compareTo(Label another) {
		return this.ordering - another.ordering;
	}

	/**
	 * Create without _id
	 */
	public static Label createNewLabel(String title, int ordering, String bgColor) {
		final Label res = new Label();

		res.gid = Gid.newGid();
		res.title = title;
		res.ordering = ordering;
		res.backgroundColor = bgColor;

		return res;
	}

	public static Label createEmptyLabel() {
		return new Label();
	}
}
