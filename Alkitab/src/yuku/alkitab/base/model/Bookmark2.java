package yuku.alkitab.base.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.provider.BaseColumns;

import java.io.IOException;
import java.util.Date;

import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;

import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.util.Sqlitil;

public class Bookmark2 {
	public long _id;
	public int ari;
	public int kind;
	public String caption;
	public Date addTime;
	public Date modifyTime;
	
	/**
	 * Create without _id
	 */
	public Bookmark2(int ari, int kind, String caption, Date addTime, Date modifyTime) {
		this.ari = ari;
		this.kind = kind;
		this.caption = caption;
		this.addTime = addTime;
		this.modifyTime = modifyTime;
	}
	
	/**
	 * _id is not stored
	 */
	public ContentValues toContentValues() {
		ContentValues res = new ContentValues();
		
		res.put(Db.Bookmark2.ari, ari);
		res.put(Db.Bookmark2.kind, kind);
		res.put(Db.Bookmark2.caption, caption);
		res.put(Db.Bookmark2.addTime, Sqlitil.toInt(addTime));
		res.put(Db.Bookmark2.modifyTime, Sqlitil.toInt(modifyTime));
		
		return res;
	}
	
	
	public static final String XMLTAG_Bukmak2 = "Bukmak2"; //$NON-NLS-1$
	private static final String XMLATTR_ari = "ari"; //$NON-NLS-1$
	private static final String XMLATTR_kind = "jenis"; //$NON-NLS-1$
	private static final String XMLATTR_caption = "tulisan"; //$NON-NLS-1$
	private static final String XMLATTR_addTime = "waktuTambah"; //$NON-NLS-1$
	private static final String XMLATTR_modifyTime = "waktuUbah"; //$NON-NLS-1$
	private static final String XMLATTR_relId = "relId"; //$NON-NLS-1$
	private static final String XMLVAL_bookmark = "bukmak"; //$NON-NLS-1$
	private static final String XMLVAL_note = "catatan"; //$NON-NLS-1$
	private static final String XMLVAL_highlight = "stabilo"; //$NON-NLS-1$
	
	public void writeXml(XmlSerializer xml, int relId) throws IOException {
		xml.startTag(null, XMLTAG_Bukmak2);
		xml.attribute(null, XMLATTR_relId, String.valueOf(relId));
		xml.attribute(null, XMLATTR_ari, String.valueOf(ari));
		xml.attribute(null, XMLATTR_kind, kind == Db.Bookmark2.kind_bookmark? XMLVAL_bookmark: kind == Db.Bookmark2.kind_note? XMLVAL_note: kind == Db.Bookmark2.kind_highlight? XMLVAL_highlight: String.valueOf(kind));
		if (caption != null) {
			xml.attribute(null, XMLATTR_caption, caption);
		}
		if (addTime != null) {
			xml.attribute(null, XMLATTR_addTime, String.valueOf(Sqlitil.toInt(addTime)));
		}
		if (modifyTime != null) {
			xml.attribute(null, XMLATTR_modifyTime, String.valueOf(Sqlitil.toInt(modifyTime)));
		}
		xml.endTag(null, XMLTAG_Bukmak2);
	}
	
	public static Bookmark2 fromCursor(Cursor cursor) {
		int ari = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bookmark2.ari));
		int jenis = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bookmark2.kind));
		
		return fromCursor(cursor, ari, jenis);
	}

	public static Bookmark2 fromCursor(Cursor cursor, int ari, int kind) {
		long _id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID));
		String caption = cursor.getString(cursor.getColumnIndexOrThrow(Db.Bookmark2.caption));
		Date addTime = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bookmark2.addTime)));
		Date modifyTime = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bookmark2.modifyTime)));
		
		Bookmark2 res = new Bookmark2(ari, kind, caption, addTime, modifyTime);
		res._id = _id;
		return res;
	}

	public static Bookmark2 fromAttributes(Attributes attributes) {
		int ari = Integer.parseInt(attributes.getValue("", XMLATTR_ari)); //$NON-NLS-1$
		String kind_s = attributes.getValue("", XMLATTR_kind); //$NON-NLS-1$
		int kind = kind_s.equals(XMLVAL_bookmark)? Db.Bookmark2.kind_bookmark: kind_s.equals(XMLVAL_note)? Db.Bookmark2.kind_note: kind_s.equals(XMLVAL_highlight)? Db.Bookmark2.kind_highlight: Integer.parseInt(kind_s);
		String caption = attributes.getValue("", XMLATTR_caption); //$NON-NLS-1$
		Date addTime = Sqlitil.toDate(Integer.parseInt(attributes.getValue("", XMLATTR_addTime))); //$NON-NLS-1$
		Date modifyTime = Sqlitil.toDate(Integer.parseInt(attributes.getValue("", XMLATTR_modifyTime))); //$NON-NLS-1$
		
		return new Bookmark2(ari, kind, caption, addTime, modifyTime);
	}
	
	public static int getRelId(Attributes attributes) {
		String s = attributes.getValue("", XMLATTR_relId); //$NON-NLS-1$
		return s == null? 0: Integer.parseInt(s);
	}
}
