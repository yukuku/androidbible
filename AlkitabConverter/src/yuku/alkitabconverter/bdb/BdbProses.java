package yuku.alkitabconverter.bdb;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Scanner;

import yuku.alkitab.yes.YesFile.PerikopData;

public class BdbProses {
	public static class Rec implements Comparable<Rec> {
		public int kitab_1;
		public int pasal_1;
		public int ayat_1;
		public String isi;
		
		@Override public int compareTo(Rec o) {
			if (this.kitab_1 != o.kitab_1) return this.kitab_1 - o.kitab_1;
			if (this.pasal_1 != o.pasal_1) return this.pasal_1 - o.pasal_1;
			if (this.ayat_1 != o.ayat_1) return this.ayat_1 - o.ayat_1;
			return 0;
		}
	}
	
	public interface PerikopTester {
		PerikopData.Entri getPerikopEntri(int kitab_1, int pasal_1, int ayat_1, String isi);
	}
	
	PerikopData perikopData;
	private PerikopTester perikopTester;
	boolean combineSameVerse = false;
	
	public ArrayList<Rec> parse(String nf, String charsetName) throws Exception {
		LinkedHashMap<Integer, Integer> nversePerChapter = new LinkedHashMap<Integer, Integer>();
		ArrayList<Rec> res = new ArrayList<Rec>();
		
		Scanner sc = new Scanner(new File(nf), charsetName);
		
		int lastNo = 0;
		int lastKitab_1 = 1;
		int lastPasal_1 = 1;
		int lastAyat_1 = 0;
		
		while (sc.hasNextLine()) {
			String baris = sc.nextLine();
			
			String[] xkolom = baris.split("\t");
			int no = xkolom[0].startsWith("tambah")? 0: Integer.parseInt(xkolom[0]);
			int kitab_1 = Integer.parseInt(xkolom[1]);
			int pasal_1 = Integer.parseInt(xkolom[2]);
			int ayat_1 = Integer.parseInt(xkolom[3]);
			String isi = xkolom[4];
			if (xkolom.length != 5) {
				throw new RuntimeException("kolom ngaco");
			}
			
			if (perikopTester != null) {
				PerikopData.Entri pe = perikopTester.getPerikopEntri(kitab_1, pasal_1, ayat_1, isi);
				if (pe != null) {
					if (perikopData == null) {
						perikopData = new PerikopData();
						perikopData.xentri = new ArrayList<PerikopData.Entri>();
					}
					perikopData.xentri.add(pe);
					continue; // let's continue with next line
				}
			}
			
			if (combineSameVerse && ayat_1 == lastAyat_1 && pasal_1 == lastPasal_1 && kitab_1 == lastKitab_1) {
				Rec lastRec = res.get(res.size() - 1);
				lastRec.isi += " " + isi;
			} else {
				if (ayat_1 != lastAyat_1 + 1) {
					if (pasal_1 != lastPasal_1 + 1) {
						if (kitab_1 != lastKitab_1 + 1) {
							throw new RuntimeException("urutan ngaco: " + baris);
						}
					}
				}
				
				Rec rec = new Rec();
				rec.kitab_1 = kitab_1;
				rec.pasal_1 = pasal_1;
				rec.ayat_1 = ayat_1;
				rec.isi = isi;
				
				res.add(rec);
				nversePerChapter.put(kitab_1, (nversePerChapter.get(kitab_1) == null? 0: nversePerChapter.get(kitab_1)) + 1);
			}
			
			if (no != lastNo + 1) {
				System.out.println("no ngaco: " + no + " after " + lastNo + "; ini gapapa kalo emang sengaja");
			}
			
			lastNo = no;
			lastKitab_1 = kitab_1;
			lastPasal_1 = pasal_1;
			lastAyat_1 = ayat_1;
		}
		
		for (Entry<Integer, Integer> e: nversePerChapter.entrySet()) {
			System.out.println("kitab_1 " + e.getKey() + ": " + e.getValue() + " verses");
		}
		
		System.out.println("selesai");
		
		return res;
	}

	public void setCombineSameVerse(boolean combineSameVerse) {
		this.combineSameVerse = combineSameVerse;
	}

	public void setPerikopTester(PerikopTester perikopTester) {
		this.perikopTester = perikopTester;
	}

	public PerikopData getPerikopData() {
		return perikopData;
	}

}
