package yuku.alkitab.model;

import yuku.alkitab.S;

/**
 * Alkitab resource identifier
 * @author yuku
 *
 */
public class Ari {
	public static int encode(int kitab, int pasal, int ayat) {
		return (kitab & 0xff) << 16 | (pasal & 0xff) << 8 | (ayat & 0xff);
	}
	
	/** 0..255 */
	public static int toKitab(int ari) {
		return (ari & 0x00ff0000) >> 16;
	}
	
	/** 1..255 */
	public static int toPasal(int ari) {
		return (ari & 0x0000ff00) >> 8;
	}
	
	/** 1..255 */
	public static int toAyat(int ari) {
		return (ari & 0x000000ff);
	}
	
	public static String toAlamat(int ari) {
		int kitab = toKitab(ari);
		int pasal = toPasal(ari);
		int ayat = toAyat(ari);
		
		StringBuilder sb = new StringBuilder(30);
		
		if (kitab >= S.xkitab.length) {
			sb.append('[');
			sb.append(kitab);
			sb.append("] ");
		} else {
			sb.append(S.xkitab[kitab].judul).append(" ");
		}
		
		sb.append(pasal).append(':').append(ayat);
		
		return sb.toString();
	}
}
