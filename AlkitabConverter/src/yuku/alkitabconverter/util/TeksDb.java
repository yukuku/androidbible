package yuku.alkitabconverter.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import yuku.alkitabconverter.bdb.BdbProses.Rec;


public class TeksDb {
	public static final String TAG = TeksDb.class.getSimpleName();
	
	public static class AyatState {
		// was: "public int menjorok;" but no longer used
		public String isi;
	}
	
	TreeMap<Integer, AyatState> teks = new TreeMap<Integer, AyatState>();
	
	public String append(int kitab_0, int pasal_1, int ayat_1, String s, int menjorokKini) {
		return append(Ari.encode(kitab_0, pasal_1, ayat_1), s, menjorokKini);
	}
	
	/**
	 * @param menjorokKini if -1, don't write anything. 
	 * @return
	 */
	public String append(int ari, String s, int menjorokKini) {
		AyatState as = teks.get(ari);
		if (as == null) {
			as = new AyatState();
			as.isi = "";
			teks.put(ari, as);
		}
		
		boolean writtenParaMarker = false;
		
		if (menjorokKini != -1) {
			if (menjorokKini == -2) {
				as.isi += "@^";
			} else if (menjorokKini < 0 || menjorokKini > 4) {
				throw new RuntimeException("menjorok ngaco: " + menjorokKini);
			} else {
				as.isi += "@" + String.valueOf(menjorokKini);
			}
			
			writtenParaMarker = true;
			
			// was: "update menjoroknya ayatstate" but no longer used
//			for (int i = 0; i < as.isi.length(); i++) {
//				if (as.isi.charAt(i) == '@' && as.isi.charAt(i+1) >= '0' && as.isi.charAt(i+1) <= '4') {
//					as.menjorok = as.isi.charAt(i+1) - '0';
//				}
//			}
		}
		
		if (writtenParaMarker) {
			as.isi += leftSpaceTrim(s);
		} else {
			as.isi += s;
		}
		
		// buang spasi di depan kalo ada
		while (as.isi.startsWith(" ")) {
			as.isi = as.isi.substring(1);
		}
		
		// kasih @@ kalo depannya blum ada
		if (as.isi.contains("@") && !as.isi.startsWith("@@")) {
			as.isi = "@@" + as.isi;
		}
		
		return as.isi;
	}
	
	private static String leftSpaceTrim(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) != ' ') {
				return s.substring(i);
			}
		}
		return s;
	}
	
	public void normalize() {
		Set<Integer> keys = new TreeSet<Integer>(teks.keySet());
		int last_kitab_0 = -1;
		int last_pasal_1 = 0;
		int last_ayat_1 = 0;
		
		for (int ari: keys) {
			int kitab_0 = Ari.toKitab(ari);
			int pasal_1 = Ari.toPasal(ari);
			int ayat_1 = Ari.toAyat(ari);
			
			if (kitab_0 != last_kitab_0) {
				// must start with pasal_1 1 and ayat_1 1
				if (pasal_1 != 1 || ayat_1 != 1) {
					throw new RuntimeException("at " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ": " + " new book does not start from 1:1");
				}
				// different book, ignore and restart
				last_kitab_0 = kitab_0;
				last_pasal_1 = pasal_1;
				last_ayat_1 = ayat_1;
				continue;
			}
			
			if (pasal_1 == last_pasal_1) {
				if (ayat_1 != last_ayat_1 + 1) {
					System.out.println("at " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ": " + " skipped after " + last_kitab_0 + " " + last_pasal_1 + " " + last_ayat_1);
					System.out.println("Adding empty verses:");
					for (int a = last_ayat_1 + 1; a < ayat_1; a++) {
						System.out.println("  at " + kitab_0 + " " + pasal_1 + " " + a + ": " + " (blank)");
						append(kitab_0, pasal_1, a, "", 0);
					}
				}
			} else if (pasal_1 == last_pasal_1 + 1) {
				if (ayat_1 != 1) {
					throw new RuntimeException("at " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ": " + " ayat_1 is not 1");
				}
			} else {
				throw new RuntimeException("at " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ": " + " so wrong! it's after " + last_kitab_0 + " " + last_pasal_1 + " " + last_ayat_1);
			}
			
			last_kitab_0 = kitab_0;
			last_pasal_1 = pasal_1;
			last_ayat_1 = ayat_1;
		}
		
		System.out.println("normalize done");
	}
	
	public void dump() {
		System.out.println("TOTAL teks: " + teks.size());
		for (Entry<Integer, AyatState> e: teks.entrySet()) {
			System.out.printf("%d\t%d\t%d\t%s%n", Ari.toKitab(e.getKey()) + 1, Ari.toPasal(e.getKey()), Ari.toAyat(e.getKey()), e.getValue().isi);
		}
	}

	public int size() {
		return teks.size();
	}

	public List<Rec> toRecList() {
		List<Rec> res = new ArrayList<Rec>();
		for (Entry<Integer, AyatState> e: teks.entrySet()) {
			Rec rec = new Rec();
			int ari = e.getKey();
			rec.kitab_1 = Ari.toKitab(ari) + 1;
			rec.pasal_1 = Ari.toPasal(ari);
			rec.ayat_1 = Ari.toAyat(ari);
			rec.isi = e.getValue().isi;
			res.add(rec);
		}
		return res;
	}
}
