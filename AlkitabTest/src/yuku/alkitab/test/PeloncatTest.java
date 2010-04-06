package yuku.alkitab.test;

import junit.framework.Assert;
import yuku.alkitab.Peloncat;
import yuku.alkitab.model.Kitab;
import android.test.AndroidTestCase;
import android.util.Log;

public class PeloncatTest extends AndroidTestCase {
	static Kitab[] xkitab;

	static {
		String[] ss = {
				"Kejadian", // 0
				"Keluaran",
				"Imamat",
				"Bilangan",
				"Ulangan",
				"Yosua",
				"Hakim-hakim",
				"Rut",
				"1 Samuel",
				"2 Samuel",
				"1 Raja-raja", // 10
				"2 Raja-raja",
				"1 Tawarikh",
				"2 Tawarikh",
				"Ezra",
				"Nehemia",
				"Ester",
				"Ayub",
				"Mazmur",
				"Amsal",
				"Pengkhotbah", // 20
				"Kidung Agung",
				"Yesaya",
				"Yeremia",
				"Ratapan",
				"Yehezkiel",
				"Daniel",
				"Hosea",
				"Yoel",
				"Amos",
				"Obaja", // 30
				"Yunus",
				"Mikha",
				"Nahum",
				"Habakuk",
				"Zefanya",
				"Hagai",
				"Zakharia",
				"Maleakhi",
				"Matius",
				"Markus", // 40
				"Lukas",
				"Yohanes",
				"Kisah Para Rasul", // 43
				"Roma",
				"1 Korintus",
				"2 Korintus",
				"Galatia",
				"Efesus",
				"Filipi",
				"Kolose", // 50
				"1 Tesalonika",
				"2 Tesalonika",
				"1 Timotius",
				"2 Timotius",
				"Titus",
				"Filemon",
				"Ibrani",
				"Yakobus",
				"1 Petrus",
				"2 Petrus", // 60
				"1 Yohanes",
				"2 Yohanes",
				"3 Yohanes",
				"Yudas",
				"Wahyu", // 65
		};

		xkitab = new Kitab[ss.length];
		for (int i = 0; i < ss.length; i++) {
			Kitab k = new Kitab();
			k.judul = ss[i];
			k.pos = i;
			xkitab[i] = k;
		}
	}
	
	void coba(String alamat, boolean ok, int kitab, int pasal, int ayat) {
		long wmulai = System.currentTimeMillis();
		
		try {
			Peloncat peloncat = new Peloncat();
			
			boolean ok1 = peloncat.parse(alamat);
			if (ok1) {
				int kitab1 = peloncat.getKitab(xkitab);
				Assert.assertEquals(kitab, kitab1);
				
				int pasal1 = peloncat.getPasal();
				Assert.assertEquals(pasal, pasal1);
				
				int ayat1 = peloncat.getAyat();
				Assert.assertEquals(ayat, ayat1);
				
				Log.d(PeloncatTest.class.getSimpleName(), alamat + " -> " + kitab + " " + pasal + " " + ayat);
			} else {
				Assert.assertEquals(ok, ok1);
	
				Log.d(PeloncatTest.class.getSimpleName(), alamat + " -> false");
			}
		} finally {
			Log.d(PeloncatTest.class.getSimpleName(), "butuh " + (System.currentTimeMillis() - wmulai) + " ms");
		}
	}

	public void testParse1() throws Throwable {
		coba("Kej 1:1", true, 0, 1, 1);
		coba("K 1:1", true, 0, 1, 1);
		coba("K 1 1", true, 0, 1, 1);
		coba("K 1  1", true, 0, 1, 1);
		coba("Kej1 1", true, 0, 1, 1);
		coba("K1.1", true, 0, 1, 1);
		coba("Keluaran 1:1", true, 1, 1, 1);
		coba("Kel 1:1", true, 1, 1, 1);
		coba("Klauran 1:1", true, 1, 1, 1);
		coba("Kej 13", true, 0, 13, 0);
		coba("Kej13", true, 0, 13, 0);
		coba("Kej 1 3", true, 0, 1, 3);
		coba("Kej1 34", true, 0, 1, 34);
		coba("Kej", true, 0, 0, 0);
		coba("Kel", true, 1, 0, 0);
		coba("Kid", true, 21, 0, 0);
		
		coba("Kej 4 5 1 3", false, 0, 0, 0);
		coba("Kej 4 5 1", false, 0, 0, 0);
		
		coba("Kisah", true, 43, 0, 0);
		coba("Kisah 4", true, 43, 4, 0);
		coba("Kisah 4 999", true, 43, 4, 999);
		coba("Kisah para", true, 43, 0, 0);
		coba("Kisah para 4", true, 43, 4, 0);
		coba("Kisah para 4 999", true, 43, 4, 999);
		coba("Kisah rasul", true, 43, 0, 0);
		coba("Kisah rasul 4", true, 43, 4, 0);
		coba("Kisah rasul 4 999", true, 43, 4, 999);
		
		// angka di depan
		coba("1yoh", true, 61, 0, 0);
		coba("2yo", true, 62, 0, 0);
		coba("3y", true, 63, 0, 0);
		coba("1yoh 4 5", true, 61, 4, 5);
		coba("2yo 4 5", true, 62, 4, 5);
		coba("3y 4 5", true, 63, 4, 5);
		coba("1 yoh 4 5", true, 61, 4, 5);
		coba("2 yo 4 5", true, 62, 4, 5);
		coba("3 y 4 5", true, 63, 4, 5);
		coba("1 yoh4 5", true, 61, 4, 5);
		coba("2 yo4 5", true, 62, 4, 5);
		coba("3 y4 5", true, 63, 4, 5);
		coba("1yoh4 5", true, 61, 4, 5);
		coba("2yo4 5", true, 62, 4, 5);
		coba("3y4 5", true, 63, 4, 5);
		coba("1yoh4", true, 61, 4, 0);
		coba("2yo4", true, 62, 4, 0);
		coba("3y4", true, 63, 4, 0);
		
		// tanda strip di nama kitab
		coba("1raja-raja 4 999", true, 10, 4, 999);
		coba("2 raja-raja 4 999", true, 11, 4, 999);
		coba("kidung-agung 4 999", true, 21, 4, 999);
		coba("3-yoha 4 999", true, 63, 4, 999);
		

		// pake tanda strip! susah deh.
		coba("Kejadian 1-2", true, 0, 1, 0);
		coba("Kejadian 1:2-3", true, 0, 1, 2);
		coba("Kejadian 1:2-3:4", true, 0, 1, 2);
		
		coba("Kejadian 11-22", true, 0, 11, 0);
		coba("Kejadian 11- 22", true, 0, 11, 0);
		coba("Kejadian 11 -22", true, 0, 11, 0);
		coba("Kejadian 11 - 22", true, 0, 11, 0);
		coba("Kejadian 11:22-33:44", true, 0, 11, 22);
		

		coba("1yoh 4-5", true, 61, 4, 0);
		coba("2 yo 5:6-7", true, 62, 5, 6);
		coba("3y6:7-8", true, 63, 6, 7);
	}
}
