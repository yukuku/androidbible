package yuku.alkitab.model;

import java.util.Scanner;

public class Edisi {
	public String nama;
	public 	String judul;
	public int nkitab;
	public boolean perikopAda;
	
	public static Edisi baca(Scanner sc) {
		Edisi e = new Edisi();

		String awal = sc.next();

		if (awal.equals("Edisi")) {
			while (true) {
				String key = sc.next();
				if (key.equals("nama")) {
					e.nama = sc.next();
				} else if (key.equals("judul")) {
					e.judul = sc.next();
				} else if (key.equals("nkitab")) {
					e.nkitab = sc.nextInt();
				} else if (key.equals("perikopAda")) {
					e.perikopAda = sc.nextInt() != 0;
				} else if (key.equals("uda")) {
					break;
				}
			}
			
			return e;
		} else {
			return null;
		}
	}
	
	@Override
	public String toString() {
		return String.format("%s (%s)", judul, nama);
	}
}
