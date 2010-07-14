package yuku.alkitab;

import java.lang.reflect.*;
import java.util.*;

import yuku.alkitab.model.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;

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

	public static void setCacheColorHintMaksa(ListView lv, int warna) {
		int versi = Integer.parseInt(Build.VERSION.SDK);
		
		// selain FROYO, oke deh.
		if (versi != Build.VERSION_CODES.FROYO) {
			lv.setCacheColorHint(warna);
			return;
		}
		
		try {
			Field mCacheColorHint_field = AbsListView.class.getDeclaredField("mCacheColorHint");
			mCacheColorHint_field.setAccessible(true);
			mCacheColorHint_field.setInt(lv, warna);
			
			int count = lv.getChildCount();
			for (int i = 0; i < count; i++) {
				lv.getChildAt(i).setDrawingCacheBackgroundColor(warna);
			}
			
			Field mRecycler_field = AbsListView.class.getDeclaredField("mRecycler");
			mRecycler_field.setAccessible(true);
			Object mRecycler = mRecycler_field.get(lv);
			
			{
				Field mViewTypeCount_field = mRecycler.getClass().getDeclaredField("mViewTypeCount");
				mViewTypeCount_field.setAccessible(true);
				int mViewTypeCount = mViewTypeCount_field.getInt(mRecycler);
				
				Field mCurrentScrap_field = mRecycler.getClass().getDeclaredField("mCurrentScrap");
				mCurrentScrap_field.setAccessible(true);
				ArrayList<View> mCurrentScrap = (ArrayList<View>) mCurrentScrap_field.get(mRecycler);
				
				Field mActiveViews_field = mRecycler.getClass().getDeclaredField("mActiveViews");
				mActiveViews_field.setAccessible(true);
				View[] mActiveViews = (View[]) mActiveViews_field.get(mRecycler);
				
				Field mScrapViews_field = mRecycler.getClass().getDeclaredField("mScrapViews");
				mScrapViews_field.setAccessible(true);
				ArrayList<View>[] mScrapViews = (ArrayList<View>[]) mScrapViews_field.get(mRecycler);
				
				if (mViewTypeCount == 1) {
	                final ArrayList<View> scrap = mCurrentScrap;
	                final int scrapCount = scrap.size();
	                for (int i = 0; i < scrapCount; i++) {
	                    scrap.get(i).setDrawingCacheBackgroundColor(warna);
	                }
	            } else {
	                final int typeCount = mViewTypeCount;
	                for (int i = 0; i < typeCount; i++) {
	                    final ArrayList<View> scrap = mScrapViews[i];
	                    final int scrapCount = scrap.size();
	                    for (int j = 0; j < scrapCount; j++) {
	                        scrap.get(j).setDrawingCacheBackgroundColor(warna);
	                    }
	                }
	            }
	            // Just in case this is called during a layout pass
	            final View[] activeViews = mActiveViews;
	            final int count1 = activeViews.length;
	            for (int i = 0; i < count1; ++i) {
	                final View victim = activeViews[i];
	                if (victim != null) {
	                    victim.setDrawingCacheBackgroundColor(warna);
	                }
	            }
			}
			
			Log.d("alki", "berhasil setCacheColorHint dengan maksa");
		} catch (Exception e) {
			Log.w("alki", "pake reflection gagal!", e);

			// ya uda nyerah.
			lv.setCacheColorHint(warna);
		}
	}
}
