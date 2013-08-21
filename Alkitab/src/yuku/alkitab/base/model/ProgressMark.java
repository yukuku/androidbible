package yuku.alkitab.base.model;

import android.content.ContentValues;
import android.database.Cursor;
import yuku.alkitab.base.storage.Db;

public class ProgressMark {
	public long _id;
	public String caption;

	private ProgressMark() {}

	public ProgressMark(String caption) {
		this.caption = caption;
	}

	public ProgressMark fromCursor(Cursor c) {
		ProgressMark res = new ProgressMark(c.getString(c.getColumnIndexOrThrow(Db.ProgressMark.caption)));
	}

	public ContentValues toContentValues() {
		ContentValues res = new ContentValues();
		res.put(Db.ProgressMark.caption, caption);
		return res;
	}
}
