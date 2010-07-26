package yuku.alkitab;

import java.util.*;

import yuku.alkitab.model.*;
import android.graphics.*;

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
			throw new RuntimeException("Algo selip2an perikop salah! posPK=" + posPK + " posAyat=" + posAyat + " posBlok=" + posBlok + " nayat=" + nayat + " nblok_=" + nblok
					+ " xari:" + Arrays.toString(perikop_xari) + " xblok:" + Arrays.toString(perikop_xblok));
		}

		return res;
	}
	
	public static Typeface typeface(String nama) {
		Typeface typeface;
		if (nama == null) typeface = Typeface.DEFAULT;
		else if (nama.equals("SERIF")) typeface = Typeface.SERIF;
		else if (nama.equals("SANS_SERIF")) typeface = Typeface.SANS_SERIF;
		else if (nama.equals("MONOSPACE")) typeface = Typeface.MONOSPACE;
		else typeface = Typeface.DEFAULT;
		return typeface;
	}
	
}
