package yuku.alkitab.base;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.Log;

import java.io.InputStream;
import java.util.Date;
import java.util.Locale;

import yuku.alkitab.R;
import yuku.alkitab.base.config.BuildConfig;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Edisi;
import yuku.alkitab.base.model.Kitab;
import yuku.alkitab.base.renungan.TukangDonlot;
import yuku.alkitab.base.storage.InternalDb;
import yuku.alkitab.base.storage.InternalDbHelper;
import yuku.alkitab.base.storage.InternalPembaca;
import yuku.alkitab.base.storage.PembacaDecoder;
import yuku.alkitab.base.storage.Preferences;
import yuku.alkitab.base.storage.SongDb;
import yuku.alkitab.base.storage.SongDbHelper;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.base.util.IntArrayList;


public class S {
	static final String TAG = S.class.getSimpleName();

	/**
	 * penerapan dari pengaturan
	 */
	public static class penerapan {
		/** dalam dp */
		public static float ukuranHuruf2dp;
		
		public static Typeface jenisHuruf;
		public static float lineSpacingMult;
		public static int tebalHuruf;
		
		public static int warnaHuruf; 
		public static int warnaHurufMerah;
		public static int warnaLatar;
		public static int warnaNomerAyat;
		
		// semua di bawah dalam px
		public static int jarakIndenParagraf;
		public static int jarakMenjorokSatu;
		public static int jarakMenjorokDua;
		public static int jarakMenjorokTiga;
		public static int jarakMenjorokEmpat;
		public static int jarakMenjorokExtra;

	}
	
	/**
	 * Seting yang tetep hidup walau aktiviti dimusnahkan.
	 * Pastikan ga ada acuan ke aktiviti, supaya memori ga bocor.
	 */
	public static class penampungan {
		public static String devotion_name = null;
		public static Date renungan_tanggalan = null;
		public static int renungan_skrol = 0;
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

	public static synchronized void prepareBook() {
		siapinEdisi();
		
		if (kitabAktif != null) return;
		kitabAktif = edisiAktif.getKitabPertama(); // nanti diset sama luar waktu init 
	}
	
