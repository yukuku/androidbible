package yuku.alkitab.base.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.provider.BaseColumns;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;

import yuku.alkitab.base.storage.Db;

public class Label implements Comparable<Label> {
	public static final String TAG = Label.class.getSimpleName();
	
	public long _id;
	public String title;
	public int ordering;
	public String backgroundColor;
	
	private Label() {
	}
	
	public Label(long _id, String title, int ordering, String bgColor) {
		this._id = _id;
		this.title = title;
		this.ordering = ordering;
		this.backgroundColor = bgColor;
	}

	public static Label fromCursor(Cursor c) {
		Label res = new Label();
		res._id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID));
		res.title = c.getString(c.getColumnIndexOrThrow(Db.Label.title));
		res.ordering = c.getInt(c.getColumnIndexOrThrow(Db.Label.ordering));
		res.backgroundColor = c.getString(c.getColumnIndexOrThrow(Db.Label.backgroundColor));
		return res;
	}
	
	public ContentValues toContentValues() {
		ContentValues res = new ContentValues();
		// skip _id
		res.put(Db.Label.title, title);
		res.put(Db.Label.ordering, ordering);
		res.put(Db.Label.backgroundColor, backgroundColor);
		return res;
	}

	@Override public int compareTo(Label another) {
		return this.ordering - another.ordering;
	}
	
	@Override public String toString() {
		return this.title + " (" + this._id + ")"; //$NON-NLS-1$ //$NON-NLS-2$
	}
	

	public static final String XMLTAG_Label = "Label"; //$NON-NLS-1$
	private static final String XMLATTR_title = "judul"; //$NON-NLS-1$
	private static final String XMLATTR_relId = "relId"; //$NON-NLS-1$
	private static final String XMLATTR_bgColor = "warnaLatar"; //$NON-NLS-1$
	
	public void writeXml(XmlSerializer xml, int relId) throws IOException {
		// urutan ga/belum dibekap
		xml.startTag(null, XMLTAG_Label);
		xml.attribute(null, XMLATTR_relId, String.valueOf(relId));
		xml.attribute(null, XMLATTR_title, title);
		if (backgroundColor != null) xml.attribute(null, XMLATTR_bgColor, backgroundColor);
		xml.endTag(null, XMLTAG_Label);
	}

	public static Label fromAttributes(Attributes attributes) {
		String title = attributes.getValue("", XMLATTR_title); //$NON-NLS-1$
		String bgColor = attributes.getValue("", XMLATTR_bgColor); //$NON-NLS-1$
		
		return new Label(-1, title, 0, bgColor);
	}
	
	public static int getRelId(Attributes attributes) {
		String s = attributes.getValue("", XMLATTR_relId); //$NON-NLS-1$
		return s == null? 0: Integer.parseInt(s);
	}
}
