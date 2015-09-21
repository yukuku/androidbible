package yuku.alkitabconverter.bdb;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Scanner;

import yuku.alkitabconverter.yes1.Yes1File.PericopeData;
import yuku.alkitabconverter.util.Rec;

public class BdbProses {
	public interface PericopeTester {
		PericopeData.Entry getPericopeEntry(int kitab_1, int pasal_1, int ayat_1, String isi);
	}
	
	PericopeData pericopeData;
	private PericopeTester pericopeTester;
	boolean combineSameVerse = false;
	
	public ArrayList<Rec> parse(String nf, String charsetName) throws Exception {
		LinkedHashMap<Integer, Integer> nversePerChapter = new LinkedHashMap<>();
		ArrayList<Rec> res = new ArrayList<>();
		
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
			
			if (pericopeTester != null) {
				PericopeData.Entry pe = pericopeTester.getPericopeEntry(kitab_1, pasal_1, ayat_1, isi);
				if (pe != null) {
					if (pericopeData == null) {
						pericopeData = new PericopeData();
						pericopeData.entries = new ArrayList<>();
					}
					pericopeData.entries.add(pe);
					continue; // let's continue with next line
				}
			}
			
			if (combineSameVerse && ayat_1 == lastAyat_1 && pasal_1 == lastPasal_1 && kitab_1 == lastKitab_1) {
				Rec lastRec = res.get(res.size() - 1);
				lastRec.text += " " + isi;
			} else {
				if (ayat_1 != lastAyat_1 + 1) {
					if (pasal_1 != lastPasal_1 + 1) {
						if (kitab_1 != lastKitab_1 + 1) {
							throw new RuntimeException("urutan ngaco: " + baris);
						}
					}
				}
				
				Rec rec = new Rec();
				rec.book_1 = kitab_1;
				rec.chapter_1 = pasal_1;
				rec.verse_1 = ayat_1;
				rec.text = isi;
				
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

	public void setPericopeTester(PericopeTester pericopeTester) {
		this.pericopeTester = pericopeTester;
	}

	public PericopeData getPericopeData() {
		return pericopeData;
	}

}