	public static void calculateAppliedValuesBasedOnPreferences() {
		//# atur ukuran huruf isi berdasarkan pengaturan
		{
			penerapan.ukuranHuruf2dp = Preferences.getFloat(R.string.pref_ukuranHuruf2_key, 17.f);
		}
		
		//# atur jenis huruf, termasuk boldnya
		{
			penerapan.jenisHuruf = FontManager.typeface(Preferences.getString(R.string.pref_jenisHuruf_key, null));
			penerapan.lineSpacingMult = Preferences.getFloat(R.string.pref_lineSpacingMult_key, 1.f);
			penerapan.tebalHuruf = Preferences.getBoolean(R.string.pref_boldHuruf_key, R.bool.pref_boldHuruf_default)? Typeface.BOLD: Typeface.NORMAL;
		}
		
		//# atur warna teks, latar, dan nomer ayat
		{
			penerapan.warnaHuruf = Preferences.getInt(R.string.pref_warnaHuruf_int_key, R.integer.pref_warnaHuruf_int_default);
			penerapan.warnaLatar = Preferences.getInt(R.string.pref_warnaLatar_int_key, R.integer.pref_warnaLatar_int_default); 
			penerapan.warnaNomerAyat = Preferences.getInt(R.string.pref_warnaNomerAyat_int_key, R.integer.pref_warnaNomerAyat_int_default);
			penerapan.warnaHurufMerah = hitungHurufMerah(S.penerapan.warnaHuruf, S.penerapan.warnaLatar);
		}
		
		Resources res = App.context.getResources();
		
		float skalaBerdasarUkuranHuruf = penerapan.ukuranHuruf2dp / 17.f;
		penerapan.jarakIndenParagraf = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.indenParagraf) + 0.5f);
		penerapan.jarakMenjorokSatu = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.menjorokSatu) + 0.5f);
		penerapan.jarakMenjorokDua = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.menjorokDua) + 0.5f);
		penerapan.jarakMenjorokTiga = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.menjorokTiga) + 0.5f);
		penerapan.jarakMenjorokEmpat = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.menjorokEmpat) + 0.5f);
		penerapan.jarakMenjorokExtra = (int) (skalaBerdasarUkuranHuruf * res.getDimensionPixelOffset(R.dimen.menjorokExtra) + 0.5f);
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
	
	public static synchronized String muatSatuAyat(Edisi edisi, int ari) {
		return muatSatuAyat(edisi, edisi.getKitab(Ari.toKitab(ari)), Ari.toPasal(ari), Ari.toAyat(ari));
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
	
	public static CharSequence alamat(Kitab kitab, int pasal_1, IntArrayList xayat_1) {
		StringBuilder sb = new StringBuilder(kitab == null? "[?]": kitab.judul); //$NON-NLS-1$
		sb.append(' ').append(pasal_1);
		if (xayat_1 == null || xayat_1.size() == 0) {
			return sb;
		}
		sb.append(':');
		tulisAyatRange(xayat_1, sb);
		return sb;
	}
	
	public static void tulisAyatRange(IntArrayList xayat_1, StringBuilder sb) {
		int origLen = sb.length();
		int lastAyat_1 = 0;
		int awalAyat_1 = 0;
		
		for (int i = 0; i < xayat_1.size(); i++) {
			int ayat_1 = xayat_1.get(i);
			
			if (lastAyat_1 == 0) {
				// blum ada, diam dulu aja
			} else if (lastAyat_1 == ayat_1 - 1) {
				// masih terusan, simpen awalnya
				if (awalAyat_1 == 0) awalAyat_1 = lastAyat_1;
			} else {
				// abis loncat
				if (awalAyat_1 != 0) {
					sb.append(origLen == sb.length()? "": ", ").append(awalAyat_1).append('-').append(lastAyat_1); //$NON-NLS-1$ //$NON-NLS-2$
					awalAyat_1 = 0;
				} else {
					sb.append(origLen == sb.length()? "": ", ").append(lastAyat_1); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			
			lastAyat_1 = xayat_1.get(i);
		}
		
		// penghabisan
		if (awalAyat_1 != 0) {
			sb.append(origLen == sb.length()? "": ", ").append(awalAyat_1).append('-').append(lastAyat_1);  //$NON-NLS-1$//$NON-NLS-2$
			awalAyat_1 = 0; // ga perlu, tapi biar konsisten aja dengan atas
		} else {
			sb.append(origLen == sb.length()? "": ", ").append(lastAyat_1); //$NON-NLS-1$ //$NON-NLS-2$
		}
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
	
	private static SongDb songDb;
	public static synchronized SongDb getSongDb() {
		if (songDb == null) {
			songDb = new SongDb(new SongDbHelper());
		}
		
		return songDb;
	}
	
	/**
	 * Jika ayat_1_range adalah null, ayat akan diabaikan (jadi cuma kitab dan pasal).
	 */
	public static String bikinUrlAyat(Kitab kitab, int pasal_1, String ayat_1_range) {
		BuildConfig c = BuildConfig.get(App.context);
		if (kitab.pos >= c.url_namaKitabStandar.length) {
			return null;
		}
		String calonKitab = c.url_namaKitabStandar[kitab.pos], calonPasal = String.valueOf(pasal_1), calonAyat = ayat_1_range;
		for (String format: c.url_format.split(" ")) { //$NON-NLS-1$
			if ("slash1".equals(format)) calonPasal = "/" + calonPasal; //$NON-NLS-1$ //$NON-NLS-2$
			if ("slash2".equals(format)) calonAyat = "/" + calonAyat; //$NON-NLS-1$ //$NON-NLS-2$
			if ("dot1".equals(format)) calonPasal = "." + calonPasal; //$NON-NLS-1$ //$NON-NLS-2$
			if ("dot2".equals(format)) calonAyat = "." + calonAyat; //$NON-NLS-1$ //$NON-NLS-2$
			if ("nospace0".equals(format)) calonKitab = calonKitab.replaceAll("\\s+", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		return c.url_prefix + calonKitab + calonPasal + (ayat_1_range == null? "": calonAyat); //$NON-NLS-1$
	}
	
	private static PackageInfo packageInfo;
	
	private static void initPackageInfo() {
		if (packageInfo == null) {
			try {
				packageInfo = App.context.getPackageManager().getPackageInfo(App.context.getPackageName(), 0);
			} catch (NameNotFoundException e) {
				Log.e(TAG, "PackageInfo ngaco", e); //$NON-NLS-1$
			}
		}
	}
	
	public static String getVersionName() {
		initPackageInfo();
		return packageInfo.versionName;
	}
	
	public static int getVersionCode() {
		initPackageInfo();
		return packageInfo.versionCode;
	}
}
