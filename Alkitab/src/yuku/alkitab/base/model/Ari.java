package yuku.alkitab.base.model;


/**
 * Alkitab resource identifier
 * @author yuku
 *
 */
public class Ari {
	public static int encode(int kitab, int pasal, int ayat) {
		return (kitab & 0xff) << 16 | (pasal & 0xff) << 8 | (ayat & 0xff);
	}
	
	public static int encode(int kitab, int pasal_ayat) {
		return (kitab & 0xff) << 16 | (pasal_ayat & 0xffff);
	}
	
	/** 0..255 
	 * kitab berbasis-0 (kejadian == 0)
	 * */
	public static int toKitab(int ari) {
		return (ari & 0x00ff0000) >> 16;
	}
	
	/** 1..255 
	 * pasal berbasis-1 yang dikembalikan
	 * */
	public static int toPasal(int ari) {
		return (ari & 0x0000ff00) >> 8;
	}
	
	/** 1..255 
	 * ayat berbasis-1 yang dikembalikan
	 * */
	public static int toAyat(int ari) {
		return (ari & 0x000000ff);
	}
	
	/**
	 * Gabungan kitab dan pasal. Ayat diset ke 0.
	 */
	public static int toKitabPasal(int ari) {
		return (ari & 0x00ffff00);
	}
	
	public static String toAlamat(Kitab[] xkitab, int ari) {
		int kitab = toKitab(ari);
		int pasal = toPasal(ari);
		int ayat = toAyat(ari);
		
		StringBuilder sb = new StringBuilder(30);
		
		if (kitab >= xkitab.length) {
			sb.append('[');
			sb.append(kitab);
			sb.append("] "); //$NON-NLS-1$
		} else {
			sb.append(xkitab[kitab].judul).append(' ');
		}
		
		sb.append(pasal).append(':').append(ayat);
		
		return sb.toString();
	}
}
