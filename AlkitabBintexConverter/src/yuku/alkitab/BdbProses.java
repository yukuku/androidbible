package yuku.alkitab;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

public class BdbProses {
	public static void main(String[] args) throws Exception {
		new BdbProses().u(args);
	}

	private void u(String[] args) throws Exception {
		LinkedHashMap<Integer, Integer> nn = new LinkedHashMap<Integer, Integer>();
		
		Scanner sc = new Scanner(new File("../Alkitab/publikasi/kjv_teks_bdb.txt"), "ascii");
		
		int lastNo = 0;
		int lastKitab_1 = 1;
		int lastPasal_1 = 1;
		int lastAyat_1 = 0;
		
		while (sc.hasNextLine()) {
			String baris = sc.nextLine();
			
			String[] xkolom = baris.split("\t");
			int no = Integer.parseInt(xkolom[0]);
			int kitab_1 = Integer.parseInt(xkolom[1]);
			int pasal_1 = Integer.parseInt(xkolom[2]);
			int ayat_1 = Integer.parseInt(xkolom[3]);
			String isi = xkolom[4];
			if (xkolom.length != 5) {
				throw new RuntimeException("kolom ngaco");
			}
			
			if (ayat_1 != lastAyat_1 + 1) {
				if (pasal_1 != lastPasal_1 + 1) {
					if (kitab_1 != lastKitab_1 + 1) {
						throw new RuntimeException("urutan ngaco");
					}
				}
			}
			
			if (no != lastNo + 1) {
				throw new RuntimeException("no ngaco");
			}
			
			nn.put(kitab_1, (nn.get(kitab_1) == null? 0: nn.get(kitab_1)) + 1);
			
			lastNo = no;
			lastKitab_1 = kitab_1;
			lastPasal_1 = pasal_1;
			lastAyat_1 = ayat_1;
		}
		
		for (Entry<Integer, Integer> e: nn.entrySet()) {
			System.out.println(e.getKey() + ": " + e.getValue());
		}
		
		System.out.println("selesai");
	}

}
