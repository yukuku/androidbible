package yuku.alkitab.base.model;

import android.content.*;
import android.database.*;
import android.provider.*;

import java.io.*;
import java.util.*;

import org.xml.sax.*;
import org.xmlpull.v1.*;

import yuku.alkitab.base.storage.*;
import yuku.andoutil.*;

public class Bukmak2 {
	public long _id;
	public int ari;
	public int jenis;
	public String tulisan;
	public Date waktuTambah;
	public Date waktuUbah;
	
	/**
	 * Bikin bukmak2 baru tanpa _id
	 */
	public Bukmak2(int ari, int jenis, String tulisan, Date waktuTambah, Date waktuUbah) {
		this.ari = ari;
		this.jenis = jenis;
		this.tulisan = tulisan;
		this.waktuTambah = waktuTambah;
		this.waktuUbah = waktuUbah;
	}
	
	/**
	 * _id ga termasuk
	 */
	public ContentValues toContentValues() {
		ContentValues res = new ContentValues();
		
		res.put(Db.Bukmak2.ari, ari);
		res.put(Db.Bukmak2.jenis, jenis);
		res.put(Db.Bukmak2.tulisan, tulisan);
		res.put(Db.Bukmak2.waktuTambah, Sqlitil.toInt(waktuTambah));
		res.put(Db.Bukmak2.waktuUbah, Sqlitil.toInt(waktuUbah));
		
		return res;
	}
	
	
	public static final String XMLTAG_Bukmak2 = "Bukmak2"; //$NON-NLS-1$
	private static final String XMLATTR_ari = "ari"; //$NON-NLS-1$
	private static final String XMLATTR_jenis = "jenis"; //$NON-NLS-1$
	private static final String XMLATTR_tulisan = "tulisan"; //$NON-NLS-1$
	private static final String XMLATTR_waktuTambah = "waktuTambah"; //$NON-NLS-1$
	private static final String XMLATTR_waktuUbah = "waktuUbah"; //$NON-NLS-1$
	private static final String XMLATTR_relId = "relId"; //$NON-NLS-1$
	private static final String XMLVAL_bukmak = "bukmak"; //$NON-NLS-1$
	private static final String XMLVAL_catatan = "catatan"; //$NON-NLS-1$
	private static final String XMLVAL_stabilo = "stabilo"; //$NON-NLS-1$
	
	public void writeXml(XmlSerializer xml, int relId) throws IOException {
		xml.startTag(null, XMLTAG_Bukmak2);
		xml.attribute(null, XMLATTR_relId, String.valueOf(relId));
		xml.attribute(null, XMLATTR_ari, String.valueOf(ari));
		xml.attribute(null, XMLATTR_jenis, jenis == Db.Bukmak2.jenis_bukmak? XMLVAL_bukmak: jenis == Db.Bukmak2.jenis_catatan? XMLVAL_catatan: jenis == Db.Bukmak2.jenis_stabilo? XMLVAL_stabilo: String.valueOf(jenis));
		if (tulisan != null) {
			xml.attribute(null, XMLATTR_tulisan, tulisan);
		}
		if (waktuTambah != null) {
			xml.attribute(null, XMLATTR_waktuTambah, String.valueOf(Sqlitil.toInt(waktuTambah)));
		}
		if (waktuUbah != null) {
			xml.attribute(null, XMLATTR_waktuUbah, String.valueOf(Sqlitil.toInt(waktuUbah)));
		}
		xml.endTag(null, XMLTAG_Bukmak2);
	}
	
	public static Bukmak2 dariCursor(Cursor cursor) {
		int ari = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bukmak2.ari));
		int jenis = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bukmak2.jenis));
		
		return dariCursor(cursor, ari, jenis);
	}

	public static Bukmak2 dariCursor(Cursor cursor, int ari, int jenis) {
		long _id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID));
		String tulisan = cursor.getString(cursor.getColumnIndexOrThrow(Db.Bukmak2.tulisan));
		Date waktuTambah = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bukmak2.waktuTambah)));
		Date waktuUbah = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bukmak2.waktuUbah)));
		
		Bukmak2 res = new Bukmak2(ari, jenis, tulisan, waktuTambah, waktuUbah);
		res._id = _id;
		return res;
	}

	public static Bukmak2 dariAttributes(Attributes attributes) {
		int ari = Integer.parseInt(attributes.getValue("", XMLATTR_ari)); //$NON-NLS-1$
		String jenis_s = attributes.getValue("", XMLATTR_jenis); //$NON-NLS-1$
		int jenis = jenis_s.equals(XMLVAL_bukmak)? Db.Bukmak2.jenis_bukmak: jenis_s.equals(XMLVAL_catatan)? Db.Bukmak2.jenis_catatan: jenis_s.equals(XMLVAL_stabilo)? Db.Bukmak2.jenis_stabilo: Integer.parseInt(jenis_s);
		String tulisan = attributes.getValue("", XMLATTR_tulisan); //$NON-NLS-1$
		Date waktuTambah = Sqlitil.toDate(Integer.parseInt(attributes.getValue("", XMLATTR_waktuTambah))); //$NON-NLS-1$
		Date waktuUbah = Sqlitil.toDate(Integer.parseInt(attributes.getValue("", XMLATTR_waktuUbah))); //$NON-NLS-1$
		
		return new Bukmak2(ari, jenis, tulisan, waktuTambah, waktuUbah);
	}
	
	public static int getRelId(Attributes attributes) {
		String s = attributes.getValue("", XMLATTR_relId);
		return s == null? 0: Integer.parseInt(s);
	}
}
