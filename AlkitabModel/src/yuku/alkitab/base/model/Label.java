package yuku.alkitab.base.model;

public class Label implements Comparable<Label> {
	public static final String TAG = Label.class.getSimpleName();
	
	public long _id;
	public String title;
	public int ordering;
	public String backgroundColor;
	
	public Label() {
	}
	
	public Label(long _id, String title, int ordering, String bgColor) {
		this._id = _id;
		this.title = title;
		this.ordering = ordering;
		this.backgroundColor = bgColor;
	}

	@Override public int compareTo(Label another) {
		return this.ordering - another.ordering;
	}
	
	@Override public String toString() {
		return this.title + " (" + this._id + ")"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
