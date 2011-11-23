package yuku.alkitab.base.model;

import android.content.*;
import android.database.*;
import android.provider.*;

import java.io.*;

import org.xmlpull.v1.*;

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
	

	public static final String XMLTAG_Label = "Label"; //$NON-NLS-1$
	private static final String XMLATTR_judul = "judul"; //$NON-NLS-1$
	private static final String XMLATTR_relId = "relId"; //$NON-NLS-1$
	
	public void writeXml(XmlSerializer xml, int relId) throws IOException {
		// urutan dan warnaLatar ga/belum dibekap
		xml.startTag(null, XMLTAG_Label);
		xml.attribute(null, XMLATTR_relId, String.valueOf(relId));
		xml.attribute(null, XMLATTR_judul, judul);
		xml.endTag(null, XMLTAG_Label);
	}
}
