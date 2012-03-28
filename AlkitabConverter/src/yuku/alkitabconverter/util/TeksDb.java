package yuku.alkitabconverter.util;

import java.util.Map.Entry;
import java.util.TreeMap;


public class TeksDb {
	public static final String TAG = TeksDb.class.getSimpleName();
	
	public static class AyatState {
		public int menjorok;
		public String isi;
	}
	
	TreeMap<Integer, AyatState> teks = new TreeMap<Integer, AyatState>();
	
	public String append(int kitab_0, int pasal_1, int ayat_1, String s, int menjorokKini) {
		return append(Ari.encode(kitab_0, pasal_1, ayat_1), s, menjorokKini);
	}
	
	public String append(int ari, String s, int menjorokKini) {
		AyatState as = teks.get(ari);
		if (as == null) {
			as = new AyatState();
			as.menjorok = 0;
			as.isi = "";
			teks.put(ari, as);
		}
		
		if (as.menjorok != menjorokKini) {
			if (menjorokKini < 0 || menjorokKini > 4) throw new RuntimeException("menjorok ngaco: " + menjorokKini);
			as.isi += "@" + String.valueOf(menjorokKini);
			
			// update menjoroknya ayatstate
			for (int i = 0; i < as.isi.length(); i++) {
				if (as.isi.charAt(i) == '@' && as.isi.charAt(i+1) >= '0' && as.isi.charAt(i+1) <= '4') {
					as.menjorok = as.isi.charAt(i+1) - '0';
				}
			}
		}
		
		as.isi += s;
		
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
	
	public void dump() {
		System.out.println("TOTAL teks: " + teks.size());
		for (Entry<Integer, AyatState> e: teks.entrySet()) {
			System.out.printf("%d\t%d\t%d\t%s%n", Ari.toKitab(e.getKey()) + 1, Ari.toPasal(e.getKey()), Ari.toAyat(e.getKey()), e.getValue().isi);
		}
	}
}
