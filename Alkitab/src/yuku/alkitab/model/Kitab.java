package yuku.alkitab.model;

import java.io.*;

import yuku.alkitab.*;

public class Kitab {
	public int[] nayat;
	public int npasal;
	public int[] pasal_offset;
	public String nama;
	public String judul;
	public String file;

	public static Kitab baca(SimpleScanner sc) throws IOException {
		Kitab k = new Kitab();

		String awal = sc.next();

		if (awal.equals("Kitab")) {
			while (true) {
				String key = sc.next();
				if (key.equals("nama")) {
					k.nama = sc.next();
				} else if (key.equals("judul")) {
					k.judul = sc.next();
				} else if (key.equals("file")) {
					k.file = sc.next();
				} else if (key.equals("npasal")) {
					k.npasal = sc.nextInt();
				} else if (key.equals("nayat")) {
					k.nayat = new int[k.npasal];
					for (int i = 0; i < k.npasal; i++) {
						k.nayat[i] = sc.nextInt();
					}
				} else if (key.equals("pasal_offset")) {
					k.pasal_offset = new int[k.npasal];
					for (int i = 0; i < k.npasal; i++) {
						k.pasal_offset[i] = sc.nextInt();
					}
				} else if (key.equals("uda")) {
					break;
				}
			}
			
			return k;
		} else {
			return null;
		}
	}
	
	@Override
	public String toString() {
		return String.format("%s (%d pasal)", judul, npasal);
	}
}
