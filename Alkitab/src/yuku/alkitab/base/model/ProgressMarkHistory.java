package yuku.alkitab.base.model;

import android.database.Cursor;
import android.provider.BaseColumns;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.util.Sqlitil;

import java.util.Date;

public class ProgressMarkHistory {
	public long _id;
	public int progress_mark_preset_id;
	public String progress_mark_caption;
	public int ari;
	public Date createTime;

	private ProgressMarkHistory() {}

	public static ProgressMarkHistory fromCursor(Cursor c) {
		final ProgressMarkHistory res = new ProgressMarkHistory();
		res._id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID));
		res.progress_mark_preset_id = c.getInt(c.getColumnIndexOrThrow(Db.ProgressMarkHistory.progress_mark_preset_id));
		res.progress_mark_caption = c.getString(c.getColumnIndexOrThrow(Db.ProgressMarkHistory.progress_mark_caption));
		res.ari = c.getInt(c.getColumnIndexOrThrow(Db.ProgressMarkHistory.ari));
		res.createTime = Sqlitil.toDate(c.getInt(c.getColumnIndexOrThrow(Db.ProgressMarkHistory.createTime)));

		return res;
	}
}
