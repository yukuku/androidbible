package yuku.alkitab.base;

import android.content.res.*;
import android.graphics.*;
import android.os.*;
import android.util.*;

import java.io.*;
import java.util.*;

import yuku.alkitab.*;
import yuku.alkitab.base.config.*;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.renungan.*;
import yuku.alkitab.base.storage.*;

public class S {
	static final String TAG = S.class.getSimpleName();

	/**
	 * penerapan dari pengaturan
	 */
	public static class penerapan {
		/** dalam dp */
		public static float ukuranHuruf2dp;
		public static Typeface jenisHuruf;
		public static int tebalHuruf;
		public static int warnaHuruf; 
		public static int warnaHurufMerah;
		public static int warnaLatar;
		public static int warnaNomerAyat;
	}
	
	/**
	 * Seting yang tetep hidup walau aktiviti dimusnahkan.
	 * Pastikan ga ada acuan ke aktiviti, supaya memori ga bocor.
	 */
	public static class penampungan {
		public static String renungan_nama = null;
		public static Date renungan_tanggalan = null;
	}
	
	//# 22nya harus siap di siapinKitab
	public static Edisi edisiAktif;
	public static Kitab kitabAktif;
	public static String edisiId;

	public static TukangDonlot tukangDonlot;
	
	static {
		int a = R.drawable.ambilwarna_cursor;
		if (a == 0) throw new RuntimeException(); // cuma mencegah project ambilwarna lupa dibuka
	}
	
	private static synchronized void siapinEdisi() {
		if (edisiAktif == null) {
			edisiAktif = getEdisiInternal();
		}
	}

	private static Edisi edisiInternal;
	public static synchronized Edisi getEdisiInternal() {
		if (edisiInternal == null) {
			BuildConfig c = BuildConfig.get(App.context);
			edisiInternal = new Edisi(new InternalPembaca(App.context, c.internalPrefix, c.internalJudul, new PembacaDecoder.Ascii()));
		}
		return edisiInternal;
	}

	public static synchronized void siapinKitab() {
		siapinEdisi();
		
		if (kitabAktif != null) return;
		kitabAktif = edisiAktif.getKitabPertama(); // nanti diset sama luar waktu init 
	}
	
	public static void bacaPengaturan() {
		Log.d(TAG, "bacaPengaturan mulai"); //$NON-NLS-1$

		//# atur ukuran huruf isi berdasarkan pengaturan
		{
			S.penerapan.ukuranHuruf2dp = Preferences.getFloat(R.string.pref_ukuranHuruf2_key, 17.f);
		}
		
		//# atur jenis huruf, termasuk boldnya
		{
			S.penerapan.jenisHuruf = U.typeface(Preferences.getString(R.string.pref_jenisHuruf_key, null));
			S.penerapan.tebalHuruf = Preferences.getBoolean(R.string.pref_boldHuruf_key, R.bool.pref_boldHuruf_default)? Typeface.BOLD: Typeface.NORMAL;
		}
		
		//# atur warna teks, latar, dan nomer ayat
		{
			S.penerapan.warnaHuruf = Preferences.getInt(R.string.pref_warnaHuruf_int_key, R.integer.pref_warnaHuruf_int_default);
			S.penerapan.warnaLatar = Preferences.getInt(R.string.pref_warnaLatar_int_key, R.integer.pref_warnaLatar_int_default); 
			S.penerapan.warnaNomerAyat = Preferences.getInt(R.string.pref_warnaNomerAyat_int_key, R.integer.pref_warnaNomerAyat_int_default);
			S.penerapan.warnaHurufMerah = hitungHurufMerah(S.penerapan.warnaHuruf, S.penerapan.warnaLatar);
		}
		
		Log.d(TAG, "bacaPengaturan selesai"); //$NON-NLS-1$
	}
	
	private static int hitungHurufMerah(int warnaHuruf, int warnaLatar) {
		// berdasar warna latar saja deh untuk kali ini
		int brt = Color.red(warnaLatar) * 30 + Color.green(warnaLatar) * 59 + Color.blue(warnaLatar) * 11;
		if (brt < 30 * 100) {
			return 0xffff9090;
		} else if (brt < 86 * 100) {
			return 0xffff5050;
		} else {
			return 0xffff0000;
		}
	}
	
	private static final String teksTakTersedia = "[?]"; //$NON-NLS-1$
	
	private static final String[] teksTakTersediaArray = {
		teksTakTersedia,
	};

	public static synchronized String muatSatuAyat(Edisi edisi, Kitab kitab, int pasal_1, int ayat_1) {
		if (kitab == null) {
			return teksTakTersedia;
		}
		String[] xayat = muatTeks(edisi, kitab, pasal_1, false, false);
		
		if (xayat == null) {
			return teksTakTersedia;
		}
		
		int ayat_0 = ayat_1 - 1;
		if (ayat_0 >= xayat.length) {
			return teksTakTersedia;
		}
		return xayat[ayat_0];
	}
	
	public static synchronized String[] muatTeks(Edisi edisi, Kitab kitab, int pasal_1) {
		if (kitab == null) {
			return teksTakTersediaArray;
		}
		String[] xayat = muatTeks(edisi, kitab, pasal_1, false, false);
		
		if (xayat == null) {
			return teksTakTersediaArray;
		}
		
		return xayat;
	}

