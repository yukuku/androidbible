package yuku.alkitab.base.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.provider.BaseColumns;
import yuku.alkitab.R;
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

	public static int getDefaultProgressMarkResource(int position) {
		int res = 0;
		switch (position) {
			case 0:
				res = R.string.pm_progress_1;
				break;
			case 1:
				res = R.string.pm_progress_2;
				break;
			case 2:
				res = R.string.pm_progress_3;
				break;
			case 3:
				res = R.string.pm_progress_4;
				break;
			case 4:
				res = R.string.pm_progress_5;
				break;
		}
		return res;
	}

	public static int getProgressMarkIconResource(int position) {
		int iconRes = 0;
		switch (position) {
			case 0:
				iconRes = R.drawable.ic_attr_progress_mark_1;
				break;
			case 1:
				iconRes = R.drawable.ic_attr_progress_mark_2;
				break;
			case 2:
				iconRes = R.drawable.ic_attr_progress_mark_3;
				break;
			case 3:
				iconRes = R.drawable.ic_attr_progress_mark_4;
				break;
			case 4:
				iconRes = R.drawable.ic_attr_progress_mark_5;
				break;
		}
		return iconRes;
	}

}
