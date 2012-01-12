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
					if (kitab_1 != lastKitab_1 + 1) {
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
		} else {
			throw new RuntimeException("ub belum dikenal: " + ub);
		}
	}
}
