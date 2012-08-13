package yuku.alkitab.base.util;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

public class History {
	private static final String SEJARAH_AWALAN = "sejarah/"; //$NON-NLS-1$
	private static final String TAG = History.class.getSimpleName();
	private static final int MAX = 20;
	
	int[] xari;
	int n;
	
	public History(SharedPreferences preferences) {
		n = 0;
		xari = new int[MAX];
		
		try {
			int n = preferences.getInt(SEJARAH_AWALAN + "n", 0); //$NON-NLS-1$
			for (int i = 0; i < n; i++) {
				xari[i] = preferences.getInt(SEJARAH_AWALAN + i, 0);
			}
			this.n = n;
		} catch (Exception e) {
			Log.e(TAG, "eror waktu muat preferences sejarah", e); //$NON-NLS-1$
		}
	}

	public void simpan(Editor editor) {
		editor.putInt(SEJARAH_AWALAN + "n", n); //$NON-NLS-1$
		for (int i = 0; i < n; i++) {
			editor.putInt(SEJARAH_AWALAN + i, xari[i]);
		}
	}
	
	public void add(int ari) {
		// cari dulu di yang lama, ada ari ga
		for (int i = 0; i < n; i++) {
			if (xari[i] == ari) {
				// ADA. Buang ini, majukan satu semua
				System.arraycopy(xari, 0, xari, 1, i);
				xari[0] = ari;
				return; // n tidak berubah
			}
		}
		
		// Ga ada ari. Max kah?
		System.arraycopy(xari, 0, xari, 1, MAX - 1);
		xari[0] = ari;
		if (n < MAX) {
			n++;
		}
	}

	public int getN() {
		return n;
	}

	public int getAri(int i) {
		if (i >= n) {
			return 0;
		}
		return xari[i];
	}
}
