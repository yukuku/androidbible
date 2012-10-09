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
	
//	Blok {
//		uint8 version = 3
//		autostring title
//		uint8 parallel_count
//		autostring[parallel_count] parallels
//	}

	public String title;
	public String[] parallels;
	
	public static PericopeBlock read(BintexReader in) throws IOException {
		PericopeBlock b = new PericopeBlock();
		
		int version = in.readUint8();
		
		if (version > 3) {
			throw new RuntimeException("Parallel block supported is only up to 3. Got version " + version); //$NON-NLS-1$
		}
		
		if (version == 3) {
			b.title = in.readAutoString();
		} else if (version == 2) {
			b.title = in.readLongString();
		} else if (version == 1) {
			b.title = in.readShortString();
		}
		
		int nparalel = in.readUint8();
		b.parallels = new String[nparalel];
		
		for (int i = 0; i < nparalel; i++) {
			if (version == 3) {
				b.parallels[i] = in.readAutoString();
			} else {
				b.parallels[i] = in.readShortString();
			}
		}
		
		return b;
	}

	@Override public String toString() {
		return "Blok{title=" + title + " parallels=" + Arrays.toString(parallels) + "}"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
