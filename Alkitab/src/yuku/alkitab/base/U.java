package yuku.alkitab.base;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

import yuku.alkitab.base.compat.Api11;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Blok;

public class U {

	/**
	 * Kalo ayat ga berawalan @: ga ngapa2in
	 * Sebaliknya, buang semua @ dan 1 karakter setelahnya.
	 * 
	 * @param ayat
	 */
	public static String buangKodeKusus(String ayat) {
		if (ayat.length() == 0) return ayat;
		if (ayat.charAt(0) != '@') return ayat;

		StringBuilder sb = new StringBuilder(ayat.length());
		int pos = 2;

		while (true) {
			int p = ayat.indexOf('@', pos);
			if (p == -1) {
				break;
			}

			sb.append(ayat, pos, p);
			pos = p + 2;
		}

		sb.append(ayat, pos, ayat.length());
		return sb.toString();
	}

	// di sini supaya bisa dites dari luar
	public static int[] bikinPenunjukKotak(int nayat, int[] perikop_xari, Blok[] perikop_xblok, int nblok) {
		int[] res = new int[nayat + nblok];

		int posBlok = 0;
		int posAyat = 0;
		int posPK = 0;

		while (true) {
			// cek apakah judul perikop, DAN perikop masih ada
			if (posBlok < nblok) {
				// masih memungkinkan
				if (Ari.toAyat(perikop_xari[posBlok]) - 1 == posAyat) {
					// ADA PERIKOP.
					res[posPK++] = -posBlok - 1;
					posBlok++;
					continue;
				}
			}

			// cek apakah ga ada ayat lagi
			if (posAyat >= nayat) {
				break;
			}

			// uda ga ada perikop, ATAU belom saatnya perikop. Maka masukin ayat.
			res[posPK++] = posAyat;
			posAyat++;
			continue;
		}

		if (res.length != posPK) {
			// ada yang ngaco! di algo di atas
			throw new RuntimeException("Algo selip2an perikop salah! posPK=" + posPK + " posAyat=" + posAyat + " posBlok=" + posBlok + " nayat=" + nayat + " nblok=" + nblok //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
					+ " xari:" + Arrays.toString(perikop_xari) + " xblok:" + Arrays.toString(perikop_xblok));  //$NON-NLS-1$//$NON-NLS-2$
		}

		return res;
	}
	
	public static Typeface typeface(String nama) {
		Typeface typeface;
		if (nama == null) typeface = Typeface.DEFAULT;
		else if (nama.equals("SERIF")) typeface = Typeface.SERIF; //$NON-NLS-1$
		else if (nama.equals("SANS_SERIF")) typeface = Typeface.SANS_SERIF; //$NON-NLS-1$
		else if (nama.equals("MONOSPACE")) typeface = Typeface.MONOSPACE; //$NON-NLS-1$
		else typeface = Typeface.DEFAULT;
		return typeface;
	}
	

	static StringBuilder enkodStabilo_buf;
	public static String enkodStabilo(int warnaRgb) {
		if (enkodStabilo_buf == null) {
			enkodStabilo_buf = new StringBuilder(10);
		}
		enkodStabilo_buf.setLength(0);
		enkodStabilo_buf.append('c');
		String h = Integer.toHexString(warnaRgb);
		for (int x = h.length(); x < 6; x++) {
			enkodStabilo_buf.append('0');
		}
		enkodStabilo_buf.append(h);
		return enkodStabilo_buf.toString();
	}
	
	/**
	 * @return warnaRgb
	 */
	public static int dekodStabilo(String tulisan) {
		if (tulisan.length() >= 7 && tulisan.charAt(0) == 'c') {
			return Integer.parseInt(tulisan.substring(1, 7), 16);
		} else {
			return -1;
		}
	}
	
	public static String tampilException(Throwable e) {
		StringWriter sw = new StringWriter(400);
		sw.append('(').append(e.getClass().getName()).append("): ").append(e.getMessage()).append('\n'); //$NON-NLS-1$
		e.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}
	
	public static String preprocessHtml(String s) {
		return s.replace('[', '<').replace(']', '>');
	}
	
	
	public static String dumpChars(String s) {
		StringBuilder sb = new StringBuilder(s.length() * 8);
		
		for (int i = 0, len = s.length(); i < len; i++) {
			char c = s.charAt(i);
			sb.append(Integer.toHexString(c));
			if (c >= 0x20) {
				sb.append('\'');
				sb.append(c);
				sb.append('\'');
			} else {
				sb.append('|');
			}
		}
		
		return sb.toString();
	}
	
	public static boolean tabletkah() {
		return Build.VERSION.SDK_INT /* ini diambil waktu runtime */ >= 11 /* HONEYCOMB */;
	}
	
	public static void nyalakanTitleBarHanyaKalauTablet(Activity activity) {
		if (tabletkah()) {
			activity.setTheme(Api11.getTheme_Holo());
		}
	}

	@SuppressWarnings("deprecation") public static void salin(CharSequence salinan) {
		android.text.ClipboardManager clipboardManager = (android.text.ClipboardManager) App.context.getSystemService(Context.CLIPBOARD_SERVICE);
		clipboardManager.setText(salinan); 
	}

	/**
	 * Convenience method of findViewById
	 */
	@SuppressWarnings("unchecked") public static <T extends View> T getView(View parent, int id) {
		return (T) parent.findViewById(id);
	}

	/**
	 * Convenience method of findViewById
	 */
	@SuppressWarnings("unchecked") public static <T extends View> T getView(Activity activity, int id) {
		return (T) activity.findViewById(id);
	}
	
	private static int[] colorSet;
	public static int getWarnaBerdasarkanKitabPos(int pos) {
		if (colorSet == null) {
			colorSet = new int[3];
			if (U.tabletkah()) {
				colorSet[0] = 0xffffcccf;
				colorSet[1] = 0xffccccff;
				colorSet[2] = 0xffffffff;
			} else {
				colorSet[0] = 0xff990022; // pl
				colorSet[1] = 0xff000099; // pb 
				colorSet[2] = 0xff000000; // dll
			}
		}

		if (pos >= 0 && pos < 39) {
			return colorSet[0];
		} else if (pos >= 39 && pos < 66) {
			return colorSet[1];
		} else {
			return colorSet[2];
		}
	}

	public static int alphaMixStabilo(int warnaRgb) {
		return 0xa0000000 | warnaRgb;
	}

	public static int getWarnaHiliteKontrasDengan(int warnaLatar) {
		float keterangan = 0.30f * Color.red(warnaLatar) + 0.59f * Color.green(warnaLatar) + 0.11f * Color.blue(warnaLatar);
		if (keterangan < 0.5f) {
			return 0xff66ff66;
		} else {
			return 0xff990099;
		}
	}

	public static boolean equals(Object a, Object b) {
		if (a == b) return true;
		if (a == null) return false;
		return a.equals(b);
	}
}
