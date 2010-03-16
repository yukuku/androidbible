package yuku.alkitab.model;

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
	public static int getKitab(int ari) {
		return (ari & 0x00ff0000) >> 16;
	}
	
	/** 1..255 */
	public static int getPasal(int ari) {
		return (ari & 0x0000ff00) >> 8;
	}
	
	/** 1..255 */
	public static int getAyat(int ari) {
		return (ari & 0x000000ff);
	}
}
