package yuku.alkitabconverter.col;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import yuku.alkitabconverter.util.Rec;

public class ColProses {
	static String[] xnamakitab = {
		"Gen", "Exo", "Lev", "Num", "Deu", "Jos", "Jug", "Rut", "1Sa", "2Sa",
		"1Ki", "2Ki", "1Ch", "2Ch", "Ezr", "Neh", "Est", "Job", "Psm", "Pro",
		"Ecc", "Son", "Isa", "Jer", "Lam", "Eze", "Dan", "Hos", "Joe", "Amo", 
		"Oba", "Jon", "Mic", "Nah", "Hab", "Zep", "Hag", "Zec", "Mal", // 39
		"Mat", "Mak", "Luk", "Jhn", "Act", "Rom", "1Co", "2Co", "Gal", "Eph",
		"Phl", "Col", "1Ts", "2Ts", "1Ti", "2Ti", "Tit", "Phm", "Heb", "Jas", 
		"1Pe", "2Pe", "1Jn", "2Jn", "3Jn", "Jud", "Rev", // 27 
	};
	
	public ArrayList<Rec> parse(String nf) throws Exception {
		LinkedHashMap<Integer, Integer> nn = new LinkedHashMap<Integer, Integer>();
		ArrayList<Rec> res = new ArrayList<Rec>();
		
		Scanner sc = new Scanner(new File(nf), "utf-8");
		
		int lastKitab_1 = 1;
		int lastPasal_1 = 1;
		int lastAyat_1 = 0;
		
		Pattern p = Pattern.compile("([A-Za-z1-3]{1,3}) (\\d+):(\\d+)\\s?(.*)");
		
		while (sc.hasNextLine()) {
			String baris = sc.nextLine();
			
			Matcher m = p.matcher(baris);
			if (!m.matches()) {
				throw new RuntimeException("baris tidak sesuai pattern: " + baris);
			}
			
			int kitab_1 = toKitab_1(m.group(1));
			int pasal_1 = Integer.parseInt(m.group(2));
			int ayat_1 = Integer.parseInt(m.group(3));
			String isi = m.group(4);
			
			if (ayat_1 != lastAyat_1 + 1) {
				if (pasal_1 != lastPasal_1 + 1) {
					if (kitab_1 != lastKitab_1 + 1) {
						throw new RuntimeException("urutan ngaco. k p a: " + kitab_1 + " " + pasal_1 + " " + ayat_1 + " last k p a: " + lastKitab_1 + " " + lastPasal_1 + " " + lastAyat_1);
					}
				}
			}
			
			nn.put(kitab_1, (nn.get(kitab_1) == null? 0: nn.get(kitab_1)) + 1);
			
			Rec rec = new Rec();
			rec.book_1 = kitab_1;
			rec.chapter_1 = pasal_1;
			rec.verse_1 = ayat_1;
			rec.text = isi;
			
			res.add(rec);
			
			lastKitab_1 = kitab_1;
			lastPasal_1 = pasal_1;
			lastAyat_1 = ayat_1;
		}
		
		for (Entry<Integer, Integer> e: nn.entrySet()) {
			System.out.println(e.getKey() + ": " + e.getValue());
		}
		
		System.out.println("selesai");
		
		return res;
	}

	private int toKitab_1(String namakitab) {
		for (int i = 0; i < xnamakitab.length; i++) {
			if (xnamakitab[i].equals(namakitab)) {
				return i + 1;
			}
		}
		throw new RuntimeException("namakitab ga dikenal: " + namakitab);
	}

}
