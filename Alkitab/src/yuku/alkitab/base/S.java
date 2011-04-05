package yuku.alkitab.base;

import java.io.*;
import java.util.*;

import yuku.alkitab.R;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.renungan.TukangDonlot;
import yuku.alkitab.base.storage.Preferences;
import yuku.bintex.BintexReader;
import yuku.kirimfidbek.PengirimFidbek;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.Handler;
import android.util.Log;

public class S {
	private static final String TAG = S.class.getSimpleName();

	private static Context appContext;
	
	
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
	
	//# 33nya harus siap di siapinEdisi
	public static Edisi[] xedisi;
	public static Edisi edisiAktif;
	
	//# 22nya harus siap di siapinKitab
	public static Kitab kitabAktif;

	public static PengirimFidbek pengirimFidbek;
	public static TukangDonlot tukangDonlot;
	
	static {
		int a = R.drawable.ambilwarna_panah;
		if (a == 0) throw new RuntimeException(); // cuma mencegah project ambilwarna lupa dibuka
	}
	
	public static synchronized void siapinEdisi(Context context) {
		if (xedisi != null) return;
		
		long wmulai = System.currentTimeMillis();

		BintexReader in = new BintexReader(appContext.getResources().openRawResource(R.raw.edisi_index_bt));
		ArrayList<Edisi> xedisi = new ArrayList<Edisi>();

		try {
			while (true) {
				Edisi e = Edisi.baca(in);
				xedisi.add(e);
			}
		} catch (EOFException e) {
			// selesai baca
		} catch (IOException e) {
			Log.e(TAG, "ngaco baca edisi index!!!"); //$NON-NLS-1$
		}

		S.xedisi = xedisi.toArray(new Edisi[xedisi.size()]);
		S.edisiAktif = S.xedisi[0]; // TODO selalu pilih edisi pertama
		
		Log.d(TAG, "siapinEdisi butuh ms: " + (System.currentTimeMillis() - wmulai)); //$NON-NLS-1$
	}

	public static synchronized void siapinKitab(Context context) {
		if (S.xedisi == null || S.edisiAktif == null) {
			siapinEdisi(appContext);
		}
		if (S.edisiAktif.volatile_xkitab != null) return;

		Edisi edisi = S.edisiAktif;
		Log.d(TAG, "siapinKitab mulai dengan edisi: " + edisi.nama); //$NON-NLS-1$
		S.edisiAktif.volatile_xkitab = edisi.pembaca.bacaInfoKitab(appContext, edisi);
		S.kitabAktif = S.edisiAktif.volatile_xkitab[0]; // nanti diset sama luar 
		
		if (edisi.perikopAda != 0) {
			if (edisi.volatile_indexPerikop == null) {
				edisi.volatile_indexPerikop = edisi.pembaca.bacaIndexPerikop(appContext, edisi);
			}
		}
		
		Log.d(TAG, "siapinKitab selesai"); //$NON-NLS-1$
	}
	
