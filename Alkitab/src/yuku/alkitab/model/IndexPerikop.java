package yuku.alkitab.model;

import java.io.IOException;

import yuku.bintex.BintexReader;

public class IndexPerikop {
	public int nentri;
	public String edisi_nama;
	
	private int[] xari;
	private int[] xofset;
	
	public static IndexPerikop baca(BintexReader reader, String edisi_nama) throws IOException {
		IndexPerikop ip = new IndexPerikop();
		
		int nentri = reader.readInt();
		
		ip.nentri = nentri;
		
		ip.xari = new int[nentri];
		ip.xofset = new int[nentri];
		
		for (int i = 0; i < nentri; i++) {
			ip.xari[i] = reader.readInt();
			ip.xofset[i] = reader.readInt();
		}
		
		ip.edisi_nama = edisi_nama;
		
		return ip;
	}
}
