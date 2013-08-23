package yuku.alkitab.base.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.provider.BaseColumns;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.util.Sqlitil;

import java.util.Date;

public class ProgressMark {
	public long _id;
	public String caption;
	public int ari;
	public Date modifyTime;

	private ProgressMark() {}

	public static ProgressMark fromCursor(Cursor c) {
		ProgressMark res = new ProgressMark();
		res._id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID));
		res.caption = c.getString(c.getColumnIndexOrThrow(Db.ProgressMark.caption));
		res.ari = c.getInt(c.getColumnIndexOrThrow(Db.ProgressMark.ari));
		res.modifyTime = Sqlitil.toDate(c.getInt(c.getColumnIndexOrThrow(Db.ProgressMark.modifyTime)));

		return res;
	}

	public ContentValues toContentValues() {
		ContentValues cv = new ContentValues();
		cv.put(Db.ProgressMark.caption, caption);
		cv.put(Db.ProgressMark.ari, ari);
		cv.put(Db.ProgressMark.modifyTime, Sqlitil.toInt(modifyTime));
		return cv;
	}

}
