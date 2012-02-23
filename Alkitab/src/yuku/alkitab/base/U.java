package yuku.alkitab.base;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;

import yuku.alkitab.base.compat.Api11;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Blok;
import yuku.alkitab.base.model.Label;

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
	
	public static String enkodStabilo(int warnaRgb) {
		StringBuilder sb = new StringBuilder(10);
		sb.append('c');
		String h = Integer.toHexString(warnaRgb);
		for (int x = h.length(); x < 6; x++) {
			sb.append('0');
		}
		sb.append(h);
		return sb.toString();
	}
	
	/**
	 * @return warnaRgb (belum ada alphanya) atau -1 kalau ga bisa dekod
	 */
	public static int dekodStabilo(String tulisan) {
		if (tulisan == null) return -1;
		if (tulisan.length() >= 7 && tulisan.charAt(0) == 'c') {
			return Integer.parseInt(tulisan.substring(1, 7), 16);
		} else {
			return -1;
		}
	}
	
	public static String enkodWarnaLatarLabel(int warnaRgb_background) {
		StringBuilder sb = new StringBuilder(10);
		sb.append('b'); // 'b': background color
		String h = Integer.toHexString(warnaRgb_background);
		for (int x = h.length(); x < 6; x++) {
			sb.append('0');
		}
		sb.append(h);
		return sb.toString();
	}
	
	/**
	 * @return warnaRgb (belum ada alphanya) atau -1 kalau ga bisa dekod
	 */
	public static int dekodWarnaLatarLabel(String warnaLatar) {
		if (warnaLatar == null || warnaLatar.length() == 0) return -1;
		if (warnaLatar.length() >= 7 && warnaLatar.charAt(0) == 'b') { // 'b': background color
			return Integer.parseInt(warnaLatar.substring(1, 7), 16);
		} else {
			return -1;
		}
	}
	
	public static int getWarnaDepanBerdasarWarnaLatar(int warnaRgb) {
		float[] hsl = {0.f, 0.f, 0.f};
		rgbToHsl(warnaRgb, hsl);
		//Log.d("getWarnaDepanBerdasarWarnaLatar", String.format("#%06x -> %3d %.2f %.2f", warnaRgb & 0xffffff, (int)hsl[0], hsl[1], hsl[2]));
		
		if (hsl[2] > 0.5f) hsl[2] -= 0.44f;
		else hsl[2] += 0.44f;
		
		int res = hslToRgb(hsl);
		//Log.d("getWarnaDepanBerdasarWarnaLatar", String.format("%3d %.2f %.2f -> #%06x", (int)hsl[0], hsl[1], hsl[2], res));
		
		return res;
	}
	
	public static void rgbToHsl(int rgb, float[] hsl) {
		float r = ((0x00ff0000 & rgb) >> 16) / 255.f;
		float g = ((0x0000ff00 & rgb) >> 8) / 255.f;
		float b = ((0x000000ff & rgb)) / 255.f;
		float max = Math.max(Math.max(r, g), b);
		float min = Math.min(Math.min(r, g), b);
		float c = max - min;
		
		float h_ = 0.f;
		if (c == 0) {
			h_ = 0;
		} else if (max == r) {
			h_ = (float)(g-b) / c;
			if (h_ < 0) h_ += 6.f;
		} else if (max == g) {
			h_ = (float)(b-r) / c + 2.f;
		} else if (max == b) {
			h_ = (float)(r-g) / c + 4.f;
		}
		float h = 60.f * h_;
		
		float l = (max + min) * 0.5f;
		
		float s;
		if (c == 0) {
			s = 0.f;
		} else {
			s = c / (1 - Math.abs(2.f * l - 1.f));
		}
		
		hsl[0] = h;
		hsl[1] = s;
		hsl[2] = l;
	}
	
	public static int hslToRgb(float[] hsl) {
		float h = hsl[0];
		float s = hsl[1];
		float l = hsl[2];
		
		float c = (1 - Math.abs(2.f * l - 1.f)) * s;
		float h_ = h / 60.f;
		float h_mod2 = h_;
		if (h_mod2 >= 4.f) h_mod2 -= 4.f;
		else if (h_mod2 >= 2.f) h_mod2 -= 2.f;
		
		float x = c * (1 - Math.abs(h_mod2 - 1));
		float r_, g_, b_;
		if (h_ < 1)      { r_ = c; g_ = x; b_ = 0; }
		else if (h_ < 2) { r_ = x; g_ = c; b_ = 0; }
		else if (h_ < 3) { r_ = 0; g_ = c; b_ = x; }
		else if (h_ < 4) { r_ = 0; g_ = x; b_ = c; }
		else if (h_ < 5) { r_ = x; g_ = 0; b_ = c; }
		else             { r_ = c; g_ = 0; b_ = x; }
		
		float m = l - (0.5f * c); 
		int r = (int)((r_ + m) * (255.f) + 0.5f);
		int g = (int)((g_ + m) * (255.f) + 0.5f);
		int b = (int)((b_ + m) * (255.f) + 0.5f);
		return r << 16 | g << 8 | b;
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
	
	public static ThreadLocal<SimpleDateFormat> getThreadLocalSimpleDateFormat(final String pattern) {
		return new ThreadLocal<SimpleDateFormat>() {
			@Override protected SimpleDateFormat initialValue() {
				return new SimpleDateFormat(pattern);
			}
		};
	}

	public static void pasangWarnaLabel(Label label, TextView view) {
		int warnaLatarRgb = U.dekodWarnaLatarLabel(label.warnaLatar);
		if (warnaLatarRgb == -1) {
			warnaLatarRgb = 0x777777; // warna standar
		}
		
		GradientDrawable grad = null;

		Drawable bg = view.getBackground();
		if (bg instanceof GradientDrawable) {
			grad = (GradientDrawable) bg;
		} else if (bg instanceof StateListDrawable) {
			StateListDrawable states = (StateListDrawable) bg;
			Drawable current = states.getCurrent();
			if (current instanceof GradientDrawable) {
				grad = (GradientDrawable) current;
			}
		}
		if (grad != null) {
			grad.setColor(0xff000000 | warnaLatarRgb);
			view.setTextColor(0xff000000 | U.getWarnaDepanBerdasarWarnaLatar(warnaLatarRgb));
		}
	}
}
