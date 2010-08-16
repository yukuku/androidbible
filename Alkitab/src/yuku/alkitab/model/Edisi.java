package yuku.alkitab.model;

import java.io.IOException;

import yuku.alkitab.AddonManager;
import yuku.bintex.BintexReader;

public class Edisi {
	public String nama;
	public 	String judul;
	public int nkitab;
	public int perikopAda; // 0=gaada; 1=versi 1 (pake BintexReader dan utf16) 
	public Pembaca pembaca;
	public String url;// bisa null
	
	public Kitab[] volatile_xkitab;
	public IndexPerikop volatile_indexPerikop;
	
	public static Edisi baca(BintexReader in) throws IOException {
		Edisi e = new Edisi();

		String awal = in.readShortString();

		if (awal.equals("Edisi")) {
			while (true) {
				String key = in.readShortString();
				if (key.equals("nama")) {
					e.nama = in.readShortString();
				} else if (key.equals("judul")) {
					e.judul = in.readShortString();
				} else if (key.equals("nkitab")) {
					e.nkitab = in.readInt();
				} else if (key.equals("perikopAda")) {
					e.perikopAda = in.readInt();
				} else if (key.equals("pembaca")) {
					String v = in.readShortString();
					if ("internal".equals(v)) {
						e.pembaca = new InternalPembaca();
					} else if ("yes".equals(v)) {
						e.pembaca = new YesPembaca(AddonManager.getEdisiPath(e.nama));
					}
				} else if (key.equals("url")) {
					e.url = in.readShortString();
				} else if (key.equals("end")) {
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
