package yuku.alkitab.model;

import java.util.Date;

import yuku.andoutil.Sqlitil;
import android.content.ContentValues;

public class Bukmak {
	public String alamat;
	public String cuplikan;
	public Date waktuTambah;
	public int kitab;
	public int pasal;
	public int ayat;
	
	public Bukmak(String alamat, String cuplikan, Date waktuTambah, int kitab, int pasal, int ayat) {
		this.alamat = alamat;
		this.cuplikan = cuplikan;
		this.waktuTambah = waktuTambah;
		this.kitab = kitab;
		this.pasal = pasal;
		this.ayat = ayat;
	}
	
	public ContentValues toContentValues() {
		ContentValues res = new ContentValues();
		
		res.put(AlkitabDb.KOLOM_Bukmak_alamat, alamat);
		res.put(AlkitabDb.KOLOM_Bukmak_cuplikan, cuplikan);
		res.put(AlkitabDb.KOLOM_Bukmak_waktuTambah, Sqlitil.toInt(waktuTambah));
		res.put(AlkitabDb.KOLOM_Bukmak_kitab, kitab);
		res.put(AlkitabDb.KOLOM_Bukmak_pasal, pasal);
		res.put(AlkitabDb.KOLOM_Bukmak_ayat, ayat);
		
		return res;
	}
}
