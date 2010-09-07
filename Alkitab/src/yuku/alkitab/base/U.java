package yuku.alkitab.base;

import java.io.InputStream;
import java.util.*;

import yuku.alkitab.base.model.*;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;

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
	

	public static InputStream openRaw(Context context, String name) {
		Resources resources = context.getResources();
		return resources.openRawResource(resources.getIdentifier(name, "raw", context.getPackageName())); //$NON-NLS-1$
	}

	public static void hurufkecilkanAscii(byte[] ba) {
		int blen = ba.length;
		for (int i = 0; i < blen; i++) {
			byte b = ba[i];
			if (b <= (byte)'Z' && b >= (byte)'A') {
				ba[i] |= 0x20; // perhurufkecilkan
			}
		}
	}
	
	public static String[] pisahJadiAyatAscii(byte[] ba) {
		char[] ayatBuf = new char[4000];
		int i = 0;

		ArrayList<String> res = new ArrayList<String>(60);
		
		//# HANYA BERLAKU KALAU SEMUA byte hanya 7-bit. Akan rusak kalo ada yang 0x80.
		int len = ba.length;
		for (int pos = 0; pos < len; pos++) {
			byte c = ba[pos];
			if (c == (byte)0x0a) {
				String satu = new String(ayatBuf, 0, i);
				res.add(satu);
				i = 0;
			} else {
				ayatBuf[i++] = (char) c;
			}
		}
		
		return res.toArray(new String[res.size()]);
	}
}
