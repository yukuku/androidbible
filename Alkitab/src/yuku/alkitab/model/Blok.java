package yuku.alkitab.model;

import java.io.IOException;
import java.util.Arrays;

import yuku.bintex.BintexReader;

public class Blok {

//	Blok {
//		Uint8 versi = 1
//		ShortStr judul
//		Uint8 nparalel
//		ShortStr[nparalel] xparalel
//	}

	public String judul;
	public String[] xparalel;
	
	public static Blok baca(BintexReader in) throws IOException {
		Blok b = new Blok();
		
		int versi = in.readUint8();
		
		if (versi != 1) {
			throw new RuntimeException("Versi harus 1 dong!");
		}
		
		b.judul = in.readShortString();
		
		int nparalel = in.readUint8();
		b.xparalel = new String[nparalel];
		
		for (int i = 0; i < nparalel; i++) {
			b.xparalel[i] = in.readShortString();
		}
		
		return b;
	}
	
	@Override
	public String toString() {
		return "Blok{judul=" + judul + " xparalel=" + Arrays.toString(xparalel) + "}";
	}
}
