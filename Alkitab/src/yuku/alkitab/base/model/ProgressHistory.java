package yuku.alkitab.base.model;

import android.content.ContentValues;
import yuku.alkitab.base.storage.Db;

public class ProgressHistory {
	public long _id;
	public long progress_mark_id;
	public int ari;
	public int time;

	public ProgressHistory(long progress_mark_id, int ari, int time) {
		this.progress_mark_id = progress_mark_id;
		this.ari = ari;
		this.time = time;
	}

	public ContentValues toContentValues() {
		ContentValues res = new ContentValues();
		res.put(Db.ProgressHistory.progress_mark_id, progress_mark_id);
		res.put(Db.ProgressHistory.ari, ari);
		res.put(Db.ProgressHistory.time, time);
		return res;
	}
}
