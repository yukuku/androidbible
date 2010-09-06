package yuku.alkitab.base;

import java.util.*;

import yuku.alkitab.base.model.Kitab;
import android.util.Log;

public class Peloncat {
	private String p_kitab;
	private int p_pasal;
	private int p_ayat;
	
	private static class KitabRef {
		String pendek;
		int pos;
		
		public String toString() {
			return pendek + ":" + pos;
		}
	}
	
	private static IdentityHashMap<Kitab[], Peloncat.KitabRef[]> pendekCache = new IdentityHashMap<Kitab[], Peloncat.KitabRef[]>();
	
	/**
	 * Ga bisa diparse sebagai bilangan. "4-5" true. "Halo" true. "123" false.
	 */
	private static boolean isKata(String s) {
		char c = s.charAt(0);
		if (c < '0' || c > '9') return true;
		
		try {
			Integer.parseInt(s);
			return false;
		} catch (NumberFormatException e) {
			return true;
		}
	}
	
	private static boolean isAngka(String s) {
		try {
			Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}
	
	private static int angkain(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
	
	private boolean parse0(String alamat) {
		alamat = alamat.trim();
		
		if (alamat.length() == 0) {
			return false;
		}
		
		Log.d("alki", "peloncat tahap 0: " + alamat);
		
		//# TAHAP 5: Buang spasi di sebelah kiri-kanan tanda "-"
		if (alamat.contains("-")) {
			alamat = alamat.replaceAll("\\s+-\\s+|\\s+-|-\\s+", "-");
			
			Log.d("alki", "peloncat tahap 5: " + alamat);
		}
		
		//# TAHAP 10: BELAH BERDASAR SPASI, :, TITIK, dan kosong di antara - dan angka.
		//# Contoh output salah: [Kisah, rasul34, 6-7, 8]
		//# Contoh output betul: [Kisah, rasul34, 6, -, 7, 8]
		String[] bagian = alamat.split("((\\s|:|\\.)+|(?=[0-9])(?<=-)|(?=-)(?<=[0-9]))");
		Log.d("alki", "peloncat tahap 10: " + Arrays.toString(bagian));
		
		if (bagian.length == 0) {
			return false;
		}
		
		//# TAHAP 20: lebarin dulu kasus semacam Yoh3 jadi Yoh 3
		//# Contoh output: [Kisah, rasul, 34, 6, -, 7, 8]
		{
			ArrayList<String> bel = new ArrayList<String>();
			
			for (String b: bagian) {
				if (isKata(b)) {
					String angka = "";
					for (int i = b.length() - 1; i >= 0; i--) {
						char c = b.charAt(i);
						if (c >= '0' && c <= '9') {
							// angka ketemu
							angka = c + angka;
						} else {
							break;
						}
					}
					
					if (angka.length() > 0) { // ada angka ketemu di belakang kata
						bel.add(b.substring(0, b.length() - angka.length()));
						bel.add(angka);
					} else {
						bel.add(b);
					}
				} else {
					bel.add(b);
				}
			}
			
			bagian = bel.toArray(bagian);
		}
		Log.d("alki", "peloncat tahap 20: " + Arrays.toString(bagian));
		

		//# TAHAP 25: cari elemen bagian yang "-", lalu buang mulai itu sampe belakang.
		{
			boolean adaStrip = false;
			int di = -1;
			
			for (int i = 0; i < bagian.length; i++) {
				if ("-".equals(bagian[i])) {
					adaStrip = true;
					di = i;
					break;
				}
			}
			
			if (adaStrip) {
				String[] bel = new String[di];
				System.arraycopy(bagian, 0, bel, 0, di);
				bagian = bel;
				
				Log.d("alki", "peloncat tahap 25: " + Arrays.toString(bagian));
			}
		}
		
		//# TAHAP 30: ubah semacam "3" "yohanes" jadi "3 yohanes"
		{
			ArrayList<String> bel = new ArrayList<String>(); 
			
			int mulaiKata = 0;
			
			// liat dari kanan mana yang bukan angka, itu mulainya kitab
			for (int i = bagian.length - 1; i >= 0; i--) {
				if (! isAngka(bagian[i])) {
					// ini dan depannya semua adalah kitab
					mulaiKata = i;
					
					break;
				}
				
				if (i == 0 && bagian.length > 2) {
					// kebanyakan, masa lebih dari 2 bilangan
					return false;
				}
			}

			String s = null;
			for (int j = 0; j <= mulaiKata; j++) {
				s = (s == null)? bagian[j]: s + " " + bagian[j];
			}
			
			bel.add(s);
			for (int j = mulaiKata+1; j < bagian.length; j++) {
				bel.add(bagian[j]);
			}

			bagian = bel.toArray(new String[0]);
		}
		Log.d("alki", "peloncat tahap 30: " + Arrays.toString(bagian));
		
		if (bagian.length == 1) { // 1 bagian doang
			// , berati PASAL ato KITAB doang
			if (isKata(bagian[0])) {
				// kitab
				p_kitab = bagian[0];
				return true;
			} else {
				p_pasal = angkain(bagian[0]);
				return true;
			}
		}

		if (bagian.length == 2) { // 2 bagian
			// , berarti bisa PASAL AYAT (dalam kitab sama)
			if (isAngka(bagian[0]) && isAngka(bagian[1])) {
				p_pasal = angkain(bagian[0]);
				p_ayat = angkain(bagian[1]);
				return true;
			}
			// atau KITAB PASAL
			else if (isAngka(bagian[1])) {
				p_kitab = bagian[0];
				p_pasal = angkain(bagian[1]);
				return true;
			}
			return false;
		}
		
		if (bagian.length == 3) { // 3 bagian
			// , berarti harus KITAB PASAL AYAT. Ga boleh yang lain
			p_kitab = bagian[0];
			p_pasal = angkain(bagian[1]);
			p_ayat = angkain(bagian[2]);
			return true;
		}
		
		return false;
	}
	
	public boolean parse(String alamat) {
		boolean res = parse0(alamat);
		
		Log.d("alki", "peloncat sesudah parse0: p_kitab=" + p_kitab);
		Log.d("alki", "peloncat sesudah parse0: p_pasal=" + p_pasal);
		Log.d("alki", "peloncat sesudah parse0: p_ayat=" + p_ayat);
		
		return res;
	}
	
	private int tebakKitab(Kitab[] xkitab) {
		if (p_kitab == null) {
			return -1;
		}
		
		int res = -1;
		
		// 0. bikin cache semua judul kitab yang dibuang spasinya dan dikecilin semua dan 1 jadi I, 2 jadi II, dst
		{
			Peloncat.KitabRef[] refs = pendekCache.get(xkitab);
			
			if (refs == null) {
				ArrayList<Peloncat.KitabRef> a = new ArrayList<Peloncat.KitabRef>();
				
				for (Kitab k: xkitab) {
					String judul = k.judul.replaceAll("(\\s|-|_)+", "").toLowerCase();
					
					{
						Peloncat.KitabRef ref = new KitabRef();
						ref.pendek = judul;
						ref.pos = k.pos;
						
						a.add(ref);
					}
					
					if (judul.contains("1") || judul.contains("2") || judul.contains("3")) {
						judul = judul.replaceAll("1", "i").replaceAll("2", "ii").replaceAll("3", "iii");
						
						Peloncat.KitabRef ref = new KitabRef();
						ref.pendek = judul;
						ref.pos = k.pos;
						
						a.add(ref);
					}
				}
				
				refs = a.toArray(new Peloncat.KitabRef[0]);
				pendekCache.put(xkitab, refs);
				Log.d("alki", "entri pendekCache baru: " + Arrays.toString(refs));
			}
		}
		
		// 0 juga. bersihin p_kitab
		Peloncat.KitabRef[] refs = pendekCache.get(xkitab);
		p_kitab = p_kitab.replaceAll("(\\s|-|_)", "").toLowerCase();
		Log.d("alki", "tebakKitab fase 0: p_kitab = " + p_kitab);
		
		// 1. coba cocokin keseluruhan (co: "kejadian", "yohanes")
		for (Peloncat.KitabRef ref: refs) {
			if (ref.pendek.equals(p_kitab)) {
				Log.d("alki", "tebakKitab fase 1 sukses: " + p_kitab);
				return ref.pos;
			}
		}
		
		// 2. coba cocokin depannya, kalo ada 1 doang yang lulus, sukses
		int pos_buatNanti = -1;
		{
			int lulus = 0;
			for (Peloncat.KitabRef ref: refs) {
				if (ref.pendek.startsWith(p_kitab)) {
					lulus++;
					if (lulus == 1) pos_buatNanti = ref.pos;
				}
			}
			
			if (lulus == 1) {
				Log.d("alki", "tebakKitab fase 2 sukses: " + pos_buatNanti + " untuk " + p_kitab);
				return pos_buatNanti;
			} else {
				Log.d("alki", "tebakKitab fase 2: lulus = " + lulus);
			}
		}
		
		// 3. String matching hanya kalo p_kitab 2 huruf ato lebih
		if (p_kitab.length() >= 2) {
			int minSkor = 99999999;
			int pos = -1;
			
			for (Peloncat.KitabRef ref: refs) {
				int skor = Levenshtein.distance(p_kitab, ref.pendek);
				if (p_kitab.charAt(0) != ref.pendek.charAt(0)) {
					skor += 150; // kira2 1.5 insertion
				}
				
				Log.d("alki", "tebakKitab fase 3: dengan " + ref.pendek + ":" + ref.pos + " skor " + skor);
				
				if (skor < minSkor) {
					minSkor = skor;
					pos = ref.pos;
				}
			}
			
			if (pos != -1) {
				Log.d("alki", "tebakKitab fase 3 sukses: " + pos + " dengan skor " + minSkor);
				return pos;
			}
		}
		
		// 7. Keluarin yang pertama cocok kalo ada lebih dari 1 yang lulus fase 2
		if (pos_buatNanti != -1) {
			Log.d("alki", "tebakKitab fase 7 sukses: " + pos_buatNanti + " untuk " + p_kitab);
			return pos_buatNanti;
		}
		
		return res;
	}
	
	public int getKitab(Kitab[] xkitab) {
		return tebakKitab(xkitab);
	}
	
	public int getPasal() {
		return p_pasal;
	}
	
	public int getAyat() {
		return p_ayat;
	}
}
