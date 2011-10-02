package yuku.alkitab.base.model;

import android.content.*;
import android.database.*;
import android.provider.*;

import yuku.alkitab.base.storage.*;

public class Label implements Comparable<Label> {
	public static final String TAG = Label.class.getSimpleName();
	
	public long _id;
	public String judul;
	public int urutan;
	public String warnaLatar;
	
	private Label() {
	}
	
	public Label(long _id, String judul, int urutan, String warnaLatar) {
		this._id = _id;
		this.judul = judul;
		this.urutan = urutan;
		this.warnaLatar = warnaLatar;
	}

	public static Label fromCursor(Cursor c) {
		Label res = new Label();
		res._id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID));
		res.judul = c.getString(c.getColumnIndexOrThrow(Db.Label.judul));
		res.urutan = c.getInt(c.getColumnIndexOrThrow(Db.Label.urutan));
		res.warnaLatar = c.getString(c.getColumnIndexOrThrow(Db.Label.warnaLatar));
		return res;
	}
	
	public ContentValues toContentValues() {
		ContentValues res = new ContentValues();
		// skip _id
		res.put(Db.Label.judul, judul);
		res.put(Db.Label.urutan, urutan);
		res.put(Db.Label.warnaLatar, warnaLatar);
		return res;
	}

	@Override public int compareTo(Label another) {
		return this.urutan - another.urutan;
	}
	
	@Override public boolean equals(Object o) {
		return (o instanceof Label) && (this._id == ((Label)o)._id);
	}
	
	@Override public String toString() {
		return this.judul + " (" + this._id + ")"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
