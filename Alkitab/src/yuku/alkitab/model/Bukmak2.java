package yuku.alkitab.model;

import java.util.*;

import yuku.andoutil.*;
import android.content.*;

public class Bukmak2 {
	public int ari;
	public int jenis;
	public String tulisan;
	public Date waktuTambah;
	public Date waktuUbah;
	
	public Bukmak2(int ari, int jenis, String tulisan, Date waktuTambah, Date waktuUbah) {
		this.ari = ari;
		this.jenis = jenis;
		this.tulisan = tulisan;
		this.waktuTambah = waktuTambah;
		this.waktuUbah = waktuUbah;
	}
	
	public ContentValues toContentValues() {
		ContentValues res = new ContentValues();
		
		res.put(AlkitabDb.KOLOM_Bukmak2_ari, ari);
		res.put(AlkitabDb.KOLOM_Bukmak2_jenis, jenis);
		res.put(AlkitabDb.KOLOM_Bukmak2_tulisan, tulisan);
		res.put(AlkitabDb.KOLOM_Bukmak2_waktuTambah, Sqlitil.toInt(waktuTambah));
		res.put(AlkitabDb.KOLOM_Bukmak2_waktuUbah, Sqlitil.toInt(waktuUbah));
		
		return res;
	}
}
