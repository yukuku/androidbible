package yuku.alkitabconverter.unboundbible;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;

import yuku.alkitabconverter.bdb.BdbProses.Rec;

public class UnboundBibleReader {
	public static List<Rec> parse(String nf, int kolom_orig_book_index, int kolom_pasal_1, int kolom_ayat_1, int kolom_isi) throws Exception {
		LinkedHashMap<Integer, Integer> nn = new LinkedHashMap<Integer, Integer>();
		List<Rec> res = new ArrayList<Rec>();
		
		Scanner sc = new Scanner(new File(nf), "utf-8");
		
		int lastKitab_1 = 1;
		int lastPasal_1 = 1;
		int lastAyat_1 = 0;
		
		while (sc.hasNextLine()) {
			String baris = sc.nextLine();
			
			if (baris.startsWith("#") || baris.trim().length() == 0) {
				continue;
			}
			
			String[] xkolom = baris.split("\t", -1);
			if (xkolom.length != kolom_isi + 1) {
				throw new RuntimeException("baris ngaco: " + baris);
			}
			
			String orig_book_index = xkolom[kolom_orig_book_index];
			int pasal_1 = Integer.parseInt(xkolom[kolom_pasal_1]);
			int ayat_1 = Integer.parseInt(xkolom[kolom_ayat_1]);
			// @SuppressWarnings("unused") String subayat_1 = xkolom[3];
			// @SuppressWarnings("unused") int order_by = Integer.parseInt(xkolom[4]);
			String isi = xkolom[kolom_isi];
			
			int kitab_1 = ubToKitab1(orig_book_index);
			
			if (ayat_1 != lastAyat_1 + 1) {
				if (pasal_1 != lastPasal_1 + 1) {
					// if (kitab_1 != lastKitab_1 + 1) {
					if (pasal_1 != 1) {
						throw new RuntimeException("urutan ngaco: " + baris);
					}
				}
			}
			
			nn.put(kitab_1, (nn.get(kitab_1) == null? 0: nn.get(kitab_1)) + 1);
			
			Rec rec = new Rec();
			rec.kitab_1 = kitab_1;
			rec.pasal_1 = pasal_1;
			rec.ayat_1 = ayat_1;
			rec.isi = isi;
			
			res.add(rec);
			
			lastKitab_1 = kitab_1;
			lastPasal_1 = pasal_1;
			lastAyat_1 = ayat_1;
		}
		
		for (Entry<Integer, Integer> e: nn.entrySet()) {
			System.out.println(e.getKey() + ": " + e.getValue());
		}
		
		System.out.println("selesai. Total rec: " + res.size());
		
		return res;
	}

	private static int ubToKitab1(String ub) {
		if (ub.matches("[0-9][0-9](O|N)")) {
			return Integer.parseInt(ub.substring(0, 2));
		} else if (ub.matches("[0-9][0-9]A")) {
			/*
			 * 145 or 740 1 esdras 66
			 * 146 or 750 2 esdras 67
			 * 170 tobit 68
			 * 180 judit 69
			 * 200 1 makabe 70
			 * 210 2 makabe 71
			 * 215 3 makabe 72
			 * 216 4 makabe 73
			 * 231 Psalms (from Heb.) Ps (H) Vulg.: Jerome's translation from the Hebrew 74
			 * 235 Odes 75
			 * 270 wisdom of solomon 76
			 * 280 sirach / Ecclesiasticus 77
			 * 285 Psalms of Solomon 78
			 * 315 or 760 Letter of Jeremiah 79
			 * 320 baruk 80
			 * 335 susanna 81
			 * 345 or 770 Prayer of Azariah and the Song of the Three Jews 82
			 * 346 or 780 Bel and the Dragon 83
			 * 790 Prayer of Manasseh 84
			 * 980 Additions to Esther 85
			 * 991 mazmur / psalm 151 86
			 * 1802 Epistle to the Laodicaeans 87
			 */
			int n = Integer.parseInt(ub.substring(0, 2));
			int[] v = {
				68, // 67A Tobit                       
				69, // 68A Judith                      
				85, // 69A Esther, Greek               
				76, // 70A Wisdom of Solomon           
				77, // 71A Ecclesiasticus (Sira        
				80, // 72A Baruch                      
				79, // 73A Epistle of Jeremiah         
				82, // 74A Prayer of Azariah           
				81, // 75A Susanna                     
				83, // 76A Bel and the Dragon          
				70, // 77A 1 Maccabees                 
				71, // 78A 2 Maccabees                 
				72, // 79A 3 Maccabees                 
				73, // 80A 4 Maccabees                 
				66, // 81A 1 Esdras                    
				67, // 82A 2 Esdras                    
				84, // 83A Prayer of Manasseh          
				86, // 84A Psalm 151                   
				78, // 85A Psalm of Solomon            
			    75, // 86A Odes                        
			};
			int idx = n - 67; // 67A -> idx=0
			if (idx >= 0 && idx < v.length) return v[idx] + 1 /* karena yang diminta kitab_1 */;
			throw new RuntimeException("ub belum dikenal: " + ub);
		} else {
			throw new RuntimeException("ub belum dikenal: " + ub);
		}
	}
}
