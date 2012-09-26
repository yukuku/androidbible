package yuku.alkitab.base.model;

import java.io.IOException;
import java.util.Arrays;

import yuku.bintex.BintexReader;

public class PericopeBlock {

//	Blok {
//		Uint8 versi = 1
//		ShortStr judul
//		Uint8 nparalel
//		ShortStr[nparalel] xparalel
//	}

//	Blok {
//		Uint8 versi = 2
//		LongStr judul
//		Uint8 nparalel
//		ShortStr[nparalel] xparalel
//	}

	public String title;
	public String[] xparalel;
	
	public static PericopeBlock baca(BintexReader in) throws IOException {
		PericopeBlock b = new PericopeBlock();
		
		int versi = in.readUint8();
		
		if (versi > 2) {
			throw new RuntimeException("Versi blok yang didukung cuma sampe 2. Keterima versi " + versi); //$NON-NLS-1$
		}
		
		if (versi == 1) {
			b.title = in.readShortString();
		} else if (versi == 2) {
			b.title = in.readLongString();
		}
		
		int nparalel = in.readUint8();
		b.xparalel = new String[nparalel];
		
		for (int i = 0; i < nparalel; i++) {
			b.xparalel[i] = in.readShortString();
		}
		
		return b;
	}
	
	@Override
	public String toString() {
		return "Blok{judul=" + title + " xparalel=" + Arrays.toString(xparalel) + "}"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