	public static synchronized String muatTeksJanganPisahAyatHurufKecil(Edisi edisi, Kitab kitab, int pasal_1) {
		if (kitab == null) {
			return teksTakTersedia;
		}
		String[] xayat_denganSatuElemen = muatTeks(edisi, kitab, pasal_1, true, true);
		
		if (xayat_denganSatuElemen == null) {
			return teksTakTersedia;
		}
		
		return xayat_denganSatuElemen[0];
	}
	
	private static String[] muatTeks(Edisi edisi, Kitab kitab, int pasal_1, boolean janganPisahAyat, boolean hurufKecil) {
		return edisi.pembaca.muatTeks(kitab, pasal_1, janganPisahAyat, hurufKecil);
	}

	public static String alamat(Edisi edisi, int ari) {
		int kitabPos = Ari.toKitab(ari);
		int pasal_1 = Ari.toPasal(ari);
		int ayat_1 = Ari.toAyat(ari);
		
		StringBuilder hasil = new StringBuilder(40);
		Kitab k = edisi.getKitab(kitabPos);
		if (k == null) {
			hasil.append('[').append(kitabPos).append("] "); //$NON-NLS-1$
		} else {
			hasil.append(k.judul).append(' ');
		}
		
		hasil.append(pasal_1);
		if (ayat_1 != 0) {
			hasil.append(':').append(ayat_1);
		}
		return hasil.toString();
	}

	public static String alamat(Kitab kitab, int pasal_1) {
		return (kitab == null? "[?]": kitab.judul) + " " + pasal_1; //$NON-NLS-1$ //$NON-NLS-2$
	}

	public static String alamat(Kitab kitab, int pasal_1, int ayat_1) {
		return (kitab == null? "[?]": kitab.judul) + " " + pasal_1 + ":" + ayat_1;  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
	}
	
	/**
	 * @param handler Jangan null kalo mau dicek ulang 200ms kemudian. Harus null kalo jangan ulang lagi. 20110620 Uda ga dipake lagi.
	 */
	public static void terapkanPengaturanBahasa(final Handler handler, final int cobaLagi) {
		String bahasa = Preferences.getString(R.string.pref_bahasa_key, R.string.pref_bahasa_default);

		Locale locale;
		if ("DEFAULT".equals(bahasa)) { //$NON-NLS-1$
			locale = Locale.getDefault();
		} else {
			locale = new Locale(bahasa);
		}
		
		App.updateConfigurationWithLocale(App.context.getResources().getConfiguration(), locale);
	}

	private static float[] precomputedValues = null;
	private static void precomputeValues() {
		if (precomputedValues != null) return;
		precomputedValues = new float[] {
			App.context.getResources().getDimension(R.dimen.indenParagraf), // [0]
			App.context.getResources().getDimension(R.dimen.menjorokSatu), // [1]
			App.context.getResources().getDimension(R.dimen.menjorokDua), // [2]
		};
	}
	
	public static int getIndenParagraf() {
		precomputeValues();
		return (int) (precomputedValues[0] * Preferences.getFloat(R.string.pref_ukuranHuruf2_key, 17.f) / 17.f);
	}
	
	public static int getMenjorokSatu() {
		precomputeValues();
		return (int) (precomputedValues[1] * Preferences.getFloat(R.string.pref_ukuranHuruf2_key, 17.f) / 17.f);
	}
	
	public static int getMenjorokDua() {
		precomputeValues();
		return (int) (precomputedValues[2] * Preferences.getFloat(R.string.pref_ukuranHuruf2_key, 17.f) / 17.f);
	}
	
	public static InputStream openRaw(String name) {
		Resources resources = App.context.getResources();
		int resId = resources.getIdentifier(name, "raw", App.context.getPackageName()); //$NON-NLS-1$
		if (resId == 0) {
			return null;
		}
		return resources.openRawResource(resId);
	}
	
	private static InternalDb db;
	public static synchronized InternalDb getDb() {
		if (db == null) {
			db = new InternalDb(new InternalDbHelper(App.context));
		}
		
		return db;
	}
	
	public static String bikinUrlAyat(Kitab kitab, int pasal_1, int ayat_1) {
		BuildConfig c = BuildConfig.get(App.context);
		if (kitab.pos >= c.url_namaKitabStandar.length) {
			return null;
		}
		String calonKitab = c.url_namaKitabStandar[kitab.pos], calonPasal = String.valueOf(pasal_1), calonAyat = String.valueOf(ayat_1);
		for (String format: c.url_format.split(" ")) {
			if ("slash1".equals(format)) calonPasal = "/" + calonPasal;
			if ("slash2".equals(format)) calonAyat = "/" + calonAyat;
			if ("dot1".equals(format)) calonPasal = "." + calonPasal;
			if ("dot2".equals(format)) calonAyat = "." + calonAyat;
			if ("nospace0".equals(format)) calonKitab = calonKitab.replaceAll("\\s+", "");
		}
		return c.url_prefix + calonKitab + calonPasal + calonAyat;
	}
}