	public static void bacaPengaturan(Context context) {
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

	public static synchronized String[] muatTeks(Edisi edisi, Kitab kitab, int pasal_1) {
		return muatTeks(edisi, kitab, pasal_1, false, false);
	}

	public static synchronized String muatTeksJanganPisahAyatHurufKecil(Edisi edisi, Kitab kitab, int pasal_1) {
		return muatTeks(edisi, kitab, pasal_1, true, true)[0];
	}
	
	private static String[] muatTeks(Edisi edisi, Kitab kitab, int pasal_1, boolean janganPisahAyat, boolean hurufKecil) {
		return edisi.pembaca.muatTeks(appContext, edisi, kitab, pasal_1, janganPisahAyat, hurufKecil);
	}

	/**
	 * @return berapa yang keisi
	 */
	public static synchronized int muatPerikop(Edisi edisi, int kitab, int pasal, int[] xari, Blok[] xblok, int max) {
		return edisi.pembaca.muatPerikop(appContext, edisi, kitab, pasal, xari, xblok, max);
	}

	public static synchronized void siapinPengirimFidbek(final Context context) {
		if (pengirimFidbek == null) {
			pengirimFidbek = new PengirimFidbek(context, getPreferences(context));
			pengirimFidbek.activateDefaultUncaughtExceptionHandler();
			pengirimFidbek.setOnSuccessListener(new PengirimFidbek.OnSuccessListener() {
				@Override
				public void onSuccess(final byte[] response) {
					Log.e(TAG, "KirimFidbek respon: " + new String(response, 0, response.length)); //$NON-NLS-1$
				}
			});
		}
	}

	public static SharedPreferences getPreferences(Context context) {
		return context.getSharedPreferences(context.getPackageName(), 0);
	}

	public static String alamat(Edisi edisi, int ari) {
		int kitab = Ari.toKitab(ari);
		int pasal_1 = Ari.toPasal(ari);
		int ayat_1 = Ari.toAyat(ari);
		
		StringBuilder hasil = new StringBuilder(40);
		if (kitab >= edisi.volatile_xkitab.length) {
			hasil.append('[').append(kitab).append("] "); //$NON-NLS-1$
		} else {
			hasil.append(edisi.volatile_xkitab[kitab].judul).append(' ');
		}
		
		hasil.append(pasal_1);
		if (ayat_1 != 0) {
			hasil.append(':').append(ayat_1);
		}
		return hasil.toString();
	}

	public static String alamat(Kitab kitab, int pasal_1) {
		return kitab.judul + " " + pasal_1; //$NON-NLS-1$
	}

	public static String alamat(Kitab kitab, int pasal_1, int ayat_1) {
		return kitab.judul + " " + pasal_1 + ":" + ayat_1;  //$NON-NLS-1$//$NON-NLS-2$
	}
	
	/**
	 * @param handler Jangan null kalo mau dicek ulang 200ms kemudian. Harus null kalo jangan ulang lagi.
	 */
	public static void terapkanPengaturanBahasa(final Context context, final Handler handler, final int cobaLagi) {
		String bahasa = Preferences.getString(R.string.pref_bahasa_key, R.string.pref_bahasa_default);

		Locale locale;
		if ("DEFAULT".equals(bahasa)) { //$NON-NLS-1$
			locale = Locale.getDefault();
		} else {
			locale = new Locale(bahasa);
		}
		
		Configuration config1 = context.getResources().getConfiguration();
		if (locale.getLanguage() != null && locale.getLanguage().equals(config1.locale.getLanguage())) {
			// ga ada perubahan, biarkan.
		} else {
			Configuration config2 = new Configuration();
			config2.locale = locale;
			if (handler != null) {
				Log.d(TAG, "(Handler ga null) Update locale dari " + config1.locale.toString() + " ke " + config2.locale.toString()); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				Log.d(TAG, "(Handler null) Update locale dari " + config1.locale.toString() + " ke " + config2.locale.toString());  //$NON-NLS-1$//$NON-NLS-2$
			}
			context.getResources().updateConfiguration(config2, null);
		}
		
		if (handler != null) {
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (cobaLagi == 0) {
						terapkanPengaturanBahasa(context, null, 0);
					} else {
						terapkanPengaturanBahasa(context, handler, cobaLagi - 1);
					}
				}
			}, 200);
		}
	}

	private static float[] precomputedValues = null;
	private static void precomputeValues() {
		if (precomputedValues != null) return;
		precomputedValues = new float[] {
			appContext.getResources().getDimension(R.dimen.indenParagraf), // [0]
			appContext.getResources().getDimension(R.dimen.menjorokSatu), // [1]
			appContext.getResources().getDimension(R.dimen.menjorokDua), // [2]
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
	
	public static void setAppContext(Context appContext) {
		S.appContext = appContext;
	}

	public static Context getAppContext() {
		return appContext;
	}
}
