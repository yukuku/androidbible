package yuku.alkitab.model;

import java.io.*;

import yuku.bintex.*;

public class Kitab {
	public int[] nayat;
	public int npasal;
	public int[] pasal_offset;
	public String nama;
	public String judul;
	public String file;

	public static Kitab baca(BintexReader in) throws IOException {
		Kitab k = new Kitab();

		String awal = in.readShortString();

		if (awal.equals("Kitab")) {
			while (true) {
				String key = in.readShortString();
				if (key.equals("nama")) {
					k.nama = in.readShortString();
				} else if (key.equals("judul")) {
					k.judul = in.readShortString();
				} else if (key.equals("file")) {
					k.file = in.readShortString();
				} else if (key.equals("npasal")) {
					k.npasal = in.readInt();
				} else if (key.equals("nayat")) {
					k.nayat = new int[k.npasal];
					for (int i = 0; i < k.npasal; i++) {
						k.nayat[i] = in.readUint8();
					}
				} else if (key.equals("pasal_offset")) {
					k.pasal_offset = new int[k.npasal];
					for (int i = 0; i < k.npasal; i++) {
						k.pasal_offset[i] = in.readInt();
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
