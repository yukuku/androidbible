package yuku.alkitab.base.model;

import java.io.IOException;

import yuku.bintex.BintexReader;

public class Kitab {
	public int[] nayat;
	public int npasal;
	public int[] pasal_offset;
	public String nama;
	public String judul;
	public String file;
	public int pos;
	/** Hanya dipake di YesPembaca */
	public int offset = -1;

	public static Kitab baca(BintexReader in, int pos) throws IOException {
		Kitab k = new Kitab();
		k.pos = pos;
		
		String awal = in.readShortString();

		if (awal.equals("Kitab")) {
			while (true) {
				String key = in.readShortString();
				if (key.equals("nama")) {
					k.nama = in.readShortString();
				} else if (key.equals("judul")) {
					k.judul = in.readShortString();
					
					k.judul = bersihinJudul(k.judul);
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
	
	private static String bersihinJudul(String judul) {
		return judul.replace('_', ' ');
	}

	@Override
	public String toString() {
		return String.format("%s (%d pasal)", judul, npasal);
	}
}
