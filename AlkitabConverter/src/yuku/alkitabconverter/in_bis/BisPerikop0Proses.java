package yuku.alkitabconverter.in_bis;

import java.io.File;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.TreeSet;

public class BisPerikop0Proses {
	public static void main(String[] args) throws Exception {
		Scanner sc = new Scanner(new File("../Alkitab/publikasi/bis_perikop_0.txt"));
		PrintWriter pw = new PrintWriter(new File("../Alkitab/publikasi/bis_perikop_1.txt"), "ascii");
		
		TreeSet<String> kiwod = new TreeSet<String>();
		int mode = 0; // 0 == abai
		
		while (sc.hasNextLine()) {
			String baris = sc.nextLine().trim();
			
			if (baris.contains(":")) {
				String kiri = baris.split(":")[0].trim();
				if (kiri.contains(" ") && kiri.split(" ").length == 2 && Character.isDigit(kiri.split(" ")[1].charAt(0))) {
					// ayat
					kiwod.add("ayat: " + kiri.split(" ")[0]);
				} else {
					// jenis
					kiwod.add("jenis: " + kiri);
				}
			} else {
				String kiri = baris;
				kiwod.add("aneh: " + kiri);
			}
			
			if (baris.startsWith("Judul: ")) {
				mode = 1; // 1= judul
				pw.println("Judul: " + baris.substring(7));
			} else if (baris.startsWith("Judul Besar: ")) {
				mode = 1; // 1= judul
				pw.println("Judul: " + baris.substring(13));
			} else if (baris.startsWith("Pasal-pasal: ")) {
				mode = 1; // 1= masih dianggap judul
				pw.println("Judul: " + baris); // kita anggap ini judul perikop saja (contoh: "Pasal-pasal: MAZMUR 1--40") 
			} else if (baris.startsWith("Judul Kecil: ")) {
				mode = 1; // 1= judul
				pw.println("Judul: " + baris.substring(13));
			} else if (baris.startsWith("Paralel: ")) {
				if (mode == 2) {
					throw new RuntimeException("PARALEL LAGI?? baris=" + baris);
				}
				mode = 2; // 2= paralel
				
				// belah
				String semua = baris.substring(9);
				String[] bag = semua.split(";");
				for (String b: bag) {
					b = b.trim();
					pw.println("Paralel: " + b);
					if (bag.length > 1 && Character.isDigit(b.charAt(0))) {
						System.out.println("baris paralel MUNGKIN ga lengkap: " + baris);
					}
				}
				if (semua.contains(",")) {
					System.out.println("Paralel berkoma: " + baris);
				}
			} else if (baris.startsWith("Silang:") || baris.startsWith("Silang untuk:")) {
				mode = 4; // 4= silang
				// abai
			} else {
				if (mode == 1) {
					pw.println("Perikop: " + baris);
				}
			}
		}
		
		for (String s: kiwod) {
			System.out.println(s);
		}
		
		pw.close();
	}
}
